package com.squemadylan.wolfcha.domain

/**
 * Cleans LLM speech output for in-game chat display.
 */
object SpeechSanitizer {

    private val REASONING_PATTERNS = listOf(
        thinkBlockPattern("think"),
        thinkBlockPattern("redacted_reasoning"),
        Regex("(?is)<thinking>[\\s\\S]*?</thinking>"),
        Regex("(?is)\\[think\\][\\s\\S]*?\\[/think\\]"),
        Regex("(?is)```think\\s*[\\s\\S]*?```"),
        Regex("(?is)^思考[:：][\\s\\S]*?(?=\\n\\n|$)")
    )

    fun sanitize(raw: String): String {
        var text = raw.trim()

        REASONING_PATTERNS.forEach { pattern ->
            text = text.replace(pattern, "")
        }

        // Strip code fences
        text = text.replace(Regex("^```[a-zA-Z0-9_-]*\\s*"), "")
        text = text.replace(Regex("\\s*```\\s*$"), "")

        // Strip surrounding quotes
        text = text.trim('"', '\'', '\u201c', '\u201d', '\u2018', '\u2019')

        // If model outputs bilingual text, keep the Chinese portion
        text = preferChineseSegment(text)

        text = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

        if (text.length > 160) {
            text = text.substring(0, 160) + "\u2026"
        }
        return text
    }

    fun isAcceptableChineseSpeech(text: String): Boolean {
        if (text.isBlank()) return false
        val chineseCount = text.count { it in '\u4e00'..'\u9fff' }
        val latinCount = text.count { it.isLetter() && it.code < 128 }
        return chineseCount >= 4 && chineseCount >= latinCount
    }

    private fun preferChineseSegment(text: String): String {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return text

        val chineseLines = lines.filter { line ->
            val chinese = line.count { it in '\u4e00'..'\u9fff' }
            val latin = line.count { it.isLetter() && it.code < 128 }
            chinese >= 2 && chinese >= latin
        }
        if (chineseLines.isNotEmpty()) {
            return chineseLines.joinToString(" ")
        }

        val matcher = Regex("[\u4e00-\u9fff\uFF0C\u3002\uFF01\uFF1F\u3001\uFF1B\uFF1A\u201c\u201d\u2018\u2019\uFF08\uFF09\u3010\u3011\u300a\u300b\u2026\u2014\\s]+")
            .findAll(text)
            .map { it.value.trim() }
        val joined = matcher.filter { it.length >= 4 }.joinToString("")
        return joined.ifBlank { text }
    }

    private fun thinkBlockPattern(tag: String): Regex {
        return Regex("(?is)<$tag\\b[\\s\\S]*?</$tag>")
    }
}

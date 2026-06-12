package com.squemadylan.wolfcha.data.remote

import com.squemadylan.wolfcha.data.model.AiPersonaProfile
import com.squemadylan.wolfcha.data.model.LlmConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uses the configured LLM to generate AI player personas.
 */
class PersonaGenerator(
    private val llmService: LlmService = LlmService()
) {

    sealed class Result {
        data class Success(val profiles: List<AiPersonaProfile>) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun generateOne(config: LlmConfig, index: Int): Result {
        return generateBatch(config, count = 1, startIndex = index)
    }

    suspend fun generateAll(config: LlmConfig, count: Int): Result {
        return generateBatch(config, count = count, startIndex = 0)
    }

    private suspend fun generateBatch(
        config: LlmConfig,
        count: Int,
        startIndex: Int
    ): Result {
        if (!config.isReady) {
            return Result.Failure("请先在设置中配置并启用大模型接口")
        }

        val system = """
            你是狼人杀游戏的角色设计师。请为 AI 玩家生成中文人设，适合社交推理桌游。
            要求：
            1. 全部字段使用中文（MBTI 类型除外，使用四字母如 INTJ）。
            2. 名字要有辨识度，避免张三李四王五这类模板化姓名。
            3. 背景故事 30-60 字，有具体职业或生活细节。
            4. 性格标签简洁，如「冷静分析型」「话痨干扰型」。
            5. 只输出 JSON 数组，不要 markdown，不要解释。
        """.trimIndent()

        val user = """
            请生成 $count 个不同的 AI 玩家人设，JSON 数组格式，每个对象字段：
            - displayName: 中文姓名
            - background: 人物背景
            - mbti: 16型人格四字母
            - styleLabel: 性格标签
            - gender: male 或 female
            - age: 18-45 的整数
            
            从第 ${startIndex + 1} 个角色开始编号即可，共 $count 个。
        """.trimIndent()

        val chatResult = llmService.chat(
            config = config.copy(maxTokens = maxOf(config.maxTokens, 800)),
            messages = listOf(
                LlmService.Message("system", system),
                LlmService.Message("user", user)
            )
        )

        return when (chatResult) {
            is LlmService.Result.Success -> {
                val profiles = parseProfiles(chatResult.content, count, startIndex)
                if (profiles.isEmpty()) {
                    Result.Failure("模型返回格式无法解析，请重试")
                } else {
                    Result.Success(profiles)
                }
            }
            is LlmService.Result.Failure -> Result.Failure(chatResult.message)
        }
    }

    private fun parseProfiles(raw: String, expectedCount: Int, startIndex: Int): List<AiPersonaProfile> {
        val jsonText = extractJsonArray(raw) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonText)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AiPersonaProfile(
                    displayName = obj.optString("displayName").ifBlank {
                        AiPersonaProfile.DEFAULT_NAMES.getOrElse(startIndex + i) { "AI${startIndex + i + 1}" }
                    },
                    background = obj.optString("background", ""),
                    mbti = obj.optString("mbti", "INTJ").uppercase().take(4),
                    styleLabel = obj.optString("styleLabel", "冷静分析型"),
                    gender = obj.optString("gender", "male").lowercase(),
                    age = obj.optInt("age", 25).coerceIn(16, 60)
                )
            }.take(expectedCount)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String? {
        var cleaned = text.trim()
        cleaned = cleaned.replace(Regex("```[a-zA-Z]*\\s*"), "").replace("```", "").trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1)
        }
        return null
    }
}

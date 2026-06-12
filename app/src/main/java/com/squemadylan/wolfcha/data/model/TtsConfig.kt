package com.squemadylan.wolfcha.data.model

data class TtsConfig(
    val appId: String = DEFAULT_APP_ID,
    val accessToken: String = "",
    val narratorVoice: String = NARRATOR_VOICE,
    val maleVoice: String = DEFAULT_MALE_VOICE,
    val femaleVoice: String = DEFAULT_FEMALE_VOICE,
    val maleRandom: Boolean = true,
    val femaleRandom: Boolean = true,
    val narratorEnabled: Boolean = true,
    val playerSpeechEnabled: Boolean = true,
    val enabled: Boolean = false
) {
    val isReady: Boolean
        get() = enabled && appId.isNotBlank() && accessToken.isNotBlank()

    companion object {
        const val DEFAULT_APP_ID = "2216356170"
        const val NARRATOR_VOICE = "BV700_streaming"
        const val DEFAULT_MALE_VOICE = "BV002_streaming"
        const val DEFAULT_FEMALE_VOICE = "BV001_streaming"
    }
}

enum class VoiceGender {
    MALE, FEMALE
}

data class VolcVoiceOption(
    val voiceType: String,
    val displayName: String,
    val gender: VoiceGender
)

object VolcVoiceCatalog {
    val narratorVoice = VolcVoiceOption(TtsConfig.NARRATOR_VOICE, "灿灿（旁白）", VoiceGender.FEMALE)

    val maleVoices = listOf(
        VolcVoiceOption("BV002_streaming", "通用男声", VoiceGender.MALE),
        VolcVoiceOption("BV701_streaming", "擎苍", VoiceGender.MALE),
        VolcVoiceOption("BV102_streaming", "儒雅青年", VoiceGender.MALE),
        VolcVoiceOption("BV119_streaming", "通用赘婿", VoiceGender.MALE),
        VolcVoiceOption("BV033_streaming", "温柔小哥", VoiceGender.MALE),
        VolcVoiceOption("BV056_streaming", "阳光男声", VoiceGender.MALE),
        VolcVoiceOption("BV705_streaming", "炀炀", VoiceGender.MALE),
        VolcVoiceOption("BV019_streaming", "重庆小伙", VoiceGender.MALE),
        VolcVoiceOption("BV021_streaming", "东北老铁", VoiceGender.MALE),
        VolcVoiceOption("BV213_streaming", "广西表哥", VoiceGender.MALE)
    )

    val femaleVoices = listOf(
        VolcVoiceOption("BV001_streaming", "通用女声", VoiceGender.FEMALE),
        VolcVoiceOption("BV700_streaming", "灿灿", VoiceGender.FEMALE),
        VolcVoiceOption("BV005_streaming", "活泼女声", VoiceGender.FEMALE),
        VolcVoiceOption("BV113_streaming", "甜宠少御", VoiceGender.FEMALE),
        VolcVoiceOption("BV115_streaming", "古风少御", VoiceGender.FEMALE),
        VolcVoiceOption("BV007_streaming", "亲切女声", VoiceGender.FEMALE),
        VolcVoiceOption("BV034_streaming", "知性姐姐", VoiceGender.FEMALE)
    )

    fun resolvePlayerVoice(gender: String, config: TtsConfig): String {
        val isFemale = gender.equals("female", ignoreCase = true)
        return if (isFemale) {
            if (config.femaleRandom) femaleVoices.random().voiceType else config.femaleVoice
        } else {
            if (config.maleRandom) maleVoices.random().voiceType else config.maleVoice
        }
    }
}

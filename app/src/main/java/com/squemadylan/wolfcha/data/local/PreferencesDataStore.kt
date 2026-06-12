package com.squemadylan.wolfcha.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squemadylan.wolfcha.data.model.AiPersonaProfile
import com.squemadylan.wolfcha.data.model.DifficultyLevel
import com.squemadylan.wolfcha.data.model.GameConfig
import com.squemadylan.wolfcha.data.model.LlmConfig
import com.squemadylan.wolfcha.data.model.LlmProvider
import com.squemadylan.wolfcha.data.model.TtsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wolfcha_preferences")

data class AppGamePreferences(
    val playerName: String = "玩家",
    val playerCount: Int = 10,
    val difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
    val aiPersonaPool: List<AiPersonaProfile> = AiPersonaProfile.defaultPool(9),
    val llmConfig: LlmConfig = LlmConfig(),
    val ttsConfig: TtsConfig = TtsConfig()
)

/**
 * Local data source using DataStore Preferences.
 */
class PreferencesDataStore(private val context: Context) {

    companion object {
        val PLAYER_NAME_KEY = stringPreferencesKey("player_name")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        val DIFFICULTY_KEY = stringPreferencesKey("difficulty")
        val AI_PERSONA_POOL_KEY = stringPreferencesKey("ai_persona_pool")

        val LLM_ENABLED_KEY = booleanPreferencesKey("llm_enabled")
        val LLM_PROVIDER_KEY = stringPreferencesKey("llm_provider")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_BASE_URL_KEY = stringPreferencesKey("llm_base_url")
        val LLM_MODEL_KEY = stringPreferencesKey("llm_model")
        val LLM_TEMPERATURE_KEY = floatPreferencesKey("llm_temperature")
        val LLM_MAX_TOKENS_KEY = intPreferencesKey("llm_max_tokens")

        val TTS_ENABLED_KEY = booleanPreferencesKey("tts_enabled")
        val TTS_APP_ID_KEY = stringPreferencesKey("tts_app_id")
        val TTS_ACCESS_TOKEN_KEY = stringPreferencesKey("tts_access_token")
        val TTS_MALE_VOICE_KEY = stringPreferencesKey("tts_male_voice")
        val TTS_FEMALE_VOICE_KEY = stringPreferencesKey("tts_female_voice")
        val TTS_MALE_RANDOM_KEY = booleanPreferencesKey("tts_male_random")
        val TTS_FEMALE_RANDOM_KEY = booleanPreferencesKey("tts_female_random")
        val TTS_NARRATOR_ENABLED_KEY = booleanPreferencesKey("tts_narrator_enabled")
        val TTS_PLAYER_SPEECH_ENABLED_KEY = booleanPreferencesKey("tts_player_speech_enabled")
    }

    val appGamePreferences: Flow<AppGamePreferences> = context.dataStore.data
        .map { preferences ->
            val playerCount = (preferences[PLAYER_COUNT_KEY] ?: 10)
                .coerceIn(GameConfig.MIN_PLAYERS, GameConfig.MAX_PLAYERS)
            val poolSize = (playerCount - 1).coerceAtLeast(0)
            val difficulty = runCatching {
                DifficultyLevel.valueOf(preferences[DIFFICULTY_KEY] ?: DifficultyLevel.NORMAL.name)
            }.getOrDefault(DifficultyLevel.NORMAL)

            val llmEnabled = preferences[LLM_ENABLED_KEY] ?: false

            val providerName = preferences[LLM_PROVIDER_KEY]
            val provider = runCatching {
                LlmProvider.valueOf(providerName ?: LlmProvider.OPENAI_COMPATIBLE.name)
            }.getOrDefault(LlmProvider.OPENAI_COMPATIBLE)

            AppGamePreferences(
                playerName = preferences[PLAYER_NAME_KEY] ?: "玩家",
                playerCount = playerCount,
                difficulty = difficulty,
                aiPersonaPool = AiPersonaProfile.decodePool(
                    preferences[AI_PERSONA_POOL_KEY],
                    poolSize
                ),
                llmConfig = LlmConfig(
                    provider = provider,
                    apiKey = preferences[LLM_API_KEY] ?: "",
                    baseUrl = preferences[LLM_BASE_URL_KEY] ?: provider.defaultBaseUrl,
                    model = preferences[LLM_MODEL_KEY] ?: provider.defaultModel,
                    temperature = preferences[LLM_TEMPERATURE_KEY] ?: 0.7f,
                    maxTokens = preferences[LLM_MAX_TOKENS_KEY] ?: 400,
                    enabled = llmEnabled
                ),
                ttsConfig = TtsConfig(
                    appId = preferences[TTS_APP_ID_KEY] ?: TtsConfig.DEFAULT_APP_ID,
                    accessToken = preferences[TTS_ACCESS_TOKEN_KEY] ?: "",
                    maleVoice = preferences[TTS_MALE_VOICE_KEY] ?: TtsConfig.DEFAULT_MALE_VOICE,
                    femaleVoice = preferences[TTS_FEMALE_VOICE_KEY] ?: TtsConfig.DEFAULT_FEMALE_VOICE,
                    maleRandom = preferences[TTS_MALE_RANDOM_KEY] ?: true,
                    femaleRandom = preferences[TTS_FEMALE_RANDOM_KEY] ?: true,
                    narratorEnabled = preferences[TTS_NARRATOR_ENABLED_KEY] ?: true,
                    playerSpeechEnabled = preferences[TTS_PLAYER_SPEECH_ENABLED_KEY] ?: true,
                    enabled = preferences[TTS_ENABLED_KEY] ?: false
                )
            )
        }

    val playerName: Flow<String> = appGamePreferences.map { it.playerName }
    val playerCount: Flow<Int> = appGamePreferences.map { it.playerCount }
    val difficulty: Flow<DifficultyLevel> = appGamePreferences.map { it.difficulty }
    val aiPersonaPool: Flow<List<AiPersonaProfile>> = appGamePreferences.map { it.aiPersonaPool }
    val llmConfig: Flow<LlmConfig> = appGamePreferences.map { it.llmConfig }
    val ttsConfig: Flow<TtsConfig> = appGamePreferences.map { it.ttsConfig }

    suspend fun savePlayerName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PLAYER_NAME_KEY] = name
        }
    }

    suspend fun savePlayerCount(count: Int) {
        val safeCount = count.coerceIn(GameConfig.MIN_PLAYERS, GameConfig.MAX_PLAYERS)
        context.dataStore.edit { preferences ->
            preferences[PLAYER_COUNT_KEY] = safeCount
            val poolSize = (safeCount - 1).coerceAtLeast(0)
            val currentPool = AiPersonaProfile.decodePool(
                preferences[AI_PERSONA_POOL_KEY],
                poolSize
            )
            preferences[AI_PERSONA_POOL_KEY] = AiPersonaProfile.encodePool(
                AiPersonaProfile.resizePool(currentPool, poolSize)
            )
        }
    }

    suspend fun saveDifficulty(level: DifficultyLevel) {
        context.dataStore.edit { preferences ->
            preferences[DIFFICULTY_KEY] = level.name
        }
    }

    suspend fun saveAiPersonaPool(pool: List<AiPersonaProfile>) {
        context.dataStore.edit { preferences ->
            preferences[AI_PERSONA_POOL_KEY] = AiPersonaProfile.encodePool(pool)
        }
    }

    suspend fun saveLlmConfig(config: LlmConfig) {
        context.dataStore.edit { preferences ->
            preferences[LLM_ENABLED_KEY] = config.enabled
            preferences[LLM_PROVIDER_KEY] = config.provider.name
            preferences[LLM_API_KEY] = config.apiKey
            preferences[LLM_BASE_URL_KEY] = config.baseUrl
            preferences[LLM_MODEL_KEY] = config.model
            preferences[LLM_TEMPERATURE_KEY] = config.temperature
            preferences[LLM_MAX_TOKENS_KEY] = config.maxTokens
        }
    }

    suspend fun saveTtsConfig(config: TtsConfig) {
        context.dataStore.edit { preferences ->
            preferences[TTS_ENABLED_KEY] = config.enabled
            preferences[TTS_APP_ID_KEY] = config.appId.ifBlank { TtsConfig.DEFAULT_APP_ID }
            preferences[TTS_ACCESS_TOKEN_KEY] = config.accessToken
            preferences[TTS_MALE_VOICE_KEY] = config.maleVoice
            preferences[TTS_FEMALE_VOICE_KEY] = config.femaleVoice
            preferences[TTS_MALE_RANDOM_KEY] = config.maleRandom
            preferences[TTS_FEMALE_RANDOM_KEY] = config.femaleRandom
            preferences[TTS_NARRATOR_ENABLED_KEY] = config.narratorEnabled
            preferences[TTS_PLAYER_SPEECH_ENABLED_KEY] = config.playerSpeechEnabled
        }
    }
}

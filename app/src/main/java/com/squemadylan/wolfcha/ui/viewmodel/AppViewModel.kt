package com.squemadylan.wolfcha.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squemadylan.wolfcha.data.local.AppGamePreferences
import com.squemadylan.wolfcha.data.local.PreferencesDataStore
import com.squemadylan.wolfcha.data.model.AiPersonaProfile
import com.squemadylan.wolfcha.data.model.AvatarCatalog
import com.squemadylan.wolfcha.data.model.DifficultyLevel
import com.squemadylan.wolfcha.data.model.GameConfig
import com.squemadylan.wolfcha.data.model.GameSettings
import com.squemadylan.wolfcha.data.model.LlmConfig
import com.squemadylan.wolfcha.data.remote.PersonaGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    application: Application,
    private val preferencesDataStore: PreferencesDataStore
) : AndroidViewModel(application) {

    private val personaGenerator = PersonaGenerator()

    val preferences: StateFlow<AppGamePreferences> = preferencesDataStore.appGamePreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppGamePreferences()
        )

    private val _personaMessage = MutableStateFlow<String?>(null)
    val personaMessage: StateFlow<String?> = _personaMessage.asStateFlow()

    private val _isGeneratingPersona = MutableStateFlow(false)
    val isGeneratingPersona: StateFlow<Boolean> = _isGeneratingPersona.asStateFlow()

    fun buildGameSettings(): GameSettings {
        val prefs = preferences.value
        return GameSettings(
            playerCount = prefs.playerCount,
            difficulty = prefs.difficulty,
            humanName = prefs.playerName.ifBlank { "玩家" },
            aiPersonaPool = prefs.aiPersonaPool
        )
    }

    fun updatePlayerName(name: String) {
        viewModelScope.launch {
            preferencesDataStore.savePlayerName(name.trim())
        }
    }

    fun updatePlayerCount(count: Int) {
        viewModelScope.launch {
            preferencesDataStore.savePlayerCount(count)
        }
    }

    fun updateDifficulty(level: DifficultyLevel) {
        viewModelScope.launch {
            preferencesDataStore.saveDifficulty(level)
        }
    }

    fun updatePersonaAt(index: Int, profile: AiPersonaProfile) {
        viewModelScope.launch {
            val pool = preferences.value.aiPersonaPool.toMutableList()
            if (index !in pool.indices) return@launch
            pool[index] = profile
            preferencesDataStore.saveAiPersonaPool(pool)
        }
    }

    fun randomizeAvatarAt(index: Int) {
        viewModelScope.launch {
            val pool = preferences.value.aiPersonaPool.toMutableList()
            if (index !in pool.indices) return@launch
            val current = pool[index]
            pool[index] = current.copy(avatarKey = AvatarCatalog.pickRandomKey(current.gender))
            preferencesDataStore.saveAiPersonaPool(pool)
        }
    }

    fun setAvatarAt(index: Int, avatarKey: String) {
        viewModelScope.launch {
            val pool = preferences.value.aiPersonaPool.toMutableList()
            if (index !in pool.indices) return@launch
            pool[index] = pool[index].copy(avatarKey = avatarKey)
            preferencesDataStore.saveAiPersonaPool(pool)
        }
    }

    fun generateAllPersonas() {
        viewModelScope.launch {
            val prefs = preferences.value
            val count = (prefs.playerCount - 1).coerceAtLeast(1)
            generatePersonas(count, startIndex = 0, replaceAll = true)
        }
    }

    fun generatePersonaAt(index: Int) {
        viewModelScope.launch {
            generatePersonas(count = 1, startIndex = index, replaceAll = false)
        }
    }

    private suspend fun generatePersonas(count: Int, startIndex: Int, replaceAll: Boolean) {
        _isGeneratingPersona.value = true
        _personaMessage.value = null
        val config = preferences.value.llmConfig
        val result = if (replaceAll) {
            personaGenerator.generateAll(config, count)
        } else {
            personaGenerator.generateOne(config, startIndex)
        }
        when (result) {
            is PersonaGenerator.Result.Success -> {
                val pool = preferences.value.aiPersonaPool.toMutableList()
                result.profiles.forEachIndexed { offset, profile ->
                    val targetIndex = startIndex + offset
                    if (targetIndex in pool.indices) {
                        val existing = pool[targetIndex]
                        val newGender = profile.gender.ifBlank { existing.gender }
                        val avatarKey = when {
                            newGender != existing.gender -> AvatarCatalog.pickRandomKey(newGender)
                            existing.avatarKey.isNotBlank() -> existing.avatarKey
                            else -> AvatarCatalog.pickRandomKey(newGender)
                        }
                        pool[targetIndex] = profile.copy(
                            gender = newGender,
                            avatarKey = avatarKey
                        )
                    }
                }
                preferencesDataStore.saveAiPersonaPool(pool)
                _personaMessage.value = "已生成 ${result.profiles.size} 个 AI 人设"
            }
            is PersonaGenerator.Result.Failure -> {
                _personaMessage.value = result.message
            }
        }
        _isGeneratingPersona.value = false
    }

    fun importPersonaPool(pool: List<AiPersonaProfile>) {
        viewModelScope.launch {
            preferencesDataStore.saveAiPersonaPool(pool)
            _personaMessage.value = "已导入 ${pool.size} 个 AI 人设"
        }
    }

    fun clearPersonaMessage() {
        _personaMessage.value = null
    }

    fun notifyPersonaMessage(message: String) {
        _personaMessage.value = message
    }

    fun difficultyLabel(level: DifficultyLevel): String = when (level) {
        DifficultyLevel.EASY -> "简单"
        DifficultyLevel.NORMAL -> "普通"
        DifficultyLevel.HARD -> "困难"
    }

    fun playerCountOptions(): List<Int> =
        (GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS).toList()
}

class AppViewModelFactory(
    private val application: Application,
    private val preferencesDataStore: PreferencesDataStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(application, preferencesDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

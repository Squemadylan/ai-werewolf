package com.squemadylan.wolfcha.data.model

import java.util.UUID

enum class DifficultyLevel {
    EASY, NORMAL, HARD
}

enum class SpeechDirection {
    CLOCKWISE, COUNTERCLOCKWISE
}

data class GamePlayer(
    val playerId: String = UUID.randomUUID().toString(),
    val seat: Int = 0,
    val displayName: String = "",
    val avatarSeed: String = "",
    val avatarKey: String = "",
    val alive: Boolean = true,
    val role: Role = Role.Villager,
    val alignment: Alignment = Alignment.VILLAGE,
    val isHuman: Boolean = false,
    val agentProfile: AgentProfile? = null
)

data class AgentProfile(
    val modelRef: ModelRef = ModelRef(),
    val persona: Persona = Persona()
)

data class ModelRef(
    val provider: String = "zenmux",
    val model: String = "deepseek-v3.2",
    val temperature: Float? = null
)

data class Persona(
    val mbti: String = "INTJ",
    val gender: String = "male",
    val age: Int = 25,
    val styleLabel: String = "冷静分析型",
    val background: String = "",
    val voiceRules: List<String> = emptyList()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val playerId: String = "",
    val playerName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val day: Int = 0,
    val phase: Phase = Phase.LOBBY,
    val isSystem: Boolean = false,
    val isLastWords: Boolean = false
)

data class GameEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: GameEventType,
    val visibility: EventVisibility = EventVisibility.PUBLIC,
    val visibleTo: List<String> = emptyList(),
    val payload: Map<String, String> = emptyMap()
)

enum class GameEventType {
    GAME_START,
    ROLE_ASSIGNED,
    PHASE_CHANGED,
    CHAT_MESSAGE,
    SYSTEM_MESSAGE,
    NIGHT_ACTION,
    VOTE_CAST,
    PLAYER_DIED,
    GAME_END
}

enum class EventVisibility {
    PUBLIC, PRIVATE
}

data class NightActions(
    val guardTarget: Int? = null,
    val lastGuardTarget: Int? = null,
    val wolfVotes: Map<String, Int> = emptyMap(),
    val wolfTarget: Int? = null,
    val witchSave: Boolean? = null,
    val witchPoison: Int? = null,
    val seerTarget: Int? = null,
    val seerResult: SeerResult? = null,
    val seerHistory: List<SeerHistoryEntry> = emptyList(),
    val pendingWolfVictim: Int? = null,
    val pendingPoisonVictim: Int? = null
)

data class SeerResult(
    val targetSeat: Int,
    val isWolf: Boolean
)

data class SeerHistoryEntry(
    val targetSeat: Int,
    val isWolf: Boolean,
    val day: Int
)

data class RoleAbilities(
    val witchHealUsed: Boolean = false,
    val witchPoisonUsed: Boolean = false,
    val hunterCanShoot: Boolean = true,
    val idiotRevealed: Boolean = false,
    val whiteWolfKingBoomUsed: Boolean = false
)

data class BadgeState(
    val holderSeat: Int? = null,
    val candidates: List<Int> = emptyList(),
    val signup: Map<String, Boolean> = emptyMap(),
    val votes: Map<String, Int> = emptyMap(),
    val allVotes: Map<String, Int> = emptyMap(),
    val history: Map<Int, Map<String, Int>> = emptyMap(),
    val revoteCount: Int = 0
)

data class GameSettings(
    val playerCount: Int = 10,
    val difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
    val isSpectatorMode: Boolean = false,
    val preferredRole: Role? = null,
    val humanName: String = "玩家",
    val aiPersonaPool: List<AiPersonaProfile> = emptyList()
)

object GameConfig {
    const val TOTAL_PLAYERS = 10
    const val WOLF_COUNT = 3
    const val MAX_REVOTE_COUNT = 3
    const val MAX_BADGE_REVOTE_COUNT = 2

    val STANDARD_ROLES = listOf(
        Role.Werewolf,
        Role.Werewolf,
        Role.WhiteWolfKing,
        Role.Seer,
        Role.Witch,
        Role.Hunter,
        Role.Guard,
        Role.Villager,
        Role.Villager,
        Role.Villager
    )

    fun getRoleConfiguration(playerCount: Int): List<Role> {
        return when (playerCount.coerceIn(MIN_PLAYERS, MAX_PLAYERS)) {
            6 -> listOf(
                Role.Werewolf, Role.Werewolf,
                Role.Seer, Role.Witch,
                Role.Villager, Role.Villager
            )
            7 -> listOf(
                Role.Werewolf, Role.Werewolf,
                Role.Seer, Role.Witch, Role.Hunter,
                Role.Villager, Role.Villager
            )
            8 -> listOf(
                Role.Werewolf, Role.Werewolf, Role.Werewolf,
                Role.Seer, Role.Witch, Role.Hunter,
                Role.Villager, Role.Villager
            )
            9 -> listOf(
                Role.Werewolf, Role.Werewolf, Role.Werewolf,
                Role.Seer, Role.Witch, Role.Hunter,
                Role.Villager, Role.Villager, Role.Villager
            )
            10 -> STANDARD_ROLES
            11 -> listOf(
                Role.Werewolf, Role.Werewolf, Role.WhiteWolfKing,
                Role.Seer, Role.Witch, Role.Hunter, Role.Guard,
                Role.Villager, Role.Villager, Role.Villager, Role.Villager
            )
            12 -> listOf(
                Role.Werewolf, Role.Werewolf, Role.Werewolf, Role.WhiteWolfKing,
                Role.Seer, Role.Witch, Role.Hunter, Role.Guard,
                Role.Villager, Role.Villager, Role.Villager, Role.Villager
            )
            else -> STANDARD_ROLES
        }
    }

    const val MIN_PLAYERS = 6
    const val MAX_PLAYERS = 12
}

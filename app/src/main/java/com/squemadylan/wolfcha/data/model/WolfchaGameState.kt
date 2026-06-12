package com.squemadylan.wolfcha.data.model

import java.util.UUID

data class WolfchaGameState(
    val gameId: String = UUID.randomUUID().toString(),
    val phase: Phase = Phase.LOBBY,
    val day: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val difficulty: DifficultyLevel = DifficultyLevel.NORMAL,
    val players: List<GamePlayer> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val currentSpeakerSeat: Int? = null,
    val nextSpeakerSeatOverride: Int? = null,
    val daySpeechStartSeat: Int? = null,
    val lastExecutedSeat: Int? = null,
    val speechDirection: SpeechDirection = SpeechDirection.CLOCKWISE,
    val pkTargets: List<Int> = emptyList(),
    val pkSource: String? = null,
    val badge: BadgeState = BadgeState(),
    val votes: Map<String, Int> = emptyMap(),
    val voteReasons: Map<String, String> = emptyMap(),
    val lastVoteReasons: Map<String, String> = emptyMap(),
    val voteHistory: Map<Int, Map<String, Int>> = emptyMap(),
    val nightActions: NightActions = NightActions(),
    val roleAbilities: RoleAbilities = RoleAbilities(),
    val winner: Alignment? = null,
    val isPaused: Boolean = false,
    val isSpectatorMode: Boolean = false
) {
    val humanPlayer: GamePlayer?
        get() = players.find { it.isHuman }

    val isNight: Boolean
        get() = phase.isNightPhase()

    val alivePlayers: List<GamePlayer>
        get() = players.filter { it.alive }

    val aliveWolves: List<GamePlayer>
        get() = players.filter { it.alive && it.role.isWolfRole() }

    val aliveVillagers: List<GamePlayer>
        get() = players.filter { it.alive && !it.role.isWolfRole() }

    fun getPlayerBySeat(seat: Int): GamePlayer? {
        return players.find { it.seat == seat }
    }

    fun getPlayerById(playerId: String): GamePlayer? {
        return players.find { it.playerId == playerId }
    }

    fun getNextAliveSeat(currentSeat: Int, direction: SpeechDirection = SpeechDirection.CLOCKWISE): Int? {
        val aliveSeats = alivePlayers.map { it.seat }.sorted()
        if (aliveSeats.isEmpty()) return null

        val currentIndex = aliveSeats.indexOf(currentSeat)
        if (currentIndex == -1) return aliveSeats.first()

        return when (direction) {
            SpeechDirection.CLOCKWISE -> {
                val nextIndex = (currentIndex + 1) % aliveSeats.size
                aliveSeats[nextIndex]
            }
            SpeechDirection.COUNTERCLOCKWISE -> {
                val nextIndex = (currentIndex - 1 + aliveSeats.size) % aliveSeats.size
                aliveSeats[nextIndex]
            }
        }
    }

    val isHumanWolf: Boolean
        get() = humanPlayer?.role?.isWolfRole() ?: false

    val wolfTeammateSeats: Set<Int>
        get() {
            if (!isHumanWolf) return emptySet()
            val humanSeat = humanPlayer?.seat ?: return emptySet()
            return aliveWolves.filter { it.seat != humanSeat }.map { it.seat }.toSet()
        }

    fun getVoteTargetSeat(voterSeat: Int): Int? {
        val voter = getPlayerBySeat(voterSeat) ?: return null
        return votes[voter.playerId]
    }

    val voteDisplayList: List<Pair<GamePlayer, Int?>>
        get() {
            val result = mutableListOf<Pair<GamePlayer, Int?>>()
            for (player in alivePlayers.sortedBy { it.seat }) {
                val targetSeat = votes[player.playerId]
                result.add(player to targetSeat)
            }
            return result
        }
}

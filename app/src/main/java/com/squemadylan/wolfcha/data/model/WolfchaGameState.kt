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
    val executedHistory: Map<Int, Int> = emptyMap(), // day -> seat
    val executedReasonHistory: Map<Int, String> = emptyMap(), // day -> reason
    val nightDeaths: Map<Int, List<Pair<Int, String>>> = emptyMap(), // day -> list of (seat, reason)
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

    /** 当前轮（尚未 resolve 的）投票中每人的投票目标（按座位号排序） */
    val voteDisplayList: List<Pair<GamePlayer, Int?>>
        get() {
            val result = mutableListOf<Pair<GamePlayer, Int?>>()
            for (player in alivePlayers.sortedBy { it.seat }) {
                val targetSeat = votes[player.playerId]
                result.add(player to targetSeat)
            }
            return result
        }

    /** 当前轮（尚未 resolve 的）投票中，每个目标玩家收到多少票 */
    val voteTargetCounts: Map<Int, Int>
        get() {
            val counts = mutableMapOf<Int, Int>()
            votes.values.forEach { seat ->
                counts[seat] = (counts[seat] ?: 0) + 1
            }
            return counts
        }

    /** 从投票历史获取某一天每个投票者的目标座位
     * 返回: Map<voterPlayerId, targetSeat> */
    fun votesOnDay(targetDay: Int): Map<String, Int> = voteHistory[targetDay] ?: emptyMap()

    /** 某一天投票出局的玩家座位 */
    fun executedSeatOnDay(targetDay: Int): Int? = executedHistory[targetDay]

    fun executedReasonOnDay(targetDay: Int): String = executedReasonHistory[targetDay] ?: "被投票出局"

    /** 某一夜死亡的玩家 */
    fun nightDeathsOnNight(nightDay: Int): List<Pair<Int, String>> = nightDeaths[nightDay] ?: emptyList()

    /** 返回指定玩家到现在为止的所有发言 */
    fun speechesByPlayer(playerId: String): List<String> {
        return messages.filter { it.playerId == playerId }.map { it.content }
    }

    /** 返回某一天所有玩家的发言（按发言顺序） */
    fun speechesOnDay(targetDay: Int): List<ChatMessage> {
        return messages.filter { it.day == targetDay && !it.isSystem }
    }

    /** 返回所有已经公开的死亡信息（不含玩家身份） */
    fun publicDeathSummary(): List<String> {
        val lines = mutableListOf<String>()
        for (d in 1..day) {
            val deaths = nightDeaths[d]
            if (!deaths.isNullOrEmpty()) {
                val names = deaths.joinToString("、") { (seat, reason) ->
                    val name = getPlayerBySeat(seat)?.displayName ?: ""
                    "${seat + 1}号$name（$reason）"
                }
                lines.add("第$d 天夜晚: $names")
            }
            val executed = executedHistory[d]
            if (executed != null) {
                val player = getPlayerBySeat(executed)
                val reason = executedReasonHistory[d] ?: "被投票出局"
                lines.add("第$d 天白天: ${executed + 1}号${player?.displayName ?: ""}（$reason）")
            }
        }
        return lines
    }
}

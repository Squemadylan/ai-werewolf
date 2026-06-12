package com.squemadylan.wolfcha.domain

import com.squemadylan.wolfcha.data.model.ChatMessage
import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.Phase
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.data.model.isNightPhase

/**
 * Builds the information boundary for a single AI/human player.
 *
 * Each LLM call must only receive what [buildFor] returns — never the raw
 * [WolfchaGameState] or another player's prompt payload.
 */
object PlayerKnowledgeScope {

    data class ScopedKnowledge(
        val viewerId: String,
        val viewerName: String,
        val day: Int,
        val phase: Phase,
        val aliveSummary: String,
        val deadSummary: String,
        val publicAnnouncements: String,
        val publicSpeeches: String,
        val privateFacts: String
    )

    /**
     * All players at the table can hear these system lines (deaths, votes, day/night).
     * Internal/debug lines must never enter any player's prompt.
     */
    private val INTERNAL_SYSTEM_PREFIXES = listOf(
        "[",
        "模型调用失败",
        "PHASE_CHANGED"
    )

    fun buildFor(state: WolfchaGameState, viewer: GamePlayer): ScopedKnowledge {
        val alive = state.alivePlayers.sortedBy { it.seat }
        val aliveSummary = alive.joinToString("、") { "${it.seat + 1}号 ${it.displayName}" }
        val deadSummary = state.players.filter { !it.alive }
            .joinToString("、") { "${it.seat + 1}号 ${it.displayName}" }
            .ifEmpty { "无" }

        val publicAnnouncements = state.messages
            .filter { msg -> isPublicSystemMessage(msg) && msg.day <= state.day }
            .takeLast(15)
            .joinToString("\n") { it.content }
            .ifEmpty { "暂无公开事件播报。" }

        val publicSpeeches = state.messages
            .filter { !it.isSystem && it.day == state.day }
            .takeLast(20)
            .joinToString("\n") { msg ->
                val aliveFlag = if (state.getPlayerById(msg.playerId)?.alive == true) "" else "（已出局）"
                "${msg.playerName}$aliveFlag：${msg.content}"
            }
            .ifEmpty { "本轮白天还没有人发言。" }

        val privateFacts = buildPrivateFacts(viewer, state)

        return ScopedKnowledge(
            viewerId = viewer.playerId,
            viewerName = viewer.displayName,
            day = state.day,
            phase = state.phase,
            aliveSummary = aliveSummary,
            deadSummary = deadSummary,
            publicAnnouncements = publicAnnouncements,
            publicSpeeches = publicSpeeches,
            privateFacts = privateFacts
        )
    }

    private fun isPublicSystemMessage(msg: ChatMessage): Boolean {
        if (!msg.isSystem) return false
        if (INTERNAL_SYSTEM_PREFIXES.any { msg.content.startsWith(it) }) return false
        return true
    }

    private fun buildPrivateFacts(viewer: GamePlayer, state: WolfchaGameState): String {
        val lines = mutableListOf<String>()
        when (viewer.role) {
            Role.Seer -> {
                val history = state.nightActions.seerHistory
                if (history.isNotEmpty()) {
                    val recent = history.takeLast(5).joinToString("；") { entry ->
                        val target = state.getPlayerBySeat(entry.targetSeat)
                        val verdict = if (entry.isWolf) "狼人" else "好人"
                        "第${entry.day}夜 查验 ${target?.seat?.plus(1) ?: "?"}号 ${target?.displayName ?: ""} → $verdict"
                    }
                    lines += "你的查验记录（仅你知道）：$recent"
                }
            }
            Role.Werewolf, Role.WhiteWolfKing -> {
                val wolves = state.players
                    .filter { it.playerId != viewer.playerId && it.alive && it.role.isWolfLike() }
                    .joinToString("、") { "${it.seat + 1}号 ${it.displayName}" }
                if (wolves.isNotBlank()) {
                    lines += "你活着的狼队友（仅狼人知晓）：$wolves"
                }
            }
            Role.Witch -> {
                val used = state.roleAbilities
                lines += "解药${if (used.witchHealUsed) "已用" else "未用"}，毒药${if (used.witchPoisonUsed) "已用" else "未用"}（仅你知道）"
                if (state.phase.isNightPhase()) {
                    state.nightActions.wolfTarget?.let { seat ->
                        val victim = state.getPlayerBySeat(seat)
                        lines += "今晚狼人袭击目标（仅女巫夜晚可知）：${seat + 1}号 ${victim?.displayName ?: ""}"
                    }
                }
            }
            Role.Guard -> {
                state.nightActions.lastGuardTarget?.let {
                    lines += "你上一晚守护的是 ${it + 1}号（仅你知道）"
                }
            }
            else -> {}
        }
        return lines.joinToString("\n")
    }

    private fun Role.isWolfLike(): Boolean =
        this == Role.Werewolf || this == Role.WhiteWolfKing
}

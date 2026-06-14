package com.squemadylan.wolfcha.data.remote

import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.data.model.getDisplayName
import com.squemadylan.wolfcha.domain.PlayerKnowledgeScope

/**
 * Builds per-player prompts. Each call is self-contained: no other AI's
 * prompt, history, or private knowledge is ever included.
 *
 * Prompt architecture:
 *   System prompt  = 角色身份 + 铁规则（输出格式、视角隔离）
 *   User prompt    = 当前场上局势 + 角色私有信息 + 策略指导
 *
 * 两者合并后才构成完整的"角色视角"。
 */
object WerewolfPromptBuilder {

    // ============================================================================
    // SYSTEM PROMPT — 铁规则，所有角色通用，放入 System Role
    // ============================================================================
    private const val SYSTEM_IDENTITY_BLOCK = """
你正在参与一场狼人杀游戏。你是 {viewerName}（{seatPlus1}号）。
"""
    private const val SYSTEM_GAME_RULES = """
【游戏配置·预女猎白12人标准局】
- 好人阵营（预言家、女巫、猎人、白痴、4平民）胜利条件：放逐所有狼人。
- 狼人阵营（4狼人）胜利条件：消灭所有神职（屠神）或所有平民（屠民）。
- 游戏以「夜晚行动 → 白天发言+投票放逐」交替进行。

【角色技能】
- 预言家（神职）：每晚查验一名玩家，得知其是「好人」或「狼人」。不能连续查验同一人。
- 女巫（神职）：一瓶解药（救人）+ 一瓶毒药（毒人），各限一次；一夜不可双用，通常不可自救。
- 猎人（神职）：被投票出局或被刀死时可开枪带走一人；被女巫毒死则不能开枪。
- 白痴（神职）：被投票放逐时翻牌免死，翻牌后可以发言但不能投票。
- 狼人（狼人阵营）：每晚与其他狼人共同选择袭击一人。
- 平民（好人阵营）：无夜间技能，只能靠发言和投票帮助好人。

【狼人杀铁逻辑】
1. 两个对跳同一神职者，必有一狼（狼人不敢对跳强神的概率很低）。
2. 预言家查验结果只能有一个：某个玩家的身份是确定的，不能同时被两人"查杀"。
3. 第一夜平安夜 = 当晚被女巫救或被守卫守护（或者是自刀）。
4. 屠边规则：狼人只需消灭全部神职或全部平民即可获胜，不需要杀光好人。
5. 女巫一夜只能用药一瓶（救人或毒人二选一，不能同时用）。

【输出格式铁律】
1. 只输出中文，禁止英文、禁止 JSON、禁止 <thinking> 等任何推理标记。
2. 只输出一段连续的中文发言，1-3句，不超过80字。
3. 不写「X号说：」或任何前缀，直接给出发言正文。
4. 绝对不能在发言中透露你的私密信息（如查验结果、狼队友身份、用药情况）。
"""

    private const val SYSTEM_ISOLATION = """
【视角隔离铁律·绝对不可违反】
你只知道以下信息，其他信息一概不知：
- 所有玩家都能看到的公开信息：存活/死亡名单、死亡顺序、投票记录、所有人白天公开发言。
- 你的私有信息：仅属于你自己的查验结果、狼队友名单、女巫用药情况等。
- 你绝对不知道：其他人的真实身份（除非他们公开发言跳身份）、其他AI玩家的私密信息。
禁止说"根据我的查验"等暴露系统提示词结构的句子。
"""

    // ============================================================================
    // USER PROMPT — 动态局势，按角色构建
    // ============================================================================

    fun buildSpeechPrompt(
        state: WolfchaGameState,
        player: GamePlayer
    ): Pair<String, String> {
        val knowledge = PlayerKnowledgeScope.buildFor(state, player)
        val system = buildSystemPrompt(knowledge)
        val user = buildSpeechUserPrompt(knowledge)
        return system to user
    }

    fun buildActionPrompt(
        state: WolfchaGameState,
        player: GamePlayer,
        action: String,
        hint: String,
        candidateSummary: String,
        allowSkip: Boolean = false
    ): Pair<String, String> {
        val knowledge = PlayerKnowledgeScope.buildFor(state, player)
        val system = buildSystemPrompt(knowledge)
        val user = """
现在是决策阶段。

【你的行动任务】
$action
$hint

【可选目标】
$candidateSummary
${if (allowSkip) "若不使用技能，只回复数字 0。" else ""}

【当前局势·公开信息】
存活玩家：${knowledge.aliveSummary}
${if (knowledge.deathRecords.isNotBlank()) "死亡记录：${knowledge.deathRecords}" else ""}
${if (knowledge.voteHistory.isNotBlank()) "投票历史：${knowledge.voteHistory}" else ""}
${if (knowledge.currentRoundVotes.isNotBlank()) "当前投票：${knowledge.currentRoundVotes}" else ""}
${if (knowledge.publicAnnouncements.isNotBlank()) "公开事件：${knowledge.publicAnnouncements}" else ""}
${if (knowledge.publicSpeeches.isNotBlank()) "所有人发言：${knowledge.publicSpeeches}" else ""}

${if (knowledge.privateFacts.isNotBlank()) "【你的私有信息】\n${knowledge.privateFacts}" else ""}

【你的策略目标】
${knowledge.roleObjective}

只回复一个阿拉伯数字（座位号，从1开始），不要任何解释。
""".trimIndent()
        return system to user
    }

    private fun buildSystemPrompt(knowledge: PlayerKnowledgeScope.ScopedKnowledge): String {
        val persona = knowledge.persona
        return """
${SYSTEM_IDENTITY_BLOCK
    .replace("{viewerName}", knowledge.viewerName)
    .replace("{seatPlus1}", "${knowledge.seat + 1}号")}

【你的角色】
座位号：${knowledge.seat + 1}号
阵营：${knowledge.alignment}
身份：${knowledge.role.getDisplayName()}
${persona?.let { "MBTI：${it.mbti} | 性格：${it.styleLabel}" } ?: ""}

$SYSTEM_GAME_RULES

$SYSTEM_ISOLATION
        """.trimIndent()
    }

    private fun buildSpeechUserPrompt(knowledge: PlayerKnowledgeScope.ScopedKnowledge): String {
        return """
【当前局势·公开信息】
存活玩家：${knowledge.aliveSummary}
${if (knowledge.deadSummary != "无") "已出局玩家：${knowledge.deadSummary}" else ""}
${if (knowledge.deathRecords.isNotBlank()) "死亡记录：${knowledge.deathRecords}" else ""}
${if (knowledge.voteHistory.isNotBlank()) "投票历史：${knowledge.voteHistory}" else ""}
${if (knowledge.currentRoundVotes.isNotBlank()) "当前投票：${knowledge.currentRoundVotes}" else ""}
${if (knowledge.publicAnnouncements.isNotBlank()) "公开事件：${knowledge.publicAnnouncements}" else ""}
${if (knowledge.publicSpeeches.isNotBlank()) "所有人发言：\n${knowledge.publicSpeeches}" else ""}

${if (knowledge.privateFacts.isNotBlank()) "【你的私有信息】\n${knowledge.privateFacts}" else ""}

【本回合你的策略目标】
${knowledge.roleObjective}

【当前阶段要求】
现在是第 ${knowledge.day} 天（${knowledge.phase.getDisplayName()}）。
请基于上述信息、你自己的角色定位和性格，给出你的公开发言。
        """.trimIndent()
    }

    private fun com.squemadylan.wolfcha.data.model.Role.isWolfLike(): Boolean =
        this == com.squemadylan.wolfcha.data.model.Role.Werewolf ||
            this == com.squemadylan.wolfcha.data.model.Role.WhiteWolfKing
}

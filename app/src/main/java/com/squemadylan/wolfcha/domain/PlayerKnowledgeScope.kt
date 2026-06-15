package com.squemadylan.wolfcha.domain

import com.squemadylan.wolfcha.data.model.ChatMessage
import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.Phase
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.data.model.getDisplayName
import com.squemadylan.wolfcha.data.model.isNightPhase

/**
 * Builds the information boundary for a single AI/human player.
 *
 * Each LLM call must only receive what [buildFor] returns — never the raw
 * [WolfchaGameState] or another player's prompt payload.
 *
 * The returned [ScopedKnowledge] contains:
 * - 公开可见信息（存活/死亡/投票/发言）→ 所有角色均可见
 * - 私密信息（查验/狼队友/用药）→ 仅对应角色可见
 * - 策略目标（roleObjective）→ 基于当前局势动态生成，引导角色行为
 */
object PlayerKnowledgeScope {

    data class ScopedKnowledge(
        val viewerId: String,
        val viewerName: String,
        val day: Int,
        val phase: Phase,
        val role: Role,
        val alignment: String,
        val seat: Int,
        /** 玩家人设（MBTI/性格等），来自 AgentProfile.persona */
        val persona: com.squemadylan.wolfcha.data.model.Persona?,
        val aliveSummary: String,
        val deadSummary: String,
        val deathRecords: String,
        val publicAnnouncements: String,
        val publicSpeeches: String,
        val voteHistory: String,
        val currentRoundVotes: String,
        /** 仅该角色可见的私密信息（如查验结果、狼队友等）。其他角色此处为空。 */
        val privateFacts: String,
        /**
         * 本回合的核心策略目标。
         * 由 buildRoleObjective() 动态生成，综合考虑：
         * - 该角色当前有什么信息（私有+公开）
         * - 当前场上局势（谁跳了身份、高票是谁、死亡顺序）
         * - 轮到几（游戏阶段）
         * 引导 AI 在本回合做出符合角色定位的决策，而不是执行刻板行为。
         */
        val roleObjective: String,
        /** 本局规则说明（按实际人数与角色配置动态生成，屠边胜负规则）。 */
        val gameRules: String
    )

    /** 系统级消息前缀，这些消息不作为公开事件播报给玩家 */
    private val INTERNAL_SYSTEM_PREFIXES = listOf(
        "[",
        "模型调用失败",
        "PHASE_CHANGED"
    )

    fun buildFor(state: WolfchaGameState, viewer: GamePlayer): ScopedKnowledge {
        val alive = state.alivePlayers.sortedBy { it.seat }
        val aliveSummary = alive.joinToString("、") { "${it.seat + 1}号${it.displayName}" }
        val deadSummary = state.players.filter { !it.alive }
            .joinToString("、") { "${it.seat + 1}号${it.displayName}" }
            .ifEmpty { "无" }

        val deathRecords = buildDeathRecords(state)
        val voteHistory = buildVoteHistory(state)
        val currentRoundVotes = buildCurrentRoundVotes(state)

        // 公开事件播报：仅非内部系统消息
        val publicAnnouncements = state.messages
            .filter { msg -> isPublicSystemMessage(msg) && msg.day <= state.day }
            .takeLast(15)
            .joinToString("\n") { it.content }
            .ifEmpty { "暂无公开事件播报。" }

        // 所有人公开发言（所有人都听过）。仅保留最近若干条，避免 token 随对局无限增长。
        val publicSpeeches = state.messages
            .filter { !it.isSystem }
            .takeLast(40)
            .joinToString("\n") { msg ->
                val dayTag = if (msg.day == state.day) "" else "[第${msg.day}天]"
                val aliveFlag = if (state.getPlayerById(msg.playerId)?.alive == true) "" else "（已出局）"
                "$dayTag${msg.playerName}$aliveFlag：${msg.content}"
            }
            .ifEmpty { "本轮还没有人发言。" }

        val privateFacts = buildPrivateFacts(viewer, state)
        val roleObjective = buildRoleObjective(viewer, state)

        val alignment = when (viewer.role) {
            Role.Werewolf, Role.WhiteWolfKing -> "狼人阵营"
            else -> "好人阵营"
        }

        return ScopedKnowledge(
            viewerId = viewer.playerId,
            viewerName = viewer.displayName,
            day = state.day,
            phase = state.phase,
            role = viewer.role,
            alignment = alignment,
            seat = viewer.seat,
            persona = viewer.agentProfile?.persona,
            aliveSummary = aliveSummary,
            deadSummary = deadSummary,
            deathRecords = deathRecords,
            publicAnnouncements = publicAnnouncements,
            publicSpeeches = publicSpeeches,
            voteHistory = voteHistory,
            currentRoundVotes = currentRoundVotes,
            privateFacts = privateFacts,
            roleObjective = roleObjective,
            gameRules = buildGameRules(state)
        )
    }

    // ==========================================================================
    // 本局规则（按实际人数与角色配置动态生成）
    // ==========================================================================

    /**
     * 角色配置（板子）在狼人杀中属于公开信息，所有玩家开局即知道有哪些角色。
     * 因此可以安全地写入每个玩家的提示词，不会泄露任何私密信息。
     */
    private fun buildGameRules(state: WolfchaGameState): String {
        val players = state.players
        val total = players.size
        val roleCounts = players.groupingBy { it.role }.eachCount()

        val wolfCount = (roleCounts[Role.Werewolf] ?: 0) + (roleCounts[Role.WhiteWolfKing] ?: 0)
        val hasWhiteWolfKing = (roleCounts[Role.WhiteWolfKing] ?: 0) > 0
        val villagerCount = roleCounts[Role.Villager] ?: 0
        val goodCount = total - wolfCount

        val godRolesOrder = listOf(Role.Seer, Role.Witch, Role.Hunter, Role.Guard, Role.Idiot)
        val presentGods = godRolesOrder.filter { (roleCounts[it] ?: 0) > 0 }
        val godSummary = presentGods.joinToString("、") { it.getDisplayName() }

        val wolfDesc = buildString {
            append("狼人${wolfCount}人")
            if (hasWhiteWolfKing) append("（含1名白狼王）")
        }
        val goodDesc = buildString {
            append("好人${goodCount}人（")
            if (godSummary.isNotBlank()) append(godSummary)
            if (villagerCount > 0) {
                if (godSummary.isNotBlank()) append("、")
                append("${villagerCount}名平民")
            }
            append("）")
        }

        val skillLines = mutableListOf<String>()
        if ((roleCounts[Role.Seer] ?: 0) > 0)
            skillLines += "- 预言家（神职）：每晚查验一名玩家，得知其是「好人」或「狼人」。不能连续查验同一人。"
        if ((roleCounts[Role.Witch] ?: 0) > 0)
            skillLines += "- 女巫（神职）：一瓶解药（救人）+ 一瓶毒药（毒人），各限一次；一夜不可双用，通常不可自救。"
        if ((roleCounts[Role.Hunter] ?: 0) > 0)
            skillLines += "- 猎人（神职）：被投票出局或被刀死时可开枪带走一人；被女巫毒死则不能开枪。"
        if ((roleCounts[Role.Guard] ?: 0) > 0)
            skillLines += "- 守卫（神职）：每晚守护一名玩家免受狼人袭击，不能连续两晚守护同一人。"
        if ((roleCounts[Role.Idiot] ?: 0) > 0)
            skillLines += "- 白痴（神职）：被投票放逐时翻牌免死，翻牌后可继续发言但永久失去投票权。"
        if (hasWhiteWolfKing)
            skillLines += "- 白狼王（狼人阵营）：白天可自爆并带走一名玩家；被毒杀时不能发动。"
        skillLines += "- 狼人（狼人阵营）：每晚与其他狼人共同选择袭击一人。"
        if (villagerCount > 0)
            skillLines += "- 平民（好人阵营）：无夜间技能，只能靠发言和投票帮助好人。"

        return """
【本局配置·${total}人局】
- 阵营构成：$wolfDesc；$goodDesc。
- 好人阵营胜利条件：放逐所有狼人。
- 狼人阵营胜利条件（屠边）：杀光全部平民，或杀光全部神职人员，二者满足其一即获胜；不需要消灭所有好人。
- 游戏以「夜晚行动 → 白天发言+投票放逐」交替进行。

【角色技能】
${skillLines.joinToString("\n")}

【狼人杀铁逻辑】
1. 两个对跳同一神职者，必有一狼（狼人不敢对跳强神的概率很低）。
2. 预言家查验结果只能有一个：某个玩家的身份是确定的，不能同时被两人"查杀"。
3. 第一夜平安夜 = 当晚被女巫救或被守卫守护（或者是自刀）。
4. 屠边规则：狼人只需消灭全部神职或全部平民即可获胜，不需要把全部好人杀光。
5. 女巫一夜只能用药一瓶（救人或毒人二选一，不能同时用）。
        """.trimIndent()
    }

    private fun isPublicSystemMessage(msg: ChatMessage): Boolean {
        if (!msg.isSystem) return false
        if (INTERNAL_SYSTEM_PREFIXES.any { msg.content.startsWith(it) }) return false
        return true
    }

    // ==========================================================================
    // 公开信息构建
    // ==========================================================================

    private fun buildDeathRecords(state: WolfchaGameState): String {
        val lines = mutableListOf<String>()
        for (d in 1..state.day) {
            val nightDeaths = state.nightDeaths[d]
            if (!nightDeaths.isNullOrEmpty()) {
                val names = nightDeaths.joinToString("、") { (seat, reason) ->
                    "${seat + 1}号${state.getPlayerBySeat(seat)?.displayName ?: ""}（$reason）"
                }
                lines += "第${d}天夜晚：$names"
            }
            val executed = state.executedHistory[d]
            if (executed != null) {
                val player = state.getPlayerBySeat(executed)
                val reason = state.executedReasonHistory[d] ?: "被投票出局"
                lines += "第${d}天白天：${executed + 1}号${player?.displayName ?: ""}（$reason）"
            }
        }
        return lines.joinToString("\n").ifEmpty { "暂无死亡记录。" }
    }

    private fun buildVoteHistory(state: WolfchaGameState): String {
        val lines = mutableListOf<String>()
        for (d in 1 until state.day) {
            val votes = state.voteHistory[d]
            if (votes != null && votes.isNotEmpty()) {
                val seatToPlayer = state.players.associateBy { it.playerId }
                val voterLines = votes.entries.joinToString("；") { (pid, seat) ->
                    val voter = seatToPlayer[pid]
                    val target = state.getPlayerBySeat(seat)
                    "${voter?.seat?.plus(1) ?: "?"}号${voter?.displayName ?: ""}→${seat + 1}号${target?.displayName ?: ""}"
                }
                val counts = votes.values.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .joinToString("、") { (seat, cnt) ->
                        "${seat + 1}号${state.getPlayerBySeat(seat)?.displayName ?: ""}（${cnt}票）"
                    }
                lines += "第${d}天投票：$voterLines。得票：$counts"
            }
        }
        return lines.joinToString("\n").ifEmpty { "暂无历史投票记录。" }
    }

    private fun buildCurrentRoundVotes(state: WolfchaGameState): String {
        if (state.votes.isEmpty()) return "当前轮投票尚未开始。"
        val seatToPlayer = state.players.associateBy { it.playerId }
        val lines = state.votes.entries.map { (pid, seat) ->
            val voter = seatToPlayer[pid]
            "${voter?.seat?.plus(1) ?: "?"}号→${seat + 1}号"
        }
        val counts = state.voteTargetCounts.entries.sortedByDescending { it.value }
            .joinToString("、") { (seat, cnt) ->
                "${seat + 1}号${state.getPlayerBySeat(seat)?.displayName ?: ""}（${cnt}票）"
            }
        return "已投票：${lines.joinToString("；")}。当前得票：$counts"
    }

    // ==========================================================================
    // 私密信息构建（仅对应角色能获取）
    // ==========================================================================

    private fun buildPrivateFacts(viewer: GamePlayer, state: WolfchaGameState): String {
        val lines = mutableListOf<String>()

        when (viewer.role) {
            // 预言家：只有自己能看到的查验记录
            Role.Seer -> {
                val history = state.nightActions.seerHistory
                if (history.isNotEmpty()) {
                    val recent = history.takeLast(5).joinToString("；") { entry ->
                        val target = state.getPlayerBySeat(entry.targetSeat)
                        "${entry.targetSeat + 1}号${target?.displayName ?: ""} → ${if (entry.isWolf) "狼人" else "好人"}"
                    }
                    lines += "【你的查验记录（仅你本人知道）】$recent"
                } else {
                    lines += "【你的查验记录】暂无（你还没有进行过查验）"
                }
            }

            // 狼人：只有自己能看到的狼队友
            Role.Werewolf, Role.WhiteWolfKing -> {
                val wolves = state.players
                    .filter { it.playerId != viewer.playerId && it.alive && it.role.isWolfLike() }
                    .joinToString("、") { "${it.seat + 1}号${it.displayName}" }
                if (wolves.isNotBlank()) {
                    lines += "【你的狼队友（仅你本人知道）】$wolves"
                } else {
                    lines += "【你的狼队友】场上只有你一匹狼。"
                }
            }

            // 女巫：用药情况 + 夜晚狼人袭击目标（仅女巫夜晚可知）
            Role.Witch -> {
                val used = state.roleAbilities
                lines += "【你的用药情况（仅你本人知道）】解药：${if (used.witchHealUsed) "已用" else "未用"}；毒药：${if (used.witchPoisonUsed) "已用" else "未用"}"
                // 女巫在夜晚睁眼时，可以看到狼人选择了谁
                if (state.phase.isNightPhase()) {
                    state.nightActions.wolfTarget?.let { seat ->
                        lines += "【狼人今夜袭击目标（仅女巫夜晚可见）】${seat + 1}号${state.getPlayerBySeat(seat)?.displayName ?: ""}"
                    }
                }
            }

            // 守卫：上一晚守了谁（不能连守同一人）
            Role.Guard -> {
                val lastGuardSeat = state.nightActions.lastGuardTarget
                if (lastGuardSeat != null) {
                    lines += "【你上一晚守护目标（仅你本人知道）】${lastGuardSeat + 1}号${state.getPlayerBySeat(lastGuardSeat)?.displayName ?: ""}"
                } else {
                    lines += "【你上一晚守护目标】暂无（首夜未使用守护）"
                }
            }

            else -> {}
        }

        return lines.joinToString("\n")
    }

    // ==========================================================================
    // 策略目标构建（roleObjective）
    //
    // 这是提示词最核心的部分！
    // 每个角色看到的是基于「当前具体局势」生成的策略指导，
    // 而不是刻板的固定行为规则。
    // ==========================================================================

    /**
     * 综合场上所有公开+私有信息，为指定角色生成当前回合的策略目标。
     *
     * 设计原则：
     * 1. 每个策略都基于"具体局势"而非"刻板规则"
     * 2. 同一角色在不同局势下有完全不同的策略（预言家查到狼 vs 查到好人 vs 没查到）
     * 3. 策略要回答"这一回合我应该做什么"这个核心问题
     */
    private fun buildRoleObjective(viewer: GamePlayer, state: WolfchaGameState): String {
        val wolves = state.players.filter {
            it.playerId != viewer.playerId && it.alive && it.role.isWolfLike()
        }
        val totalAlive = state.alivePlayers.size
        val wolfCount = state.aliveWolves.size
        val villageCount = totalAlive - wolfCount

        // 公共局势分析
        val seerClaimant = findSeerClaimant(state) // 场上谁跳了预言家（公开发言）
        val topVoteTarget = findTopVoteTarget(state) // 当前票数最高者
        val lastNightDeaths = state.nightDeaths[state.day]
        val lastCheck = state.nightActions.seerHistory.lastOrNull()

        return when (viewer.role) {

            // ====================================================================
            // 预言家 — 策略核心：根据查验情况决定是否跳身份
            // ====================================================================
            Role.Seer -> {
                val hasWolf = lastCheck?.isWolf == true   // 查到了狼人？
                val hasCheck = state.nightActions.seerHistory.isNotEmpty()
                val someoneElseClaimed = seerClaimant != null && seerClaimant != viewer.seat

                buildSeerObjective(
                    viewer = viewer,
                    state = state,
                    hasWolf = hasWolf,
                    hasCheck = hasCheck,
                    lastCheck = lastCheck,
                    someoneElseClaimed = someoneElseClaimed,
                    seerClaimant = seerClaimant,
                    totalAlive = totalAlive,
                    villageCount = villageCount,
                    wolfCount = wolfCount,
                    topVoteTarget = topVoteTarget
                )
            }

            // ====================================================================
            // 狼人 — 策略核心：被查杀时必须对跳，否则隐藏身份伺机冲票
            // ====================================================================
            Role.Werewolf, Role.WhiteWolfKing -> {
                buildWolfObjective(
                    viewer = viewer,
                    state = state,
                    wolves = wolves,
                    seerClaimant = seerClaimant,
                    someoneElseClaimed = someoneElseClaimed(state),
                    someoneElseClaimedSeat = seerClaimant,
                    lastCheck = lastCheck,
                    totalAlive = totalAlive,
                    wolfCount = wolfCount,
                    villageCount = villageCount,
                    topVoteTarget = topVoteTarget
                )
            }

            // ====================================================================
            // 女巫
            // ====================================================================
            Role.Witch -> {
                val healUsed = state.roleAbilities.witchHealUsed
                val poisonUsed = state.roleAbilities.witchPoisonUsed
                val wolfTarget = state.nightActions.wolfTarget
                val wolfVictimName = wolfTarget?.let { "${it + 1}号${state.getPlayerBySeat(it)?.displayName ?: ""}" } ?: "无"

                buildWitchObjective(
                    viewer = viewer,
                    state = state,
                    healUsed = healUsed,
                    poisonUsed = poisonUsed,
                    wolfTarget = wolfTarget,
                    wolfVictimName = wolfVictimName,
                    someoneElseClaimed = someoneElseClaimed(state),
                    someoneElseClaimedSeat = seerClaimant,
                    totalAlive = totalAlive,
                    villageCount = villageCount
                )
            }

            // ====================================================================
            // 猎人
            // ====================================================================
            Role.Hunter -> {
                buildHunterObjective(
                    viewer = viewer,
                    state = state,
                    someoneElseClaimed = someoneElseClaimed(state),
                    someoneElseClaimedSeat = seerClaimant,
                    topVoteTarget = topVoteTarget
                )
            }

            // ====================================================================
            // 守卫
            // ====================================================================
            Role.Guard -> {
                val lastGuard = state.nightActions.lastGuardTarget
                val lastNightDead = lastNightDeaths?.firstOrNull()?.first
                buildGuardObjective(
                    viewer = viewer,
                    state = state,
                    lastGuard = lastGuard,
                    lastNightDead = lastNightDead,
                    someoneElseClaimed = someoneElseClaimed(state),
                    someoneElseClaimedSeat = seerClaimant
                )
            }

            // ====================================================================
            // 白痴 / 平民
            // ====================================================================
            else -> {
                buildVillagerObjective(
                    viewer = viewer,
                    state = state,
                    someoneElseClaimed = someoneElseClaimed(state),
                    someoneElseClaimedSeat = seerClaimant,
                    topVoteTarget = topVoteTarget,
                    totalAlive = totalAlive,
                    wolfCount = wolfCount,
                    villageCount = villageCount
                )
            }
        }
    }

    // ==========================================================================
    // 各角色策略子函数
    // ==========================================================================

    private fun buildSeerObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        hasWolf: Boolean,
        hasCheck: Boolean,
        lastCheck: com.squemadylan.wolfcha.data.model.SeerHistoryEntry?,
        someoneElseClaimed: Boolean,
        seerClaimant: Int?,
        totalAlive: Int,
        villageCount: Int,
        wolfCount: Int,
        topVoteTarget: Int?
    ): String {
        val seat = viewer.seat + 1
        val targetName = lastCheck?.let { "${it.targetSeat + 1}号${state.getPlayerBySeat(it.targetSeat)?.displayName ?: ""}" } ?: "?"
        val resultStr = if (hasWolf) "查杀（狼人）" else "金水（好人）"

        // 核心决策树：
        // 1. 查到了狼人 → 必须跳！报查验+归票，这是对好人最大的贡献
        // 2. 有人对跳预言家 → 必须对跳！你才是真的，对面是狼
        // 3. 有金水但没查到狼 + 没人跳预言家 → 可以考虑跳保金水，或继续隐藏查验
        // 4. 还没查验 + 没人跳预言家 → 可以不跳，继续查验积累信息
        // 5. 被狼人盯上（票数高/有人质疑）→ 需要权衡是否跳身份自保

        return if (hasWolf && hasCheck && lastCheck != null) {
            // 【查杀局】必须跳身份归票！
            val nextCheck = state.alivePlayers
                .filter { it.seat != viewer.seat && it.seat != lastCheck.targetSeat && it.alive }
                .minByOrNull { it.seat }
            val nextPlan = nextCheck?.let { "接下来我会查验${it.seat + 1}号${it.displayName}。" } ?: ""

            """
【你的查验结果】昨夜查验${targetName} → $resultStr！
【本回合你必须做的事情】
1. 跳预言家！发言格式："我是预言家，昨夜查验了${lastCheck?.targetSeat?.plus(1) ?: "?"}号，结果是$resultStr！"
2. 归票出${targetName}！他已被查杀，必须出局。
3. $nextPlan
4. 如果有人跟你对跳预言家，他就是狼人（铁逻辑），你必须指出："${seerClaimant?.let { "${it + 1}号" } ?: "对跳者"}和我对跳，他必是狼！"
【投票策略】有查杀投查杀目标；无查杀但有对跳投对跳者；否则投最高票。
【当前场上】${totalAlive}人存活，狼人约${wolfCount}个。${if (someoneElseClaimed) "⚠有人跳了预言家，必须对跳！" else ""}
            """.trimIndent()
        } else if (someoneElseClaimed) {
            // 【有人跳预言家】你也是预言家，必须对跳！
            val claimerSeat = seerClaimant?.plus(1) ?: "?"
            """
【当前局势】${claimerSeat}号在公开发言中声称自己是预言家。
【本回合你必须做的事情】
1. 你是预言家，${claimerSeat}号是在对跳你！对跳者必有一狼（铁逻辑），你必须跳出来！
2. 发言格式："我是预言家！${claimerSeat}号悍跳！他的查验是假的！"（如果你本轮有查验结果，就报出来）
3. ${if (hasCheck) "你可以用你的查验结果来证明自己是真的：如果${targetName}是金水，就说'我给了${targetName}金水，所以我是真的'；如果是查杀，就直接归票！" else "你目前还没查到狼，可以先报查验计划争取信任。"}
4. 请对比两边的发言逻辑：预言家查验结果只能有一个，不可能两个都是真的。
【投票策略】投${claimerSeat}号！对跳者中必有一狼，投他的同时也是在为好人找狼。
            """.trimIndent()
        } else if (hasCheck && !hasWolf) {
            // 【查到金水】可以选择跳身份保金水，或继续隐藏
            val survivalRounds = minOf(villageCount, wolfCount)
            """
【你的查验结果】${targetName}是你的金水（好人）。
【当前局势分析】
- 你还没查到狼人，场上还没有人跳预言家。
- 好人大约还需要$survivalRounds 轮才能把狼人投干净（狼人约${wolfCount}个）。
【本回合建议】
可选策略 A（跳身份）："我是预言家，${targetName}是我的金水，大家不要怀疑他。"这样可以帮好人排除一个疑点，但也会暴露你的身份。
可选策略 B（继续隐藏）："我是平民/好人，没有信息。"继续查验积累更多信息，但没人知道你的查验结果。
推荐策略 B（隐藏）——还没查到狼，不急着暴露。如果${targetName}是疑似狼人的金水，可以适当保护。
【投票策略】根据场上发言判断，优先投发言疑点最大的玩家。
            """.trimIndent()
        } else {
            // 【还没查验】可以不跳
            val firstCheckTarget = state.alivePlayers
                .filter { it.seat != viewer.seat && it.alive }
                .minByOrNull { it.seat }
            """
【当前局势】你还没有进行任何查验，场上也没有人跳预言家。
【本回合建议】
可以不跳预言家（预言家不一定第一轮就要跳身份）。
发言可以给模糊的好人发言，例如："我是好人，目前还没拿到信息，先听大家怎么说。"
${firstCheckTarget?.let { "今晚查验建议：${it.seat + 1}号${it.displayName}。" } ?: ""}
【投票策略】根据场上发言判断，优先投发言疑点最大的人。如果有人发言特别像狼，可以投他。
            """.trimIndent()
        }
    }

    private fun buildWolfObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        wolves: List<GamePlayer>,
        seerClaimant: Int?,
        someoneElseClaimed: Boolean,
        someoneElseClaimedSeat: Int?,
        lastCheck: com.squemadylan.wolfcha.data.model.SeerHistoryEntry?,
        totalAlive: Int,
        wolfCount: Int,
        villageCount: Int,
        topVoteTarget: Int?
    ): String {
        val seat = viewer.seat + 1
        val wolfNames = wolves.joinToString("、") { "${it.seat + 1}号${it.displayName}" }
        val teammateCnt = wolves.size

        // 核心决策树：
        // 1. 场上有人跳预言家且查验到了你或队友 → 必须对跳！
        // 2. 场上有人跳预言家但没查到狼人团队 → 可以对跳争取主动，或隐藏
        // 3. 没人跳预言家 → 正常隐藏，偶尔冲锋或倒钩
        // 4. 被多人质疑/高票 → 必须反击

        // 检查是否有人跳预言家，并查验到了狼人团队
        val claimedSeerWhoCheckedUs = lastCheck?.let { check ->
            val isMe = check.targetSeat == viewer.seat
            val isTeammate = wolves.any { it.seat == check.targetSeat }
            isMe || isTeammate
        } ?: false

        return if (claimedSeerWhoCheckedUs) {
            // 被真预言家查杀了！必须对跳
            val checkedPlayer = lastCheck?.targetSeat?.plus(1) ?: "?"
            val checkedName = lastCheck?.let { "${it.targetSeat + 1}号${state.getPlayerBySeat(it.targetSeat)?.displayName ?: ""}" } ?: "?"
            """
【紧急局势】真预言家查验了${checkedName}是狼人！${if (lastCheck?.targetSeat == viewer.seat) "查验的是你自己！" else "查验的是你的队友！"}
【本回合你必须做的事情】
必须对跳预言家！发言格式："我是预言家，${someoneElseClaimedSeat?.plus(1) ?: "对跳者"}号是悍跳！我昨晚验了${if (lastCheck?.targetSeat == viewer.seat) "我自己" else checkedName}，他才是查杀！"
这是你唯一的生存机会——不跳就出局！跳了至少能让局势混乱，给队友争取轮次。
【投票策略】投真预言家（${someoneElseClaimedSeat?.plus(1) ?: "对跳者"}号）！投出预言家 = 消灭好人最大的信息来源。
【狼队配合】${if (teammateCnt > 0) "通知队友${wolfNames}：配合投票，一起投真预言家！" else "你是场上唯一狼人，尽量不要对跳，先保全自己。"}
            """.trimIndent()
        } else if (someoneElseClaimed && state.day <= 2 && Math.random() < 0.4) {
            // 有人跳了预言家，狼队可以选择对跳抢节奏
            val claimerSeat = (someoneElseClaimedSeat?.plus(1) ?: "?")
            """
【当前局势】${claimerSeat}号公开跳了预言家。
【狼队可选策略】
策略 A（对跳预言家）："我是预言家，${claimerSeat}号是悍跳！我昨晚验了X号是金水！"这是强攻策略，适合在真预言家还没查到你们的时候抢先出手。
策略 B（倒钩/隐藏）：假装站边${claimerSeat}号，说"我相信${claimerSeat}号"，倒钩进好人阵营混淆视听。适合队友被查杀时使用。
【推荐策略】${if (state.day == 1) "首日对跳风险大，建议先隐藏。" else "视场上情况决定，如果对跳收益大就对跳，否则倒钩。"}
【投票策略】${if (topVoteTarget != null) "目前最高票：${topVoteTarget + 1}号${state.getPlayerBySeat(topVoteTarget)?.displayName ?: ""}。" else ""} ${wolfNames.takeIf { wolves.isNotEmpty() }?.let { "狼队应集中投票同一目标。" } ?: "分散投票不利于狼队。"}
            """.trimIndent()
        } else {
            // 没人跳预言家，或者不需要立刻对跳
            val survivalRounds = minOf(villageCount, wolfCount)
            val suspiciousPlayer = topVoteTarget?.let { "${it + 1}号${state.getPlayerBySeat(it)?.displayName ?: ""}" }

            """
【当前局势】${totalAlive}人存活，狼${wolfCount}个 vs 好人${villageCount}个。狼队大约还需要$survivalRounds 轮获胜。
${if (teammateCnt > 0) "狼队友：${wolfNames}。" else "你是场上唯一狼人，务必低调！"}
【你的伪装策略】
狼人最重要的事是隐藏身份！发言要：
- 假装好人视角分析："我觉得${suspiciousPlayer ?: "目前票数最高的人"}发言有点可疑"
- 不要说任何狼人专属的话（如"我们知道谁是狼"）
- 可以偶尔质疑一个发言一般的玩家，混淆视听
${if (suspiciousPlayer != null) "【投票建议】可以暗示投${suspiciousPlayer}，但不一定要自己投他" else "【投票建议】观察场上投票方向，选择对自己最有利的票型"}
【轮次策略】如果预言家跳出来了，要注意他报的方向；如果没人跳预言家，就按普通好人的逻辑发言。
            """.trimIndent()
        }
    }

    private fun buildWitchObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        healUsed: Boolean,
        poisonUsed: Boolean,
        wolfTarget: Int?,
        wolfVictimName: String,
        someoneElseClaimed: Boolean,
        someoneElseClaimedSeat: Int?,
        totalAlive: Int,
        villageCount: Int
    ): String {
        val seat = viewer.seat + 1
        val seerClaimant = someoneElseClaimedSeat?.plus(1) ?: "?"

        return when {
            healUsed && poisonUsed -> """
【你的状态】解药已用，毒药已用。女巫药已耗尽，你现在是半个平民。
【发言策略】按平民逻辑发言即可，可以说"我是好人，没有更多信息了"。
【投票策略】根据场上发言投票，优先投发言最可疑的玩家。
            """.trimIndent()

            !healUsed && wolfTarget != null && !state.phase.isNightPhase() -> {
                // 夜晚已结束，看清了狼人刀了谁
                """
【昨夜狼人刀人】${wolfVictimName}
【你的药情况】解药：${if (healUsed) "已用" else "可用（狼刀目标：${wolfVictimName}）"}；毒药：${if (poisonUsed) "已用" else "可用"}
【本回合关键决策】
1. ${if (!healUsed) "狼人刀了${wolfVictimName}，你要救吗？（解药可以救他）" else "解药已用，无法救人"}
2. ${if (!poisonUsed) "毒药还没用，可以考虑毒死发言最可疑的人" else "毒药已用"}
3. 如果${wolfVictimName}是你认识的预言家（有人跳了预言家且预言家还没死），应该救！
【发言策略】
${if (!healUsed && wolfVictimName != "无") "可以说：'昨晚${wolfVictimName}被刀了，我救了，银水。'" else "不要暴露女巫身份，可以说'我是好人'"}
${if (!poisonUsed && !someoneElseClaimed) "如果有人跳预言家很可疑，可以考虑用毒药。" else ""}
【投票策略】根据场上发言判断。
                """.trimIndent()
            }

            else -> """
【你的药情况】解药：${if (healUsed) "已用" else "可用"}；毒药：${if (poisonUsed) "已用" else "可用"}
【发言策略】
- 女巫通常不需要第一轮就跳身份，可以暗示"我有身份"但不说明是什么。
- ${if (someoneElseClaimed) "有人跳了预言家（${seerClaimant}号），${seerClaimant}号可能是真预言家，你可以考虑保他。" else "目前没人跳预言家，先听发言。"}
- 如果有人质疑你，可以威慑："我有药，投我小心。"但不要明说是女巫。
【投票策略】优先投发言疑点最大的人；如果有人跳预言家被对跳，投对跳者。
            """.trimIndent()
        }
    }

    private fun buildHunterObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        someoneElseClaimed: Boolean,
        someoneElseClaimedSeat: Int?,
        topVoteTarget: Int?
    ): String {
        val seat = viewer.seat + 1
        val seerSeat = someoneElseClaimedSeat?.plus(1) ?: "?"

        return """
【你的角色】猎人——被投出局或被刀死时可以开枪带走一人（被女巫毒死则不能开枪）。
【当前局势】
${if (someoneElseClaimed) "有人跳了预言家（${seerSeat}号），${seerSeat}号可能是真预言家。" else "目前没人跳预言家。"}
${if (topVoteTarget != null) "当前票数最高：${topVoteTarget + 1}号${state.getPlayerBySeat(topVoteTarget)?.displayName ?: ""}。" else ""}
【发言策略】
- 猎人一般强势发言但不轻易暴露身份。可以说"我是强神，敢投我小心"来威慑。
- ${if (someoneElseClaimed) "可以站边${seerSeat}号，理由是'${seerSeat}号的发言逻辑清晰，我站他'。" else "先观察，等预言家跳出来再决定站边。"}
- 如果有人对跳预言家且你相信另一方，可以在发言中暗示"我是强神"。
【投票策略】
优先跟票高票者；如果有人对跳预言家，投你认为是假的那一方。
【开枪策略】若被投出局，开枪带走你最怀疑是狼人的玩家（而非随机带走）。
        """.trimIndent()
    }

    private fun buildGuardObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        lastGuard: Int?,
        lastNightDead: Int?,
        someoneElseClaimed: Boolean,
        someoneElseClaimedSeat: Int?
    ): String {
        val seat = viewer.seat + 1
        val lastGuardName = lastGuard?.let { "${it + 1}号${state.getPlayerBySeat(it)?.displayName ?: ""}" } ?: "无"
        val seerSeat = someoneElseClaimedSeat?.plus(1) ?: "?"

        return """
【你的角色】守卫——每晚守护一名玩家免受狼人袭击，不能连续两晚守护同一人。
【你的历史守护】上一晚守护：${lastGuardName}
${if (lastNightDead != null) "昨夜${lastNightDead + 1}号${state.getPlayerBySeat(lastNightDead)?.displayName ?: ""}死亡了。" else "昨夜平安夜（要么被女巫救了要么被守了）。"}
【本回合策略】
1. ${if (someoneElseClaimed) "跳了预言家（${seerSeat}号），优先守护他！预言家是最有价值的信息来源。" else "可以先守自己或守身份未明的好人。"}
2. 不能连续两晚守护同一人——如果上一晚守了X号，今晚必须换人。
3. 如果预言家跳出来了，守护预言家是最优策略。
【发言策略】
- 守卫通常不需要跳身份，可以说"我是好人，没有信息"。
- 可以暗示"我有身份"，但不说明是什么。
【投票策略】根据场上发言判断，优先投发言疑点最大的玩家。
        """.trimIndent()
    }

    private fun buildVillagerObjective(
        viewer: GamePlayer,
        state: WolfchaGameState,
        someoneElseClaimed: Boolean,
        someoneElseClaimedSeat: Int?,
        topVoteTarget: Int?,
        totalAlive: Int,
        wolfCount: Int,
        villageCount: Int
    ): String {
        val seat = viewer.seat + 1
        val survivalRounds = minOf(villageCount, wolfCount)

        return """
【你的角色】平民——没有夜间技能，靠发言和投票帮助好人找出狼人。
【场上局势】
${totalAlive}人存活，狼约${wolfCount}个，好人约${villageCount}个。好人大约还需要$survivalRounds 轮才能把狼人全部投出。
${if (someoneElseClaimed) "有人跳了预言家（${someoneElseClaimedSeat?.plus(1) ?: "?"}号）！" else "目前还没人跳预言家。"}
${if (topVoteTarget != null) "当前票数最高：${topVoteTarget + 1}号${state.getPlayerBySeat(topVoteTarget)?.displayName ?: ""}。" else ""}
【核心任务】认真分析场上每一个人的发言，关注：
1. ${if (someoneElseClaimed) "${someoneElseClaimedSeat?.plus(1) ?: "跳预言家者"}的发言是否有逻辑？他是真预言家还是对跳的狼人？" else "谁会在第一轮就跳预言家？"}
2. 投票方向：谁投了谁？有没有人投票方向异常（跟着狼人冲票）？
3. 发言一致性：有没有人前后矛盾、回避关键问题？
【投票策略】
${if (someoneElseClaimed) "如果${someoneElseClaimedSeat?.plus(1) ?: "跳预言家者"}的逻辑清晰，站边他；如果对跳预言家有两个人，投其中逻辑较弱的那一个。" else "优先投发言最可疑的人。"}
${if (topVoteTarget != null) "当前票数最高：${topVoteTarget + 1}号${state.getPlayerBySeat(topVoteTarget)?.displayName ?: ""}，如果你也觉得这人可疑，可以跟票。" else ""}
【重要心态】不要轻易站边，要等更多信息。预言家的查验+逻辑是判断的核心。
        """.trimIndent()
    }

    // ==========================================================================
    // 辅助函数
    // ==========================================================================

    /** 从公开发言中找跳预言家的玩家座位号（只看存活玩家的发言） */
    private fun findSeerClaimant(state: WolfchaGameState): Int? {
        val claimant = state.messages
            .takeLast(30)
            .lastOrNull { msg ->
                val player = state.getPlayerById(msg.playerId)
                if (player?.alive != true) false
                else isExplicitSeerClaim(msg.content)
            }
        return claimant?.let { state.getPlayerById(it.playerId)?.seat }
    }

    /** 判断一条发言是否是发言者本人跳预言家 —— 排除引用/讨论他人的情况 */
    private fun isExplicitSeerClaim(content: String): Boolean {
        // 第一关：必须包含"我是预言家"或"我跳预言家"
        if (!content.contains("我是预言家") && !content.contains("我跳预言家")) {
            return false
        }
        // 第二关：排除转述/讨论他人的情况
        if (content.contains("他说") || content.contains("有人说") ||
            content.contains("跳了预言家") || content.contains("说他是预言家") ||
            content.contains("没人跳预言家") || content.contains("没人跳预言") ||
            content.contains("还没跳预言") || content.contains("还没人跳预言") ||
            content.contains("还没跳") || content.contains("还没人跳")
        ) {
            return false
        }
        // 第三关：必须以明确的自我声明格式开头
        val hasExplicitPrefix = content.startsWith("我是预言家") ||
            content.startsWith("我跳预言家") ||
            content.startsWith("那我是预言家") ||
            content.startsWith("但我是预言家")
        return hasExplicitPrefix
    }

    /** 场上是否有人跳了预言家 */
    private fun someoneElseClaimed(state: WolfchaGameState): Boolean = findSeerClaimant(state) != null

    /** 当前轮得票最高的玩家座位号 */
    private fun findTopVoteTarget(state: WolfchaGameState): Int? =
        state.voteTargetCounts.entries.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

    private fun Role.isWolfLike(): Boolean =
        this == Role.Werewolf || this == Role.WhiteWolfKing
}

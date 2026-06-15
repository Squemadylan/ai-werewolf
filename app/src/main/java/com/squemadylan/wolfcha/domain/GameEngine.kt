package com.squemadylan.wolfcha.domain

import com.squemadylan.wolfcha.data.model.*
import com.squemadylan.wolfcha.data.remote.LlmService
import com.squemadylan.wolfcha.data.remote.WerewolfPromptBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

class GameEngine(
    private val llmService: LlmService = LlmService()
) {

    private val _gameState = MutableStateFlow(WolfchaGameState())
    val gameState: StateFlow<WolfchaGameState> = _gameState.asStateFlow()

    @Volatile
    private var llmConfigProvider: () -> LlmConfig = { LlmConfig() }

    fun setLlmConfigProvider(provider: () -> LlmConfig) {
        llmConfigProvider = provider
    }

    fun createGame(settings: GameSettings = GameSettings()): WolfchaGameState {
        val state = WolfchaGameState(
            gameId = UUID.randomUUID().toString(),
            phase = Phase.SETUP,
            day = 0,
            difficulty = settings.difficulty,
            isSpectatorMode = settings.isSpectatorMode
        )
        _gameState.value = state
        return state
    }

    fun setupPlayers(settings: GameSettings): WolfchaGameState {
        val playerCount = settings.playerCount
        val roles = GameConfig.getRoleConfiguration(playerCount).shuffled()
        val personaPool = settings.aiPersonaPool.ifEmpty {
            AiPersonaProfile.defaultPool(playerCount - 1)
        }.shuffled()

        val players = mutableListOf<GamePlayer>()

        // Create human player
        val humanSeat = Random.nextInt(playerCount)
        val humanRole = settings.preferredRole ?: roles[humanSeat]

        players.add(
            GamePlayer(
                playerId = "human_${UUID.randomUUID()}",
                seat = humanSeat,
                displayName = settings.humanName,
                avatarSeed = "human_${Random.nextInt(1000)}",
                alive = true,
                role = humanRole,
                alignment = humanRole.getAlignment(),
                isHuman = true
            )
        )

        // Create AI players
        var aiIndex = 0
        for (seat in 0 until playerCount) {
            if (seat == humanSeat) continue

            val role = roles[seat]
            val personaProfile = personaPool.getOrElse(aiIndex) {
                AiPersonaProfile.defaultPool(1).first()
            }
            players.add(
                GamePlayer(
                    playerId = "ai_${UUID.randomUUID()}",
                    seat = seat,
                    displayName = personaProfile.displayName.ifBlank { "AI${aiIndex + 1}" },
                    avatarSeed = "ai_${Random.nextInt(1000)}",
                    avatarKey = personaProfile.avatarKey,
                    alive = true,
                    role = role,
                    alignment = role.getAlignment(),
                    isHuman = false,
                    agentProfile = AgentProfile(
                        persona = personaProfile.toPersona()
                    )
                )
            )
            aiIndex++
        }

        val sortedPlayers = players.sortedBy { it.seat }
        val newState = _gameState.value.copy(
            players = sortedPlayers,
            phase = Phase.NIGHT_START,
            day = 1
        )

        _gameState.value = newState
        return newState
    }

    fun transitionPhase(newPhase: Phase): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            phase = newPhase,
            events = currentState.events + GameEvent(
                type = GameEventType.PHASE_CHANGED,
                payload = mapOf(
                    "from" to currentState.phase.name,
                    "to" to newPhase.name,
                    "day" to currentState.day.toString()
                )
            )
        )
        _gameState.value = newState
        return newState
    }

    fun setDaySpeechStartSeat(seat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            daySpeechStartSeat = seat,
            currentSpeakerSeat = seat
        )
        _gameState.value = newState
        return newState
    }

    fun addSystemMessage(content: String, day: Int? = null): WolfchaGameState {
        val currentState = _gameState.value
        val message = ChatMessage(
            playerId = "system",
            playerName = "系统",
            content = content,
            day = day ?: currentState.day,
            phase = currentState.phase,
            isSystem = true
        )
        val newState = currentState.copy(
            messages = currentState.messages + message,
            events = currentState.events + GameEvent(
                type = GameEventType.SYSTEM_MESSAGE,
                payload = mapOf("content" to content)
            )
        )
        _gameState.value = newState
        return newState
    }

    fun addPlayerMessage(playerId: String, content: String): WolfchaGameState {
        val currentState = _gameState.value
        val player = currentState.getPlayerById(playerId) ?: return currentState

        val message = ChatMessage(
            playerId = playerId,
            playerName = player.displayName,
            content = content,
            day = currentState.day,
            phase = currentState.phase
        )
        val newState = currentState.copy(
            messages = currentState.messages + message
        )
        _gameState.value = newState
        return newState
    }

    fun performNightActionGuard(targetSeat: Int): WolfchaGameState {
        val currentState = _gameState.value
        // 历史记录统一在 resolveNight() 中写入，避免重复记录
        val newState = currentState.copy(
            nightActions = currentState.nightActions.copy(
                guardTarget = targetSeat,
                lastGuardTarget = targetSeat
            )
        )
        _gameState.value = newState
        return newState
    }

    fun performNightActionWolf(targetSeat: Int): WolfchaGameState {
        val currentState = _gameState.value
        // 历史记录（含是否真正击杀）统一在 resolveNight() 中写入，避免重复记录
        val newState = currentState.copy(
            nightActions = currentState.nightActions.copy(
                wolfTarget = targetSeat
            )
        )
        _gameState.value = newState
        return newState
    }

    fun performNightActionWitch(save: Boolean? = null, poisonTarget: Int? = null): WolfchaGameState {
        val currentState = _gameState.value
        var newNightActions = currentState.nightActions
        var newRoleAbilities = currentState.roleAbilities

        if (save == true) {
            newNightActions = newNightActions.copy(witchSave = true)
            newRoleAbilities = newRoleAbilities.copy(witchHealUsed = true)
        }
        if (poisonTarget != null) {
            newNightActions = newNightActions.copy(witchPoison = poisonTarget)
            newRoleAbilities = newRoleAbilities.copy(witchPoisonUsed = true)
        }

        val newState = currentState.copy(
            nightActions = newNightActions,
            roleAbilities = newRoleAbilities
        )
        _gameState.value = newState
        return newState
    }

    fun performNightActionSeer(targetSeat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val targetPlayer = currentState.getPlayerBySeat(targetSeat)
        val isWolf = targetPlayer?.role?.isWolfRole() ?: false

        val newSeerHistory = currentState.nightActions.seerHistory + SeerHistoryEntry(
            targetSeat = targetSeat,
            isWolf = isWolf,
            day = currentState.day
        )

        val newState = currentState.copy(
            nightActions = currentState.nightActions.copy(
                seerTarget = targetSeat,
                seerResult = SeerResult(targetSeat, isWolf),
                seerHistory = newSeerHistory
            )
        )
        _gameState.value = newState
        return newState
    }

    fun resolveNight(): Pair<WolfchaGameState, List<Pair<Int, String>>> {
        val currentState = _gameState.value
        val nightActions = currentState.nightActions
        val day = currentState.day

        var roleAbilities = currentState.roleAbilities

        val wolfTarget = nightActions.wolfTarget
        val guardTarget = nightActions.guardTarget
        val witchSave = nightActions.witchSave
        val witchPoison = nightActions.witchPoison

        val deaths = computeNightDeaths(nightActions).toMutableList()

        // === 记录夜间行动历史（保留，不会被 reset）===
        val newWolfKillHistory = nightActions.wolfKillHistory.toMutableList()
        if (wolfTarget != null) {
            val actuallyKilled = wolfTarget != guardTarget && witchSave != true
            newWolfKillHistory.add(WolfKillEntry(day, wolfTarget, actuallyKilled))
        }

        val newWitchActionHistory = nightActions.witchActionHistory.toMutableList()
        if (witchSave != null || witchPoison != null) {
            newWitchActionHistory.add(
                WitchActionEntry(
                    day,
                    if (witchSave == true) wolfTarget else null,
                    witchPoison
                )
            )
        }

        val newGuardActionHistory = nightActions.guardActionHistory.toMutableList()
        if (guardTarget != null) {
            newGuardActionHistory.add(GuardActionEntry(day, guardTarget))
        }

        val newPlayers = currentState.players.toMutableList()
        for ((seat, reason) in deaths) {
            val index = newPlayers.indexOfFirst { it.seat == seat }
            if (index != -1) {
                val player = newPlayers[index]
                newPlayers[index] = player.copy(alive = false)
                if (player.role == Role.Hunter && reason == "poison") {
                    roleAbilities = roleAbilities.copy(hunterCanShoot = false)
                }
            }
        }

        val deathPairsForStorage = deaths.map { (seat, _) ->
            seat to "死亡"
        }

        val newNightDeaths = currentState.nightDeaths.toMutableMap()
        newNightDeaths[currentState.day] = deathPairsForStorage

        val newState = currentState.copy(
            players = newPlayers,
            phase = Phase.DAY_START,
            // === reset 当夜晚的临时字段，但保留历史记录 ===
            nightActions = NightActions(
                lastGuardTarget = nightActions.guardTarget,
                seerHistory = nightActions.seerHistory,
                wolfKillHistory = newWolfKillHistory,
                witchActionHistory = newWitchActionHistory,
                guardActionHistory = newGuardActionHistory
            ),
            roleAbilities = roleAbilities,
            nightDeaths = newNightDeaths
        )

        _gameState.value = newState

        return _gameState.value to deaths.toList()
    }

    fun castVote(voterId: String, targetSeat: Int): WolfchaGameState {
        val currentState = _gameState.value
        // 已翻牌白痴永久失去投票权
        val voter = currentState.getPlayerById(voterId)
        if (voter != null && voter.role == Role.Idiot && currentState.roleAbilities.idiotRevealed) {
            return currentState
        }

        val newVotes = currentState.votes.toMutableMap()
        newVotes[voterId] = targetSeat

        val newState = currentState.copy(votes = newVotes)
        _gameState.value = newState
        return newState
    }

    /** 该玩家当前是否有投票权（已翻牌白痴没有）。 */
    fun canVote(player: GamePlayer): Boolean = canVote(player, _gameState.value)

    fun resolveVotes(): Pair<WolfchaGameState, Int?> {
        val currentState = _gameState.value
        val votes = currentState.votes

        if (votes.isEmpty()) {
            return currentState.copy(phase = Phase.DAY_RESOLVE) to null
        }

        val (executedSeat, _) = computeExecutedSeat(votes)

        return if (executedSeat == null) {
            val newState = currentState.copy(
                phase = Phase.DAY_RESOLVE,
                voteHistory = currentState.voteHistory + (currentState.day to votes)
            )
            _gameState.value = newState
            newState to null
        } else {
            val voteCount = votes.values.count { it == executedSeat }
            val newExecutedHistory = currentState.executedHistory.toMutableMap()
            newExecutedHistory[currentState.day] = executedSeat
            val reason = "被投票出局（${voteCount}票）"
            val newReasonHistory = currentState.executedReasonHistory.toMutableMap()
            newReasonHistory[currentState.day] = reason

            val newState = currentState.copy(
                phase = Phase.DAY_RESOLVE,
                voteHistory = currentState.voteHistory + (currentState.day to votes),
                executedHistory = newExecutedHistory,
                executedReasonHistory = newReasonHistory
            )
            _gameState.value = newState
            newState to executedSeat
        }
    }

    fun clearVotes(): WolfchaGameState {
        val newState = _gameState.value.copy(votes = emptyMap())
        _gameState.value = newState
        return newState
    }

    fun setBadgeHolder(seat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            badge = currentState.badge.copy(holderSeat = seat)
        )
        _gameState.value = newState
        return newState
    }

    /** 设置警长竞选的上警候选人（座位号集合）。 */
    fun setBadgeCandidates(seats: List<Int>): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            badge = currentState.badge.copy(candidates = seats.distinct().sorted())
        )
        _gameState.value = newState
        return newState
    }

    /** 追加一名上警候选人。 */
    fun addBadgeCandidate(seat: Int): WolfchaGameState {
        val currentState = _gameState.value
        if (seat in currentState.badge.candidates) return currentState
        val newState = currentState.copy(
            badge = currentState.badge.copy(
                candidates = (currentState.badge.candidates + seat).distinct().sorted()
            )
        )
        _gameState.value = newState
        return newState
    }

    fun revealIdiot(seat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            roleAbilities = currentState.roleAbilities.copy(idiotRevealed = true)
        )
        _gameState.value = newState
        return newState
    }

    fun markHunterShotUsed(): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            roleAbilities = currentState.roleAbilities.copy(hunterCanShoot = false)
        )
        _gameState.value = newState
        return newState
    }

    /**
     * 屠边胜负判定：
     * - 狼人全部死亡 → 好人胜
     * - 全部神职死亡（屠神）或 全部平民死亡（屠民） → 狼人胜
     * - 否则游戏继续（不提前结束）
     *
     * 白痴归类为神职。
     */
    fun checkWinCondition(): Alignment? = evaluateWinner(_gameState.value)

    fun killPlayer(seat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val newPlayers = currentState.players.toMutableList()
        val index = newPlayers.indexOfFirst { it.seat == seat }
        if (index != -1) {
            newPlayers[index] = newPlayers[index].copy(alive = false)
        }

        val player = currentState.getPlayerBySeat(seat)
        val newState = currentState.copy(
            players = newPlayers,
            lastExecutedSeat = seat,
            events = currentState.events + GameEvent(
                type = GameEventType.PLAYER_DIED,
                payload = mapOf(
                    "seat" to seat.toString(),
                    "playerName" to (player?.displayName ?: "")
                )
            )
        )
        _gameState.value = newState
        return newState
    }

    fun setWinner(winner: Alignment): WolfchaGameState {
        val currentState = _gameState.value
        val newState = currentState.copy(
            winner = winner,
            phase = Phase.GAME_END
        )
        _gameState.value = newState
        return newState
    }

    fun resetGame() {
        _gameState.value = WolfchaGameState()
    }

    /**
     * Advance the internal state to the next day. Used by the ViewModel
     * after a vote has been resolved so that phase becomes NIGHT_START
     * without having to expose the MutableStateFlow.
     */
    fun advanceToNextDay(): WolfchaGameState {
        val current = _gameState.value
        val next = current.copy(
            day = current.day + 1,
            votes = emptyMap(),
            phase = Phase.NIGHT_START
        )
        _gameState.value = next
        return next
    }

    fun setPaused(paused: Boolean): WolfchaGameState {
        val newState = _gameState.value.copy(isPaused = paused)
        _gameState.value = newState
        return newState
    }

    /**
     * Generate a speech string for the given AI player. When the user has
     * configured an LLM, this calls the remote model with a role-aware prompt.
     * Otherwise it falls back to a small set of built-in role templates so the
     * game still plays out of the box.
     */
    suspend fun generateAISpeech(playerId: String): String {
        val currentState = _gameState.value
        val player = currentState.getPlayerById(playerId) ?: return "..."

        val config = llmConfigProvider()
        if (config.isReady) {
            val difficulty = currentState.difficulty
            val (system, user) = WerewolfPromptBuilder.buildSpeechPrompt(currentState, player)
            val temperature = when (difficulty) {
                DifficultyLevel.EASY -> 0.9f
                DifficultyLevel.NORMAL -> 0.7f
                DifficultyLevel.HARD -> 0.4f
            }
            val result = llmService.chat(
                config = config.copy(temperature = temperature),
                messages = listOf(
                    LlmService.Message(role = "system", content = system),
                    LlmService.Message(role = "user", content = user)
                ),
                isolationKey = "${currentState.gameId}_${player.playerId}"
            )
            when (result) {
                is LlmService.Result.Success -> {
                    val cleaned = SpeechSanitizer.sanitize(result.content)
                    if (SpeechSanitizer.isAcceptableChineseSpeech(cleaned)) return cleaned
                }
                is LlmService.Result.Failure -> {
                    addSystemMessage("[${player.displayName}] 模型调用失败：${result.message}")
                }
            }
        }
        return fallbackSpeech(player, currentState)
    }

    // ==========================================================================
    // Fallback 发言（与 PlayerKnowledgeScope 策略保持一致）
    // ==========================================================================

    private fun fallbackSpeech(player: GamePlayer, state: WolfchaGameState): String {
        return when (player.role) {
            Role.Villager, Role.Idiot -> generateVillagerSpeechV3(player, state)
            Role.Werewolf, Role.WhiteWolfKing -> generateWolfSpeechV3(player, state)
            Role.Seer -> generateSeerSpeechV3(player, state)
            Role.Witch -> generateWitchSpeechV3(player, state)
            Role.Hunter -> generateHunterSpeechV3(player, state)
            Role.Guard -> generateGuardSpeechV3(player, state)
        }
    }

    /** 从发言历史中找最近跳预言家的存活玩家 - 更严格的语义识别 */
    private fun seerClaimantFromHistory(state: WolfchaGameState): Int? {
        val msg = state.messages.takeLast(30).lastOrNull { msg ->
            val player = state.getPlayerById(msg.playerId)
            if (player?.alive != true) false
            else isExplicitSeerClaim(msg.content)
        }
        return msg?.let { state.getPlayerById(it.playerId)?.seat }
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

    /** 当前票数最高者（仅座位号） */
    private fun topVoteFromCounts(state: WolfchaGameState): Int? =
        state.voteTargetCounts.entries.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

    private fun generateVillagerSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val seerClaimant = seerClaimantFromHistory(state)
        val topVote = topVoteFromCounts(state)
        val lastExecuted = state.executedHistory[state.day - 1]
        val lastName = lastExecuted?.let { state.getPlayerBySeat(it)?.displayName ?: "" } ?: ""
        val lastCount = state.voteHistory[state.day - 1]?.values?.count { it == lastExecuted } ?: 0

        val lines = mutableListOf<String>()
        lines.add("我是${seat}号${player.displayName}，平民。")

        if (seerClaimant != null) {
            val seerName = state.getPlayerBySeat(seerClaimant)?.displayName ?: ""
            lines.add("有人跳预言家（${seerClaimant + 1}号${seerName}），我看他的逻辑再说。")
        } else {
            lines.add("还没人跳预言家，先听发言再判断。")
        }

        if (lastExecuted != null && state.day >= 2) {
            lines.add("昨天${lastExecuted + 1}号$lastName 拿了$lastCount 票出局。")
        }

        topVote?.let { tv ->
            if (tv != player.seat) {
                val tvName = state.getPlayerBySeat(tv)?.displayName ?: ""
                lines.add("目前${tv + 1}号$tvName 票最高，值得关注。")
            }
        }

        return lines.shuffled().take(2).joinToString(" ")
    }

    private fun generateWolfSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val wolves = state.players.filter {
            it.playerId != player.playerId && it.alive && it.role.isWolfRole()
        }
        val wolfNames = wolves.joinToString("、") { "${it.seat + 1}号${it.displayName}" }
        val seerClaimant = seerClaimantFromHistory(state)
        val lastCheck = state.nightActions.seerHistory.lastOrNull()
        val topVote = topVoteFromCounts(state)

        // 被真预言家查杀了？必须对跳！
        val checkedUs = lastCheck?.let { check ->
            check.isWolf && (check.targetSeat == player.seat || wolves.any { it.seat == check.targetSeat })
        } ?: false

        return if (checkedUs && seerClaimant != null) {
            val seerName = state.getPlayerBySeat(seerClaimant)?.displayName ?: ""
            val checkedName = lastCheck?.let { "${it.targetSeat + 1}号${state.getPlayerBySeat(it.targetSeat)?.displayName ?: ""}" } ?: "?"
            if (lastCheck?.targetSeat == player.seat) {
                "我是${seat}号${player.displayName}，我是预言家！${seerClaimant + 1}号$seerName 是悍跳，我才是真的！昨夜我验了${lastCheck.targetSeat + 1}号$checkedName 是狼！请出${seerClaimant + 1}号$seerName！"
            } else {
                "我是${seat}号${player.displayName}，我是预言家！${seerClaimant + 1}号$seerName 是悍跳！${lastCheck?.targetSeat?.plus(1) ?: "?"}号$checkedName 是我的金水，请出${seerClaimant + 1}号$seerName！"
            }
        } else if (seerClaimant != null && state.day <= 2 && Math.random() < 0.35) {
            val seerName = state.getPlayerBySeat(seerClaimant)?.displayName ?: ""
            "我是${seat}号${player.displayName}，预言家！${seerClaimant + 1}号$seerName 悍跳！"
        } else {
            val lines = mutableListOf<String>()
            lines.add("我是${seat}号${player.displayName}，平民。")
            topVote?.let { tv ->
                if (tv != player.seat && !wolves.any { it.seat == tv }) {
                    val tvName = state.getPlayerBySeat(tv)?.displayName ?: ""
                    lines.add("目前${tv + 1}号$tvName 票最高，我也在关注他。")
                }
            }
            lines.add("先听预言家发言，站边再决定。")
            lines.shuffled().take(2).joinToString(" ")
        }
    }

    private fun generateSeerSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val history = state.nightActions.seerHistory
        val lastCheck = history.lastOrNull()
        val seerClaimant = seerClaimantFromHistory(state)
        val hasWolf = lastCheck?.isWolf == true
        val hasCheck = history.isNotEmpty()
        val someoneClaimed = seerClaimant != null && seerClaimant != player.seat

        return when {
            hasWolf && hasCheck -> {
                val target = lastCheck?.targetSeat?.let { state.getPlayerBySeat(it) }
                val tName = target?.displayName ?: ""
                val tSeat = lastCheck?.targetSeat?.plus(1) ?: "?"
                val nextCheck = state.alivePlayers
                    .filter { it.seat != player.seat && it.seat != lastCheck?.targetSeat && it.alive }
                    .minByOrNull { it.seat }
                val next = nextCheck?.let { "接下来我会查${it.seat + 1}号${it.displayName}。" } ?: ""
                if (someoneClaimed) {
                    val claimer = state.getPlayerBySeat(seerClaimant!!)
                    "我是${seat}号${player.displayName}，我是预言家！${seerClaimant + 1}号${claimer?.displayName ?: ""} 悍跳！昨夜我查了${tSeat}号$tName 是查杀，请出他！$next"
                } else {
                    "我是${seat}号${player.displayName}，我是预言家！昨夜查验${tSeat}号$tName 是查杀！${next}请大家出${tSeat}号！"
                }
            }
            someoneClaimed -> {
                val claimer = state.getPlayerBySeat(seerClaimant!!)
                if (hasCheck && !hasWolf) {
                    val tName = lastCheck?.targetSeat?.let { "${it + 1}号${state.getPlayerBySeat(it)?.displayName ?: ""}" } ?: "?"
                    "我是${seat}号${player.displayName}，我是预言家！${seerClaimant + 1}号${claimer?.displayName ?: ""} 悍跳！昨夜我验了$tName 是金水，大家相信我！"
                } else {
                    "我是${seat}号${player.displayName}，预言家！${seerClaimant + 1}号${claimer?.displayName ?: ""} 是悍跳！请大家相信我！"
                }
            }
            hasCheck && !hasWolf -> {
                val tName = lastCheck?.targetSeat?.let { "${it + 1}号${state.getPlayerBySeat(it)?.displayName ?: ""}" } ?: "?"
                "我是${seat}号${player.displayName}，好人，${tName}是我的金水，先听大家发言。"
            }
            else -> {
                "我是${seat}号${player.displayName}，好人，先听大家怎么说。"
            }
        }
    }

    private fun generateWitchSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val healUsed = state.roleAbilities.witchHealUsed
        val poisonUsed = state.roleAbilities.witchPoisonUsed
        val wolfTarget = state.nightActions.wolfTarget
        val seerClaimant = seerClaimantFromHistory(state)

        return when {
            healUsed && poisonUsed -> "我是${seat}号${player.displayName}，药没了，先听发言。"
            healUsed -> "我是${seat}号${player.displayName}，我有身份，毒药还在。"
            poisonUsed -> "我是${seat}号${player.displayName}，我有身份，解药还在。"
            wolfTarget != null && !state.phase.isNightPhase() -> {
                val wName = state.getPlayerBySeat(wolfTarget)?.displayName ?: ""
                "我是${seat}号${player.displayName}，女巫。昨夜${wolfTarget + 1}号$wName 被刀了，我救了。"
            }
            else -> {
                val lines = mutableListOf<String>()
                lines.add("我是${seat}号${player.displayName}，我有身份。")
                seerClaimant?.let {
                    val sName = state.getPlayerBySeat(it)?.displayName ?: ""
                    lines.add("有人跳预言家（${it + 1}号$sName），我听他怎么说。")
                }
                lines.shuffled().take(2).joinToString(" ")
            }
        }
    }

    private fun generateHunterSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val seerClaimant = seerClaimantFromHistory(state)
        return if (seerClaimant != null) {
            val sName = state.getPlayerBySeat(seerClaimant)?.displayName ?: ""
            "我是${seat}号${player.displayName}，猎人。我站边${seerClaimant + 1}号$sName，${seerClaimant + 1}号的逻辑清晰。"
        } else {
            "我是${seat}号${player.displayName}，强神，有身份。先听预言家发言再说。"
        }
    }

    private fun generateGuardSpeechV3(player: GamePlayer, state: WolfchaGameState): String {
        val seat = player.seat + 1
        val seerClaimant = seerClaimantFromHistory(state)
        return if (seerClaimant != null) {
            val sName = state.getPlayerBySeat(seerClaimant)?.displayName ?: ""
            "我是${seat}号${player.displayName}，守卫。昨夜我守了${seerClaimant + 1}号$sName，他平安夜。"
        } else {
            "我是${seat}号${player.displayName}，守卫。今晚我会守人。"
        }
    }

    /** 根据某玩家的视角和当前票数，给出投票建议目标（用于 AI 投票决策） */
    fun suggestVoteTarget(playerId: String): Int? {
        val state = _gameState.value
        val player = state.getPlayerById(playerId) ?: return null
        val aliveOthers = state.alivePlayers.filter { it.playerId != playerId }

        // 预言家优先投查到的狼
        if (player.role == Role.Seer) {
            val wolfEntry = state.nightActions.seerHistory
                .filter { it.isWolf }
                .firstOrNull { entry ->
                    state.getPlayerBySeat(entry.targetSeat)?.alive == true
                }
            if (wolfEntry != null) return wolfEntry.targetSeat
        }

        // 狼人优先投当前得票最高的非狼玩家（从众 + 保队友）
        if (player.role.isWolfRole()) {
            val nonWolfTargets = state.voteTargetCounts.entries
                .filter { (seat, _) ->
                    val p = state.getPlayerBySeat(seat)
                    p != null && !p.role.isWolfRole()
                }
                .sortedByDescending { it.value }

            if (nonWolfTargets.isNotEmpty()) return nonWolfTargets.first().key

            val nonWolf = aliveOthers.filter { !it.role.isWolfRole() }
            if (nonWolf.isNotEmpty()) return nonWolf.random().seat
        }

        // 其他人：从众投票当前票数最多的人；没有就随机
        val maxTarget = state.voteTargetCounts.entries
            .maxByOrNull { it.value }
        if (maxTarget != null && maxTarget.value > 0) return maxTarget.key

        return aliveOthers.randomOrNull()?.seat
    }

    // ==========================================================================
    // 纯函数（无状态副作用，便于单元测试）
    // ==========================================================================
    companion object {

        /**
         * 屠边胜负判定：
         * - 狼人全部死亡 → 好人胜
         * - 全部神职死亡（屠神）或全部平民死亡（屠民） → 狼人胜
         * - 否则返回 null（游戏继续）
         * 白痴归类为神职。
         */
        fun evaluateWinner(state: WolfchaGameState): Alignment? {
            val aliveWolves = state.players.count { it.alive && it.role.isWolfRole() }
            if (aliveWolves == 0) return Alignment.VILLAGE

            val aliveGods = state.players.count { it.alive && it.role.isGodRole() }
            val aliveCivilians = state.players.count { it.alive && it.role.isCivilianRole() }
            return if (aliveGods == 0 || aliveCivilians == 0) Alignment.WOLF else null
        }

        /**
         * 计算一夜的死亡（座位号 to 死因）。
         * - 狼刀目标：若未被守卫守护且未被女巫解药救，则死亡（reason="wolf"）
         * - 女巫毒药目标：死亡（reason="poison"）
         */
        fun computeNightDeaths(nightActions: NightActions): List<Pair<Int, String>> {
            val deaths = mutableListOf<Pair<Int, String>>()
            val wolfTarget = nightActions.wolfTarget
            if (wolfTarget != null &&
                wolfTarget != nightActions.guardTarget &&
                nightActions.witchSave != true
            ) {
                deaths.add(wolfTarget to "wolf")
            }
            nightActions.witchPoison?.let { deaths.add(it to "poison") }
            return deaths
        }

        /**
         * 根据投票统计出局者。
         * @return Pair(出局座位号或 null, 是否平票)
         */
        fun computeExecutedSeat(votes: Map<String, Int>): Pair<Int?, Boolean> {
            if (votes.isEmpty()) return null to false
            val counts = mutableMapOf<Int, Int>()
            votes.values.forEach { seat -> counts[seat] = (counts[seat] ?: 0) + 1 }
            val max = counts.maxByOrNull { it.value } ?: return null to false
            val isTie = counts.values.count { it == max.value } > 1
            return if (isTie) null to true else max.key to false
        }

        /** 该玩家当前是否有投票权（已翻牌白痴没有）。 */
        fun canVote(player: GamePlayer, state: WolfchaGameState): Boolean {
            if (!player.alive) return false
            if (player.role == Role.Idiot && state.roleAbilities.idiotRevealed) return false
            return true
        }
    }
}

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

        val deaths = mutableListOf<Pair<Int, String>>()
        var roleAbilities = currentState.roleAbilities

        val wolfTarget = nightActions.wolfTarget
        val guardTarget = nightActions.guardTarget
        val witchSave = nightActions.witchSave

        if (wolfTarget != null && wolfTarget != guardTarget && witchSave != true) {
            deaths.add(wolfTarget to "wolf")
        }

        val witchPoison = nightActions.witchPoison
        if (witchPoison != null) {
            deaths.add(witchPoison to "poison")
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

        val newState = currentState.copy(
            players = newPlayers,
            phase = Phase.DAY_START,
            nightActions = NightActions(
                lastGuardTarget = nightActions.guardTarget
            ),
            roleAbilities = roleAbilities
        )

        _gameState.value = newState

        return _gameState.value to deaths.toList()
    }

    fun castVote(voterId: String, targetSeat: Int): WolfchaGameState {
        val currentState = _gameState.value
        val newVotes = currentState.votes.toMutableMap()
        newVotes[voterId] = targetSeat

        val newState = currentState.copy(votes = newVotes)
        _gameState.value = newState
        return newState
    }

    fun resolveVotes(): Pair<WolfchaGameState, Int?> {
        val currentState = _gameState.value
        val votes = currentState.votes

        if (votes.isEmpty()) {
            return currentState.copy(phase = Phase.DAY_RESOLVE) to null
        }

        val voteCounts = mutableMapOf<Int, Int>()
        votes.values.forEach { seat ->
            voteCounts[seat] = (voteCounts[seat] ?: 0) + 1
        }

        val maxVotes = voteCounts.maxByOrNull { it.value }
        val totalVotes = votes.size
        val isTie = voteCounts.values.count { it == maxVotes?.value } > 1

        return if (isTie || maxVotes == null) {
            val newState = currentState.copy(phase = Phase.DAY_RESOLVE)
            _gameState.value = newState
            newState to null
        } else {
            val executedSeat = maxVotes.key
            val newState = currentState.copy(
                phase = Phase.DAY_RESOLVE,
                voteHistory = currentState.voteHistory + (currentState.day to votes)
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

    fun checkWinCondition(): Alignment? {
        val currentState = _gameState.value
        val aliveWolves = currentState.aliveWolves.size
        val aliveVillagers = currentState.aliveVillagers.size

        return when {
            aliveWolves == 0 -> Alignment.VILLAGE
            aliveWolves >= aliveVillagers -> Alignment.WOLF
            else -> null
        }
    }

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

    private fun fallbackSpeech(player: GamePlayer, state: WolfchaGameState): String {
        return when (player.role) {
            Role.Villager -> generateVillagerSpeech(player)
            Role.Werewolf, Role.WhiteWolfKing -> generateWolfSpeech(player)
            Role.Seer -> generateSeerSpeech(player, state)
            Role.Witch -> generateWitchSpeech(player, state)
            Role.Hunter -> generateHunterSpeech(player)
            Role.Guard -> generateGuardSpeech(player)
            Role.Idiot -> generateIdiotSpeech(player)
        }
    }

    private fun generateVillagerSpeech(player: GamePlayer): String {
        val speeches = listOf(
            "我是好人，请大家仔细听逻辑，少被带节奏。",
            "我没拿到神职，但愿意帮大家梳理一下今天的发言。",
            "建议先关注发言前后矛盾的人，别急着站队。",
            "听了一圈，暂时没看出明显漏洞，我再观望一轮。"
        )
        return speeches.random()
    }

    private fun generateWolfSpeech(player: GamePlayer): String {
        val speeches = listOf(
            "我觉得 ${player.displayName} 发言最有逻辑，先听听别人怎么看。",
            "我没拿到身份牌，从发言看更倾向相信刚刚那位。",
            "今晚的票我已经有方向了，先卖个关子。",
            "大家别被情绪带跑，先把逻辑捋清楚再说。"
        )
        return speeches.random()
    }

    private fun generateSeerSpeech(player: GamePlayer, state: WolfchaGameState): String {
        val seerHistory = state.nightActions.seerHistory
        return if (seerHistory.isNotEmpty()) {
            val lastCheck = seerHistory.last()
            val target = state.getPlayerBySeat(lastCheck.targetSeat)
            val result = if (lastCheck.isWolf) "狼人" else "好人"
            "我是预言家，昨晚查的是 ${target?.seat?.plus(1) ?: "?"}号 ${target?.displayName ?: ""}，他是 $result。"
        } else {
            "我是预言家，昨晚查了人，但现在不方便直接报。"
        }
    }

    private fun generateWitchSpeech(player: GamePlayer, state: WolfchaGameState): String {
        return if (state.roleAbilities.witchHealUsed && state.roleAbilities.witchPoisonUsed) {
            "药都用完了，下面只能靠大家的判断了。"
        } else {
            "我是平民，今晚没拿到特殊信息，先听听别人怎么说。"
        }
    }

    private fun generateHunterSpeech(player: GamePlayer): String {
        return "我是猎人，希望这轮别投我，不然我一定开枪。"
    }

    private fun generateGuardSpeech(player: GamePlayer): String {
        return "我是守卫，已经守过一个人，今晚得换个目标。"
    }

    private fun generateIdiotSpeech(player: GamePlayer): String {
        return "嗯…那个…我其实也没啥好说的…大家加油…"
    }
}

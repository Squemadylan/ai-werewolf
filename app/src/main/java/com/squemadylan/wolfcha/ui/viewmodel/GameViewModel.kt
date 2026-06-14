package com.squemadylan.wolfcha.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squemadylan.wolfcha.data.local.PreferencesDataStore
import com.squemadylan.wolfcha.data.model.*
import com.squemadylan.wolfcha.domain.GameEngine
import com.squemadylan.wolfcha.domain.NightAiDecision
import com.squemadylan.wolfcha.util.VoiceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GameViewModel(
    application: Application,
    private val preferencesDataStore: PreferencesDataStore
) : AndroidViewModel(application) {

    private val gameEngine = GameEngine()
    private val nightAiDecision = NightAiDecision()

    val gameState: StateFlow<WolfchaGameState> = gameEngine.gameState

    /** Incremented on quit to cancel in-flight game loops. */
    private var gameSessionId = 0

    init {
        gameEngine.setLlmConfigProvider { llmConfigCached }
        VoiceHelper.init(application)
    }

    @Volatile
    private var llmConfigCached: LlmConfig = LlmConfig()

    @Volatile
    private var ttsConfigCached: TtsConfig = TtsConfig()

    private var speechCursor: Int = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentDialogue = MutableStateFlow<DialogueState?>(null)
    val currentDialogue: StateFlow<DialogueState?> = _currentDialogue.asStateFlow()

    private val _showRoleReveal = MutableStateFlow(false)
    val showRoleReveal: StateFlow<Boolean> = _showRoleReveal.asStateFlow()

    private val _showNightAction = MutableStateFlow(false)
    val showNightAction: StateFlow<Boolean> = _showNightAction.asStateFlow()

    private val _showVoteDialog = MutableStateFlow(false)
    val showVoteDialog: StateFlow<Boolean> = _showVoteDialog.asStateFlow()

    private val _showResultPanel = MutableStateFlow(false)
    val showResultPanel: StateFlow<Boolean> = _showResultPanel.asStateFlow()

    private val _waitingForSpeechContinue = MutableStateFlow(false)
    val waitingForSpeechContinue: StateFlow<Boolean> = _waitingForSpeechContinue.asStateFlow()

    private val _gameEnded = MutableStateFlow(false)
    val gameEnded: StateFlow<Boolean> = _gameEnded.asStateFlow()

    private val _winner = MutableStateFlow<Alignment?>(null)
    val winner: StateFlow<Alignment?> = _winner.asStateFlow()

    private val _showEndGameConfirm = MutableStateFlow(false)
    val showEndGameConfirm: StateFlow<Boolean> = _showEndGameConfirm.asStateFlow()

    private fun captureSession(): Int = gameSessionId

    private suspend fun waitIfPaused() {
        while (_isPaused.value) {
            delay(200)
        }
    }

    private suspend fun activeOrAbort(session: Int): Boolean {
        waitIfPaused()
        return session == gameSessionId && !_gameEnded.value
    }

    private suspend fun announceSystem(text: String) {
        gameEngine.addSystemMessage(text)
        if (ttsConfigCached.isReady) {
            VoiceHelper.speakNarration(text)
        }
    }

    private suspend fun announcePlayerSpeech(player: GamePlayer, text: String) {
        if (player.isHuman || !ttsConfigCached.isReady) return
        val gender = player.agentProfile?.persona?.gender ?: "male"
        VoiceHelper.speakPlayer(text, gender)
    }

    fun togglePause() {
        val paused = !_isPaused.value
        _isPaused.value = paused
        gameEngine.setPaused(paused)
    }

    fun requestEndGame() {
        _showEndGameConfirm.value = true
    }

    fun dismissEndGameRequest() {
        _showEndGameConfirm.value = false
    }

    fun confirmEndGame() {
        _showEndGameConfirm.value = false
        quitToLobby()
    }

    fun quitToLobby() {
        gameSessionId++
        VoiceHelper.stop()
        _isPaused.value = false
        _waitingForSpeechContinue.value = false
        resetGame()
    }

    fun onSpeechContinue() {
        _waitingForSpeechContinue.value = false
        _showNightAction.value = false
        _currentDialogue.value = null
    }

    fun toggleResultPanel() {
        _showResultPanel.value = !_showResultPanel.value
    }

    fun dismissResultPanel() {
        _showResultPanel.value = false
    }

    fun startNewGame(settings: GameSettings? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            gameSessionId++
            val session = captureSession()
            speechCursor = 0
            _gameEnded.value = false
            _winner.value = null
            _isPaused.value = false
            _waitingForSpeechContinue.value = false

            val prefs = preferencesDataStore.appGamePreferences.first()
            llmConfigCached = prefs.llmConfig
            ttsConfigCached = prefs.ttsConfig
            VoiceHelper.updateConfig(ttsConfigCached)

            val gameSettings = settings ?: GameSettings(
                playerCount = prefs.playerCount,
                difficulty = prefs.difficulty,
                humanName = prefs.playerName.ifBlank { "玩家" },
                aiPersonaPool = prefs.aiPersonaPool
            )
            gameEngine.createGame(gameSettings)
            gameEngine.setupPlayers(gameSettings)
            _showRoleReveal.value = true
            _isLoading.value = false

            if (!activeOrAbort(session)) return@launch
        }
    }

    fun onRoleRevealComplete() {
        _showRoleReveal.value = false
        viewModelScope.launch {
            startNightPhase(captureSession())
        }
    }

    private suspend fun startNightPhase(session: Int) {
        if (!activeOrAbort(session)) return
        val state = gameEngine.transitionPhase(Phase.NIGHT_START)
        announceSystem("第 ${state.day} 天夜晚开始了...")
        delay(1000)
        if (!activeOrAbort(session)) return

        val guard = state.players.find { it.role == Role.Guard && it.alive }
        if (guard != null) {
            gameEngine.transitionPhase(Phase.NIGHT_GUARD_ACTION)
            if (guard.isHuman) {
                _showNightAction.value = true
                _currentDialogue.value = DialogueState(
                    speaker = "系统",
                    text = "守卫请行动，请选择要保护的玩家（不能连续两晚保护同一人）",
                    actionType = NightActionType.GUARD
                )
                return
            } else {
                delay(1500)
                if (!activeOrAbort(session)) return
                val target = nightAiDecision.pickGuardTarget(
                    state = gameEngine.gameState.value,
                    guard = guard,
                    difficulty = state.difficulty,
                    llmConfig = llmConfigCached
                )
                target?.let { gameEngine.performNightActionGuard(it) }
            }
        }

        continueToWolfPhase(session)
    }

    private suspend fun continueToWolfPhase(session: Int) {
        if (!activeOrAbort(session)) return
        val state = gameEngine.gameState.value
        gameEngine.transitionPhase(Phase.NIGHT_WOLF_ACTION)

        val wolves = state.players.filter { it.role.isWolfRole() && it.alive }
        val humanWolf = wolves.find { it.isHuman }

        if (humanWolf != null) {
            _showNightAction.value = true
            _currentDialogue.value = DialogueState(
                speaker = "系统",
                text = "狼人请行动，请选择要击杀的目标",
                actionType = NightActionType.WOLF
            )
            return
        } else if (wolves.isNotEmpty()) {
            delay(1500)
            if (!activeOrAbort(session)) return
            val actingWolf = wolves.first()
            val target = nightAiDecision.pickWolfTarget(
                state = gameEngine.gameState.value,
                wolf = actingWolf,
                difficulty = state.difficulty,
                llmConfig = llmConfigCached
            )
            target?.let { gameEngine.performNightActionWolf(it) }
        }

        continueToWitchPhase(session)
    }

    private suspend fun continueToWitchPhase(session: Int) {
        if (!activeOrAbort(session)) return
        val state = gameEngine.gameState.value
        val witch = state.players.find { it.role == Role.Witch && it.alive }
        val canWitchAct = witch != null &&
            (!state.roleAbilities.witchHealUsed || !state.roleAbilities.witchPoisonUsed)

        if (canWitchAct) {
            gameEngine.transitionPhase(Phase.NIGHT_WITCH_ACTION)
            if (witch?.isHuman == true) {
                _showNightAction.value = true
                val wolfTarget = state.nightActions.wolfTarget
                val victimInfo = if (wolfTarget != null && !state.roleAbilities.witchHealUsed) {
                    val victim = state.getPlayerBySeat(wolfTarget)
                    "今晚${wolfTarget + 1}号${victim?.displayName ?: ""}被狼人攻击了"
                } else {
                    "今晚没有玩家被狼人攻击"
                }
                _currentDialogue.value = DialogueState(
                    speaker = "系统",
                    text = "$victimInfo\n\n女巫请行动：",
                    actionType = NightActionType.WITCH,
                    extraData = mapOf("wolfTarget" to (wolfTarget?.toString() ?: ""))
                )
                return
            } else if (witch != null) {
                delay(1500)
                if (!activeOrAbort(session)) return
                val current = gameEngine.gameState.value
                val save = nightAiDecision.pickWitchSave(current, current.difficulty)
                val poison = nightAiDecision.pickWitchPoison(
                    state = current,
                    witch = witch,
                    difficulty = current.difficulty,
                    llmConfig = llmConfigCached
                )
                gameEngine.performNightActionWitch(
                    save = if (save) true else null,
                    poisonTarget = poison
                )
            }
        }

        continueToSeerPhase(session)
    }

    private suspend fun continueToSeerPhase(session: Int) {
        if (!activeOrAbort(session)) return
        val state = gameEngine.gameState.value
        val seer = state.players.find { it.role == Role.Seer && it.alive }

        if (seer != null) {
            gameEngine.transitionPhase(Phase.NIGHT_SEER_ACTION)
            if (seer.isHuman) {
                _showNightAction.value = true
                _currentDialogue.value = DialogueState(
                    speaker = "系统",
                    text = "预言家请行动，请选择要查验的玩家",
                    actionType = NightActionType.SEER
                )
                return
            } else {
                delay(1500)
                if (!activeOrAbort(session)) return
                val target = nightAiDecision.pickSeerTarget(
                    state = gameEngine.gameState.value,
                    seer = seer,
                    difficulty = state.difficulty,
                    llmConfig = llmConfigCached
                )
                target?.let { gameEngine.performNightActionSeer(it) }
            }
        }

        continueToDayPhase(session)
    }

    private suspend fun continueToDayPhase(session: Int) {
        if (!activeOrAbort(session)) return
        _showNightAction.value = false
        val (_, nightDeaths) = gameEngine.resolveNight()

        val deathMessages = nightDeaths.map { (seat, _) ->
            val player = gameEngine.gameState.value.getPlayerBySeat(seat)
            "${seat + 1}号 ${player?.displayName ?: ""} 死亡了"
        }
        if (deathMessages.isNotEmpty()) {
            announceSystem("天亮了，昨晚有人死亡：")
            deathMessages.forEach { announceSystem(it) }
        } else {
            announceSystem("天亮了，昨晚是平安夜")
        }

        delay(1000)
        if (!activeOrAbort(session)) return

        val state = gameEngine.gameState.value
        val winner = gameEngine.checkWinCondition()
        if (winner != null) {
            endGame(winner, session)
            return
        }

        val hunterWolfKill = nightDeaths.firstOrNull { (seat, reason) ->
            reason == "wolf" &&
                state.getPlayerBySeat(seat)?.role == Role.Hunter &&
                gameEngine.gameState.value.roleAbilities.hunterCanShoot
        }
        if (hunterWolfKill != null) {
            runHunterShoot(session, hunterWolfKill.first)
            return
        }

        if (state.day == 1 &&
            state.players.size >= 9 &&
            state.badge.holderSeat == null
        ) {
            runSheriffElection(session)
            return
        }

        gameEngine.transitionPhase(Phase.DAY_SPEECH)
        val startSeat = state.lastExecutedSeat?.let { executedSeat ->
            state.getNextAliveSeat(executedSeat, state.speechDirection)
        } ?: state.alivePlayers.minByOrNull { it.seat }?.seat
        if (startSeat != null) {
            gameEngine.setDaySpeechStartSeat(startSeat)
        }
        val anchor = gameEngine.gameState.value.daySpeechStartSeat
        if (anchor != null) {
            announceSystem("第 ${state.day} 天白天，请大家依次发言")
        }
        runDaySpeech(session)
    }

    private suspend fun runSheriffElection(session: Int) {
        if (!activeOrAbort(session)) return
        gameEngine.transitionPhase(Phase.DAY_BADGE_ELECTION)
        announceSystem("第1天警长竞选，请全体玩家投票")

        val state = gameEngine.gameState.value
        if (state.humanPlayer?.alive == true) {
            _showVoteDialog.value = true
            return
        }

        delay(1000)
        if (!activeOrAbort(session)) return
        castAiVotes(session)
        resolveVote(session, isBadgeElection = true)
    }

    private suspend fun runDaySpeech(session: Int) {
        if (!activeOrAbort(session)) return
        val state = gameEngine.gameState.value
        val alivePlayers = state.alivePlayers.sortedBy { it.seat }
        if (alivePlayers.isEmpty()) return

        val orderedSeats = if (state.speechDirection == SpeechDirection.CLOCKWISE) {
            alivePlayers.map { it.seat }
        } else {
            alivePlayers.map { it.seat }.reversed()
        }
        val startSeat = state.daySpeechStartSeat
            ?: state.currentSpeakerSeat
            ?: alivePlayers.minByOrNull { it.seat }?.seat
        val startIndex = orderedSeats.indexOf(startSeat).coerceAtLeast(0)
        val rotated = orderedSeats.drop(startIndex) + orderedSeats.take(startIndex)

        var index = speechCursor.coerceAtLeast(0)
        while (index < rotated.size) {
            if (!activeOrAbort(session)) return
            val targetSeat = rotated[index]
            val player = state.getPlayerBySeat(targetSeat)
            if (player == null || !player.alive) {
                index += 1
                continue
            }

            if (player.isHuman) {
                speechCursor = index + 1
                _showNightAction.value = true
                _currentDialogue.value = DialogueState(
                    speaker = "系统",
                    text = "轮到你了，请发表你的看法",
                    actionType = NightActionType.SPEECH
                )
                return
            } else {
                announceSystem("请${player.seat + 1}号 ${player.displayName} 发言")
                _currentDialogue.value = DialogueState(
                    speaker = player.displayName,
                    text = "正在思考…",
                    actionType = NightActionType.NONE
                )
                val speech = gameEngine.generateAISpeech(player.playerId)
                gameEngine.addPlayerMessage(player.playerId, speech)
                announcePlayerSpeech(player, speech)
                _showNightAction.value = true
                _waitingForSpeechContinue.value = true
                _currentDialogue.value = DialogueState(
                    speaker = player.displayName,
                    text = speech,
                    actionType = NightActionType.AI_SPEECH_CONTINUE
                )
                while (_waitingForSpeechContinue.value) {
                    delay(100)
                    if (session != captureSession() || _gameEnded.value) return
                }
                index += 1
                speechCursor = index
            }
        }

        speechCursor = 0
        startVotePhase(session)
    }

    private suspend fun startVotePhase(session: Int) {
        if (!activeOrAbort(session)) return
        gameEngine.transitionPhase(Phase.DAY_VOTE)
        announceSystem("发言结束，请大家投票")

        val state = gameEngine.gameState.value
        if (state.humanPlayer?.alive == true) {
            _showVoteDialog.value = true
            return
        }

        delay(1000)
        if (!activeOrAbort(session)) return
        castAiVotes(session)
        resolveVote(session, isBadgeElection = false)
    }

    private suspend fun resolveVote(session: Int, isBadgeElection: Boolean) {
        if (!activeOrAbort(session)) return
        _showVoteDialog.value = false

        val (newState, executedSeat) = gameEngine.resolveVotes()

        if (isBadgeElection) {
            if (executedSeat != null) {
                gameEngine.setBadgeHolder(executedSeat)
                val player = newState.getPlayerBySeat(executedSeat)
                announceSystem("${executedSeat + 1}号 ${player?.displayName ?: ""} 成为警长")
            } else {
                announceSystem("警长竞选投票平局，本局无警长")
            }
            gameEngine.clearVotes()
            gameEngine.transitionPhase(Phase.DAY_SPEECH)
            announceSystem("第 ${gameEngine.gameState.value.day} 天白天，请大家依次发言")
            runDaySpeech(session)
            return
        }

        if (executedSeat == null) {
            announceSystem("投票平局，无人出局")
            delay(1000)
            if (!activeOrAbort(session)) return
            gameEngine.advanceToNextDay()
            startNightPhase(session)
            return
        }

        val player = newState.getPlayerBySeat(executedSeat)
        announceSystem("${executedSeat + 1}号 ${player?.displayName ?: ""} 被投票出局")
        delay(500)
        if (!activeOrAbort(session)) return

        handleVoteExecution(session, executedSeat)
    }

    private suspend fun handleVoteExecution(session: Int, executedSeat: Int) {
        if (!activeOrAbort(session)) return
        val player = gameEngine.gameState.value.getPlayerBySeat(executedSeat) ?: run {
            advanceAfterExecution(session, executedSeat, wasHunter = false)
            return
        }

        when (player.role) {
            Role.Idiot -> {
                gameEngine.revealIdiot(executedSeat)
                announceSystem("${executedSeat + 1}号 ${player.displayName} 翻牌白痴，不会出局")
                delay(1000)
                if (!activeOrAbort(session)) return
                gameEngine.advanceToNextDay()
                startNightPhase(session)
            }
            Role.WhiteWolfKing -> {
                runWhiteWolfBoom(session, executedSeat)
            }
            else -> {
                runLastWords(session, executedSeat) {
                    advanceAfterExecution(session, executedSeat, wasHunter = player.role == Role.Hunter)
                }
            }
        }
    }

    private suspend fun runLastWords(session: Int, seat: Int, onComplete: suspend () -> Unit) {
        if (!activeOrAbort(session)) return
        val player = gameEngine.gameState.value.getPlayerBySeat(seat) ?: run {
            onComplete()
            return
        }

        gameEngine.transitionPhase(Phase.DAY_LAST_WORDS)
        announceSystem("${seat + 1}号 ${player.displayName} 发表遗言")

        if (player.isHuman) {
            _showNightAction.value = true
            _currentDialogue.value = DialogueState(
                speaker = "系统",
                text = "请发表你的遗言",
                actionType = NightActionType.LAST_WORDS,
                extraData = mapOf("seat" to seat.toString())
            )
            return
        }

        val speech = gameEngine.generateAISpeech(player.playerId)
        gameEngine.addPlayerMessage(player.playerId, speech)
        announcePlayerSpeech(player, speech)

        _showNightAction.value = true
        _waitingForSpeechContinue.value = true
        _currentDialogue.value = DialogueState(
            speaker = player.displayName,
            text = speech,
            actionType = NightActionType.AI_SPEECH_CONTINUE,
            extraData = mapOf("role" to "last_words")
        )
        while (_waitingForSpeechContinue.value) {
            delay(100)
            if (session != captureSession() || _gameEnded.value) return
        }
        onComplete()
    }

    private suspend fun advanceAfterExecution(
        session: Int,
        executedSeat: Int,
        wasHunter: Boolean
    ) {
        if (!activeOrAbort(session)) return
        gameEngine.killPlayer(executedSeat)
        val player = gameEngine.gameState.value.getPlayerBySeat(executedSeat)
        announceSystem("${executedSeat + 1}号 ${player?.displayName ?: ""} 出局")

        delay(500)
        if (!activeOrAbort(session)) return

        val winner = gameEngine.checkWinCondition()
        if (winner != null) {
            endGame(winner, session)
            return
        }

        if (wasHunter && gameEngine.gameState.value.roleAbilities.hunterCanShoot) {
            runHunterShoot(session, executedSeat)
            return
        }

        delay(1000)
        if (!activeOrAbort(session)) return
        gameEngine.advanceToNextDay()
        startNightPhase(session)
    }

    private suspend fun runWhiteWolfBoom(session: Int, wwkSeat: Int) {
        if (!activeOrAbort(session)) return
        val wwk = gameEngine.gameState.value.getPlayerBySeat(wwkSeat) ?: return

        gameEngine.transitionPhase(Phase.WHITE_WOLF_KING_BOOM)
        announceSystem("${wwkSeat + 1}号 ${wwk.displayName} 白狼王自爆，请选择要带走的玩家")

        if (wwk.isHuman) {
            _showNightAction.value = true
            _currentDialogue.value = DialogueState(
                speaker = "系统",
                text = "请选择要带走的玩家",
                actionType = NightActionType.WHITE_WOLF_BOOM,
                extraData = mapOf("seat" to wwkSeat.toString())
            )
            return
        }

        delay(1500)
        if (!activeOrAbort(session)) return
        val target = pickBoomTarget(wwk)
        finishWhiteWolfBoom(session, wwkSeat, target)
    }

    private suspend fun finishWhiteWolfBoom(session: Int, wwkSeat: Int, targetSeat: Int?) {
        if (!activeOrAbort(session)) return
        _showNightAction.value = false

        gameEngine.killPlayer(wwkSeat)
        val wwk = gameEngine.gameState.value.getPlayerBySeat(wwkSeat)
        announceSystem("${wwkSeat + 1}号 ${wwk?.displayName ?: ""} 自爆出局")

        if (targetSeat != null) {
            gameEngine.killPlayer(targetSeat)
            val target = gameEngine.gameState.value.getPlayerBySeat(targetSeat)
            announceSystem("${targetSeat + 1}号 ${target?.displayName ?: ""} 被白狼王带走")
        }

        delay(500)
        if (!activeOrAbort(session)) return

        val winner = gameEngine.checkWinCondition()
        if (winner != null) {
            endGame(winner, session)
            return
        }

        delay(1000)
        if (!activeOrAbort(session)) return
        gameEngine.advanceToNextDay()
        startNightPhase(session)
    }

    private suspend fun runHunterShoot(session: Int, hunterSeat: Int) {
        if (!activeOrAbort(session)) return
        if (!gameEngine.gameState.value.roleAbilities.hunterCanShoot) {
            delay(1000)
            if (!activeOrAbort(session)) return
            gameEngine.advanceToNextDay()
            startNightPhase(session)
            return
        }

        val hunter = gameEngine.gameState.value.getPlayerBySeat(hunterSeat)
        gameEngine.transitionPhase(Phase.HUNTER_SHOOT)
        announceSystem("${hunterSeat + 1}号 ${hunter?.displayName ?: ""} 猎人开枪，请选择目标")

        if (hunter?.isHuman == true) {
            _showNightAction.value = true
            _currentDialogue.value = DialogueState(
                speaker = "系统",
                text = "请选择要带走的玩家",
                actionType = NightActionType.HUNTER_SHOOT,
                extraData = mapOf("seat" to hunterSeat.toString())
            )
            return
        }

        delay(1500)
        if (!activeOrAbort(session)) return
        val target = pickHunterTarget(hunterSeat)
        finishHunterShoot(session, hunterSeat, target)
    }

    private suspend fun finishHunterShoot(session: Int, hunterSeat: Int, targetSeat: Int?) {
        if (!activeOrAbort(session)) return
        _showNightAction.value = false
        gameEngine.markHunterShotUsed()

        if (targetSeat != null) {
            gameEngine.killPlayer(targetSeat)
            val target = gameEngine.gameState.value.getPlayerBySeat(targetSeat)
            announceSystem("${targetSeat + 1}号 ${target?.displayName ?: ""} 被猎人带走")
        } else {
            announceSystem("猎人选择不开枪")
        }

        delay(500)
        if (!activeOrAbort(session)) return

        val winner = gameEngine.checkWinCondition()
        if (winner != null) {
            endGame(winner, session)
            return
        }

        delay(1000)
        if (!activeOrAbort(session)) return
        gameEngine.advanceToNextDay()
        startNightPhase(session)
    }

    private suspend fun pickBoomTarget(wwk: GamePlayer): Int? {
        val state = gameEngine.gameState.value
        val candidates = state.alivePlayers.filter {
            it.seat != wwk.seat && !it.role.isWolfRole()
        }
        if (candidates.isEmpty()) return null
        return nightAiDecision.pickVoteTarget(state, wwk, state.difficulty, llmConfigCached)
            ?.takeIf { seat -> candidates.any { it.seat == seat } }
            ?: candidates.random().seat
    }

    private suspend fun pickHunterTarget(hunterSeat: Int): Int? {
        val state = gameEngine.gameState.value
        val hunter = state.getPlayerBySeat(hunterSeat) ?: return null
        val candidates = state.alivePlayers.filter { it.seat != hunterSeat }
        if (candidates.isEmpty()) return null
        return nightAiDecision.pickVoteTarget(state, hunter, state.difficulty, llmConfigCached)
            ?.takeIf { seat -> candidates.any { it.seat == seat } }
            ?: candidates.random().seat
    }

    fun onHumanNightAction(
        action: NightActionType,
        targetSeat: Int? = null,
        extraData: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val session = captureSession()
            when (action) {
                NightActionType.GUARD -> {
                    targetSeat?.let { gameEngine.performNightActionGuard(it) }
                    continueToWolfPhase(session)
                }
                NightActionType.WOLF -> {
                    targetSeat?.let { gameEngine.performNightActionWolf(it) }
                    continueToWitchPhase(session)
                }
                NightActionType.WITCH -> {
                    val save = extraData["save"]?.toBoolean() ?: false
                    val poison = extraData["poison"]?.toIntOrNull()
                    gameEngine.performNightActionWitch(save, poison)
                    continueToSeerPhase(session)
                }
                NightActionType.SEER -> {
                    targetSeat?.let { gameEngine.performNightActionSeer(it) }
                    val result = gameEngine.gameState.value.nightActions.seerHistory.lastOrNull()
                    if (result != null) {
                        val targetPlayer = gameEngine.gameState.value.getPlayerBySeat(result.targetSeat)
                        val resultText = if (result.isWolf) "狼人" else "好人阵营"
                        _currentDialogue.value = DialogueState(
                            speaker = "系统",
                            text = "查验结果：${result.targetSeat + 1}号 ${targetPlayer?.displayName ?: ""} 是$resultText",
                            actionType = NightActionType.SEER_RESULT
                        )
                    }
                }
                NightActionType.SEER_RESULT -> {
                    _showNightAction.value = false
                    _currentDialogue.value = null
                    continueToDayPhase(session)
                }
                NightActionType.SPEECH -> {
                    val speech = extraData["speech"] ?: "..."
                    val human = gameState.value.humanPlayer
                    human?.let { gameEngine.addPlayerMessage(it.playerId, speech) }
                    _showNightAction.value = false
                    runDaySpeech(session)
                }
                NightActionType.LAST_WORDS -> {
                    val speech = extraData["speech"] ?: "..."
                    val seat = extraData["seat"]?.toIntOrNull()
                    val player = seat?.let { gameEngine.gameState.value.getPlayerBySeat(it) }
                        ?: gameState.value.humanPlayer
                    player?.let { gameEngine.addPlayerMessage(it.playerId, speech) }
                    _showNightAction.value = false
                    if (seat != null) {
                        advanceAfterExecution(
                            session,
                            seat,
                            wasHunter = player?.role == Role.Hunter
                        )
                    }
                }
                NightActionType.HUNTER_SHOOT -> {
                    val hunterSeat = extraData["seat"]?.toIntOrNull()
                    if (hunterSeat != null) {
                        finishHunterShoot(session, hunterSeat, targetSeat)
                    }
                }
                NightActionType.WHITE_WOLF_BOOM -> {
                    val wwkSeat = extraData["seat"]?.toIntOrNull()
                    if (wwkSeat != null) {
                        finishWhiteWolfBoom(session, wwkSeat, targetSeat)
                    }
                }
                else -> {}
            }
        }
    }

    fun onHumanVote(targetSeat: Int) {
        viewModelScope.launch {
            val session = captureSession()
            val isBadgeElection = gameState.value.phase == Phase.DAY_BADGE_ELECTION
            val humanPlayer = gameState.value.humanPlayer
            if (humanPlayer != null) {
                gameEngine.castVote(humanPlayer.playerId, targetSeat)
            }
            _showVoteDialog.value = false
            castAiVotes(session)
            resolveVote(session, isBadgeElection = isBadgeElection)
        }
    }

    private suspend fun castAiVotes(session: Int) {
        if (!activeOrAbort(session)) return
        for (player in gameEngine.gameState.value.alivePlayers) {
            if (player.isHuman) continue
            val targetSeat = gameEngine.suggestVoteTarget(player.playerId)
                ?: nightAiDecision.pickVoteTarget(
                    state = gameEngine.gameState.value,
                    voter = player,
                    difficulty = gameEngine.gameState.value.difficulty,
                    llmConfig = llmConfigCached
                )
            targetSeat?.let { gameEngine.castVote(player.playerId, it) }
        }
    }

    private suspend fun endGame(winner: Alignment, session: Int) {
        if (!activeOrAbort(session)) return
        gameEngine.setWinner(winner)
        _winner.value = winner
        _gameEnded.value = true
        val winnerText = if (winner == Alignment.VILLAGE) "好人阵营胜利！" else "狼人阵营胜利！"
        announceSystem("游戏结束！$winnerText")
    }

    fun resetGame() {
        _gameEnded.value = false
        _winner.value = null
        _showRoleReveal.value = false
        _showNightAction.value = false
        _showVoteDialog.value = false
        _showEndGameConfirm.value = false
        _currentDialogue.value = null
        _isPaused.value = false
        _waitingForSpeechContinue.value = false
        speechCursor = 0
        gameEngine.resetGame()
    }

    fun skipToNextPhase() {
        viewModelScope.launch {
            val session = captureSession()
            when (gameState.value.phase) {
                Phase.NIGHT_GUARD_ACTION -> continueToWolfPhase(session)
                Phase.NIGHT_WOLF_ACTION -> continueToWitchPhase(session)
                Phase.NIGHT_WITCH_ACTION -> continueToSeerPhase(session)
                Phase.NIGHT_SEER_ACTION -> continueToDayPhase(session)
                else -> {}
            }
        }
    }
}

data class DialogueState(
    val speaker: String,
    val text: String,
    val actionType: NightActionType,
    val extraData: Map<String, String> = emptyMap()
)

enum class NightActionType {
    GUARD,
    WOLF,
    WITCH,
    SEER,
    SEER_RESULT,
    SPEECH,
    LAST_WORDS,
    HUNTER_SHOOT,
    WHITE_WOLF_BOOM,
    AI_SPEECH_CONTINUE,
    NONE
}

class GameViewModelFactory(
    private val application: Application,
    private val preferencesDataStore: PreferencesDataStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(application, preferencesDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

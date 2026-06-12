package com.squemadylan.wolfcha.domain

import com.squemadylan.wolfcha.data.model.DifficultyLevel
import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.LlmConfig
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.data.model.isWolfRole
import com.squemadylan.wolfcha.data.remote.LlmService
import com.squemadylan.wolfcha.data.remote.WerewolfPromptBuilder
import kotlin.random.Random

/**
 * Picks night/vote targets for AI players based on difficulty.
 */
class NightAiDecision(
    private val llmService: LlmService = LlmService()
) {

    suspend fun pickGuardTarget(
        state: WolfchaGameState,
        guard: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig
    ): Int? {
        val alivePlayers = state.alivePlayers.filter { it.seat != guard.seat }
        val lastTarget = state.nightActions.lastGuardTarget
        val validTargets = alivePlayers.filter { it.seat != lastTarget }
        if (validTargets.isEmpty()) return alivePlayers.randomOrNull()?.seat

        return when (difficulty) {
            DifficultyLevel.EASY -> validTargets.random().seat
            DifficultyLevel.NORMAL -> {
                val seer = validTargets.find { it.role == Role.Seer }
                (seer ?: validTargets.random()).seat
            }
            DifficultyLevel.HARD -> {
                llmPickSeat(
                    state, guard, difficulty, llmConfig,
                    action = "守卫",
                    hint = "选择今晚要守护的玩家座位号（不能连续两晚守同一人）",
                    candidates = validTargets
                ) ?: validTargets.random().seat
            }
        }
    }

    suspend fun pickWolfTarget(
        state: WolfchaGameState,
        wolf: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig
    ): Int? {
        val candidates = state.alivePlayers.filter { !it.role.isWolfRole() }
        if (candidates.isEmpty()) return null

        return when (difficulty) {
            DifficultyLevel.EASY -> candidates.random().seat
            DifficultyLevel.NORMAL -> {
                val threats = candidates.filter { it.role == Role.Seer || it.role == Role.Witch }
                (threats.randomOrNull() ?: candidates.random()).seat
            }
            DifficultyLevel.HARD -> {
                llmPickSeat(
                    state, wolf, difficulty, llmConfig,
                    action = "狼人刀人",
                    hint = "选择今晚要击杀的好人座位号",
                    candidates = candidates
                ) ?: candidates.random().seat
            }
        }
    }

    suspend fun pickSeerTarget(
        state: WolfchaGameState,
        seer: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig
    ): Int? {
        val unchecked = state.alivePlayers.filter { p ->
            p.seat != seer.seat && state.nightActions.seerHistory.none { it.targetSeat == p.seat }
        }
        val candidates = unchecked.ifEmpty {
            state.alivePlayers.filter { it.seat != seer.seat }
        }
        if (candidates.isEmpty()) return null

        return when (difficulty) {
            DifficultyLevel.EASY -> candidates.random().seat
            DifficultyLevel.NORMAL -> candidates.random().seat
            DifficultyLevel.HARD -> {
                llmPickSeat(
                    state, seer, difficulty, llmConfig,
                    action = "预言家查验",
                    hint = "选择今晚要查验的玩家座位号",
                    candidates = candidates
                ) ?: candidates.random().seat
            }
        }
    }

    fun pickWitchSave(
        state: WolfchaGameState,
        difficulty: DifficultyLevel
    ): Boolean {
        val wolfTarget = state.nightActions.wolfTarget ?: return false
        if (state.roleAbilities.witchHealUsed) return false
        return when (difficulty) {
            DifficultyLevel.EASY -> Random.nextFloat() < 0.35f
            DifficultyLevel.NORMAL -> Random.nextFloat() < 0.55f
            DifficultyLevel.HARD -> {
                val target = state.getPlayerBySeat(wolfTarget)
                target?.role != Role.Villager || Random.nextFloat() < 0.7f
            }
        }
    }

    suspend fun pickWitchPoison(
        state: WolfchaGameState,
        witch: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig
    ): Int? {
        if (state.roleAbilities.witchPoisonUsed) return null
        val candidates = state.alivePlayers.filter { it.seat != witch.seat }
        if (candidates.isEmpty()) return null

        return when (difficulty) {
            DifficultyLevel.EASY -> if (Random.nextFloat() < 0.15f) candidates.random().seat else null
            DifficultyLevel.NORMAL -> if (Random.nextFloat() < 0.25f) candidates.random().seat else null
            DifficultyLevel.HARD -> {
                if (!llmConfig.isReady) {
                    return if (Random.nextFloat() < 0.35f) candidates.random().seat else null
                }
                llmPickSeat(
                    state, witch, difficulty, llmConfig,
                    action = "女巫毒人",
                    hint = "若今晚要使用毒药，返回目标座位号；若不用，返回 0",
                    candidates = candidates,
                    allowSkip = true
                )
            }
        }
    }

    suspend fun pickVoteTarget(
        state: WolfchaGameState,
        voter: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig
    ): Int? {
        val candidates = state.alivePlayers.filter { it.seat != voter.seat }
        if (candidates.isEmpty()) return null

        if (llmConfig.isReady) {
            return llmPickSeat(
                state, voter, difficulty, llmConfig,
                action = "白天投票",
                hint = "选择你要投票放逐的玩家座位号",
                candidates = candidates
            ) ?: candidates.random().seat
        }

        return when (difficulty) {
            DifficultyLevel.EASY -> candidates.random().seat
            DifficultyLevel.NORMAL -> {
                if (voter.role.isWolfRole()) {
                    val nonWolves = candidates.filter { !it.role.isWolfRole() }
                    (nonWolves.randomOrNull() ?: candidates.random()).seat
                } else {
                    candidates.random().seat
                }
            }
            DifficultyLevel.HARD -> candidates.random().seat
        }
    }

    private suspend fun llmPickSeat(
        state: WolfchaGameState,
        actor: GamePlayer,
        difficulty: DifficultyLevel,
        llmConfig: LlmConfig,
        action: String,
        hint: String,
        candidates: List<GamePlayer>,
        allowSkip: Boolean = false
    ): Int? {
        if (!llmConfig.isReady) return null

        val candidateText = candidates.joinToString("、") { "${it.seat + 1}号 ${it.displayName}" }
        val (system, user) = WerewolfPromptBuilder.buildActionPrompt(
            state = state,
            player = actor,
            action = action,
            hint = hint,
            candidateSummary = candidateText,
            allowSkip = allowSkip
        )

        val temp = when (difficulty) {
            DifficultyLevel.HARD -> 0.3f
            DifficultyLevel.NORMAL -> 0.5f
            DifficultyLevel.EASY -> 0.8f
        }

        val result = llmService.chat(
            config = llmConfig.copy(temperature = temp, maxTokens = 16),
            messages = listOf(
                LlmService.Message("system", system),
                LlmService.Message("user", user)
            ),
            isolationKey = "${state.gameId}_${actor.playerId}_action"
        )

        if (result !is LlmService.Result.Success) return null
        val number = Regex("\\d+").find(result.content)?.value?.toIntOrNull() ?: return null
        if (allowSkip && number == 0) return null
        val seat = number - 1
        return candidates.find { it.seat == seat }?.seat
    }
}

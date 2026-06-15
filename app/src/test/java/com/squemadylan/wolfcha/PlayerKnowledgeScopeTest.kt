package com.squemadylan.wolfcha

import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.NightActions
import com.squemadylan.wolfcha.data.model.Phase
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.SeerHistoryEntry
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.domain.PlayerKnowledgeScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKnowledgeScopeTest {

    private fun player(seat: Int, role: Role) = GamePlayer(
        playerId = "p$seat",
        seat = seat,
        displayName = "P$seat",
        role = role
    )

    private val players = listOf(
        player(0, Role.Seer),
        player(1, Role.Werewolf),
        player(2, Role.Werewolf),
        player(3, Role.Villager),
        player(4, Role.Witch),
        player(5, Role.Villager)
    )

    private val state = WolfchaGameState(
        players = players,
        day = 1,
        phase = Phase.DAY_SPEECH,
        nightActions = NightActions(
            // 预言家昨夜查验 1 号（狼人）
            seerHistory = listOf(SeerHistoryEntry(targetSeat = 1, isWolf = true, day = 1))
        )
    )

    @Test
    fun seerSeesOwnCheckResult() {
        val k = PlayerKnowledgeScope.buildFor(state, players[0])
        assertTrue(k.privateFacts.contains("查验"))
        assertTrue(k.privateFacts.contains("狼人"))
    }

    @Test
    fun wolfSeesTeammates() {
        val k = PlayerKnowledgeScope.buildFor(state, players[1])
        assertTrue(k.privateFacts.contains("狼队友"))
        // 队友是 3 号（座位 2）
        assertTrue(k.privateFacts.contains("3号"))
    }

    @Test
    fun villagerHasNoPrivateInfo() {
        val k = PlayerKnowledgeScope.buildFor(state, players[3])
        assertTrue(k.privateFacts.isBlank())
    }

    @Test
    fun villagerCannotSeeSeerCheckOrWolfTeam() {
        val k = PlayerKnowledgeScope.buildFor(state, players[3])
        assertFalse(k.privateFacts.contains("查验"))
        assertFalse(k.privateFacts.contains("狼队友"))
    }

    @Test
    fun gameRulesAreDynamicAndUseTubian() {
        val k = PlayerKnowledgeScope.buildFor(state, players[3])
        assertTrue(k.gameRules.contains("6人局"))
        assertTrue(k.gameRules.contains("屠边"))
        assertTrue(k.gameRules.contains("狼人2人"))
    }

    @Test
    fun idiotKnowsOwnRoleFromStart() {
        // 白痴 AI 在开局就应该知道自己是白痴（不需要等被票翻牌）
        val stateWithIdiot = state.copy(
            players = state.players + player(6, Role.Idiot)
        )
        val k = PlayerKnowledgeScope.buildFor(stateWithIdiot, stateWithIdiot.players[6])
        assertTrue("白痴应从开局知道身份", k.privateFacts.contains("白痴"))
    }

    @Test
    fun revealedIdiotGetsExtraStateInPrivateFacts() {
        val stateWithIdiot = state.copy(
            players = state.players + player(6, Role.Idiot),
            roleAbilities = state.roleAbilities.copy(idiotRevealed = true)
        )
        val k = PlayerKnowledgeScope.buildFor(stateWithIdiot, stateWithIdiot.players[6])
        assertTrue("白痴被翻牌后应被告知翻牌状态", k.privateFacts.contains("已翻牌"))
    }
}

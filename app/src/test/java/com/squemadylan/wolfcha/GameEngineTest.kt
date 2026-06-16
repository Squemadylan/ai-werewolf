package com.squemadylan.wolfcha

import com.squemadylan.wolfcha.data.model.Alignment
import com.squemadylan.wolfcha.data.model.GameConfig
import com.squemadylan.wolfcha.data.model.GamePlayer
import com.squemadylan.wolfcha.data.model.GameSettings
import com.squemadylan.wolfcha.data.model.NightActions
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.RoleAbilities
import com.squemadylan.wolfcha.data.model.WolfchaGameState
import com.squemadylan.wolfcha.data.model.isWolfRole
import com.squemadylan.wolfcha.domain.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun player(
        seat: Int,
        role: Role,
        alive: Boolean = true,
        human: Boolean = false
    ) = GamePlayer(
        playerId = "p$seat",
        seat = seat,
        displayName = "P$seat",
        alive = alive,
        role = role,
        isHuman = human
    )

    private fun stateOf(players: List<GamePlayer>, abilities: RoleAbilities = RoleAbilities()) =
        WolfchaGameState(players = players, roleAbilities = abilities)

    // ====================== 屠边胜负判定 ======================

    @Test
    fun winner_villageWhenAllWolvesDead() {
        val state = stateOf(
            listOf(
                player(0, Role.Werewolf, alive = false),
                player(1, Role.WhiteWolfKing, alive = false),
                player(2, Role.Seer),
                player(3, Role.Villager)
            )
        )
        assertEquals(Alignment.VILLAGE, GameEngine.evaluateWinner(state))
    }

    @Test
    fun winner_wolfWhenAllGodsDead_idiotCountsAsGod() {
        val state = stateOf(
            listOf(
                player(0, Role.Werewolf),
                player(1, Role.Seer, alive = false),
                player(2, Role.Witch, alive = false),
                player(3, Role.Idiot, alive = false), // 白痴算神职
                player(4, Role.Villager) // 平民还活着
            )
        )
        assertEquals(Alignment.WOLF, GameEngine.evaluateWinner(state))
    }

    @Test
    fun winner_wolfWhenAllCiviliansDead() {
        val state = stateOf(
            listOf(
                player(0, Role.Werewolf),
                player(1, Role.Seer), // 神职还活着
                player(2, Role.Villager, alive = false),
                player(3, Role.Villager, alive = false)
            )
        )
        assertEquals(Alignment.WOLF, GameEngine.evaluateWinner(state))
    }

    @Test
    fun winner_nullWhenBothSidesAlive() {
        val state = stateOf(
            listOf(
                player(0, Role.Werewolf),
                player(1, Role.Seer),
                player(2, Role.Villager)
            )
        )
        assertNull(GameEngine.evaluateWinner(state))
    }

    // ====================== 夜间死亡计算 ======================

    @Test
    fun nightDeaths_wolfKillSucceeds() {
        val deaths = GameEngine.computeNightDeaths(NightActions(wolfTarget = 3))
        assertEquals(listOf(3 to "wolf"), deaths)
    }

    @Test
    fun nightDeaths_guardBlocksWolfKill() {
        val deaths = GameEngine.computeNightDeaths(NightActions(wolfTarget = 3, guardTarget = 3))
        assertTrue(deaths.isEmpty())
    }

    @Test
    fun nightDeaths_witchSaveBlocksWolfKill() {
        val deaths = GameEngine.computeNightDeaths(NightActions(wolfTarget = 3, witchSave = true))
        assertTrue(deaths.isEmpty())
    }

    @Test
    fun nightDeaths_poisonKills() {
        val deaths = GameEngine.computeNightDeaths(NightActions(witchPoison = 2))
        assertEquals(listOf(2 to "poison"), deaths)
    }

    @Test
    fun nightDeaths_wolfAndPoisonBothDie() {
        val deaths = GameEngine.computeNightDeaths(NightActions(wolfTarget = 1, witchPoison = 2))
        assertEquals(2, deaths.size)
        assertTrue(deaths.contains(1 to "wolf"))
        assertTrue(deaths.contains(2 to "poison"))
    }

    // ====================== 投票计票 ======================

    @Test
    fun executed_clearMajority() {
        val votes = mapOf("a" to 1, "b" to 1, "c" to 2)
        val (seat, tie) = GameEngine.computeExecutedSeat(votes)
        assertEquals(1, seat)
        assertFalse(tie)
    }

    @Test
    fun executed_tieReturnsNull() {
        val votes = mapOf("a" to 1, "b" to 2)
        val (seat, tie) = GameEngine.computeExecutedSeat(votes)
        assertNull(seat)
        assertTrue(tie)
    }

    @Test
    fun executed_emptyVotes() {
        val (seat, tie) = GameEngine.computeExecutedSeat(emptyMap())
        assertNull(seat)
        assertFalse(tie)
    }

    // ====================== 白痴投票权 ======================

    @Test
    fun revealedIdiotCannotVote() {
        val idiot = player(0, Role.Idiot)
        val state = stateOf(listOf(idiot), RoleAbilities(idiotRevealed = true))
        assertFalse(GameEngine.canVote(idiot, state))
    }

    @Test
    fun unrevealedIdiotCanVote() {
        val idiot = player(0, Role.Idiot)
        val state = stateOf(listOf(idiot), RoleAbilities(idiotRevealed = false))
        assertTrue(GameEngine.canVote(idiot, state))
    }

    @Test
    fun deadPlayerCannotVote() {
        val p = player(0, Role.Villager, alive = false)
        assertFalse(GameEngine.canVote(p, stateOf(listOf(p))))
    }

    // ====================== 角色配置 ======================

    @Test
    fun roleConfigSizeMatchesPlayerCount() {
        for (n in GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS) {
            assertEquals(n, GameConfig.getRoleConfiguration(n).size)
        }
    }

    @Test
    fun idiotEntersPoolForNinePlus() {
        assertFalse(GameConfig.getRoleConfiguration(8).contains(Role.Idiot))
        assertTrue(GameConfig.getRoleConfiguration(9).contains(Role.Idiot))
        assertTrue(GameConfig.getRoleConfiguration(12).contains(Role.Idiot))
    }

    // ====================== 夜间历史无重复（回归测试）======================

    @Test
    fun nightHistoryRecordedOnceAfterResolve() {
        val engine = GameEngine()
        engine.createGame(GameSettings(playerCount = 9))
        engine.setupPlayers(GameSettings(playerCount = 9))

        engine.performNightActionWolf(0)
        engine.performNightActionGuard(1)
        // 行动阶段不写历史（统一在 resolveNight 写入）
        assertTrue(engine.gameState.value.nightActions.wolfKillHistory.isEmpty())
        assertTrue(engine.gameState.value.nightActions.guardActionHistory.isEmpty())

        engine.resolveNight()
        assertEquals(1, engine.gameState.value.nightActions.wolfKillHistory.size)
        assertEquals(1, engine.gameState.value.nightActions.guardActionHistory.size)
    }

    // ====================== U5：狼自爆流 ======================

    @Test
    fun wolfCanKillTeammate() {
        val engine = GameEngine()
        engine.createGame(GameSettings(playerCount = 9))
        engine.setupPlayers(GameSettings(playerCount = 9))
        val state = engine.gameState.value
        val wolves = state.players.filter { it.role.isWolfRole() }.sortedBy { it.seat }
        assertTrue("需要至少 2 个狼", wolves.size >= 2)
        val teammate = wolves[1]
        engine.performNightActionWolf(teammate.seat)
        assertEquals(teammate.seat, engine.gameState.value.nightActions.wolfTarget)
    }

    @Test
    fun wolfCanKillSelf() {
        val engine = GameEngine()
        engine.createGame(GameSettings(playerCount = 9))
        engine.setupPlayers(GameSettings(playerCount = 9))
        val state = engine.gameState.value
        val selfWolf = state.players.first { it.role.isWolfRole() }
        engine.performNightActionWolf(selfWolf.seat)
        assertEquals(selfWolf.seat, engine.gameState.value.nightActions.wolfTarget)
    }

    @Test
    fun wolfCannotKillDead() {
        val engine = GameEngine()
        engine.createGame(GameSettings(playerCount = 9))
        engine.setupPlayers(GameSettings(playerCount = 9))
        val state = engine.gameState.value
        val target = state.alivePlayers.first()
        engine.killPlayer(target.seat)
        engine.performNightActionWolf(target.seat)
        // 已死不能被记录为目标（自刀/杀队友是 alive 才允许）
        assertNull(engine.gameState.value.nightActions.wolfTarget)
    }
}

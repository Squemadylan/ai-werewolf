package com.squemadylan.wolfcha.ui.screens.game

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squemadylan.wolfcha.data.model.AvatarCatalog
import com.squemadylan.wolfcha.data.model.Alignment as GameAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.data.model.*
import com.squemadylan.wolfcha.ui.theme.*
import com.squemadylan.wolfcha.ui.viewmodel.AppViewModel
import com.squemadylan.wolfcha.ui.viewmodel.GameViewModel
import com.squemadylan.wolfcha.ui.viewmodel.NightActionType

@Composable
fun GameScreen(
    gameViewModel: GameViewModel,
    appViewModel: AppViewModel
) {
    val viewModel = gameViewModel
    val prefs by appViewModel.preferences.collectAsState()

    val gameState by viewModel.gameState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showRoleReveal by viewModel.showRoleReveal.collectAsState()
    val showNightAction by viewModel.showNightAction.collectAsState()
    val showVoteDialog by viewModel.showVoteDialog.collectAsState()
    val showResultPanel by viewModel.showResultPanel.collectAsState()
    val currentDialogue by viewModel.currentDialogue.collectAsState()
    val gameEnded by viewModel.gameEnded.collectAsState()
    val winner by viewModel.winner.collectAsState()
    val waitingForSpeechContinue by viewModel.waitingForSpeechContinue.collectAsState()
    val cheatMode by viewModel.cheatMode.collectAsState()

    var endGameConfirmStep by remember { mutableIntStateOf(0) }

    if (endGameConfirmStep == 1) {
        AlertDialog(
            onDismissRequest = { endGameConfirmStep = 0 },
            containerColor = DarkCard,
            title = { Text("确定要结束本局吗？", color = TextPrimary) },
            text = { Text("当前对局进度将丢失。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { endGameConfirmStep = 2 }) {
                    Text("确定", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { endGameConfirmStep = 0 }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }

    if (endGameConfirmStep == 2) {
        AlertDialog(
            onDismissRequest = { endGameConfirmStep = 0 },
            containerColor = DarkCard,
            title = { Text("再次确认：结束本局？", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    endGameConfirmStep = 0
                    viewModel.quitToLobby()
                }) {
                    Text("结束本局", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { endGameConfirmStep = 0 }) {
                    Text("返回", color = TextSecondary)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (gameState.isNight) NightBackground else DayBackground)
    ) {
        when {
            gameState.phase == Phase.LOBBY -> {
                GameLobbyScreen(
                    playerCount = prefs.playerCount,
                    difficultyLabel = appViewModel.difficultyLabel(prefs.difficulty),
                    onStartGame = { viewModel.startNewGame(appViewModel.buildGameSettings()) }
                )
            }
            showRoleReveal -> {
                RoleRevealScreen(
                    player = gameState.humanPlayer,
                    onContinue = { viewModel.onRoleRevealComplete() }
                )
            }
            gameEnded -> {
                GameEndScreen(
                    winner = winner,
                    players = gameState.players,
                    onPlayAgain = { viewModel.resetGame() }
                )
            }
            else -> {
                GamePlayScreen(
                    gameState = gameState,
                    currentDialogue = currentDialogue,
                    showNightAction = showNightAction,
                    showVoteDialog = showVoteDialog,
                    waitingForSpeechContinue = waitingForSpeechContinue,
                    isPaused = gameState.isPaused,
                    onTogglePause = { viewModel.togglePause() },
                    onRequestEndGame = { endGameConfirmStep = 1 },
                    onNightAction = { action, target, extra ->
                        viewModel.onHumanNightAction(action, target, extra)
                    },
                    onVote = { targetSeat ->
                        viewModel.onHumanVote(targetSeat)
                    },
                    onSpeechContinue = { viewModel.onSpeechContinue() },
                    showResultPanel = showResultPanel,
                    onToggleResultPanel = { viewModel.toggleResultPanel() },
                    onDismissResultPanel = { viewModel.dismissResultPanel() },
                    cheatMode = cheatMode,
                    onWolfBoom = { viewModel.requestWolfBoom() }
                )
            }
        }

        if (
            gameState.phase != Phase.LOBBY &&
            !showRoleReveal &&
            !gameEnded &&
            gameState.isPaused
        ) {
            // 暂停弹窗中的"5 连点暂停图标"探测（解锁/关闭天眼模式）
            var pauseIconTaps by remember { mutableStateOf(0) }
            val tapResetMillis = 3_000L
            var firstTapTime by remember { mutableStateOf(0L) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 紫色暂停图标——5 连点触发天眼
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = WolfchaPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if (pauseIconTaps == 0 || now - firstTapTime > tapResetMillis) {
                                        firstTapTime = now
                                        pauseIconTaps = 1
                                    } else {
                                        pauseIconTaps += 1
                                    }
                                    if (pauseIconTaps >= 5) {
                                        viewModel.toggleCheatMode()
                                        pauseIconTaps = 0
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "游戏已暂停",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (cheatMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = WolfchaPrimary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "天眼模式已开启",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WolfchaPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.togglePause() },
                            colors = ButtonDefaults.buttonColors(containerColor = WolfchaPrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("继续游戏")
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WolfchaPrimary)
            }
        }
    }
}

@Composable
private fun GameLobbyScreen(
    playerCount: Int,
    difficultyLabel: String,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "准备开始",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "${playerCount}人局 · 难度：$difficultyLabel",
            style = MaterialTheme.typography.titleMedium,
            color = WolfchaPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "角色配置已根据人数自动分配\n可在首页配置 AI 人设，在设置中调整人数与难度",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WolfchaPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "开始匹配",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RoleRevealScreen(
    player: GamePlayer?,
    onContinue: () -> Unit
) {
    val role = player?.role ?: Role.Villager
    val roleColor = when (role) {
        Role.Werewolf -> WerewolfRed
        Role.WhiteWolfKing -> WhiteWolfKingCrimson
        Role.Seer -> SeerPurple
        Role.Witch -> WitchGreen
        Role.Hunter -> HunterOrange
        Role.Guard -> GuardTeal
        Role.Idiot -> Color(0xFF8B5CF6)
        Role.Villager -> VillagerBlue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "你的身份是",
            style = MaterialTheme.typography.headlineMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(roleColor.copy(alpha = 0.2f))
                .border(4.dp, roleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = role.getDisplayName(),
                style = MaterialTheme.typography.displaySmall,
                color = roleColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = role.getDisplayName(),
            style = MaterialTheme.typography.displayMedium,
            color = roleColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (role) {
                Role.Werewolf -> "你是狼人，夜晚可以和其他狼人一起选择击杀目标"
                Role.WhiteWolfKing -> "你是白狼王，白天可以自爆并带走一名玩家"
                Role.Seer -> "你是预言家，夜晚可以查验一名玩家的身份"
                Role.Witch -> "你是女巫，拥有一瓶解药和一瓶毒药"
                Role.Hunter -> "你是猎人，出局时可以开枪带走一名玩家"
                Role.Guard -> "你是守卫，夜晚可以保护一名玩家"
                Role.Idiot -> "你是白痴，被投票出局时不会死亡"
                Role.Villager -> "你是平民，没有特殊能力，但可以通过分析找出狼人"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = roleColor
            )
        ) {
            Text(
                text = "开始游戏",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GamePlayScreen(
    gameState: WolfchaGameState,
    currentDialogue: com.squemadylan.wolfcha.ui.viewmodel.DialogueState?,
    showNightAction: Boolean,
    showVoteDialog: Boolean,
    waitingForSpeechContinue: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRequestEndGame: () -> Unit,
    onNightAction: (NightActionType, Int?, Map<String, String>) -> Unit,
    onVote: (Int) -> Unit,
    onSpeechContinue: () -> Unit,
    showResultPanel: Boolean,
    onToggleResultPanel: () -> Unit,
    onDismissResultPanel: () -> Unit,
    cheatMode: Boolean = false,
    onWolfBoom: () -> Unit = {}
) {
    val isHumanWolf = gameState.isHumanWolf
    val isHumanSeer = gameState.humanPlayer?.role == Role.Seer
    val wolfTeammateSeats = gameState.wolfTeammateSeats
    val idiotRevealed = gameState.roleAbilities.idiotRevealed
    // 预言家查验过且被查杀（isWolf=true）的座位集合
    val seerWolfSeats = remember(gameState.nightActions.seerHistory) {
        gameState.nightActions.seerHistory
            .filter { it.isWolf }
            .map { it.targetSeat }
            .toSet()
    }

    // 白天发言/投票阶段，人类是狼人/白狼王 → 浮动"自爆"按钮
    val isDayPhase = !gameState.isNight
    val canShowWolfBoomButton = isHumanWolf && isDayPhase && !isPaused

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        GameTopBar(
            gameState = gameState,
            isPaused = isPaused,
            onTogglePause = onTogglePause,
            onRequestEndGame = onRequestEndGame,
            onToggleResultPanel = onToggleResultPanel,
            showWolfBoomButton = canShowWolfBoomButton,
            onWolfBoom = onWolfBoom
        )

        // Player Grid
        PlayerGrid(
            players = gameState.players,
            humanPlayer = gameState.humanPlayer,
            isHumanWolf = isHumanWolf,
            isHumanSeer = isHumanSeer,
            wolfTeammateSeats = wolfTeammateSeats,
            seerWolfSeats = seerWolfSeats,
            idiotRevealed = idiotRevealed,
            cheatMode = cheatMode,
            modifier = Modifier.weight(1f)
        )

        // Message Area
        MessageArea(
            messages = gameState.messages.takeLast(20),
            players = gameState.players,
            modifier = Modifier.weight(1f)
        )

        // Vote Record Panel (during day or when there are votes)
        if (!gameState.isNight || gameState.votes.isNotEmpty()) {
            VoteRecordPanel(
                gameState = gameState,
                isHumanWolf = isHumanWolf,
                wolfTeammateSeats = wolfTeammateSeats
            )
        }

        // Action Area
        if (waitingForSpeechContinue && currentDialogue != null) {
            SpeechContinuePanel(
                dialogue = currentDialogue,
                onClick = onSpeechContinue
            )
        } else if (showNightAction && currentDialogue != null &&
            currentDialogue.actionType != NightActionType.AI_SPEECH_CONTINUE
        ) {
            NightActionPanel(
                dialogue = currentDialogue,
                players = gameState.players,
                alivePlayers = gameState.alivePlayers,
                humanPlayer = gameState.humanPlayer,
                roleAbilities = gameState.roleAbilities,
                onAction = onNightAction
            )
        }

        if (showVoteDialog) {
            val isBadgeVote = gameState.phase == Phase.DAY_BADGE_ELECTION
            // 显示完整 alive 列表；已出局者通过白痴/出局标记排除（VotePanel 内根据 idiotRevealed 灰化）
            val votablePlayers = if (isBadgeVote) {
                gameState.players.filter { it.seat in gameState.badge.candidates }
            } else {
                gameState.players
            }
            VotePanel(
                players = votablePlayers,
                humanPlayer = gameState.humanPlayer,
                title = if (isBadgeVote) "请投票选出警长" else "请选择投票目标",
                idiotRevealed = gameState.roleAbilities.idiotRevealed,
                onVote = onVote
            )
        }

        if (showResultPanel) {
            ResultPanel(
                gameState = gameState,
                onDismiss = onDismissResultPanel
            )
        }
    }
}

@Composable
private fun GameTopBar(
    gameState: WolfchaGameState,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRequestEndGame: () -> Unit,
    onToggleResultPanel: () -> Unit = {},
    showWolfBoomButton: Boolean = false,
    onWolfBoom: () -> Unit = {}
) {
    Surface(
        color = if (gameState.isNight) NightSurface else DaySurface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第 ${gameState.day} 天",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = gameState.phase.getDisplayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gameState.isNight) WolfchaSecondary else WolfchaAccent
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showWolfBoomButton) {
                    val isWhiteWolfKing = gameState.humanPlayer?.role == Role.WhiteWolfKing
                    Surface(
                        color = WerewolfRed.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onWolfBoom() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "自爆",
                                tint = WerewolfRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isWhiteWolfKing) "自爆带走" else "自爆",
                                style = MaterialTheme.typography.labelMedium,
                                color = WerewolfRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                StatusBadge(
                    text = "存活: ${gameState.alivePlayers.size}",
                    color = SuccessGreen
                )
                StatusBadge(
                    text = "狼: ${gameState.aliveWolves.size}",
                    color = WerewolfRed
                )
                IconButton(onClick = onToggleResultPanel) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "记录",
                        tint = WolfchaPrimary
                    )
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "继续" else "暂停",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onRequestEndGame) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "结束本局",
                        tint = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun PlayerGrid(
    players: List<GamePlayer>,
    humanPlayer: GamePlayer?,
    isHumanWolf: Boolean = false,
    isHumanSeer: Boolean = false,
    wolfTeammateSeats: Set<Int> = emptySet(),
    seerWolfSeats: Set<Int> = emptySet(),
    idiotRevealed: Boolean = false,
    cheatMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(players.chunked(2)) { rowPlayers ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPlayers.forEach { player ->
                    PlayerCard(
                        player = player,
                        isHuman = player.playerId == humanPlayer?.playerId,
                        isWolfTeammate = isHumanWolf && wolfTeammateSeats.contains(player.seat),
                        isCheckedWolf = isHumanSeer && seerWolfSeats.contains(player.seat),
                        isIdiotRevealed = idiotRevealed && player.role == Role.Idiot,
                        cheatMode = cheatMode,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPlayers.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 玩家卡片。
 *
 * 序号（头像圈内文字）颜色规则（按优先级从高到低）：
 * 1. 玩家已死亡 → 灰色
 * 2. cheatMode = true → 按真实身份显示 8 种颜色
 * 3. 玩家是人类自己 → 显示其身份的原色
 * 4. 玩家是人类的狼队友 → 狼队友红
 * 5. 玩家是预言家查验出来的狼人 → 红
 * 6. 其余 → 默认灰色
 *
 * 删除线（不改变颜色，仅 TextDecoration）：
 * - 玩家已死亡
 * - 玩家是白痴且已翻牌（即便还活着也加删除线，体现"票出"标记）
 */
@Composable
private fun PlayerCard(
    player: GamePlayer,
    isHuman: Boolean,
    isWolfTeammate: Boolean = false,
    isCheckedWolf: Boolean = false,
    isIdiotRevealed: Boolean = false,
    cheatMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val roleColor = when (player.role) {
        Role.Werewolf -> WerewolfRed
        Role.WhiteWolfKing -> WhiteWolfKingCrimson
        Role.Seer -> SeerPurple
        Role.Witch -> WitchGreen
        Role.Hunter -> HunterOrange
        Role.Guard -> GuardTeal
        Role.Idiot -> Color(0xFF8B5CF6)
        Role.Villager -> VillagerBlue
    }

    val avatarNumberColor: Color = when {
        !player.alive -> Color.Gray
        cheatMode -> cheatColorFor(player.role)
        isHuman -> roleColor
        isWolfTeammate -> WerewolfRed
        isCheckedWolf -> WerewolfRed
        else -> VillagerGray
    }

    val isOutOfGame = !player.alive || isIdiotRevealed
    val strike = if (isOutOfGame) TextDecoration.LineThrough else TextDecoration.None

    val cardModifier = if (isWolfTeammate && player.alive) {
        modifier.border(
            width = 2.dp,
            color = WerewolfRed,
            shape = RoundedCornerShape(12.dp)
        )
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (player.alive) DarkCard else DarkCard.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (player.alive) avatarNumberColor.copy(alpha = 0.3f)
                        else Color.Gray.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${player.seat + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = avatarNumberColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = strike
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.displayName + if (isHuman) " (你)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (player.alive) TextPrimary else TextMuted,
                    fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = strike
                )
                if (isWolfTeammate && player.alive) {
                    Text(
                        text = "狼队友",
                        style = MaterialTheme.typography.labelSmall,
                        color = WerewolfRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isCheckedWolf && player.alive) {
                    Text(
                        text = "查杀",
                        style = MaterialTheme.typography.labelSmall,
                        color = WerewolfRed,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isIdiotRevealed && player.alive) {
                    Text(
                        text = "已翻牌（无法被投票）",
                        style = MaterialTheme.typography.labelSmall,
                        color = WolfchaAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!player.alive) {
                    Text(
                        text = "已出局",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }

            if (player.alive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen)
                )
            }
        }
    }
}

/** 天眼模式：8 种身份 → 8 种序号色。 */
private fun cheatColorFor(role: Role): Color = when (role) {
    Role.Werewolf -> WerewolfRed
    Role.WhiteWolfKing -> WhiteWolfKingCrimson
    Role.Villager -> VillagerGray
    Role.Idiot -> IdiotLightGreen
    Role.Witch -> WitchGreen
    Role.Hunter -> HunterOrange
    Role.Seer -> SeerPurple
    Role.Guard -> GuardYellow
}

@Composable
private fun MessageArea(
    messages: List<ChatMessage>,
    players: List<GamePlayer>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        messages.forEach { message ->
            MessageItem(message = message, players = players)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, players: List<GamePlayer>) {
    if (message.isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = WolfchaPrimary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = WolfchaPrimary
                )
            }
        }
    } else {
        val isHuman = message.playerId.startsWith("human")
        val speaker = players.firstOrNull { it.playerId == message.playerId }
            val avatarRes = speaker?.let { findAvatarResource(it) } ?: 0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isHuman) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isHuman && avatarRes != 0) {
                Image(
                    painter = painterResource(id = avatarRes),
                    contentDescription = message.playerName,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkCard),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                color = if (isHuman) WolfchaPrimary.copy(alpha = 0.3f) else DarkCard,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.playerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHuman) WolfchaPrimary else TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
            if (isHuman) {
                Spacer(Modifier.width(8.dp))
                // Placeholder avatar for human (use first male as default)
                Image(
                    painter = painterResource(id = com.squemadylan.wolfcha.R.drawable.avatar_man_1),
                    contentDescription = "我",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkCard),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

private fun findAvatarResource(player: GamePlayer): Int {
    val gender = player.agentProfile?.persona?.gender ?: "male"
    val key = if (player.avatarKey.isNotBlank()) {
        player.avatarKey
    } else {
        val keys = AvatarCatalog.keysFor(gender)
        if (keys.isEmpty()) return 0
        keys[(player.avatarSeed.hashCode() and Int.MAX_VALUE) % keys.size]
    }
    return AvatarCatalog.resourceFor(key, gender)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NightActionPanel(
    dialogue: com.squemadylan.wolfcha.ui.viewmodel.DialogueState,
    players: List<GamePlayer>,
    alivePlayers: List<GamePlayer>,
    humanPlayer: GamePlayer?,
    roleAbilities: RoleAbilities,
    onAction: (NightActionType, Int?, Map<String, String>) -> Unit
) {
    var selectedTarget by remember { mutableStateOf<Int?>(null) }
    var speechText by remember { mutableStateOf("") }

    Surface(
        color = NightSurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = dialogue.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (dialogue.actionType) {
                NightActionType.GUARD -> {
                    TargetSelector(
                        players = alivePlayers,
                        onTargetSelected = { selectedTarget = it }
                    )
                }
                NightActionType.WOLF -> {
                    TargetSelector(
                        players = alivePlayers.filter { !it.role.isWolfRole() },
                        onTargetSelected = { selectedTarget = it }
                    )
                }
                NightActionType.WITCH -> {
                    WitchActionPanel(
                        wolfTarget = dialogue.extraData["wolfTarget"]?.toIntOrNull(),
                        healUsed = roleAbilities.witchHealUsed,
                        poisonUsed = roleAbilities.witchPoisonUsed,
                        players = alivePlayers,
                        onSave = { onAction(NightActionType.WITCH, null, mapOf("save" to "true")) },
                        onPoison = { target ->
                            onAction(NightActionType.WITCH, target, mapOf("poison" to target.toString()))
                        },
                        onPass = { onAction(NightActionType.WITCH, null, emptyMap()) }
                    )
                }
                NightActionType.SEER -> {
                    TargetSelector(
                        players = alivePlayers,
                        onTargetSelected = { selectedTarget = it }
                    )
                }
                NightActionType.SEER_RESULT -> {
                    Text(
                        text = "点击「继续」进入天亮阶段",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                NightActionType.SPEECH, NightActionType.LAST_WORDS -> {
                    OutlinedTextField(
                        value = speechText,
                        onValueChange = { speechText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (dialogue.actionType == NightActionType.LAST_WORDS) {
                                    "输入遗言..."
                                } else {
                                    "输入你的发言..."
                                },
                                color = TextMuted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WolfchaPrimary,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                NightActionType.HUNTER_SHOOT, NightActionType.WHITE_WOLF_BOOM -> {
                    val actorSeat = dialogue.extraData["seat"]?.toIntOrNull()
                    TargetSelector(
                        players = alivePlayers.filter { player ->
                            player.alive && player.seat != actorSeat
                        },
                        onTargetSelected = { selectedTarget = it }
                    )
                }
                NightActionType.BADGE_SIGNUP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                onAction(
                                    NightActionType.BADGE_SIGNUP,
                                    null,
                                    mapOf("signup" to "true")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WolfchaPrimary)
                        ) {
                            Text("上警", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                onAction(
                                    NightActionType.BADGE_SIGNUP,
                                    null,
                                    mapOf("signup" to "false")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Text("不上警")
                        }
                    }
                }
                else -> {}
            }

            if (dialogue.actionType != NightActionType.WITCH &&
                dialogue.actionType != NightActionType.BADGE_SIGNUP
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        when (dialogue.actionType) {
                            NightActionType.SPEECH -> {
                                onAction(
                                    dialogue.actionType,
                                    null,
                                    mapOf(
                                        "speech" to speechText,
                                        "context" to (dialogue.extraData["context"] ?: "")
                                    )
                                )
                            }
                            NightActionType.LAST_WORDS -> {
                                val seat = dialogue.extraData["seat"] ?: ""
                                onAction(
                                    dialogue.actionType,
                                    null,
                                    mapOf("speech" to speechText, "seat" to seat)
                                )
                            }
                            NightActionType.HUNTER_SHOOT, NightActionType.WHITE_WOLF_BOOM -> {
                                val seat = dialogue.extraData["seat"] ?: ""
                                onAction(dialogue.actionType, selectedTarget, mapOf("seat" to seat))
                            }
                            else -> {
                                onAction(dialogue.actionType, selectedTarget, emptyMap())
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = when (dialogue.actionType) {
                        NightActionType.SPEECH, NightActionType.LAST_WORDS -> speechText.isNotBlank()
                        NightActionType.HUNTER_SHOOT, NightActionType.WHITE_WOLF_BOOM -> selectedTarget != null
                        NightActionType.SEER_RESULT -> true
                        else -> selectedTarget != null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WolfchaPrimary
                    )
                ) {
                    Text(
                        text = when (dialogue.actionType) {
                            NightActionType.SPEECH -> "发言"
                            NightActionType.LAST_WORDS -> "发表遗言"
                            NightActionType.HUNTER_SHOOT -> "开枪"
                            NightActionType.WHITE_WOLF_BOOM -> "自爆带走"
                            else -> "确认"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetSelector(
    players: List<GamePlayer>,
    onTargetSelected: (Int) -> Unit
) {
    var selectedSeat by remember { mutableStateOf<Int?>(null) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        players.forEach { player ->
            val isSelected = selectedSeat == player.seat
            FilterChip(
                selected = isSelected,
                onClick = {
                    selectedSeat = player.seat
                    onTargetSelected(player.seat)
                },
                label = {
                    Text("${player.seat + 1}号 ${player.displayName}")
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WolfchaPrimary.copy(alpha = 0.3f),
                    selectedLabelColor = TextPrimary,
                    containerColor = DarkCard,
                    labelColor = TextSecondary
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WitchActionPanel(
    wolfTarget: Int?,
    healUsed: Boolean,
    poisonUsed: Boolean,
    players: List<GamePlayer>,
    onSave: () -> Unit,
    onPoison: (Int) -> Unit,
    onPass: () -> Unit
) {
    var showPoisonSelector by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (wolfTarget != null && !healUsed) {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WitchGreen
                )
            ) {
                Text("使用解药救人")
            }
        }

        if (!poisonUsed) {
            if (showPoisonSelector) {
                Text(
                    text = "选择毒杀目标：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    players.forEach { player ->
                        FilterChip(
                            selected = false,
                            onClick = { onPoison(player.seat) },
                            label = { Text("${player.seat + 1}号 ${player.displayName}") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkCard,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            } else {
                Button(
                    onClick = { showPoisonSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WerewolfRed
                    )
                ) {
                    Text("使用毒药")
                }
            }
        }

        OutlinedButton(
            onClick = onPass,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("跳过")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VotePanel(
    players: List<GamePlayer>,
    humanPlayer: GamePlayer?,
    title: String = "请选择投票目标",
    idiotRevealed: Boolean = false,
    onVote: (Int) -> Unit
) {
    Surface(
        color = DaySurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "提示：灰色带删除线按钮表示该玩家无法被投票（已出局/已翻牌白痴）",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                players.forEach { player ->
                    val isSelf = player.playerId == humanPlayer?.playerId
                    val isDead = !player.alive
                    val isRevealedIdiot = idiotRevealed && player.role == Role.Idiot
                    // 不可被投票：出局 / 已翻牌白痴 / 自己（白痴翻牌后自己不能投自己——后面 canVote 已盖住，
                    // 但为了 UI 反馈，这里给白痴翻牌时也禁用自己）
                    val disabled = isDead || isRevealedIdiot

                    if (disabled) {
                        // 不可选样式：灰底 + 删除线 + 角标
                        Surface(
                            color = Color.Gray.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${player.seat + 1}号 ${player.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isRevealedIdiot) WolfchaAccent.copy(alpha = 0.3f)
                                            else ErrorRed.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isRevealedIdiot) "已翻牌" else "已出局",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isRevealedIdiot) WolfchaAccent else ErrorRed,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { onVote(player.seat) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelf)
                                    WolfchaPrimary.copy(alpha = 0.25f)
                                else DarkCard
                            )
                        ) {
                            Text("${player.seat + 1}号 ${player.displayName}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultPanel(
    gameState: WolfchaGameState,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("投票记录", "出局记录", "我的行动")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "游戏记录",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        TextButton(
                            onClick = { selectedTab = index },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (selectedTab == index) WolfchaPrimary else Color.Transparent,
                                contentColor = if (selectedTab == index) Color.White else TextSecondary
                            )
                        ) {
                            Text(tab)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> VoteRecordContent(gameState)
                    1 -> DeathRecordContent(gameState)
                    2 -> PrivateActionRecordContent(gameState)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoteRecordContent(gameState: WolfchaGameState) {
    val currentDay = gameState.day
    val playerMap = gameState.players.associateBy { it.playerId }
    val isHumanWolf = gameState.isHumanWolf
    val wolfTeammateSeats = gameState.wolfTeammateSeats

    if (currentDay < 1) {
        EmptyRecord("暂无投票记录")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (d in 1..currentDay) {
            val dayVotes = gameState.votesOnDay(d)
            val executedSeat = gameState.executedSeatOnDay(d)
            val executedReason = gameState.executedReasonOnDay(d)

            if (dayVotes.isNotEmpty()) {
                item {
                    RecordDayCard(
                        title = "第 $d 天白天",
                        content = {
                            Column {
                                Text(
                                    text = "投票明细",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WolfchaAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val sortedVotes = dayVotes.entries.sortedBy { entry ->
                                        playerMap[entry.key]?.seat ?: 0
                                    }
                                    sortedVotes.forEach { (voterId, targetSeat) ->
                                        val voter = playerMap[voterId]
                                        val target = gameState.getPlayerBySeat(targetSeat)
                                        val isTeammateVote = isHumanWolf &&
                                            (wolfTeammateSeats.contains(voter?.seat) || voter?.isHuman == true)
                                        val bgColor = if (isTeammateVote) {
                                            WerewolfRed.copy(alpha = 0.25f)
                                        } else {
                                            WolfchaPrimary.copy(alpha = 0.12f)
                                        }
                                        val textColor = if (isTeammateVote) WerewolfRed else WolfchaPrimary
                                        Surface(
                                            color = bgColor,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${(voter?.seat?.plus(1)) ?: "?"}号 → " +
                                                    "${targetSeat + 1}号 ${target?.displayName ?: ""}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = textColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                fontWeight = if (isTeammateVote) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "得票统计",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WolfchaAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                val voteCounts = mutableMapOf<Int, Int>()
                                dayVotes.values.forEach { seat ->
                                    voteCounts[seat] = (voteCounts[seat] ?: 0) + 1
                                }
                                val sortedTargets = voteCounts.entries
                                    .sortedByDescending { it.value }
                                sortedTargets.forEach { (seat, count) ->
                                    val targetPlayer = gameState.getPlayerBySeat(seat)
                                    val isExecuted = (seat == executedSeat)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${seat + 1}号 ${targetPlayer?.displayName ?: ""}",
                                            color = if (isExecuted) ErrorRed else TextPrimary,
                                            fontWeight = if (isExecuted) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        StatusBadge(text = "$count 票", color = WolfchaAccent)
                                        if (isExecuted) {
                                            Spacer(Modifier.width(6.dp))
                                            StatusBadge(text = executedReason, color = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeathRecordContent(gameState: WolfchaGameState) {
    val currentDay = gameState.day
    if (currentDay < 1) {
        EmptyRecord("暂无出局记录")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (d in 1..currentDay) {
            val nightDeathsList = gameState.nightDeaths[d] ?: emptyList()
            val executedSeat = gameState.executedSeatOnDay(d)
            val executedReason = gameState.executedReasonOnDay(d)

            val hasContent = nightDeathsList.isNotEmpty() || executedSeat != null

            if (hasContent) {
                item {
                    RecordDayCard(
                        title = "第 $d 天",
                        content = {
                            Column {
                                if (nightDeathsList.isNotEmpty()) {
                                    Text(
                                        text = "夜间",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WolfchaSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    nightDeathsList.forEach { (seat, reason) ->
                                        val player = gameState.getPlayerBySeat(seat)
                                        Row(
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${seat + 1}号 ${player?.displayName ?: ""}",
                                                color = TextPrimary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            StatusBadge(text = reason, color = ErrorRed)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }

                                if (executedSeat != null) {
                                    Text(
                                        text = "白天",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WolfchaAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    val player = gameState.getPlayerBySeat(executedSeat)
                                    Row(
                                        modifier = Modifier.padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${executedSeat + 1}号 ${player?.displayName ?: ""}",
                                            color = TextPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        StatusBadge(text = executedReason, color = ErrorRed)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateActionRecordContent(gameState: WolfchaGameState) {
    val humanPlayer = gameState.humanPlayer
    val humanRole = humanPlayer?.role

    val seerHistory = gameState.nightActions.seerHistory
    val wolfKillHistory = gameState.nightActions.wolfKillHistory
    val witchActionHistory = gameState.nightActions.witchActionHistory
    val guardActionHistory = gameState.nightActions.guardActionHistory

    val isSeer = humanRole == Role.Seer
    val isWolf = humanRole?.isWolfRole() == true
    val isWitch = humanRole == Role.Witch
    val isGuard = humanRole == Role.Guard

    val hasSeerInfo = isSeer && seerHistory.isNotEmpty()
    val hasWolfInfo = isWolf && wolfKillHistory.isNotEmpty()
    val hasWitchInfo = isWitch && witchActionHistory.isNotEmpty()
    val hasGuardInfo = isGuard && guardActionHistory.isNotEmpty()

    if (!hasSeerInfo && !hasWolfInfo && !hasWitchInfo && !hasGuardInfo) {
        EmptyRecord("你当前没有专属行动记录\n（预言家查验、女巫救/毒、狼人刀人、守卫守人等仅对本人可见）")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasWolfInfo) {
            item {
                RecordDayCard(
                    title = "🐺 狼人刀人记录",
                    content = {
                        Column {
                            wolfKillHistory.sortedBy { it.day }.forEach { entry ->
                                val targetPlayer = gameState.getPlayerBySeat(entry.targetSeat)
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第${entry.day}晚 刀 ${entry.targetSeat + 1}号 ${targetPlayer?.displayName ?: ""}",
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(
                                        text = if (entry.killed) "成功击杀" else "被救",
                                        color = if (entry.killed) ErrorRed else WolfchaPrimary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        if (hasWitchInfo) {
            item {
                RecordDayCard(
                    title = "🧙 女巫用药记录",
                    content = {
                        Column {
                            witchActionHistory.sortedBy { it.day }.forEach { entry ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val healTarget = entry.healedTarget
                                    val poisonTarget = entry.poisonedTarget
                                    val healText = if (healTarget != null) {
                                        val target = gameState.getPlayerBySeat(healTarget)
                                        "救了${healTarget + 1}号${target?.displayName ?: ""}"
                                    } else ""
                                    val poisonText = if (poisonTarget != null) {
                                        val target = gameState.getPlayerBySeat(poisonTarget)
                                        "毒了${poisonTarget + 1}号${target?.displayName ?: ""}"
                                    } else ""
                                    val actionText = listOfNotNull(healText, poisonText).joinToString("，")
                                    Text(
                                        text = "第${entry.day}晚 $actionText",
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        if (hasGuardInfo) {
            item {
                RecordDayCard(
                    title = "🛡️ 守卫守护记录",
                    content = {
                        Column {
                            guardActionHistory.sortedBy { it.day }.forEach { entry ->
                                val targetPlayer = gameState.getPlayerBySeat(entry.targetSeat)
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第${entry.day}晚 守 ${entry.targetSeat + 1}号 ${targetPlayer?.displayName ?: ""}",
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        if (hasSeerInfo) {
            item {
                RecordDayCard(
                    title = "🔮 预言家查验记录",
                    content = {
                        Column {
                            seerHistory.sortedBy { it.day }.forEach { entry ->
                                val targetPlayer = gameState.getPlayerBySeat(entry.targetSeat)
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第${entry.day}晚 查 ${entry.targetSeat + 1}号 ${targetPlayer?.displayName ?: ""}",
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(
                                        text = if (entry.isWolf) "狼人" else "好人",
                                        color = if (entry.isWolf) ErrorRed else SuccessGreen
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RecordDayCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = Color(0xFF2A2D35),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = WolfchaPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun EmptyRecord(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoteRecordPanel(
    gameState: WolfchaGameState,
    isHumanWolf: Boolean,
    wolfTeammateSeats: Set<Int>
) {
    val playerMap = gameState.players.associateBy { it.playerId }

    val hasCurrentVotes = gameState.votes.isNotEmpty()
    val hasHistory = gameState.voteHistory.isNotEmpty()

    if (!hasCurrentVotes && !hasHistory) {
        Surface(
            color = if (gameState.isNight) NightSurface else DaySurface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "投票记录：暂无",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
        return
    }

    Surface(
        color = if (gameState.isNight) NightSurface else DaySurface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ===== 历史投票记录（仅显示摘要） =====
            if (hasHistory) {
                val sortedDays = gameState.voteHistory.keys.sorted().reversed().take(2)
                sortedDays.forEach { day ->
                    val dayVotes = gameState.voteHistory[day] ?: emptyMap()
                    if (dayVotes.isEmpty()) return@forEach

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = WolfchaPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "第 ${day} 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = WolfchaPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        // 被投出局的人
                        gameState.executedHistory[day]?.let { executedSeat ->
                            Spacer(modifier = Modifier.width(8.dp))
                            val executedName = gameState.getPlayerBySeat(executedSeat)?.displayName ?: ""
                            val voteCountForExecuted = dayVotes.values.count { it == executedSeat }
                            Surface(
                                color = ErrorRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${executedSeat + 1}号 $executedName 出局（${voteCountForExecuted}票）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (gameState.voteHistory.size > 2) {
                    Text(
                        text = "点击右上角「记录」查看完整投票详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ===== 当前轮（未结算）投票 =====
            if (hasCurrentVotes) {
                if (hasHistory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = TextMuted.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "本轮投票（进行中）",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val voteCounts = mutableMapOf<Int, Int>()
                gameState.votes.values.forEach { seat ->
                    voteCounts[seat] = (voteCounts[seat] ?: 0) + 1
                }
                if (voteCounts.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        voteCounts.entries
                            .sortedByDescending { it.value }
                            .forEach { (seat, count) ->
                                val targetPlayer = gameState.getPlayerBySeat(seat)
                                val topColor = if (count == voteCounts.values.maxOrNull()) {
                                    ErrorRed
                                } else {
                                    ErrorRed.copy(alpha = 0.7f)
                                }
                                Surface(
                                    color = topColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${seat + 1}号 ${targetPlayer?.displayName ?: ""}: ${count}票",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = topColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                    }
                }
                Text(
                    text = "已投票: ${gameState.votes.size}/${gameState.alivePlayers.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeechContinuePanel(
    dialogue: com.squemadylan.wolfcha.ui.viewmodel.DialogueState,
    onClick: () -> Unit
) {
    Surface(
        color = DaySurface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = WolfchaPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = dialogue.speaker,
                        style = MaterialTheme.typography.titleSmall,
                        color = WolfchaPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = dialogue.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WolfchaPrimary
                )
            ) {
                Text(
                    "继续",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GameEndScreen(
    winner: GameAlignment?,
    players: List<GamePlayer>,
    onPlayAgain: () -> Unit
) {
    val winnerColor = when (winner) {
        GameAlignment.VILLAGE -> VillagerBlue
        GameAlignment.WOLF -> WerewolfRed
        else -> TextSecondary
    }

    val winnerText = when (winner) {
        GameAlignment.VILLAGE -> "好人阵营胜利！"
        GameAlignment.WOLF -> "狼人阵营胜利！"
        else -> "游戏结束"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = winnerText,
            style = MaterialTheme.typography.displayMedium,
            color = winnerColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "角色 reveal",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        players.forEach { player ->
            val roleColor = when (player.role) {
                Role.Werewolf -> WerewolfRed
                Role.WhiteWolfKing -> WhiteWolfKingCrimson
                Role.Seer -> SeerPurple
                Role.Witch -> WitchGreen
                Role.Hunter -> HunterOrange
                Role.Guard -> GuardTeal
                Role.Idiot -> Color(0xFF8B5CF6)
                Role.Villager -> VillagerBlue
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${player.seat + 1}号 ${player.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = player.role.getDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = roleColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WolfchaPrimary
            )
        ) {
            Text(
                text = "再来一局",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

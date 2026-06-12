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
    val currentDialogue by viewModel.currentDialogue.collectAsState()
    val gameEnded by viewModel.gameEnded.collectAsState()
    val winner by viewModel.winner.collectAsState()
    val waitingForSpeechContinue by viewModel.waitingForSpeechContinue.collectAsState()

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
                    onSpeechContinue = { viewModel.onSpeechContinue() }
                )
            }
        }

        if (
            gameState.phase != Phase.LOBBY &&
            !showRoleReveal &&
            !gameEnded &&
            gameState.isPaused
        ) {
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
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = null,
                            tint = WolfchaPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "游戏已暂停",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
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
    onSpeechContinue: () -> Unit
) {
    val isHumanWolf = gameState.isHumanWolf
    val wolfTeammateSeats = gameState.wolfTeammateSeats

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        GameTopBar(
            gameState = gameState,
            isPaused = isPaused,
            onTogglePause = onTogglePause,
            onRequestEndGame = onRequestEndGame
        )

        // Player Grid
        PlayerGrid(
            players = gameState.players,
            humanPlayer = gameState.humanPlayer,
            isHumanWolf = isHumanWolf,
            wolfTeammateSeats = wolfTeammateSeats,
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
            VotePanel(
                players = gameState.alivePlayers,
                humanPlayer = gameState.humanPlayer,
                onVote = onVote
            )
        }
    }
}

@Composable
private fun GameTopBar(
    gameState: WolfchaGameState,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onRequestEndGame: () -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = "存活: ${gameState.alivePlayers.size}",
                    color = SuccessGreen
                )
                StatusBadge(
                    text = "狼: ${gameState.aliveWolves.size}",
                    color = WerewolfRed
                )
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
    wolfTeammateSeats: Set<Int> = emptySet(),
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

@Composable
private fun PlayerCard(
    player: GamePlayer,
    isHuman: Boolean,
    isWolfTeammate: Boolean = false,
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
                        if (player.alive) roleColor.copy(alpha = 0.3f)
                        else Color.Gray.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${player.seat + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (player.alive) roleColor else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.displayName + if (isHuman) " (你)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (player.alive) TextPrimary else TextMuted,
                    fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal
                )
                if (isWolfTeammate && player.alive) {
                    Text(
                        text = "狼队友",
                        style = MaterialTheme.typography.labelSmall,
                        color = WerewolfRed,
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
                        players = alivePlayers.filter { it.seat != humanPlayer?.seat },
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
                        players = alivePlayers.filter { it.seat != humanPlayer?.seat },
                        onTargetSelected = { selectedTarget = it }
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
                else -> {}
            }

            if (dialogue.actionType != NightActionType.WITCH) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        when (dialogue.actionType) {
                            NightActionType.SPEECH -> {
                                onAction(dialogue.actionType, null, mapOf("speech" to speechText))
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
                text = "请选择投票目标",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                players.filter { it.playerId != humanPlayer?.playerId }.forEach { player ->
                    Button(
                        onClick = { onVote(player.seat) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard
                        )
                    ) {
                        Text("${player.seat + 1}号 ${player.displayName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteRecordPanel(
    gameState: WolfchaGameState,
    isHumanWolf: Boolean,
    wolfTeammateSeats: Set<Int>
) {
    val voteMap = gameState.votes
    val playerMap = gameState.players.associateBy { it.playerId }

    if (voteMap.isEmpty()) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "本轮投票",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val sortedVotes = voteMap.entries.sortedBy { entry ->
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
                        WolfchaPrimary.copy(alpha = 0.15f)
                    }
                    val textColor = if (isTeammateVote) WerewolfRed else WolfchaPrimary
                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${(voter?.seat?.plus(1)) ?: "?"}号 → " +
                                "${targetSeat + 1}号 ${target?.displayName ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = if (isTeammateVote) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Vote count summary
            val voteCounts = mutableMapOf<Int, Int>()
            voteMap.values.forEach { seat ->
                voteCounts[seat] = (voteCounts[seat] ?: 0) + 1
            }
            if (voteCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "得票统计",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    voteCounts.entries
                        .sortedByDescending { it.value }
                        .forEach { (seat, count) ->
                            val targetPlayer = gameState.getPlayerBySeat(seat)
                            Surface(
                                color = ErrorRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${seat + 1}号 ${targetPlayer?.displayName ?: ""}: ${count}票",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ErrorRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                }
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

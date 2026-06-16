package com.squemadylan.wolfcha.ui.screens.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.squemadylan.wolfcha.data.model.Alignment as GameAlignment
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.data.model.getDisplayName
import com.squemadylan.wolfcha.data.replay.ReplayEvent
import com.squemadylan.wolfcha.data.replay.ReplayPlayer
import com.squemadylan.wolfcha.data.replay.ReplayRecord
import com.squemadylan.wolfcha.data.replay.ReplayStore
import com.squemadylan.wolfcha.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * U6 复盘详情页：
 * - 顶部摘要（玩家身份 + 出局记录）
 * - 全部事件按时间顺序列出（payload 翻译成可读文字）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayDetailScreen(
    gameId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ReplayStore(context) }
    val records by store.replays.collectAsState(initial = emptyList())
    val record = records.firstOrNull { it.gameId == gameId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复盘详情", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到该对局记录", color = TextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ReplayHeader(record) }
            item { ReplayPlayersSection(record.players) }
            item {
                Text(
                    text = "事件流（共 ${record.events.size} 条）",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(record.events) { event ->
                ReplayEventItem(event, record.players)
            }
        }
    }
}

@Composable
private fun ReplayHeader(record: ReplayRecord) {
    val winColor = when (record.winner) {
        GameAlignment.WOLF.name -> WerewolfRed
        GameAlignment.VILLAGE.name -> SuccessGreen
        else -> TextMuted
    }
    val winText = when (record.winner) {
        GameAlignment.WOLF.name -> "🐺 狼人阵营胜利"
        GameAlignment.VILLAGE.name -> "🌟 好人阵营胜利"
        else -> if (record.isFinished) "已结束" else "中途退出"
    }
    val startStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.startTime))
    val endStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.endTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(record.headline(), color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(winText, color = winColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("开始：$startStr    结束：$endStr", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReplayPlayersSection(players: List<ReplayPlayer>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "玩家身份",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            players.sortedBy { it.seat }.forEach { p ->
                val role = runCatching { Role.valueOf(p.role) }.getOrDefault(Role.Villager)
                val roleColor = roleColorOf(role)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${p.seat + 1}号 ${p.displayName}${if (p.isHuman) " (你)" else ""}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (p.isHuman) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(color = roleColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = role.getDisplayName(),
                            color = roleColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val statusText = if (!p.survived && p.diedOnDay != null) {
                        "第${p.diedOnDay}天 ${p.diedReason ?: "出局"}"
                    } else {
                        "存活"
                    }
                    val statusColor = if (!p.survived) ErrorRed else SuccessGreen
                    Text(statusText, color = statusColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReplayEventItem(event: ReplayEvent, players: List<ReplayPlayer>) {
    val (icon, text) = humanize(event, players)
    val (badgeColor, badgeText) = when (event.type) {
        "GAME_START" -> SuccessGreen to "开局"
        "ROLE_ASSIGNED" -> WolfchaAccent to "发牌"
        "PHASE_CHANGED" -> InfoBlue to "阶段"
        "CHAT_MESSAGE" -> TextSecondary to "发言"
        "SYSTEM_MESSAGE" -> TextMuted to "公告"
        "NIGHT_ACTION" -> NightAccent to "夜动"
        "VOTE_CAST" -> WolfchaAccent to "投票"
        "PLAYER_DIED" -> ErrorRed to "死亡"
        "GAME_END" -> SuccessGreen to "终局"
        "BADGE_SIGNUP" -> WolfchaAccent to "上警"
        "BADGE_SPEECH" -> TextSecondary to "上警发言"
        "BADGE_ELECTION_VOTE" -> WolfchaAccent to "警选票"
        "BADGE_HOLDER_SET" -> SuccessGreen to "警徽"
        "GUARD_ACTION" -> GuardTeal to "守卫"
        "WOLF_KILL" -> WerewolfRed to "夜袭"
        "WITCH_ACTION" -> WitchGreen to "女巫"
        "SEER_CHECK" -> SeerPurple to "查验"
        "HUNTER_SHOOT" -> HunterOrange to "猎人"
        "WHITE_WOLF_BOOM" -> WerewolfRed to "白狼"
        "WOLF_BOOM" -> WerewolfRed to "自爆"
        "IDIOT_REVEAL" -> WolfchaAccent to "白痴"
        "LAST_WORDS" -> TextSecondary to "遗言"
        else -> TextMuted to event.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E32))
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = badgeColor.copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                if (event.payload.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = event.payload.entries.joinToString(" · ") { "${it.key}=${it.value}" },
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = "D${event.day}",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun humanize(event: ReplayEvent, players: List<ReplayPlayer>): Pair<String, String> {
    val p = players.associateBy { it.seat }
    fun name(seat: Int?): String = seat?.let { "${it + 1}号${p[it]?.displayName ?: ""}" } ?: "?"
    return when (event.type) {
        "GAME_START" -> "🎮" to "对局开始（${players.size} 人）"
        "ROLE_ASSIGNED" -> "🎴" to "身份已分配"
        "PHASE_CHANGED" -> "🔄" to "阶段切换 → ${event.payload["newPhase"] ?: "?"}"
        "CHAT_MESSAGE" -> "💬" to "${event.payload["playerName"] ?: "?"}：${event.payload["content"] ?: ""}"
        "SYSTEM_MESSAGE" -> "📢" to (event.payload["content"] ?: "")
        "NIGHT_ACTION" -> "🌙" to "夜动：${event.payload["action"] ?: "?"} 目标=${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "VOTE_CAST" -> "🗳" to "${name(event.payload["voterSeat"]?.toIntOrNull())} 投 ${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "PLAYER_DIED" -> "💀" to "${name(event.payload["seat"]?.toIntOrNull())} 死亡（${event.payload["reason"] ?: ""}）"
        "GAME_END" -> "🏁" to "游戏结束：${event.payload["winner"] ?: "?"}"
        "BADGE_SIGNUP" -> "✋" to "${name(event.payload["seat"]?.toIntOrNull())} ${if (event.payload["signup"] == "true") "上警" else "不上警"}"
        "BADGE_SPEECH" -> "🎤" to "${name(event.payload["seat"]?.toIntOrNull())} 上警发言：${event.payload["content"] ?: ""}"
        "BADGE_ELECTION_VOTE" -> "🗳" to "${name(event.payload["voterSeat"]?.toIntOrNull())} 投 ${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "BADGE_HOLDER_SET" -> "🏅" to "${name(event.payload["seat"]?.toIntOrNull())} 当选警长"
        "GUARD_ACTION" -> "🛡" to "${name(event.payload["seat"]?.toIntOrNull())} 守 ${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "WOLF_KILL" -> "🐺" to "狼队刀 ${name(event.payload["targetSeat"]?.toIntOrNull())}（${event.payload["succeeded"] ?: ""}）"
        "WITCH_ACTION" -> "🧪" to buildString {
            val heal = event.payload["healedSeat"]?.toIntOrNull()?.let { "救${name(it)}" }
            val poison = event.payload["poisonedSeat"]?.toIntOrNull()?.let { "毒${name(it)}" }
            append(listOfNotNull(heal, poison).joinToString("，"))
            if (isEmpty()) append("女巫没用药")
        }
        "SEER_CHECK" -> "🔮" to "${name(event.payload["seat"]?.toIntOrNull())} 查 ${name(event.payload["targetSeat"]?.toIntOrNull())} → ${event.payload["result"] ?: "?"}"
        "HUNTER_SHOOT" -> "🏹" to "${name(event.payload["seat"]?.toIntOrNull())} 开枪 → ${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "WHITE_WOLF_BOOM" -> "💥" to "${name(event.payload["seat"]?.toIntOrNull())} 自爆带走 ${name(event.payload["targetSeat"]?.toIntOrNull())}"
        "WOLF_BOOM" -> "💥" to "${name(event.payload["seat"]?.toIntOrNull())} 狼自爆"
        "IDIOT_REVEAL" -> "🤪" to "${name(event.payload["seat"]?.toIntOrNull())} 翻牌白痴"
        "LAST_WORDS" -> "🕯" to "${name(event.payload["seat"]?.toIntOrNull())} 遗言：${event.payload["content"] ?: ""}"
        else -> "•" to (event.payload.toString().ifBlank { event.type })
    }
}

private fun roleColorOf(role: Role): Color = when (role) {
    Role.Werewolf -> WerewolfRed
    Role.WhiteWolfKing -> WhiteWolfKingCrimson
    Role.Seer -> SeerPurple
    Role.Witch -> WitchGreen
    Role.Hunter -> HunterOrange
    Role.Guard -> GuardTeal
    Role.Idiot -> IdiotLightGreen
    Role.Villager -> VillagerBlue
}

// 浅夜色徽标（避免用未声明的 NightAccent）
private val NightAccent = Color(0xFF7C3AED)

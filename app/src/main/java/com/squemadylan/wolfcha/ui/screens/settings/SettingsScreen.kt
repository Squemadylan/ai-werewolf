package com.squemadylan.wolfcha.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.data.model.DifficultyLevel
import com.squemadylan.wolfcha.ui.theme.*
import com.squemadylan.wolfcha.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(
    appViewModel: AppViewModel,
    onNavigateToLlmSettings: () -> Unit = {},
    onNavigateToTtsSettings: () -> Unit = {},
    onNavigateToHowToPlay: () -> Unit = {}
) {
    val prefs by appViewModel.preferences.collectAsState()
    val scrollState = rememberScrollState()
    var showPlayerCountDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var humanName by remember(prefs.playerName) { mutableStateOf(prefs.playerName) }

    if (showPlayerCountDialog) {
        PlayerCountDialog(
            current = prefs.playerCount,
            options = appViewModel.playerCountOptions(),
            onDismiss = { showPlayerCountDialog = false },
            onSelect = {
                appViewModel.updatePlayerCount(it)
                showPlayerCountDialog = false
            }
        )
    }

    if (showDifficultyDialog) {
        DifficultyDialog(
            current = prefs.difficulty,
            onDismiss = { showDifficultyDialog = false },
            onSelect = {
                appViewModel.updateDifficulty(it)
                showDifficultyDialog = false
            },
            labelFor = appViewModel::difficultyLabel
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsSection(title = "大模型接口") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBackground)
            ) {
                Text(
                    text = "每个 AI 独立请求，无对话历史，一个 API Key 即可",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }
            SettingsItem(
                icon = Icons.Default.SmartToy,
                title = "AI 模型配置",
                subtitle = "配置 API Key、Base URL 与模型，让 AI 玩家使用大模型发言",
                onClick = onNavigateToLlmSettings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "音频") {
            SettingsItem(
                icon = Icons.Default.RecordVoiceOver,
                title = "语音合成",
                subtitle = "配置豆包 TTS、旁白与玩家朗读音色",
                onClick = onNavigateToTtsSettings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "游戏设置") {
            OutlinedTextField(
                value = humanName,
                onValueChange = {
                    humanName = it
                    appViewModel.updatePlayerName(it)
                },
                label = { Text("你的昵称") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = WolfchaPrimary,
                    unfocusedBorderColor = TextMuted
                )
            )

            SettingsItem(
                icon = Icons.Default.Person,
                title = "玩家数量",
                subtitle = "${prefs.playerCount} 人局",
                onClick = { showPlayerCountDialog = true }
            )

            SettingsItem(
                icon = Icons.Default.Star,
                title = "难度",
                subtitle = appViewModel.difficultyLabel(prefs.difficulty),
                onClick = { showDifficultyDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "1.0.0",
                onClick = { }
            )

            SettingsItem(
                icon = Icons.Default.Description,
                title = "游戏说明",
                subtitle = "查看详细规则",
                onClick = onNavigateToHowToPlay
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlayerCountDialog(
    current: Int,
    options: List<Int>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("选择玩家人数", color = TextPrimary) },
        text = {
            Column {
                options.forEach { count ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(count) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = count == current,
                            onClick = { onSelect(count) },
                            colors = RadioButtonDefaults.colors(selectedColor = WolfchaPrimary)
                        )
                        Text("$count 人局", color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun DifficultyDialog(
    current: DifficultyLevel,
    onDismiss: () -> Unit,
    onSelect: (DifficultyLevel) -> Unit,
    labelFor: (DifficultyLevel) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("选择难度", color = TextPrimary) },
        text = {
            Column {
                DifficultyLevel.entries.forEach { level ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(level) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = level == current,
                            onClick = { onSelect(level) },
                            colors = RadioButtonDefaults.colors(selectedColor = WolfchaPrimary)
                        )
                        Text(labelFor(level), color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WolfchaPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WolfchaPrimary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

package com.squemadylan.wolfcha.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.data.local.PreferencesDataStore
import com.squemadylan.wolfcha.data.model.TtsConfig
import com.squemadylan.wolfcha.data.model.VolcVoiceCatalog
import com.squemadylan.wolfcha.ui.theme.*
import com.squemadylan.wolfcha.util.VoiceHelper
import kotlinx.coroutines.launch

private const val TEST_SAMPLE_TEXT = "这是语音合成测试。欢迎来到狼人杀，请享受游戏。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { PreferencesDataStore(context.applicationContext) }

    val initialConfig by dataStore.ttsConfig.collectAsState(initial = TtsConfig())

    var appId by remember { mutableStateOf(initialConfig.appId) }
    var accessToken by remember { mutableStateOf(initialConfig.accessToken) }
    var enabled by remember { mutableStateOf(initialConfig.enabled) }
    var maleVoice by remember { mutableStateOf(initialConfig.maleVoice) }
    var femaleVoice by remember { mutableStateOf(initialConfig.femaleVoice) }
    var maleRandom by remember { mutableStateOf(initialConfig.maleRandom) }
    var femaleRandom by remember { mutableStateOf(initialConfig.femaleRandom) }
    var narratorEnabled by remember { mutableStateOf(initialConfig.narratorEnabled) }
    var playerSpeechEnabled by remember { mutableStateOf(initialConfig.playerSpeechEnabled) }
    var showToken by remember { mutableStateOf(false) }
    var maleMenuExpanded by remember { mutableStateOf(false) }
    var femaleMenuExpanded by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialConfig) {
        appId = initialConfig.appId
        accessToken = initialConfig.accessToken
        enabled = initialConfig.enabled
        maleVoice = initialConfig.maleVoice
        femaleVoice = initialConfig.femaleVoice
        maleRandom = initialConfig.maleRandom
        femaleRandom = initialConfig.femaleRandom
        narratorEnabled = initialConfig.narratorEnabled
        playerSpeechEnabled = initialConfig.playerSpeechEnabled
    }

    fun buildConfig() = TtsConfig(
        appId = appId.ifBlank { TtsConfig.DEFAULT_APP_ID },
        accessToken = accessToken.trim(),
        maleVoice = maleVoice,
        femaleVoice = femaleVoice,
        maleRandom = maleRandom,
        femaleRandom = femaleRandom,
        narratorEnabled = narratorEnabled,
        playerSpeechEnabled = playerSpeechEnabled,
        enabled = enabled
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音合成") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = WolfchaPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "豆包 TTS",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "配置火山引擎 OpenSpeech 凭证后，旁白与 AI 玩家发言可自动朗读。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WolfchaPrimary,
                                checkedTrackColor = WolfchaPrimary.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (enabled) "已启用" else "未启用",
                            color = if (enabled) SuccessGreen else TextMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = appId,
                        onValueChange = { appId = it.filter { c -> c.isDigit() } },
                        label = { Text("AppID") },
                        placeholder = { Text(TtsConfig.DEFAULT_APP_ID) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = ttsTextFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "隐藏" else "显示", color = WolfchaPrimary)
                            }
                        },
                        colors = ttsTextFieldColors()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("旁白音色", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = "灿灿 BV700（固定）",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ttsTextFieldColors()
                    )

                    Spacer(Modifier.height(16.dp))
                    VoiceGenderSection(
                        title = "男声音色",
                        random = maleRandom,
                        onRandomChange = { maleRandom = it },
                        selectedVoice = maleVoice,
                        onVoiceSelected = { maleVoice = it },
                        voices = VolcVoiceCatalog.maleVoices,
                        menuExpanded = maleMenuExpanded,
                        onMenuExpandedChange = { maleMenuExpanded = it }
                    )

                    Spacer(Modifier.height(16.dp))
                    VoiceGenderSection(
                        title = "女声音色",
                        random = femaleRandom,
                        onRandomChange = { femaleRandom = it },
                        selectedVoice = femaleVoice,
                        onVoiceSelected = { femaleVoice = it },
                        voices = VolcVoiceCatalog.femaleVoices,
                        menuExpanded = femaleMenuExpanded,
                        onMenuExpandedChange = { femaleMenuExpanded = it }
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("旁白语音", color = TextPrimary)
                        Switch(
                            checked = narratorEnabled,
                            onCheckedChange = { narratorEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WolfchaPrimary,
                                checkedTrackColor = WolfchaPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("玩家语音", color = TextPrimary)
                        Switch(
                            checked = playerSpeechEnabled,
                            onCheckedChange = { playerSpeechEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WolfchaPrimary,
                                checkedTrackColor = WolfchaPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        scope.launch {
                            val cfg = buildConfig()
                            VoiceHelper.updateConfig(cfg)
                            VoiceHelper.speakNarration(TEST_SAMPLE_TEXT)
                            testing = false
                        }
                    },
                    enabled = !testing && accessToken.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (testing) "播放中…" else "测试朗读")
                }
                Button(
                    onClick = {
                        scope.launch {
                            val cfg = buildConfig()
                            dataStore.saveTtsConfig(cfg)
                            VoiceHelper.updateConfig(cfg)
                            saveStatus = "已保存"
                        }
                    },
                    enabled = accessToken.isNotBlank() || !enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WolfchaPrimary)
                ) {
                    Text("保存配置")
                }
            }

            saveStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Access Token 仅保存在本地 DataStore 中。",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceGenderSection(
    title: String,
    random: Boolean,
    onRandomChange: (Boolean) -> Unit,
    selectedVoice: String,
    onVoiceSelected: (String) -> Unit,
    voices: List<com.squemadylan.wolfcha.data.model.VolcVoiceOption>,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("随机音色", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = random,
            onCheckedChange = onRandomChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WolfchaPrimary,
                checkedTrackColor = WolfchaPrimary.copy(alpha = 0.5f)
            )
        )
    }
    if (!random) {
        Spacer(Modifier.height(8.dp))
        val displayName = voices.find { it.voiceType == selectedVoice }?.displayName
            ?: voices.firstOrNull()?.displayName.orEmpty()
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { onMenuExpandedChange(!menuExpanded) }
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = ttsTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.displayName) },
                        onClick = {
                            onVoiceSelected(voice.voiceType)
                            onMenuExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ttsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WolfchaPrimary,
    unfocusedBorderColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = WolfchaPrimary,
    unfocusedLabelColor = TextSecondary
)

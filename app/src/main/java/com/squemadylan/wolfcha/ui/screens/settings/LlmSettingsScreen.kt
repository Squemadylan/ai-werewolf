package com.squemadylan.wolfcha.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SmartToy
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
import com.squemadylan.wolfcha.data.model.LlmConfig
import com.squemadylan.wolfcha.data.model.LlmProvider
import com.squemadylan.wolfcha.data.remote.LlmService
import com.squemadylan.wolfcha.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { PreferencesDataStore(context.applicationContext) }
    val llmService = remember { LlmService() }

    val initialConfig by dataStore.llmConfig.collectAsState(initial = LlmConfig())

    var provider by remember { mutableStateOf(initialConfig.provider) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var model by remember { mutableStateOf(initialConfig.model) }
    var temperatureText by remember { mutableStateOf(initialConfig.temperature.toString()) }
    var maxTokensText by remember { mutableStateOf(initialConfig.maxTokens.toString()) }
    var enabled by remember { mutableStateOf(initialConfig.enabled) }
    var showApiKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialConfig) {
        // When the underlying DataStore value changes, refresh the local form
        // state so it reflects whatever was previously persisted.
        provider = initialConfig.provider
        apiKey = initialConfig.apiKey
        baseUrl = initialConfig.baseUrl.ifBlank { initialConfig.provider.defaultBaseUrl }
        model = initialConfig.model.ifBlank { initialConfig.provider.defaultModel }
        temperatureText = initialConfig.temperature.toString()
        maxTokensText = initialConfig.maxTokens.toString()
        enabled = initialConfig.enabled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("大模型接口") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
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
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = WolfchaPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI 玩家接入",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "开启后，AI 玩家将通过你配置的大模型生成发言。未开启时将使用内置模板。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WolfchaPrimary,
                                checkedTrackColor = WolfchaPrimary.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (enabled) "已启用" else "未启用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (enabled) SuccessGreen else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "服务商",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = providerMenuExpanded,
                        onExpandedChange = { providerMenuExpanded = !providerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = provider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WolfchaPrimary,
                                unfocusedBorderColor = TextMuted,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            LlmProvider.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        provider = option
                                        baseUrl = baseUrl.ifBlank { option.defaultBaseUrl }
                                        model = model.ifBlank { option.defaultModel }
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text(provider.defaultBaseUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(
                                    text = if (showApiKey) "隐藏" else "显示",
                                    color = WolfchaPrimary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "模型",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it.trim() },
                        label = { Text("Model") },
                        placeholder = { Text(provider.defaultModel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors()
                    )

                    if (provider.presetModels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            OutlinedButton(
                                onClick = { modelMenuExpanded = true }
                            ) {
                                Text("选择预设模型")
                            }
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false }
                            ) {
                                provider.presetModels.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset) },
                                        onClick = {
                                            model = preset
                                            modelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = temperatureText,
                            onValueChange = { temperatureText = it },
                            label = { Text("Temperature") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                        OutlinedTextField(
                            value = maxTokensText,
                            onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                            label = { Text("Max Tokens") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = textFieldColors()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val cfg = buildConfig(
                                provider, apiKey, baseUrl, model,
                                temperatureText, maxTokensText, enabled
                            )
                            val result = llmService.chat(
                                config = cfg,
                                messages = listOf(
                                    LlmService.Message(
                                        role = "system",
                                        content = "你是一个测试助手，请用一句话回复「OK」。"
                                    ),
                                    LlmService.Message(
                                        role = "user",
                                        content = "测试连接"
                                    )
                                )
                            )
                            testing = false
                            when (result) {
                                is LlmService.Result.Success -> {
                                    testSuccess = true
                                    testResult = "连接成功，返回内容：${result.content.take(60)}"
                                }
                                is LlmService.Result.Failure -> {
                                    testSuccess = false
                                    testResult = "连接失败：${result.message}"
                                }
                            }
                        }
                    },
                    enabled = !testing && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val cfg = buildConfig(
                                provider, apiKey, baseUrl, model,
                                temperatureText, maxTokensText, enabled
                            )
                            dataStore.saveLlmConfig(cfg)
                            saveStatus = "已保存"
                        }
                    },
                    enabled = apiKey.isNotBlank() || !enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = WolfchaPrimary)
                ) {
                    Text("保存配置")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            testResult?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (testSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (testSuccess) SuccessGreen else ErrorRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            saveStatus?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "提示：API Key 仅保存在本地 DataStore 中。",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WolfchaPrimary,
    unfocusedBorderColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = WolfchaPrimary,
    unfocusedLabelColor = TextSecondary
)

private fun buildConfig(
    provider: LlmProvider,
    apiKey: String,
    baseUrl: String,
    model: String,
    temperatureText: String,
    maxTokensText: String,
    enabled: Boolean
): LlmConfig {
    val temperature = temperatureText.toFloatOrNull()?.coerceIn(0f, 2f) ?: 0.7f
    val maxTokens = maxTokensText.toIntOrNull()?.coerceIn(16, 8192) ?: 400
    return LlmConfig(
        provider = provider,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl },
        model = model.ifBlank { provider.defaultModel },
        temperature = temperature,
        maxTokens = maxTokens,
        enabled = enabled
    )
}

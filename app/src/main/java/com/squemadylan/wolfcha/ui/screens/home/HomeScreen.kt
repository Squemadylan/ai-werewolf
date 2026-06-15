package com.squemadylan.wolfcha.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.data.model.AiPersonaProfile
import com.squemadylan.wolfcha.data.model.AvatarCatalog
import com.squemadylan.wolfcha.ui.components.HowToPlayDialog
import com.squemadylan.wolfcha.ui.theme.*
import com.squemadylan.wolfcha.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appViewModel: AppViewModel,
    onStartGame: () -> Unit
) {
    val prefs by appViewModel.preferences.collectAsState()
    val isGenerating by appViewModel.isGeneratingPersona.collectAsState()
    val personaMessage by appViewModel.personaMessage.collectAsState()
    var showHowToPlay by remember { mutableStateOf(false) }
    var humanName by remember(prefs.playerName) { mutableStateOf(prefs.playerName) }
    var selectedIndex by remember(prefs.aiPersonaPool.size) { mutableIntStateOf(0) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            val ok = writeJsonToUri(context, uri, json)
            appViewModel.notifyPersonaMessage(if (ok) "已保存人设文件" else "保存失败，请检查权限")
        }
        pendingExportJson = null
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextFromUri(context, uri)
            if (!text.isNullOrBlank()) {
                val parsed = parsePoolJson(text)
                if (parsed.isNotEmpty()) {
                    appViewModel.importPersonaPool(parsed)
                } else {
                    appViewModel.notifyPersonaMessage("人设文件解析失败")
                }
            } else {
                appViewModel.notifyPersonaMessage("无法读取该文件")
            }
        }
    }

    LaunchedEffect(personaMessage) {
        personaMessage?.let {
            snackbarHostState.showSnackbar(it)
            appViewModel.clearPersonaMessage()
        }
    }

    if (showHowToPlay) {
        HowToPlayDialog(onDismiss = { showHowToPlay = false })
    }

    val safeIndex = selectedIndex.coerceIn(0, (prefs.aiPersonaPool.size - 1).coerceAtLeast(0))
    val selected = prefs.aiPersonaPool.getOrNull(safeIndex) ?: AiPersonaProfile()

    if (showAvatarPicker) {
        AvatarPickerDialog(
            currentKey = selected.avatarKey,
            gender = selected.gender,
            onDismiss = { showAvatarPicker = false },
            onPick = { key ->
                appViewModel.setAvatarAt(safeIndex, key)
                showAvatarPicker = false
            },
            onRandom = {
                appViewModel.randomizeAvatarAt(safeIndex)
                showAvatarPicker = false
            }
        )
    }

    if (showImportDialog) {
        ImportPersonaDialog(
            initialText = importJsonText,
            onTextChange = { importJsonText = it },
            onDismiss = { showImportDialog = false; importJsonText = "" },
            onConfirm = {
                val parsed = parsePoolJson(importJsonText)
                if (parsed.isNotEmpty()) {
                    appViewModel.importPersonaPool(parsed)
                } else {
                    appViewModel.notifyPersonaMessage("人设 JSON 解析失败")
                }
                showImportDialog = false
                importJsonText = ""
            },
            onPickFile = {
                showImportDialog = false
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = com.squemadylan.wolfcha.R.drawable.ic_launcher_bg),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI 玩家配置",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${prefs.playerCount} 人局 · ${appViewModel.difficultyLabel(prefs.difficulty)} · 共 ${prefs.aiPersonaPool.size} 名 AI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .clickable { showHowToPlay = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = "游戏说明", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = humanName,
                onValueChange = {
                    humanName = it
                    appViewModel.updatePlayerName(it)
                },
                label = { Text("你的昵称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = outlinedTextFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            CharacterShowcase(
                profiles = prefs.aiPersonaPool,
                selectedIndex = safeIndex,
                onPrev = {
                    val next = if (safeIndex - 1 < 0) prefs.aiPersonaPool.size - 1 else safeIndex - 1
                    selectedIndex = next
                },
                onNext = {
                    val next = if (safeIndex + 1 >= prefs.aiPersonaPool.size) 0 else safeIndex + 1
                    selectedIndex = next
                },
                onSelect = { selectedIndex = it }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showAvatarPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("更换全身形象")
            }

            Spacer(Modifier.height(16.dp))

            PersonaDetailCard(
                index = safeIndex,
                persona = selected,
                isGenerating = isGenerating,
                onPersonaChange = { appViewModel.updatePersonaAt(safeIndex, it) },
                onGenerateOne = { appViewModel.generatePersonaAt(safeIndex) }
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { appViewModel.generateAllPersonas() },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1.2f),
                    colors = ButtonDefaults.buttonColors(containerColor = WolfchaSecondary)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("一键生成全部")
                }
                OutlinedButton(
                    onClick = {
                        val json = com.squemadylan.wolfcha.data.model.AiPersonaProfile.encodePool(prefs.aiPersonaPool)
                        pendingExportJson = json
                        exportLauncher.launch("wolfcha_personas_${System.currentTimeMillis()}.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出")
                }
                OutlinedButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导入")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStartGame,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WolfchaPrimary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开始游戏", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CharacterShowcase(
    profiles: List<AiPersonaProfile>,
    selectedIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit
) {
    if (profiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(DarkCard, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无 AI 玩家", color = TextSecondary)
        }
        return
    }
    val displayIndex = selectedIndex.coerceIn(0, profiles.size - 1)
    val current = profiles[displayIndex]
    val isFemale = current.gender == "female"
    val gradientColors = if (isFemale) {
        listOf(Color(0xFF6E4F9B), Color(0xFFB86ABF), Color(0xFFFFB199))
    } else {
        listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF06B6D4))
    }
    val cardGradient = Brush.verticalGradient(gradientColors)
    val bottomGlass = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
    ) {
        AnimatedContent(
            targetState = displayIndex,
            transitionSpec = {
                (slideInHorizontally { fullWidth -> fullWidth } + fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                    (slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut() + scaleOut(targetScale = 0.96f))
            },
            label = "character"
        ) { idx ->
            val profile = profiles[idx.coerceIn(0, profiles.size - 1)]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomStart
            ) {
                Image(
                    painter = painterResource(id = profile.avatarRes()),
                    contentDescription = profile.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bottomGlass)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = profile.displayName.ifBlank { "未命名" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${if (isFemale) "女" else "男"} · ${profile.age}岁 · ${profile.mbti} · ${profile.styleLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (profile.background.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = profile.background,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 2
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onPrev,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上一个", tint = Color.White)
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下一个", tint = Color.White)
        }
    }

    Spacer(Modifier.height(10.dp))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = displayIndex)
    LaunchedEffect(displayIndex) {
        listState.animateScrollToItem(displayIndex.coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(profiles.size) { idx ->
            val profile = profiles[idx]
            val isSelected = idx == displayIndex
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else Color.Transparent,
                label = "thumbBorder"
            )
            Image(
                painter = painterResource(id = profile.avatarRes()),
                contentDescription = profile.displayName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .clickable { onSelect(idx) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaDetailCard(
    index: Int,
    persona: AiPersonaProfile,
    isGenerating: Boolean,
    onPersonaChange: (AiPersonaProfile) -> Unit,
    onGenerateOne: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI 玩家 ${index + 1} 详情",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onGenerateOne, enabled = !isGenerating) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "生成此人设", tint = WolfchaPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = persona.displayName,
                onValueChange = { onPersonaChange(persona.copy(displayName = it)) },
                label = { Text("名字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = outlinedTextFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = persona.background,
                onValueChange = { onPersonaChange(persona.copy(background = it)) },
                label = { Text("人物背景") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                colors = outlinedTextFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MbtiDropdown(
                    value = persona.mbti,
                    onValueChange = { onPersonaChange(persona.copy(mbti = it)) },
                    modifier = Modifier
                        .weight(1.6f, fill = true)
                        .height(56.dp)
                )
                AgeStepper(
                    value = persona.age,
                    onValueChange = { onPersonaChange(persona.copy(age = it)) },
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .height(56.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            StyleLabelDropdown(
                value = persona.styleLabel,
                onValueChange = { onPersonaChange(persona.copy(styleLabel = it)) }
            )
            Spacer(Modifier.height(8.dp))
            GenderDropdown(
                value = persona.gender,
                onValueChange = { onPersonaChange(persona.copy(gender = it)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarPickerDialog(
    currentKey: String,
    gender: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onRandom: () -> Unit
) {
    val keys = AvatarCatalog.keysFor(gender)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择全身形象", color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = onRandom) { Text("随机", color = WolfchaPrimary) }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(keys) { key ->
                    val resId = AvatarCatalog.resourceFor(key, gender)
                    val isSelected = key == currentKey
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = key,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) WolfchaPrimary else TextMuted,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onPick(key) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) }
        }
    )
}

@Composable
private fun ImportPersonaDialog(
    initialText: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onPickFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("导入人设 JSON", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = initialText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = { Text("粘贴先前导出的人设 JSON 数组…", color = TextMuted) },
                    colors = outlinedTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("从文件选择…") }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("导入", color = WolfchaPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

private fun parsePoolJson(text: String): List<AiPersonaProfile> {
    if (text.isBlank()) return emptyList()
    return try {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val arr = org.json.JSONArray(trimmed)
            (0 until arr.length()).map { AiPersonaProfile.fromJson(arr.getJSONObject(it)) }
        } else if (trimmed.startsWith("{")) {
            val obj = org.json.JSONObject(trimmed)
            if (obj.has("profiles")) {
                val arr = obj.getJSONArray("profiles")
                (0 until arr.length()).map { AiPersonaProfile.fromJson(arr.getJSONObject(it)) }
            } else if (obj.has("personas")) {
                val arr = obj.getJSONArray("personas")
                (0 until arr.length()).map { AiPersonaProfile.fromJson(arr.getJSONObject(it)) }
            } else {
                listOf(AiPersonaProfile.fromJson(obj))
            }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun writeJsonToUri(context: android.content.Context, uri: android.net.Uri, json: String): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
            os.flush()
            true
        } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun readTextFromUri(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = WolfchaPrimary,
    unfocusedBorderColor = TextMuted,
    focusedLabelColor = WolfchaPrimary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = WolfchaPrimary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MbtiDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val list = AiPersonaProfile.MBTI_FULL_LIST
    val current = list.firstOrNull { it.first == value } ?: list.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .menuAnchor()
                .border(1.dp, TextMuted, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true },
            color = DarkCard
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MBTI",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${current.first} · ${current.second}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            list.forEach { (code, label) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    },
                    onClick = {
                        onValueChange(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleLabelDropdown(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = AiPersonaProfile.STYLE_LABELS
    val current = if (value in options) value else options.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text("性格标签") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = WolfchaPrimary,
                unfocusedBorderColor = TextMuted,
                focusedLabelColor = WolfchaPrimary,
                unfocusedLabelColor = TextSecondary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label, color = TextPrimary) },
                    onClick = {
                        onValueChange(label)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderDropdown(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = if (value == "female") "女" else "男",
            onValueChange = {},
            readOnly = true,
            label = { Text("性别") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = WolfchaPrimary,
                unfocusedBorderColor = TextMuted,
                focusedLabelColor = WolfchaPrimary,
                unfocusedLabelColor = TextSecondary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = { Text("男", color = TextPrimary) }, onClick = {
                onValueChange("male")
                expanded = false
            })
            DropdownMenuItem(text = { Text("女", color = TextPrimary) }, onClick = {
                onValueChange("female")
                expanded = false
            })
        }
    }
}

@Composable
private fun AgeStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minAge: Int = 16,
    maxAge: Int = 45
) {
    val coerced = value.coerceIn(minAge, maxAge)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp)),
        color = DarkCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, TextMuted)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            StepperButton(
                label = "−",
                enabled = coerced > minAge,
                onClick = { onValueChange((coerced - 1).coerceAtLeast(minAge)) }
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$coerced",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "岁",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            StepperButton(
                label = "+",
                enabled = coerced < maxAge,
                onClick = { onValueChange((coerced + 1).coerceAtMost(maxAge)) }
            )
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) WolfchaPrimary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextMuted,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

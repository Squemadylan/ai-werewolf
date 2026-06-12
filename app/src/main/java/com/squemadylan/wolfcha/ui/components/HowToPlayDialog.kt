package com.squemadylan.wolfcha.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.ui.screens.howtoplay.HowToPlayContent
import com.squemadylan.wolfcha.ui.theme.*

@Composable
fun HowToPlayDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "游戏说明",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HowToPlayContent(showLlmHint = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = WolfchaPrimary)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

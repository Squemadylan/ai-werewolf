package com.squemadylan.wolfcha.ui.screens.howtoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.ui.theme.*

@Composable
fun HowToPlayContent(showLlmHint: Boolean = false) {
    SectionCard(
        title = "游戏概述",
        icon = Icons.Default.Info,
        color = WolfchaPrimary
    ) {
        Text(
            text = "狼人杀是一款社交推理游戏。玩家被分为两大阵营：好人阵营与狼人阵营。白天大家通过发言和投票找出并放逐狼人，夜晚狼人会袭击一名好人。重复进行直到任一阵营达成胜利条件。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard(
        title = "阵营与胜利条件",
        icon = Icons.Default.Info,
        color = WolfchaSecondary
    ) {
        BulletLine("好人阵营：放逐所有狼人即获胜。")
        BulletLine("狼人阵营：狼人数量 ≥ 好人数量时即获胜。")
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard(
        title = "夜晚阶段",
        icon = Icons.Default.NightsStay,
        color = NightBackground
    ) {
        Text(
            text = "天黑请闭眼，特殊角色按顺序行动：",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        BulletLine("守卫：守护一名玩家，当晚不会被狼人击杀；不能连续两晚守同一人。")
        BulletLine("狼人：共同商议并选择一名玩家作为击杀目标。")
        BulletLine("女巫：得知当晚被袭击的玩家，可使用解药救人或使用毒药毒杀一名玩家；解药和毒药各只能使用一次。")
        BulletLine("预言家：每晚可查验一名玩家，得知他属于好人还是狼人。")
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard(
        title = "白天阶段",
        icon = Icons.Default.WbSunny,
        color = DaySurface
    ) {
        Text(
            text = "天亮后公布昨晚的死亡情况，幸存者依次发言讨论，然后投票放逐得票最多的玩家。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        BulletLine("发言阶段：按座位顺序发言，可自我介绍、表明身份或质疑他人。")
        BulletLine("投票阶段：每人投出一票，得票最多者被放逐出局（若平票则无人出局）。")
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard(
        title = "角色介绍",
        icon = Icons.Default.Info,
        color = HunterOrange
    ) {
        BulletLine("狼人：每晚可与其他狼人商议击杀一名玩家。")
        BulletLine("白狼王：属于狼人阵营，拥有自爆技能（部分版本）。")
        BulletLine("预言家：好人阵营，每晚可查验一名玩家。")
        BulletLine("女巫：好人阵营，拥有一瓶解药和一瓶毒药。")
        BulletLine("猎人：好人阵营，被放逐或被狼人击杀时可开枪带走一名玩家（被毒杀则不能开枪）。")
        BulletLine("守卫：好人阵营，每晚可守护一名玩家。")
        BulletLine("白痴：好人阵营，被投票放逐时翻牌免死，但失去投票和发言权利（部分版本）。")
        BulletLine("平民：好人阵营，没有特殊技能，只能靠推理与发言找出狼人。")
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard(
        title = "投票与放逐",
        icon = Icons.Default.HowToVote,
        color = WitchGreen
    ) {
        Text(
            text = "白天所有玩家必须投票（可弃票取决于规则）。得票最多者被放逐并立即公布身份，然后进入下一夜。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }

    if (showLlmHint) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "提示：本应用使用大语言模型驱动 AI 玩家发言，可在「设置 → 大模型接口」中配置 API Key、Base URL 与模型名称。",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
internal fun SectionCard(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun BulletLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = WolfchaPrimary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

package com.squemadylan.wolfcha.ui.screens.roles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.squemadylan.wolfcha.data.model.Role
import com.squemadylan.wolfcha.ui.theme.*

@Composable
fun RolesScreen(
    onRoleClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "角色介绍",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "了解每个角色的能力和胜利条件",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Wolf Team
        Text(
            text = "狼人阵营",
            style = MaterialTheme.typography.titleLarge,
            color = WerewolfRed,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Werewolf,
            name = "狼人",
            description = "每晚可以和其他狼人一起选择一名玩家击杀。白天需要隐藏身份，通过发言误导好人。",
            color = WerewolfRed,
            winCondition = "所有好人出局",
            onClick = { onRoleClick("Werewolf") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.WhiteWolfKing,
            name = "白狼王",
            description = "属于狼人阵营，白天可以自爆并带走一名玩家。自爆后直接进入夜晚。",
            color = WhiteWolfKingCrimson,
            winCondition = "所有好人出局",
            onClick = { onRoleClick("WhiteWolfKing") }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Village Team
        Text(
            text = "好人阵营",
            style = MaterialTheme.typography.titleLarge,
            color = VillagerBlue,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Seer,
            name = "预言家",
            description = "每晚可以查验一名玩家的身份，知道他是好人还是狼人。是好人阵营的核心信息来源。",
            color = SeerPurple,
            winCondition = "所有狼人出局",
            onClick = { onRoleClick("Seer") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Witch,
            name = "女巫",
            description = "拥有一瓶解药和一瓶毒药。解药可以救活被狼人击杀的玩家，毒药可以毒死任意一名玩家。",
            color = WitchGreen,
            winCondition = "所有狼人出局",
            onClick = { onRoleClick("Witch") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Hunter,
            name = "猎人",
            description = "出局时可以开枪带走一名玩家。但如果被女巫毒死，则无法开枪。",
            color = HunterOrange,
            winCondition = "所有狼人出局",
            onClick = { onRoleClick("Hunter") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Guard,
            name = "守卫",
            description = "每晚可以保护一名玩家，使其免受狼人击杀。不能连续两晚保护同一名玩家。",
            color = GuardTeal,
            winCondition = "所有狼人出局",
            onClick = { onRoleClick("Guard") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RoleCard(
            role = Role.Villager,
            name = "平民",
            description = "没有特殊能力，但可以通过分析发言和投票来帮助好人阵营找出狼人。",
            color = VillagerBlue,
            winCondition = "所有狼人出局",
            onClick = { onRoleClick("Villager") }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoleCard(
    role: Role,
    name: String,
    description: String,
    color: Color,
    winCondition: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Role Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "胜利条件: $winCondition",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}

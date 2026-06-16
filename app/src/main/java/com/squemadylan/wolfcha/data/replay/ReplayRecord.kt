package com.squemadylan.wolfcha.data.replay

import com.squemadylan.wolfcha.data.model.Alignment
import com.squemadylan.wolfcha.data.model.GameEvent
import com.squemadylan.wolfcha.data.model.GameEventType
import com.squemadylan.wolfcha.data.model.Role

/**
 * U6 复盘：单条事件的扁平化结构（便于 JSON 序列化）。
 */
data class ReplayEvent(
    val type: String,                // GameEventType.name
    val day: Int,
    val phase: String,
    val visibility: String,          // PUBLIC / PRIVATE
    val timestamp: Long,
    val payload: Map<String, String>
)

/**
 * U6 复盘：玩家最终身份（开局后被揭晓，方便复盘页直接显示）。
 */
data class ReplayPlayer(
    val seat: Int,
    val displayName: String,
    val role: String,                 // Role.name
    val alignment: String,            // Alignment.name
    val isHuman: Boolean,
    val survived: Boolean,            // 是否存活到结束
    val diedOnDay: Int? = null,       // 出局日（null = 没死 / 游戏未完结）
    val diedReason: String? = null    // 出局原因（wolf/vote/poison/boom/...）
)

/**
 * U6 复盘：每局完整快照。
 */
data class ReplayRecord(
    val gameId: String,
    val startTime: Long,
    val endTime: Long,
    val playerCount: Int,
    val isFinished: Boolean,           // true=正常结束，false=中途退出
    val winner: String?,               // Alignment.name / null
    val players: List<ReplayPlayer>,
    val events: List<ReplayEvent>      // 全量 GameEvent 顺序
) {
    /** 复盘页头一行：玩家人数 / 胜者 / 时长 */
    fun headline(): String {
        val winText = when (winner) {
            Alignment.WOLF.name -> "狼人阵营胜"
            Alignment.VILLAGE.name -> "好人阵营胜"
            else -> if (isFinished) "已结束" else "中途退出"
        }
        val durationSec = (endTime - startTime) / 1000
        val mins = durationSec / 60
        val secs = durationSec % 60
        return "$playerCount 人局 · $winText · ${mins}分${secs}秒"
    }
}

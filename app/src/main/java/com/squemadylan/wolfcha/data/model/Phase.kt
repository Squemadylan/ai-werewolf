package com.squemadylan.wolfcha.data.model

enum class Phase {
    LOBBY,
    SETUP,
    NIGHT_START,
    NIGHT_GUARD_ACTION,
    NIGHT_WOLF_ACTION,
    NIGHT_WITCH_ACTION,
    NIGHT_SEER_ACTION,
    NIGHT_RESOLVE,
    DAY_START,
    DAY_BADGE_SIGNUP,
    DAY_BADGE_SPEECH,
    DAY_BADGE_ELECTION,
    DAY_PK_SPEECH,
    DAY_SPEECH,
    DAY_LAST_WORDS,
    DAY_VOTE,
    DAY_RESOLVE,
    BADGE_TRANSFER,
    HUNTER_SHOOT,
    WHITE_WOLF_KING_BOOM,
    GAME_END
}

fun Phase.isNightPhase(): Boolean = when (this) {
    Phase.NIGHT_START,
    Phase.NIGHT_GUARD_ACTION,
    Phase.NIGHT_WOLF_ACTION,
    Phase.NIGHT_WITCH_ACTION,
    Phase.NIGHT_SEER_ACTION,
    Phase.NIGHT_RESOLVE -> true
    else -> false
}

fun Phase.isDayPhase(): Boolean = when (this) {
    Phase.DAY_START,
    Phase.DAY_BADGE_SIGNUP,
    Phase.DAY_BADGE_SPEECH,
    Phase.DAY_BADGE_ELECTION,
    Phase.DAY_PK_SPEECH,
    Phase.DAY_SPEECH,
    Phase.DAY_LAST_WORDS,
    Phase.DAY_VOTE,
    Phase.DAY_RESOLVE -> true
    else -> false
}

fun Phase.isSpeechPhase(): Boolean = when (this) {
    Phase.DAY_SPEECH,
    Phase.DAY_LAST_WORDS,
    Phase.DAY_BADGE_SPEECH,
    Phase.DAY_PK_SPEECH -> true
    else -> false
}

fun Phase.getDisplayName(): String = when (this) {
    Phase.LOBBY -> "等待大厅"
    Phase.SETUP -> "游戏设置"
    Phase.NIGHT_START -> "夜晚开始"
    Phase.NIGHT_GUARD_ACTION -> "守卫行动"
    Phase.NIGHT_WOLF_ACTION -> "狼人行动"
    Phase.NIGHT_WITCH_ACTION -> "女巫行动"
    Phase.NIGHT_SEER_ACTION -> "预言家查验"
    Phase.NIGHT_RESOLVE -> "夜晚结算"
    Phase.DAY_START -> "天亮"
    Phase.DAY_BADGE_SIGNUP -> "警徽竞选"
    Phase.DAY_BADGE_SPEECH -> "警长竞选发言"
    Phase.DAY_BADGE_ELECTION -> "警长投票"
    Phase.DAY_PK_SPEECH -> "PK发言"
    Phase.DAY_SPEECH -> "白天发言"
    Phase.DAY_LAST_WORDS -> "遗言"
    Phase.DAY_VOTE -> "投票"
    Phase.DAY_RESOLVE -> "投票结算"
    Phase.BADGE_TRANSFER -> "移交警徽"
    Phase.HUNTER_SHOOT -> "猎人开枪"
    Phase.WHITE_WOLF_KING_BOOM -> "白狼王自爆"
    Phase.GAME_END -> "游戏结束"
}

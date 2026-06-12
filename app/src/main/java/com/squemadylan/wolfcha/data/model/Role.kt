package com.squemadylan.wolfcha.data.model

enum class Role {
    Villager,
    Werewolf,
    Seer,
    Witch,
    Hunter,
    Guard,
    Idiot,
    WhiteWolfKing
}

fun Role.isWolfRole(): Boolean = this == Role.Werewolf || this == Role.WhiteWolfKing

fun Role.getDisplayName(): String = when (this) {
    Role.Villager -> "平民"
    Role.Werewolf -> "狼人"
    Role.Seer -> "预言家"
    Role.Witch -> "女巫"
    Role.Hunter -> "猎人"
    Role.Guard -> "守卫"
    Role.Idiot -> "白痴"
    Role.WhiteWolfKing -> "白狼王"
}

fun Role.getAlignment(): Alignment = when (this) {
    Role.Werewolf, Role.WhiteWolfKing -> Alignment.WOLF
    else -> Alignment.VILLAGE
}

enum class Alignment {
    VILLAGE, WOLF
}

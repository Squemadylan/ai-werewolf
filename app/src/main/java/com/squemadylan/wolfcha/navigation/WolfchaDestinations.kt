package com.squemadylan.wolfcha.navigation

sealed class WolfchaDestinations(val route: String, val title: String, val icon: String) {
    data object Home : WolfchaDestinations("home", "首页", "home")
    data object Game : WolfchaDestinations("game", "游戏", "game")
    data object Roles : WolfchaDestinations("roles", "角色", "roles")
    data object Settings : WolfchaDestinations("settings", "设置", "settings")
    data object HowToPlay : WolfchaDestinations("how_to_play", "玩法", "help")
    data object LlmSettings : WolfchaDestinations("llm_settings", "大模型", "smart_toy")
    data object TtsSettings : WolfchaDestinations("tts_settings", "语音合成", "record_voice_over")
    data object RoleDetail : WolfchaDestinations("role_detail/{roleName}", "角色详情", "") {
        fun createRoute(roleName: String) = "role_detail/$roleName"
    }
}

val bottomNavItems = listOf(
    WolfchaDestinations.Home,
    WolfchaDestinations.Roles,
    WolfchaDestinations.Game,
    WolfchaDestinations.Settings
)

package com.squemadylan.wolfcha.data.model

data class LlmConfig(
    val provider: LlmProvider = LlmProvider.OPENAI_COMPATIBLE,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 400,
    val enabled: Boolean = false
) {
    val isReady: Boolean
        get() = enabled && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
}

enum class LlmProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val presetModels: List<String> = emptyList()
) {
    OPENAI_COMPATIBLE(
        displayName = "OpenAI 兼容",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        presetModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        presetModels = listOf("deepseek-chat", "deepseek-reasoner")
    ),
    ZHIPU(
        displayName = "智谱 GLM",
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        presetModels = listOf("glm-4-flash", "glm-4", "glm-4-plus")
    ),
    QWEN(
        displayName = "通义千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-turbo",
        presetModels = listOf("qwen-turbo", "qwen-plus", "qwen-max")
    ),
    MOONSHOT(
        displayName = "月之暗面 Kimi",
        defaultBaseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        presetModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
    ),
    CUSTOM(
        displayName = "自定义",
        defaultBaseUrl = "",
        defaultModel = "",
        presetModels = emptyList()
    )
}

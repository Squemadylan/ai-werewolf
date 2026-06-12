package com.squemadylan.wolfcha.data.model

/**
 * Editable AI player persona used in the pre-game pool.
 */
data class AiPersonaProfile(
    val displayName: String = "",
    val background: String = "",
    val mbti: String = "INTJ",
    val styleLabel: String = "冷静分析型",
    val gender: String = "male",
    val age: Int = 25,
    val avatarKey: String = ""
) {
    fun toPersona(): Persona = Persona(
        mbti = mbti,
        gender = gender,
        age = age,
        styleLabel = styleLabel,
        background = background
    )

    fun avatarRes(): Int = AvatarCatalog.resourceFor(avatarKey, gender)

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("displayName", displayName)
        put("background", background)
        put("mbti", mbti)
        put("styleLabel", styleLabel)
        put("gender", gender)
        put("age", age)
        put("avatarKey", avatarKey)
    }

    companion object {
        val DEFAULT_NAMES = listOf(
            "林晓雨", "陈默", "苏晚晴", "顾言", "沈若溪",
            "陆知远", "叶清禾", "方子墨", "江望月", "韩北辰", "宋知微"
        )

        val MALE_NAMES = listOf(
            "陆知远", "陈默", "顾言", "方子墨", "韩北辰", "秦墨白", "徐临川",
            "沈砚之", "何知秋", "周望舒", "谢临渊", "白屿川", "傅亦辰", "苏以安",
            "温朝歌", "柳予安", "沈听澜", "顾行舟", "霍临风", "程砚秋"
        )

        val FEMALE_NAMES = listOf(
            "林晓雨", "苏晚晴", "沈若溪", "叶清禾", "江望月", "宋知微", "温棠",
            "陆知意", "林知岁", "顾念卿", "苏酥", "沈令仪", "程若晴", "裴时雨",
            "白栀年", "楚映晚", "温瑶", "宋以沫", "叶南笙", "柳扶光"
        )

        val STYLE_LABELS = listOf(
            "冷静分析型", "激进冲锋型", "伪装高手型", "直觉敏锐型",
            "逻辑缜密型", "情绪煽动型", "沉默观察型", "话痨干扰型",
            "谨慎跟随型", "气场控场型", "感性共情型", "怀疑论调型",
            "话术辩护型", "随波逐流型", "强势立靶型", "和事佬圆场型"
        )

        val MBTI_TYPES = listOf(
            "INTJ", "ENTP", "INFJ", "ESTP", "ISTJ", "ENFP",
            "INTP", "ENFJ", "ISFP", "ESTJ"
        )

        val MBTI_LABELS = mapOf(
            "INTJ" to "建筑师",
            "ENTP" to "辩论家",
            "INFJ" to "提倡者",
            "ESTP" to "企业家",
            "ISTJ" to "物流师",
            "ENFP" to "竞选者",
            "INTP" to "逻辑学家",
            "ENFJ" to "主人公",
            "ISFP" to "探险家",
            "ESTJ" to "总经理",
            "ISTP" to "鉴赏家",
            "ESFP" to "表演者",
            "ENTJ" to "指挥官",
            "ENFP" to "竞选者",
            "ISFJ" to "守卫者",
            "ESFJ" to "执政官"
        )

        val MBTI_FULL_LIST = listOf(
            "INTJ" to "建筑师",
            "INTP" to "逻辑学家",
            "ENTJ" to "指挥官",
            "ENTP" to "辩论家",
            "INFJ" to "提倡者",
            "INFP" to "调停者",
            "ENFJ" to "主人公",
            "ENFP" to "竞选者",
            "ISTJ" to "物流师",
            "ISFJ" to "守卫者",
            "ESTJ" to "总经理",
            "ESFJ" to "执政官",
            "ISTP" to "鉴赏家",
            "ISFP" to "探险家",
            "ESTP" to "企业家",
            "ESFP" to "表演者"
        )

        fun fromJson(obj: org.json.JSONObject): AiPersonaProfile = AiPersonaProfile(
            displayName = obj.optString("displayName", ""),
            background = obj.optString("background", ""),
            mbti = obj.optString("mbti", "INTJ"),
            styleLabel = obj.optString("styleLabel", "冷静分析型"),
            gender = obj.optString("gender", "male"),
            age = obj.optInt("age", 25).coerceIn(16, 60),
            avatarKey = obj.optString("avatarKey", "")
        )

        fun defaultPool(size: Int): List<AiPersonaProfile> {
            val rng = java.util.Random(20251212L)
            return List(size.coerceAtLeast(0)) { index ->
                val isFemale = index % 2 == 0
                val gender = if (isFemale) "female" else "male"
                val namePool = if (isFemale) FEMALE_NAMES else MALE_NAMES
                val randomName = namePool.getOrElse(rng.nextInt(namePool.size)) { "AI${index + 1}" }
                AiPersonaProfile(
                    displayName = randomName,
                    background = "",
                    mbti = MBTI_TYPES[index % MBTI_TYPES.size],
                    styleLabel = STYLE_LABELS[index % STYLE_LABELS.size],
                    gender = gender,
                    age = 22 + rng.nextInt(20),
                    avatarKey = AvatarCatalog.pickRandomKey(gender)
                )
            }
        }

        fun encodePool(pool: List<AiPersonaProfile>): String {
            val arr = org.json.JSONArray()
            pool.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun decodePool(raw: String?, expectedSize: Int): List<AiPersonaProfile> {
            if (raw.isNullOrBlank()) return defaultPool(expectedSize)
            return try {
                val arr = org.json.JSONArray(raw)
                val parsed = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                resizePool(parsed, expectedSize)
            } catch (_: Exception) {
                defaultPool(expectedSize)
            }
        }

        fun resizePool(pool: List<AiPersonaProfile>, expectedSize: Int): List<AiPersonaProfile> {
            if (pool.size == expectedSize) return pool
            if (pool.size > expectedSize) return pool.take(expectedSize)
            val defaults = defaultPool(expectedSize)
            return pool + defaults.drop(pool.size)
        }
    }
}

object AvatarCatalog {
    private val MALE_KEYS = listOf(
        "avatar_man_1", "avatar_man_2", "avatar_man_3", "avatar_man_4",
        "avatar_man_5", "avatar_man_6", "avatar_man_8", "avatar_man_9",
        "avatar_man_10", "avatar_man_11", "avatar_man_12", "avatar_man_13"
    )

    private val FEMALE_KEYS = listOf(
        "avatar_woman_1", "avatar_woman_5", "avatar_woman_6", "avatar_woman_7",
        "avatar_woman_8", "avatar_woman_9", "avatar_woman_10", "avatar_woman_11",
        "avatar_woman_12", "avatar_woman_12_1", "avatar_woman_13", "avatar_woman_14"
    )

    fun keysFor(gender: String): List<String> =
        if (gender.equals("female", ignoreCase = true)) FEMALE_KEYS else MALE_KEYS

    fun pickRandomKey(gender: String): String =
        keysFor(gender).random()

    fun resourceFor(key: String, fallbackGender: String): Int {
        if (key.isNotBlank()) {
            val resId = lookupDrawable(key)
            if (resId != 0) return resId
        }
        val firstKey = keysFor(fallbackGender).firstOrNull() ?: return 0
        return lookupDrawable(firstKey)
    }

    private var cachedResources: android.content.res.Resources? = null

    fun bindResources(resources: android.content.res.Resources) {
        cachedResources = resources
    }

    private fun lookupDrawable(name: String): Int {
        val res = cachedResources ?: run {
            val appCtx = com.squemadylan.wolfcha.WolfchaApplication.instance
                ?.applicationContext
            val r = appCtx?.resources
            if (r != null) cachedResources = r
            r
        } ?: return 0
        return res.getIdentifier(name, "drawable", "com.squemadylan.wolfcha")
    }
}

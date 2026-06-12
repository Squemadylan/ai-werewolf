package com.squemadylan.wolfcha.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.squemadylan.wolfcha.data.model.TtsConfig
import com.squemadylan.wolfcha.data.model.VolcVoiceCatalog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object VoiceHelper {
    private const val TAG = "VoiceHelper"
    private var volcReady = false
    private var systemTts: TextToSpeech? = null
    private var systemReady = false
    private var ttsConfig: TtsConfig = TtsConfig()

    fun init(context: Context) {
        volcReady = false
        systemReady = false
        systemTts?.shutdown()
        systemTts = TextToSpeech(context.applicationContext) { status ->
            systemReady = status == TextToSpeech.SUCCESS
            if (systemReady) {
                systemTts?.language = Locale.CHINESE
            }
        }
    }

    fun updateConfig(config: TtsConfig) {
        ttsConfig = config
        VolcTtsHelper.updateConfig(config)
        volcReady = VolcTtsHelper.isReady()
    }

    suspend fun speakNarration(text: String): Boolean {
        if (!ttsConfig.narratorEnabled || !ttsConfig.enabled) return false
        return speakInternal(text, TtsConfig.NARRATOR_VOICE)
    }

    suspend fun speakPlayer(text: String, gender: String): Boolean {
        if (!ttsConfig.playerSpeechEnabled || !ttsConfig.enabled) return false
        val voice = VolcVoiceCatalog.resolvePlayerVoice(gender, ttsConfig)
        return speakInternal(text, voice)
    }

    fun stop() {
        VolcTtsHelper.stop()
        systemTts?.stop()
    }

    private suspend fun speakInternal(text: String, voiceType: String): Boolean {
        if (volcReady) {
            val ok = VolcTtsHelper.speak(text, voiceType)
            if (ok) return true
        }
        return speakWithSystem(text)
    }

    private suspend fun speakWithSystem(text: String): Boolean = suspendCancellableCoroutine { cont ->
        if (!systemReady || systemTts == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val utteranceId = "wolfcha_${System.currentTimeMillis()}"
        systemTts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                cont.resume(true)
            }
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "system TTS error")
                cont.resume(false)
            }
        })
        systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        cont.invokeOnCancellation { systemTts?.stop() }
    }
}

package com.squemadylan.wolfcha.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.squemadylan.wolfcha.data.model.TtsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume

object VolcTtsHelper {
    private const val TAG = "VolcTtsHelper"
    private const val TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
    private const val MAX_TEXT_LEN = 480

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private var mediaPlayer: MediaPlayer? = null
    private var config: TtsConfig = TtsConfig()

    fun updateConfig(newConfig: TtsConfig) {
        config = newConfig
    }

    fun isReady(): Boolean = config.isReady

    suspend fun speak(text: String, voiceType: String): Boolean = withContext(Dispatchers.IO) {
        if (!config.isReady) return@withContext false
        val cleaned = sanitize(text)
        if (cleaned.isBlank()) return@withContext false

        val audioFile = synthesize(cleaned, voiceType) ?: return@withContext false
        playFile(audioFile)
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun sanitize(text: String): String {
        return text
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            .trim()
            .let { if (it.length > MAX_TEXT_LEN) it.substring(0, MAX_TEXT_LEN) else it }
    }

    private fun synthesize(text: String, voiceType: String): File? {
        val payload = mapOf(
            "app" to mapOf(
                "appid" to config.appId,
                "token" to config.accessToken,
                "cluster" to "volcano_tts"
            ),
            "user" to mapOf("uid" to "wolfcha_${UUID.randomUUID()}"),
            "audio" to mapOf(
                "voice_type" to voiceType,
                "encoding" to "mp3",
                "rate" to 24000,
                "speed_ratio" to 1.0,
                "volume_ratio" to 1.0,
                "pitch_ratio" to 1.0,
                "language" to "cn"
            ),
            "request" to mapOf(
                "reqid" to UUID.randomUUID().toString(),
                "text" to text,
                "text_type" to "plain",
                "operation" to "query"
            )
        )

        val connection = (URL(TTS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer;${config.accessToken}")
            setRequestProperty("X-Appid", config.appId)
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(gson.toJson(payload))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.let { stream -> BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText() }
                ?: ""
            if (status !in 200..299) {
                Log.e(TAG, "HTTP $status: ${body.take(200)}")
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val json = gson.fromJson(body, Map::class.java) as Map<String, Any?>
            val code = (json["code"] as? Number)?.toInt() ?: -1
            if (code != 3000) {
                Log.e(TAG, "TTS code=$code msg=${json["message"]}")
                return null
            }
            val b64 = json["data"] as? String ?: return null
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            if (bytes.isEmpty()) return null
            File.createTempFile("wolfcha_tts_", ".mp3").apply {
                writeBytes(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "synthesize failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun playFile(file: File): Boolean = suspendCancellableCoroutine { cont ->
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    file.delete()
                    cont.resume(true)
                }
                setOnErrorListener { _, _, _ ->
                    file.delete()
                    cont.resume(false)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "play failed", e)
            file.delete()
            cont.resume(false)
        }
        cont.invokeOnCancellation { stop() }
    }
}

package com.squemadylan.wolfcha.data.remote

import com.squemadylan.wolfcha.data.model.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Minimal OpenAI-compatible chat completion client.
 *
 * Targets the `/chat/completions` endpoint exposed by providers such as
 * OpenAI, DeepSeek, Zhipu (GLM), Qwen (DashScope compatible-mode), Moonshot,
 * and any custom OpenAI-compatible gateway.
 */
class LlmService {

    data class Message(val role: String, val content: String)

    sealed class Result {
        data class Success(val content: String) : Result()
        data class Failure(val message: String, val status: Int = -1) : Result()
    }

    /**
     * Stateless chat completion. Each call sends [messages] only — no conversation
     * history is stored client-side or reused across AI players.
     *
     * @param isolationKey Per-player key (e.g. gameId + playerId) so provider-side
     *        routing does not merge sessions between different AI actors.
     */
    suspend fun chat(
        config: LlmConfig,
        messages: List<Message>,
        isolationKey: String? = null
    ): Result = withContext(Dispatchers.IO) {
        if (!config.isReady) {
            return@withContext Result.Failure("LLM 尚未正确配置，请在设置中填写 API Key、Base URL 与模型")
        }

        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject().apply {
            put("model", config.model)
            put("temperature", config.temperature.toDouble())
            put("max_tokens", config.maxTokens)
            put("stream", false)
            isolationKey?.let { put("user", it) }
            val arr = JSONArray()
            messages.forEach { msg ->
                arr.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", arr)
        }

        val connection: HttpURLConnection
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 60_000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("X-Request-ID", UUID.randomUUID().toString())
                isolationKey?.let {
                    setRequestProperty("X-Wolfcha-Actor", it)
                }
            }
        } catch (e: Exception) {
            return@withContext Result.Failure("无法访问 ${config.baseUrl}：${e.localizedMessage ?: e.javaClass.simpleName}")
        }

        try {
            connection.outputStream.use { os: OutputStream ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            } ?: ""

            if (status !in 200..299) {
                return@withContext Result.Failure(parseError(status, body), status)
            }

            val content = parseContent(body)
            if (content.isNullOrBlank()) {
                return@withContext Result.Failure("模型返回为空", status)
            }
            Result.Success(content)
        } catch (e: Exception) {
            Result.Failure("请求失败：${e.localizedMessage ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * U3 真 SSE 流式 chat completion。
     * 返回 Flow<TokenChunk>：每收到一个 token emit 一次。
     * 失败时 emit TokenChunk.Error。
     * OpenAI 兼容格式：data: {json}\n\n，末尾 data: [DONE]
     */
    fun chatStream(
        config: LlmConfig,
        messages: List<Message>,
        isolationKey: String? = null
    ): Flow<TokenChunk> = callbackFlow {
        if (!config.isReady) {
            trySend(TokenChunk.Error("LLM 尚未正确配置"))
            close()
            return@callbackFlow
        }

        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject().apply {
            put("model", config.model)
            put("temperature", config.temperature.toDouble())
            put("max_tokens", config.maxTokens)
            put("stream", true)
            isolationKey?.let { put("user", it) }
            val arr = JSONArray()
            messages.forEach { msg ->
                arr.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", arr)
        }

        val executor = Executors.newSingleThreadExecutor()
        // U3 补丁：30 秒硬超时（半开连接防护）
        val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        val timeoutFuture = timeoutExecutor.schedule({
            trySend(TokenChunk.Error("生成超时（30 秒未收到 token）"))
            close()
        }, 30, java.util.concurrent.TimeUnit.SECONDS)

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    setRequestProperty("X-Request-ID", UUID.randomUUID().toString())
                    isolationKey?.let { setRequestProperty("X-Wolfcha-Actor", it) }
                }

                connection.outputStream.use { os: OutputStream ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val errBody = connection.errorStream?.let { input ->
                        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                    } ?: ""
                    trySend(TokenChunk.Error(parseError(status, errBody)))
                    close()
                    timeoutFuture.cancel(false)
                    return@execute
                }

                var firstTokenReceived = false
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        if (!trimmed.startsWith("data:")) continue
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        val token: String? = try {
                            val obj = JSONObject(data)
                            val choices = obj.optJSONArray("choices")
                            if (choices == null || choices.length() == 0) {
                                null
                            } else {
                                val first = choices.optJSONObject(0)
                                if (first == null) {
                                    null
                                } else {
                                    val delta = first.optJSONObject("delta")
                                    val msg = first.optJSONObject("message")
                                    delta?.optString("content", null)
                                        ?: msg?.optString("content", null)
                                }
                            }
                        } catch (e: Exception) { null }
                        if (!token.isNullOrEmpty()) {
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                // 收到首 token，取消硬超时
                                timeoutFuture.cancel(false)
                            }
                            trySend(TokenChunk.Token(token))
                        }
                    }
                }
                trySend(TokenChunk.Done)
                close()
            } catch (e: Exception) {
                trySend(TokenChunk.Error("流式请求失败：${e.localizedMessage ?: e.javaClass.simpleName}"))
                close()
            } finally {
                timeoutFuture.cancel(false)
                connection?.disconnect()
                timeoutExecutor.shutdownNow()
            }
        }
        awaitClose {
            timeoutFuture.cancel(false)
            timeoutExecutor.shutdownNow()
            executor.shutdownNow()
        }
    }

    sealed class TokenChunk {
        data class Token(val text: String) : TokenChunk()
        data object Done : TokenChunk()
        data class Error(val message: String) : TokenChunk()
    }

    private fun parseContent(body: String): String? {
        return try {
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val first = choices.optJSONObject(0) ?: return null
            val message = first.optJSONObject("message") ?: return null
            message.optString("content", null)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseError(status: Int, body: String): String {
        if (body.isBlank()) return "HTTP $status"
        return try {
            val root = JSONObject(body)
            val err = root.optJSONObject("error")
            if (err != null) {
                val message = err.optString("message").ifBlank { err.optString("code") }
                if (message.isNotBlank()) "HTTP $status - $message" else "HTTP $status"
            } else {
                "HTTP $status - ${body.take(200)}"
            }
        } catch (e: Exception) {
            "HTTP $status - ${body.take(200)}"
        }
    }
}

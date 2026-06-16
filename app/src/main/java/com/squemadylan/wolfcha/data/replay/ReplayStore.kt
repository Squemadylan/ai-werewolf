package com.squemadylan.wolfcha.data.replay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.replayDataStore: DataStore<Preferences> by preferencesDataStore(name = "wolfcha_replays")

/**
 * U6 复盘存储：
 * - 用独立 DataStore 持久化（Preferences + JSON 字符串）
 * - FIFO 保存最近 N 局（默认 5）
 * - 提供 list/save/delete 接口
 */
class ReplayStore(private val context: Context) {

    companion object {
        val REPLAYS_KEY = stringPreferencesKey("replays_json")
        const val MAX_REPLAYS = 5
    }

    val replays: Flow<List<ReplayRecord>> = context.replayDataStore.data.map { prefs ->
        decodeList(prefs[REPLAYS_KEY] ?: "[]")
    }

    suspend fun save(record: ReplayRecord, max: Int = MAX_REPLAYS) {
        context.replayDataStore.edit { prefs ->
            val current = decodeList(prefs[REPLAYS_KEY] ?: "[]")
            val updated = (listOf(record) + current).take(max)
            prefs[REPLAYS_KEY] = encodeList(updated)
        }
    }

    suspend fun delete(gameId: String) {
        context.replayDataStore.edit { prefs ->
            val current = decodeList(prefs[REPLAYS_KEY] ?: "[]")
            val updated = current.filterNot { it.gameId == gameId }
            prefs[REPLAYS_KEY] = encodeList(updated)
        }
    }

    suspend fun clearAll() {
        context.replayDataStore.edit { prefs ->
            prefs[REPLAYS_KEY] = "[]"
        }
    }

    suspend fun getAll(): List<ReplayRecord> = replays.first()

    // ===================== JSON 编解码 =====================

    private fun encodeList(records: List<ReplayRecord>): String {
        val arr = JSONArray()
        for (r in records) arr.put(encodeRecord(r))
        return arr.toString()
    }

    private fun decodeList(json: String): List<ReplayRecord> {
        if (json.isBlank() || json == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> decodeRecord(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    private fun encodeRecord(r: ReplayRecord): JSONObject {
        val obj = JSONObject()
        obj.put("gameId", r.gameId)
        obj.put("startTime", r.startTime)
        obj.put("endTime", r.endTime)
        obj.put("playerCount", r.playerCount)
        obj.put("isFinished", r.isFinished)
        obj.put("winner", r.winner ?: JSONObject.NULL)
        obj.put("players", JSONArray().also { arr ->
            for (p in r.players) {
                arr.put(JSONObject().apply {
                    put("seat", p.seat)
                    put("displayName", p.displayName)
                    put("role", p.role)
                    put("alignment", p.alignment)
                    put("isHuman", p.isHuman)
                    put("survived", p.survived)
                    put("diedOnDay", p.diedOnDay ?: JSONObject.NULL)
                    put("diedReason", p.diedReason ?: JSONObject.NULL)
                })
            }
        })
        obj.put("events", JSONArray().also { arr ->
            for (e in r.events) {
                arr.put(JSONObject().apply {
                    put("type", e.type)
                    put("day", e.day)
                    put("phase", e.phase)
                    put("visibility", e.visibility)
                    put("timestamp", e.timestamp)
                    put("payload", JSONObject(e.payload))
                })
            }
        })
        return obj
    }

    private fun decodeRecord(obj: JSONObject): ReplayRecord {
        val playersArr = obj.getJSONArray("players")
        val players = (0 until playersArr.length()).map { i ->
            val p = playersArr.getJSONObject(i)
            ReplayPlayer(
                seat = p.getInt("seat"),
                displayName = p.getString("displayName"),
                role = p.getString("role"),
                alignment = p.getString("alignment"),
                isHuman = p.getBoolean("isHuman"),
                survived = p.getBoolean("survived"),
                diedOnDay = p.optIntOrNull("diedOnDay"),
                diedReason = p.optStringOrNull("diedReason")
            )
        }
        val eventsArr = obj.getJSONArray("events")
        val events = (0 until eventsArr.length()).map { i ->
            val e = eventsArr.getJSONObject(i)
            val payloadObj = e.optJSONObject("payload") ?: JSONObject()
            val payload = mutableMapOf<String, String>()
            for (k in payloadObj.keys()) payload[k] = payloadObj.optString(k, "")
            ReplayEvent(
                type = e.getString("type"),
                day = e.optInt("day", 0),
                phase = e.optString("phase", ""),
                visibility = e.optString("visibility", "PUBLIC"),
                timestamp = e.optLong("timestamp", 0L),
                payload = payload
            )
        }
        return ReplayRecord(
            gameId = obj.getString("gameId"),
            startTime = obj.getLong("startTime"),
            endTime = obj.getLong("endTime"),
            playerCount = obj.getInt("playerCount"),
            isFinished = obj.optBoolean("isFinished", false),
            winner = obj.optStringOrNull("winner"),
            players = players,
            events = events
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, -1).takeIf { it >= 0 }
}

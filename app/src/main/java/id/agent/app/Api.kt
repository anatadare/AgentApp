package id.agent.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** role: user | assistant | tool (tool dikirim ke API sebagai "user") */
data class Msg(val role: String, val content: String)

object Api {
    private val client = OkHttpClient.Builder()
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Streaming chat (OpenAI-compatible). Return null jika sukses, atau pesan error. */
    fun stream(
        base: String, key: String, model: String, system: String,
        history: List<Msg>, shouldStop: () -> Boolean, onDelta: (String) -> Unit
    ): String? {
        val msgs = JSONArray()
        if (system.isNotBlank())
            msgs.put(JSONObject().put("role", "system").put("content", system))
        history.forEachIndexed { i, m ->
            val role = if (m.role == "tool") "user" else m.role
            // hasil tool lama dipangkas supaya konteks tidak membengkak
            val c = if (m.role == "tool" && i < history.size - 2) m.content.take(300) + "…" else m.content
            msgs.put(JSONObject().put("role", role).put("content", c))
        }
        val body = JSONObject()
            .put("model", model).put("stream", true).put("messages", msgs).toString()
        val req = Request.Builder()
            .url(base.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return "HTTP ${r.code}: ${r.body?.string()?.take(300)}"
            val src = r.body!!.source()
            while (!src.exhausted()) {
                if (shouldStop()) break
                val line = src.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val d = line.removePrefix("data:").trim()
                if (d == "[DONE]") break
                try {
                    val c = JSONObject(d).getJSONArray("choices").optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content", "") ?: ""
                    if (c.isNotEmpty() && c != "null") onDelta(c)
                } catch (_: Exception) {}
            }
        }
        return null
    }
}

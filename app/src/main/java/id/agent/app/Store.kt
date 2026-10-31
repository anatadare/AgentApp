package id.agent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Bot(val id: String, val name: String, val emoji: String, val prompt: String)

val defaultBots = listOf(
    Bot(
        "nova", "Nova", "✨",
        "Kamu Nova, agent personal milik pengguna. Gaya: hangat, santai, bahasa Indonesia sehari-hari, boleh sedikit bercanda dan emoji secukupnya. " +
            "Kamu proaktif: kerjakan tugas sampai tuntas memakai tool tanpa banyak bertanya, tawarkan langkah berikutnya, dan ingat preferensi pengguna. " +
            "Jawaban singkat, jelas, tidak bertele-tele."
    ),
    Bot(
        "riset", "Riset", "🔎",
        "Kamu agent riset. Pecah pertanyaan jadi langkah, kumpulkan info dari beberapa sumber di web, bandingkan, lalu beri kesimpulan dengan menyebut sumber dan ketidakpastiannya."
    ),
    Bot(
        "coder", "Coder", "💻",
        "Kamu asisten pemrograman. Beri kode yang benar dan ringkas, jelaskan seperlunya, dan tanyakan hanya jika benar-benar perlu."
    ),
    Bot(
        "karakter", "Karakter", "🎭",
        "Kamu bermain peran sebagai karakter yang diminta pengguna. Tetap dalam karakter, kreatif, ekspresif, dan responsif. Jika belum ada karakter, tawarkan beberapa pilihan menarik."
    )
)

class Store(c: Context) {
    private val p = c.getSharedPreferences("agent", Context.MODE_PRIVATE)

    var base: String
        get() = p.getString("base", "https://je.jerouter.web.id/v1")!!
        set(v) { p.edit().putString("base", v).apply() }
    var key: String
        get() = p.getString("key", "")!!
        set(v) { p.edit().putString("key", v).apply() }
    var model: String
        get() = p.getString("model", "minimax-m2.7")!!
        set(v) { p.edit().putString("model", v).apply() }
    var agentOn: Boolean
        get() = p.getBoolean("agentOn", true)
        set(v) { p.edit().putBoolean("agentOn", v).apply() }
    var botId: String
        get() = p.getString("botId", "nova")!!
        set(v) { p.edit().putString("botId", v).apply() }

    fun loadBots(): List<Bot> {
        val l = try {
            val a = JSONArray(p.getString("bots", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Bot(o.getString("id"), o.getString("n"), o.getString("e"), o.getString("p"))
            }
        } catch (_: Exception) { emptyList() }
        return l.ifEmpty { defaultBots }
    }

    fun saveBots(l: List<Bot>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("e", it.emoji).put("p", it.prompt)) }
        p.edit().putString("bots", a.toString()).apply()
    }

    fun loadChat(id: String): List<Msg> = try {
        val a = JSONArray(p.getString("chat_$id", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it); Msg(o.getString("r"), o.getString("c"))
        }
    } catch (_: Exception) { emptyList() }

    fun saveChat(id: String, l: List<Msg>) {
        val a = JSONArray()
        l.takeLast(60).forEach { a.put(JSONObject().put("r", it.role).put("c", it.content.take(6000))) }
        p.edit().putString("chat_$id", a.toString()).apply()
    }

    fun deleteChat(id: String) { p.edit().remove("chat_$id").apply() }

    fun loadMemory(): List<String> = try {
        val a = JSONArray(p.getString("memory", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun saveMemory(l: List<String>) {
        val a = JSONArray()
        l.takeLast(50).forEach { a.put(it) }
        p.edit().putString("memory", a.toString()).apply()
    }
}

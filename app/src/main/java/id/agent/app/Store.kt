package id.agent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
    var personaIdx: Int
        get() = p.getInt("persona", 0)
        set(v) { p.edit().putInt("persona", v).apply() }
    var agentOn: Boolean
        get() = p.getBoolean("agentOn", true)
        set(v) { p.edit().putBoolean("agentOn", v).apply() }

    fun loadChat(): List<Msg> = try {
        val a = JSONArray(p.getString("chat", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it); Msg(o.getString("r"), o.getString("c"))
        }
    } catch (_: Exception) { emptyList() }

    fun saveChat(l: List<Msg>) {
        val a = JSONArray()
        l.takeLast(60).forEach { a.put(JSONObject().put("r", it.role).put("c", it.content.take(6000))) }
        p.edit().putString("chat", a.toString()).apply()
    }
}

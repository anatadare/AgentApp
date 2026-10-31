package id.agent.app

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun domainOf(u: String): String = u.trim().lowercase()
    .removePrefix("https://").removePrefix("http://").removePrefix("www.")
    .substringBefore("/").substringBefore("?")

object Tg {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun decode(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

    private fun strip(s: String): String = s.replace(Regex("<[^>]+>"), "")

    private fun clean(h: String): String {
        var s = h.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?is)<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")) { m ->
            val href = decode(m.groupValues[1])
            val t = decode(strip(m.groupValues[2])).trim()
            if (t.isEmpty() || t == href || href.contains(t)) href else "$t ($href)"
        }
        return decode(strip(s)).replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Baca postingan terbaru channel publik lewat halaman preview web (t.me/s/nama). */
    fun read(channel: String, limit: Int): String {
        val name = channel.trim().substringAfterLast("/").removePrefix("@").trim()
        if (name.isEmpty()) return "Nama channel kosong."
        return try {
            val req = Request.Builder().url("https://t.me/s/$name").header("User-Agent", UA).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return "HTTP ${r.code} saat membaca @$name"
                parse(name, r.body?.string() ?: "", limit)
            }
        } catch (e: Exception) {
            "Error baca @$name: ${e.message}"
        }
    }

    private fun parse(name: String, html: String, limit: Int): String {
        val parts = html.split("tgme_widget_message_wrap").drop(1)
        if (parts.isEmpty())
            return "Tidak ada postingan yang bisa dibaca di @$name (channel privat atau tidak punya preview web)."
        val out = parts.mapNotNull { p ->
            val post = Regex("data-post=\"([^\"]+)\"").find(p)?.groupValues?.get(1) ?: return@mapNotNull null
            val m = Regex("(?s)tgme_widget_message_text[^>]*>(.*?)</div>").find(p)
            val text = if (m != null) clean(m.groupValues[1]) else ""
            val time = Regex("<time[^>]*datetime=\"([^\"]+)\"").find(p)?.groupValues?.get(1) ?: ""
            if (text.isBlank()) null else "[$post | ${time.take(16)}]\n${text.take(700)}"
        }.takeLast(limit.coerceIn(1, 20))
        if (out.isEmpty()) return "Tidak ada postingan teks di @$name."
        return "Channel @$name (${out.size} postingan terbaru, yang paling lama dulu):\n\n" + out.joinToString("\n\n")
    }

    /** Kirim pesan lewat Telegram Bot API. Bot harus admin di channel tujuan. */
    fun post(token: String, chat: String, text: String): String {
        if (token.isBlank()) return "Token bot belum diisi di Pengaturan."
        if (chat.isBlank()) return "Channel tujuan belum diisi di Pengaturan."
        val c = chat.trim().let { if (it.startsWith("@") || it.startsWith("-") || it.all { ch -> ch.isDigit() }) it else "@$it" }
        return try {
            val body = FormBody.Builder().add("chat_id", c).add("text", text.take(4000)).build()
            val req = Request.Builder()
                .url("https://api.telegram.org/bot${token.trim()}/sendMessage")
                .post(body).build()
            client.newCall(req).execute().use { r ->
                val j = try { JSONObject(r.body?.string() ?: "{}") } catch (_: Exception) { JSONObject() }
                if (j.optBoolean("ok")) "Terkirim ke $c."
                else "Gagal: " + j.optString("description", "HTTP ${r.code}")
            }
        } catch (e: Exception) {
            "Error kirim: ${e.message}"
        }
    }
}

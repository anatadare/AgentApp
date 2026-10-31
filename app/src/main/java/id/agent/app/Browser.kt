package id.agent.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

class Browser(ctx: Context) {
    @Volatile private var loading = false

    @SuppressLint("SetJavaScriptEnabled")
    val web = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, url: String?, f: Bitmap?) { loading = true }
            override fun onPageFinished(v: WebView?, url: String?) { loading = false }
        }
        loadUrl("https://www.bing.com")
    }

    private suspend fun js(code: String): String = suspendCancellableCoroutine { c ->
        web.evaluateJavascript(code) { r ->
            val s = try {
                if (r == null || r == "null") "" else JSONTokener(r).nextValue().toString()
            } catch (_: Exception) { r ?: "" }
            c.resume(s)
        }
    }

    private suspend fun settle() {
        delay(800)
        var t = 0
        while (loading && t < 15000) { delay(300); t += 300 }
        delay(700)
    }

    private suspend fun read(): String = js(READ)

    /** Harus dipanggil dari main thread. */
    suspend fun run(name: String, o: JSONObject): String = try {
        when (name) {
            "open" -> {
                var u = o.getString("url")
                if (!u.startsWith("http")) u = "https://$u"
                web.loadUrl(u); settle(); read()
            }
            "read" -> read()
            "click" -> {
                val r = js(CLICK.replace("IDX", o.getInt("id").toString()))
                settle(); r + "\n" + read()
            }
            "type" -> {
                val code = TYPE
                    .replace("IDX", o.getInt("id").toString())
                    .replace("ENT", o.optBoolean("enter", false).toString())
                    .replace("TXT", JSONObject.quote(o.getString("text")))
                val r = js(code)
                settle(); r + "\n" + read()
            }
            "scroll" -> { js("window.scrollBy(0, window.innerHeight*0.8); 'ok'"); delay(500); read() }
            "back" -> { if (web.canGoBack()) web.goBack(); settle(); read() }
            "invalid" -> "Format tool tidak valid. Tulis JSON yang benar di dalam <tool>…</tool>."
            else -> "Tool tidak dikenal: $name"
        }
    } catch (e: Exception) {
        "Error tool: ${e.message}"
    }

    companion object {
        private const val READ = """(function(){
var els=document.querySelectorAll('a[href],button,input,textarea,select,[role=button],[onclick]');
var out=[],n=0;
for(var i=0;i<els.length&&n<60;i++){
 var e=els[i],r=e.getBoundingClientRect();
 if(r.width<2||r.height<2) continue;
 e.setAttribute('data-ai',n);
 var t=(e.innerText||e.value||e.placeholder||e.getAttribute('aria-label')||e.title||'').trim().replace(/\s+/g,' ').slice(0,60);
 out.push('['+n+'] '+e.tagName.toLowerCase()+(e.type?'('+e.type+')':'')+' '+t);
 n++;
}
return 'URL: '+location.href+'\nTITLE: '+document.title+'\nTEXT:\n'+document.body.innerText.replace(/\n{2,}/g,'\n').slice(0,4000)+'\nELEMEN:\n'+out.join('\n');
})()"""

        private const val CLICK = """(function(i){
var e=document.querySelector('[data-ai="'+i+'"]');
if(!e) return 'elemen tidak ada';
e.scrollIntoView(); e.click(); return 'ok';
})(IDX)"""

        private const val TYPE = """(function(i,t,en){
var e=document.querySelector('[data-ai="'+i+'"]');
if(!e) return 'elemen tidak ada';
e.focus();
var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');
if(d&&d.set) d.set.call(e,t); else e.value=t;
e.dispatchEvent(new Event('input',{bubbles:true}));
e.dispatchEvent(new Event('change',{bubbles:true}));
if(en){var f=e.form; if(f){ if(f.requestSubmit) f.requestSubmit(); else f.submit(); }
 else { e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,bubbles:true})); } }
return 'ok';
})(IDX,TXT,ENT)"""
    }
}

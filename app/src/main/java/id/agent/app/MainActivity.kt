package id.agent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

val personas = listOf(
    "Asisten" to "Kamu asisten AI yang cerdas dan membantu. Jawab dalam bahasa yang dipakai pengguna, ringkas dan jelas.",
    "Riset" to "Kamu agent riset. Pecah pertanyaan jadi langkah, kumpulkan info dari beberapa sumber, beri kesimpulan dan sebutkan ketidakpastian.",
    "Roleplay" to "Kamu bermain peran sebagai karakter yang diminta pengguna. Tetap dalam karakter, kreatif, dan responsif.",
    "Coder" to "Kamu asisten pemrograman. Beri kode yang benar dan ringkas, jelaskan seperlunya."
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val st = Store(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App(st) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(st: Store) {
    val ctx = LocalContext.current
    val browser = remember { Browser(ctx) }
    val msgs = remember { mutableStateListOf<Msg>().also { it.addAll(st.loadChat()) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSet by remember { mutableStateOf(false) }
    var pIdx by remember { mutableStateOf(st.personaIdx) }
    var agentOn by remember { mutableStateOf(st.agentOn) }
    var menu by remember { mutableStateOf(false) }
    val stop = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    LaunchedEffect(msgs.size, msgs.lastOrNull()?.content) {
        if (msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex)
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || busy) return
        if (st.key.isBlank()) { showSet = true; return }
        input = ""
        msgs.add(Msg("user", t))
        busy = true
        stop.set(false)
        val useTools = agentOn
        val sys = personas[pIdx].second + if (useTools) Agent.PROMPT else ""
        scope.launch {
            var steps = 0
            while (true) {
                msgs.add(Msg("assistant", ""))
                val idx = msgs.lastIndex
                val hist = msgs.dropLast(1).toList()
                var acc = ""
                val err = withContext(Dispatchers.IO) {
                    try {
                        Api.stream(
                            st.base, st.key, st.model, sys, hist,
                            shouldStop = { stop.get() || (useTools && acc.contains("</tool>")) }
                        ) { d ->
                            acc += d
                            val snap = acc
                            scope.launch(Dispatchers.Main) { msgs[idx] = Msg("assistant", snap) }
                        }
                    } catch (e: Exception) { "Error: ${e.message}" }
                }
                val end = acc.indexOf("</tool>")
                val finalText = if (useTools && end >= 0) acc.substring(0, end + 7) else acc
                if (err != null) {
                    msgs[idx] = Msg("assistant", finalText + "\n\n⚠ " + err)
                    break
                }
                msgs[idx] = Msg("assistant", finalText)
                if (stop.get() || !useTools) break
                val call = Agent.parse(finalText) ?: break
                steps++
                if (steps > 10) {
                    msgs.add(Msg("tool", "Batas 10 langkah tercapai, agent berhenti."))
                    break
                }
                val res = browser.run(call.name, call.args)
                msgs.add(Msg("tool", "[HASIL_TOOL] ${call.name}\n$res"))
            }
            busy = false
            st.saveChat(msgs)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("🤖 ${personas[pIdx].first} ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            personas.forEachIndexed { i, p ->
                                DropdownMenuItem(text = { Text(p.first) }, onClick = {
                                    pIdx = i; st.personaIdx = i; menu = false
                                })
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { agentOn = !agentOn; st.agentOn = agentOn }, enabled = !busy) {
                        Text(if (agentOn) "🌐 ON" else "🌐 OFF")
                    }
                    IconButton(onClick = { msgs.clear(); st.saveChat(msgs) }, enabled = !busy) {
                        Icon(Icons.Default.Delete, "Hapus chat")
                    }
                    IconButton(onClick = { showSet = true }) {
                        Icon(Icons.Default.Settings, "Pengaturan")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f), maxLines = 4,
                    placeholder = { Text("Tulis tugas / pesan…") }
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { if (busy) stop.set(true) else send() }) {
                    if (busy) Text("■") else Icon(Icons.Default.Send, "Kirim")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (agentOn) {
                AndroidView(
                    factory = { browser.web },
                    modifier = Modifier.fillMaxWidth().weight(0.45f)
                )
            }
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxWidth().weight(if (agentOn) 0.55f else 1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(msgs.size) { i ->
                    val m = msgs[i]
                    val mine = m.role == "user"
                    if (m.role == "tool") {
                        Text(
                            "🔧 " + m.content.take(220) + if (m.content.length > 220) "…" else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (mine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                SelectionContainer {
                                    Text(m.content.ifEmpty { "…" }, Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSet) {
        var base by remember { mutableStateOf(st.base) }
        var key by remember { mutableStateOf(st.key) }
        var model by remember { mutableStateOf(st.model) }
        AlertDialog(
            onDismissRequest = { showSet = false },
            title = { Text("Pengaturan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    st.base = base.trim(); st.key = key.trim(); st.model = model.trim()
                    showSet = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSet = false }) { Text("Batal") } }
        )
    }
}

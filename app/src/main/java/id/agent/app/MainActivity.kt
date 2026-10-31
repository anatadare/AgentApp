package id.agent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private val Ink = Color(0xFF0E0D16)
private val Panel = Color(0xFF1B1928)
private val Violet = Color(0xFF8B7CFF)

private val scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2F8F),
    onPrimaryContainer = Color(0xFFEAE6FF),
    secondaryContainer = Color(0xFF26233A),
    onSecondaryContainer = Color(0xFFE3DFF5),
    background = Ink,
    onBackground = Color(0xFFECE9F7),
    surface = Ink,
    onSurface = Color(0xFFECE9F7),
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFFB4AFCB)
)

private val toolRe = Regex("<tool>.*?</tool>", RegexOption.DOT_MATCHES_ALL)

private val suggestions = listOf(
    "Cek channel airdrop yang kupantau, pilah mana yang aman, lalu kerjakan yang bisa",
    "Ingat emailku: (tulis emailmu di sini)",
    "Cari berita teknologi terbaru dan ringkas jadi 5 poin",
    "Bantu aku bikin rencana belajar coding 2 minggu",
    "Ingat ya: aku suka jawaban yang singkat"
)

private fun callLabel(c: Call): String = when (c.name) {
    "open" -> "membuka " + c.args.optString("url").removePrefix("https://").removePrefix("http://").take(40)
    "read" -> "membaca halaman"
    "click" -> "klik elemen " + c.args.optInt("id")
    "type" -> "mengetik “" + c.args.optString("text").take(30) + "”"
    "scroll" -> "scroll halaman"
    "back" -> "kembali"
    "plan" -> "menyusun rencana"
    "remember" -> "menyimpan ke memory"
    "ask" -> "meminta izin"
    "telegram_read" -> "membaca @" + c.args.optString("channel").removePrefix("@")
    "telegram_post" -> "menyiapkan posting"
    "playbook" -> "playbook " + domainOf(c.args.optString("domain"))
    else -> c.name
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val st = Store(this)
        setContent { MaterialTheme(colorScheme = scheme) { App(st) } }
    }
}

@Composable
fun Avatar(emoji: String, size: Int = 32) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF5B4BD6), Color(0xFF2BB5A0)))),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size * 0.5).sp)
    }
}

@Composable
fun MsgView(m: Msg, bot: Bot, typing: Boolean, dots: String) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    when (m.role) {
        "tool" -> {
            if (m.content.startsWith("[HASIL_TOOL] plan")) {
                val steps = m.content.lines().drop(1).filter { it.isNotBlank() }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(start = 40.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📋 Rencana", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        steps.forEach { Text(it, fontSize = 13.sp) }
                    }
                }
            } else {
                val s = m.content.lineSequence().firstOrNull { it.startsWith("URL: ") }
                    ?.removePrefix("URL: ")?.take(60)
                    ?: m.content.lines().getOrNull(1)?.take(80) ?: ""
                if (s.isNotBlank()) {
                    Text("↳ $s", fontSize = 11.sp, color = dim, modifier = Modifier.padding(start = 40.dp))
                }
            }
        }
        "assistant" -> {
            val call = Agent.parse(m.content)
            val text = toolRe.replace(m.content, "").substringBefore("<tool>").trim()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Avatar(bot.emoji)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                    if (text.isNotEmpty() || call == null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            SelectionContainer {
                                Text(
                                    if (text.isEmpty()) (if (typing) dots else "…") else text,
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    if (call != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "▸ " + callLabel(call),
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp, color = Violet
                            )
                        }
                    }
                }
            }
        }
        else -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
                    color = Color.Transparent,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF8E6CFF))))) {
                        SelectionContainer {
                            Text(
                                m.content,
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = Color.White, fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(st: Store) {
    val ctx = LocalContext.current
    val browser = remember { Browser(ctx) }
    var bots by remember { mutableStateOf(st.loadBots()) }
    var botId by remember { mutableStateOf(st.botId) }
    val bot = bots.firstOrNull { it.id == botId } ?: bots.first()
    val msgs = remember { mutableStateListOf<Msg>().also { it.addAll(st.loadChat(st.botId)) } }
    val mem = remember { mutableStateListOf<String>().also { it.addAll(st.loadMemory()) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSet by remember { mutableStateOf(false) }
    var showMem by remember { mutableStateOf(false) }
    var agentOn by remember { mutableStateOf(st.agentOn) }
    var showBrowser by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bot?>(null) }
    var editingNew by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    var dots by remember { mutableStateOf("•") }
    val stop = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    val typing = busy && msgs.lastOrNull()?.let { it.role == "assistant" && it.content.isEmpty() } == true

    LaunchedEffect(busy) {
        while (busy) {
            dots = when (dots) { "•" -> "••"; "••" -> "•••"; else -> "•" }
            delay(350)
        }
    }

    LaunchedEffect(msgs.size, msgs.lastOrNull()?.content) {
        if (msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex)
    }

    fun switchBot(id: String) {
        if (busy || id == botId) return
        st.saveChat(botId, msgs)
        botId = id
        st.botId = id
        msgs.clear()
        msgs.addAll(st.loadChat(id))
    }

    fun send(raw: String) {
        val t = raw.trim()
        if (t.isEmpty() || busy) return
        if (st.key.isBlank()) { showSet = true; return }
        input = ""
        msgs.add(Msg("user", t))
        busy = true
        stop.set(false)
        val useTools = agentOn
        val b = bot
        val now = SimpleDateFormat("EEEE, d MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())
        val memBlock = if (mem.isEmpty()) "" else
            "\n\nYANG KAMU INGAT TENTANG PENGGUNA:\n" + mem.joinToString("\n") { "- $it" }
        val tgBlock = "\n\nCHANNEL TELEGRAM:\n- Channel yang dipantau: ${st.watch}\n- Channel milik pengguna (tujuan posting): ${st.tgChat}"
        val sys = b.prompt + "\n\nSekarang: $now." + memBlock + if (useTools) tgBlock + Agent.PROMPT else ""
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
                if (steps > 40) {
                    msgs.add(Msg("tool", "Batas 40 langkah tercapai, agent berhenti."))
                    break
                }
                val res: String = when (call.name) {
                    "remember" -> {
                        val x = call.args.optString("text").trim()
                        if (x.isNotEmpty() && x !in mem) {
                            mem.add(x)
                            if (mem.size > 50) mem.removeAt(0)
                            st.saveMemory(mem)
                        }
                        "Tersimpan di memory."
                    }
                    "plan" -> {
                        val a = call.args.optJSONArray("steps")
                        val items: List<String> =
                            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
                        items.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
                    }
                    "ask" -> {
                        val d = CompletableDeferred<Boolean>()
                        pending = call.args.optString("question", "Lanjutkan?") to d
                        if (d.await()) "Pengguna MENGIZINKAN aksi itu."
                        else "Pengguna MENOLAK. Jangan lakukan aksi itu, tanyakan apa yang dia mau."
                    }
                    "telegram_read" -> withContext(Dispatchers.IO) {
                        Tg.read(call.args.optString("channel"), call.args.optInt("limit", 8))
                    }
                    "telegram_post" -> {
                        val txt = call.args.optString("text").trim()
                        if (txt.isEmpty()) {
                            "Teks kosong."
                        } else {
                            val d = CompletableDeferred<Boolean>()
                            pending = ("Posting ke ${st.tgChat}:\n\n" + txt.take(600)) to d
                            if (d.await()) withContext(Dispatchers.IO) { Tg.post(st.tgToken, st.tgChat, txt) }
                            else "Pengguna MENOLAK posting itu."
                        }
                    }
                    "playbook" -> {
                        val act = call.args.optString("action", "get")
                        val dom = domainOf(call.args.optString("domain"))
                        if (dom.isEmpty()) {
                            "Domain kosong."
                        } else if (act == "save") {
                            st.savePlaybook(dom, call.args.optString("notes"))
                            "Playbook $dom tersimpan."
                        } else {
                            st.getPlaybook(dom) ?: "Belum ada playbook untuk $dom."
                        }
                    }
                    else -> {
                        val r = browser.run(call.name, call.args)
                        val pb = if (call.name == "open") st.getPlaybook(domainOf(call.args.optString("url"))) else null
                        if (pb != null) r + "\n\n[PLAYBOOK " + domainOf(call.args.optString("url")) + "]\n" + pb else r
                    }
                }
                msgs.add(Msg("tool", "[HASIL_TOOL] ${call.name}\n$res"))
                if (stop.get()) break
            }
            msgs.removeAll { it.role == "assistant" && it.content.isBlank() }
            busy = false
            st.saveChat(b.id, msgs)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { menu = true }) {
                            Text("${bot.emoji}  ${bot.name} ▾", fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            bots.forEach { x ->
                                DropdownMenuItem(
                                    text = { Text("${x.emoji}  ${x.name}") },
                                    onClick = { menu = false; switchBot(x.id) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("＋ Bot baru") }, onClick = {
                                menu = false; editingNew = true
                                editing = Bot("", "", "🤖", "Kamu asisten AI yang membantu. Jawab dalam bahasa pengguna.")
                            })
                            DropdownMenuItem(text = { Text("✎ Edit bot ini") }, onClick = {
                                menu = false; editingNew = false; editing = bot
                            })
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { agentOn = !agentOn; st.agentOn = agentOn }, enabled = !busy) {
                        Text(if (agentOn) "🛠 ON" else "🛠 OFF", fontSize = 13.sp)
                    }
                    TextButton(onClick = { showBrowser = !showBrowser }) { Text("🌐", fontSize = 16.sp) }
                    TextButton(onClick = { showMem = true }) { Text("🧠", fontSize = 16.sp) }
                    Box {
                        TextButton(onClick = { more = true }) { Text("⋮", fontSize = 18.sp) }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Hapus chat") }, enabled = !busy, onClick = {
                                more = false; msgs.clear(); st.saveChat(botId, msgs)
                            })
                            DropdownMenuItem(text = { Text("Pengaturan") }, onClick = {
                                more = false; showSet = true
                            })
                        }
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
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { Text("Suruh ${bot.name} melakukan sesuatu…") }
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = {
                    if (busy) { stop.set(true); pending?.second?.complete(false); pending = null } else send(input)
                }) {
                    if (busy) Text("■") else Icon(Icons.Default.Send, "Kirim")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val bm = if (agentOn && showBrowser)
                Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
            else Modifier.size(1.dp)
            AndroidView(factory = { browser.web }, modifier = bm)

            if (msgs.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Avatar(bot.emoji, 72)
                    Spacer(Modifier.height(12.dp))
                    Text("Halo, aku ${bot.name}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Kasih aku tugas, nanti kukerjakan sendiri di browser.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    suggestions.forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp)).clickable { send(s) }
                        ) {
                            Text(s, Modifier.padding(14.dp), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxWidth().weight(if (agentOn && showBrowser) 0.6f else 1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(msgs.size) { i ->
                        MsgView(msgs[i], bot, typing && i == msgs.lastIndex, dots)
                    }
                }
            }
        }
    }

    pending?.let { pq ->
        AlertDialog(
            onDismissRequest = { pq.second.complete(false); pending = null },
            title = { Text("${bot.name} minta izin") },
            text = { Text(pq.first) },
            confirmButton = {
                TextButton(onClick = { pq.second.complete(true); pending = null }) { Text("Izinkan") }
            },
            dismissButton = {
                TextButton(onClick = { pq.second.complete(false); pending = null }) { Text("Tolak") }
            }
        )
    }

    if (showMem) {
        AlertDialog(
            onDismissRequest = { showMem = false },
            title = { Text("🧠 Memory") },
            text = {
                if (mem.isEmpty()) {
                    Text("Belum ada. Agent akan menyimpan hal penting tentangmu di sini, atau minta saja: “ingat bahwa …”.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(mem.size) { i ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• " + mem[i], Modifier.weight(1f), fontSize = 14.sp)
                                TextButton(onClick = { mem.removeAt(i); st.saveMemory(mem) }) { Text("✕") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMem = false }) { Text("Tutup") } },
            dismissButton = {
                if (mem.isNotEmpty()) {
                    TextButton(onClick = { mem.clear(); st.saveMemory(mem) }) { Text("Hapus semua") }
                }
            }
        )
    }

    editing?.let { eb ->
        var emoji by remember(eb) { mutableStateOf(eb.emoji) }
        var name by remember(eb) { mutableStateOf(eb.name) }
        var prompt by remember(eb) { mutableStateOf(eb.prompt) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (editingNew) "Bot baru" else "Edit bot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Emoji") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
                    OutlinedTextField(
                        prompt, { prompt = it }, label = { Text("Kepribadian / instruksi") },
                        minLines = 4, maxLines = 8
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val nb = Bot(
                            if (editingNew) System.currentTimeMillis().toString() else eb.id,
                            name.trim(), emoji.ifBlank { "🤖" }, prompt.trim()
                        )
                        bots = if (editingNew) bots + nb else bots.map { if (it.id == nb.id) nb else it }
                        st.saveBots(bots)
                        editing = null
                        if (editingNew) switchBot(nb.id)
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                Row {
                    if (!editingNew && bots.size > 1) {
                        TextButton(onClick = {
                            val id = eb.id
                            editing = null
                            if (!busy) {
                                bots = bots.filter { it.id != id }
                                st.saveBots(bots)
                                st.deleteChat(id)
                                botId = bots.first().id
                                st.botId = botId
                                msgs.clear()
                                msgs.addAll(st.loadChat(botId))
                            }
                        }) { Text("Hapus") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Batal") }
                }
            }
        )
    }

    if (showSet) {
        var base by remember { mutableStateOf(st.base) }
        var key by remember { mutableStateOf(st.key) }
        var model by remember { mutableStateOf(st.model) }
        var tgToken by remember { mutableStateOf(st.tgToken) }
        var tgChat by remember { mutableStateOf(st.tgChat) }
        var watch by remember { mutableStateOf(st.watch) }
        AlertDialog(
            onDismissRequest = { showSet = false },
            title = { Text("Pengaturan") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                    HorizontalDivider()
                    Text("Telegram", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        watch, { watch = it }, label = { Text("Channel dipantau (pisah koma)") }
                    )
                    OutlinedTextField(
                        tgChat, { tgChat = it }, label = { Text("Channel milikku (tujuan posting)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        tgToken, { tgToken = it }, label = { Text("Token bot (dari @BotFather)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    st.base = base.trim(); st.key = key.trim(); st.model = model.trim()
                    st.watch = watch.trim(); st.tgChat = tgChat.trim(); st.tgToken = tgToken.trim()
                    showSet = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showSet = false }) { Text("Batal") } }
        )
    }
}

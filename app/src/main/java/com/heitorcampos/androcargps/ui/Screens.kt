package com.heitorcampos.androcargps.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heitorcampos.androcargps.AppState
import com.heitorcampos.androcargps.BuildConfig
import com.heitorcampos.androcargps.Net
import com.heitorcampos.androcargps.Pairing
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.UpdateInfo
import com.heitorcampos.androcargps.Updater
import com.heitorcampos.androcargps.service.ReceiverService
import com.heitorcampos.androcargps.service.SenderService
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(Prefs.mode(ctx)) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) { runCatching { Updater.check() }.getOrNull() }
    }
    BackHandler(enabled = screen == "settings") { screen = "home" }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val m = mode
        when {
            m == null -> ModePicker { chosen ->
                Prefs.setMode(ctx, chosen)
                mode = chosen
            }
            screen == "settings" -> SettingsScreen(
                mode = m,
                update = update,
                onBack = { screen = "home" },
                onChangeMode = {
                    SenderService.stop(ctx)
                    ReceiverService.stop(ctx)
                    Prefs.setMode(ctx, null)
                    mode = null
                    screen = "home"
                },
                onUpdateFound = { update = it },
            )
            else -> Column(Modifier.fillMaxSize()) {
                TopBar(
                    subtitle = if (m == Prefs.MODE_SEND) "Celular · envia o GPS" else "Tablet · recebe o GPS",
                    onSettings = { screen = "settings" },
                )
                if (m == Prefs.MODE_SEND) SenderScreen(update) else ReceiverScreen(update)
            }
        }
    }
}

@Composable
private fun TopBar(subtitle: String, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Androcar GPS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Muted(subtitle)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
    }
}

private fun formatMs(ms: Long?): String = when {
    ms == null -> "—"
    ms >= 10_000 -> "${ms / 1000}"
    else -> "$ms"
}

// ---------------------------------------------------------------- Escolha do modo

@Composable
private fun ModePicker(onChoose: (String) -> Unit) {
    ScreenColumn {
        Spacer(Modifier.height(40.dp))
        AppBadge(72)
        Text("Androcar GPS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Muted("Como este aparelho vai ser usado?")
        Spacer(Modifier.height(4.dp))
        ModeOption(Icons.Default.Phone, "CELULAR", "Enviar GPS", "Lê o GPS deste aparelho e envia para o tablet.") {
            onChoose(Prefs.MODE_SEND)
        }
        ModeOption(Icons.Default.Place, "TABLET", "Receber GPS", "Recebe o GPS do celular e entrega ao Google Maps como local fictício.") {
            onChoose(Prefs.MODE_RECEIVE)
        }
        Muted("Dá para trocar depois em Configurações.")
    }
}

@Composable
private fun ModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Azul.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Azul, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tag, color = Azul, style = MaterialTheme.typography.labelLarge)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Muted(desc)
            }
        }
    }
}

// ---------------------------------------------------------------- Celular (envia)

@Composable
private fun SenderScreen(update: UpdateInfo?) {
    val ctx = LocalContext.current
    val st by AppState.sender.collectAsState()
    val now by rememberNow()
    val code = remember { Prefs.senderCode(ctx) }
    val interval = remember { Prefs.intervalMs(ctx) }
    val start = rememberStarter { SenderService.start(ctx) }

    // Atualiza os IPs a cada 5 s (o hotspot pode ser ligado depois de abrir o app)
    val ips by produceState(Net.localIps()) {
        while (true) {
            delay(5000)
            value = Net.localIps()
        }
    }
    val qr = remember(code, ips) { Pairing.qr(Pairing.uri(code, ips)) }

    val fresh = st.lastFixAt > 0 && now - st.lastFixAt < 5000
    val gps = if (fresh) "GPS ok · precisão ±${st.accuracy?.roundToInt() ?: "?"} m" else "Buscando sinal de GPS…"

    ScreenColumn(wide = true) {
        update?.let { UpdateCard(it) }
        AdaptiveColumns(
            left = {
                when {
                    !st.running -> StatusHero(Cinza, "Parado", "Toque em Iniciar para enviar o GPS")
                    st.clients > 0 -> StatusHero(Verde, "Enviando para o tablet", gps, pulse = true)
                    else -> StatusHero(
                        Amarelo, "Aguardando o tablet",
                        if (st.gpsOn) gps else "GPS em espera até o tablet conectar (economiza bateria)",
                        pulse = true,
                    )
                }
                st.error?.let { InfoCard("Atenção") { Text(it, color = Vermelho) } }
                BigToggle(st.running, onStart = start, onStop = { SenderService.stop(ctx) })
                MetricsRow {
                    Metric("Precisão", if (fresh) "±${st.accuracy?.roundToInt() ?: "?"}" else "—", "m")
                    Metric("Velocidade", if (fresh) "${st.speedKmh?.roundToInt() ?: 0}" else "—", "km/h")
                    Metric("Envio", if (st.running) formatMs(st.measuredMs ?: interval) else "$interval", "ms")
                }
                var showIps by rememberSaveable { mutableStateOf(false) }
                InfoCard("Rede", Icons.Default.Share, action = { EyeButton(showIps) { showIps = !showIps } }) {
                    InfoRow(
                        "IP deste celular",
                        ips.joinToString("\n") { if (showIps) it else mask(it) }.ifEmpty { "sem rede" },
                    )
                    InfoRow("Tablets conectados", "${st.clients}")
                    Muted("Ligue o roteador (hotspot) do celular e conecte o tablet nele.")
                }
            },
            right = {
                var showPair by rememberSaveable { mutableStateOf(false) }
                InfoCard("Parear com o tablet", Icons.Default.Lock, action = { EyeButton(showPair) { showPair = !showPair } }) {
                    Muted("No tablet, toque em \"Escanear QR code do celular\" e aponte a câmera para cá:")
                    if (!showPair) {
                        // QR e PIN escondidos: toque para mostrar
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier
                                    .size(254.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { showPair = true },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Azul, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("QR code oculto", fontWeight = FontWeight.SemiBold)
                                Muted("Toque para mostrar")
                            }
                        }
                    } else if (qr != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                            ) {
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = "QR code de pareamento",
                                    modifier = Modifier.size(230.dp),
                                )
                            }
                        }
                    }
                    Muted("Ou digite o código no tablet:")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            (if (showPair) code else mask(code)).toCharArray().joinToString("  "),
                            fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Azul,
                        )
                    }
                }
                XiaomiCard()
            },
        )
    }
}

// ---------------------------------------------------------------- Tablet (recebe)

@Composable
private fun ReceiverScreen(update: UpdateInfo?) {
    val ctx = LocalContext.current
    val st by AppState.receiver.collectAsState()
    val now by rememberNow()
    var code by remember { mutableStateOf(Prefs.receiverCode(ctx) ?: "") }
    val start = rememberStarter { ReceiverService.start(ctx) }
    val paired = code.length == 4

    // Leitor de QR code (câmera)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val p = Pairing.parse(result.contents)
        if (p == null) {
            if (result.contents != null) Intents.toast(ctx, "Esse QR code não é do Androcar GPS")
        } else {
            code = p.code
            Prefs.setReceiverCode(ctx, p.code)
            Prefs.setQrHosts(ctx, p.hosts)
            Intents.toast(ctx, "Pareado com o celular!")
            if (!ReceiverService.isRunning) start()
        }
    }
    val scan = {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Aponte para o QR code na tela do celular")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
    val onCode = { d: String ->
        code = d
        Prefs.setReceiverCode(ctx, d)
    }

    val age = if (st.lastFixAt > 0) (now - st.lastFixAt) / 1000 else null
    val fresh = age != null && age < 4

    ScreenColumn(wide = true) {
        update?.let { UpdateCard(it) }
        // Sem pareamento: o pareamento vem primeiro, em destaque
        if (!paired) PairingCard(code, highlight = true, onScan = scan, onCode = onCode)

        AdaptiveColumns(
            left = {
                when {
                    !st.running -> StatusHero(Cinza, "Parado", "Toque em Iniciar para receber o GPS do celular")
                    st.mockError != null -> StatusHero(Vermelho, "Local fictício bloqueado", st.mockError)
                    st.connected && fresh -> StatusHero(Verde, "Recebendo GPS do celular", "O Google Maps já pode navegar", pulse = true)
                    st.connected -> StatusHero(Amarelo, "Conectado, aguardando GPS", "O celular ainda está buscando sinal de GPS", pulse = true)
                    else -> StatusHero(if (st.isError) Vermelho else Amarelo, "Desconectado", st.message, pulse = true)
                }
                BigToggle(st.running, onStart = start, onStop = { ReceiverService.stop(ctx) })
                Button(
                    onClick = { Intents.openMaps(ctx) },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Place, null, tint = Verde)
                    Spacer(Modifier.width(10.dp))
                    Text("Abrir Google Maps", style = MaterialTheme.typography.titleMedium)
                }
                MetricsRow {
                    Metric("Velocidade", if (fresh) "${st.speedKmh?.roundToInt() ?: 0}" else "—", "km/h", big = true)
                }
                MetricsRow {
                    Metric("Precisão", if (fresh) "±${st.accuracy?.roundToInt() ?: "?"}" else "—", "m")
                    Metric("Atualização", if (fresh) formatMs(st.measuredMs) else "—", "ms")
                    Metric("Última", age?.let { if (it < 1) "agora" else "$it" } ?: "—", if (age != null && age >= 1) "s" else "")
                }
            },
            right = {
                var showHost by rememberSaveable { mutableStateOf(false) }
                InfoCard("Posição", Icons.Default.Place, action = { EyeButton(showHost) { showHost = !showHost } }) {
                    InfoRow(
                        "Coordenadas",
                        if (st.lat != null && st.lon != null) {
                            String.format(Locale.US, "%.5f, %.5f", st.lat, st.lon)
                        } else {
                            "—"
                        },
                    )
                    InfoRow("IP do celular", st.host?.let { if (showHost) it else mask(it) } ?: "—")
                    Muted("O tempo de atualização é ajustado no celular, em Configurações.")
                }
                if (paired) PairingCard(code, highlight = false, onScan = scan, onCode = onCode)
                InfoCard("Configuração única do tablet", Icons.Default.Build) {
                    Step("1", "Ative o modo desenvolvedor: Configurações → Sobre o tablet → toque 7 vezes em \"Versão do SO\".")
                    Step("2", "Em Opções do desenvolvedor → \"Selecionar app de local fictício\", escolha Androcar GPS (no lugar do Fake GPS).")
                    Step("3", "Conecte o tablet no roteador (hotspot) do celular.")
                    WideOutlined("Abrir Opções do desenvolvedor") { Intents.openDevOptions(ctx) }
                }
                XiaomiCard()
            },
        )
    }
}

@Composable
private fun PairingCard(code: String, highlight: Boolean, onScan: () -> Unit, onCode: (String) -> Unit) {
    InfoCard(if (highlight) "1º passo: parear com o celular" else "Pareamento", Icons.Default.Lock) {
        if (highlight) Muted("No celular, abra o Androcar GPS em \"Enviar GPS\" e toque em Iniciar. Depois:")
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(if (highlight) 64.dp else 54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                if (highlight) "Escanear QR code do celular" else "Parear de novo (QR code)",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        var showCode by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = code,
            onValueChange = { v -> onCode(v.filter { it.isDigit() }.take(4)) },
            label = { Text("Ou digite o código de 4 dígitos do celular") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { EyeButton(showCode) { showCode = !showCode } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (code.length == 4) Text("Código salvo ✓", color = Verde)
    }
}

// ---------------------------------------------------------------- Configurações

@Composable
private fun SettingsScreen(
    mode: String,
    update: UpdateInfo?,
    onBack: () -> Unit,
    onChangeMode: () -> Unit,
    onUpdateFound: (UpdateInfo) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val send = mode == Prefs.MODE_SEND

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar") }
            Text("Configurações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        ScreenColumn(wide = true) {
            AdaptiveColumns(
                left = {
                    InfoCard("Modo deste aparelho", Icons.Default.Phone) {
                        Text(if (send) "Celular — envia o GPS" else "Tablet — recebe o GPS", fontWeight = FontWeight.SemiBold)
                        WideOutlined("Trocar modo", onClick = onChangeMode)
                    }
                    if (send) {
                        IntervalCard()
                        var showCode by rememberSaveable { mutableStateOf(false) }
                        InfoCard("Pareamento", Icons.Default.Lock, action = { EyeButton(showCode) { showCode = !showCode } }) {
                            var code by remember { mutableStateOf(Prefs.senderCode(ctx)) }
                            InfoRow("Código atual", if (showCode) code else mask(code))
                            WideOutlined("Gerar novo código") {
                                code = Prefs.newSenderCode(ctx)
                                if (SenderService.isRunning) {
                                    SenderService.stop(ctx)
                                    Intents.toast(ctx, "Novo código gerado. Inicie o envio de novo e pareie o tablet.")
                                } else {
                                    Intents.toast(ctx, "Novo código gerado. Pareie o tablet de novo.")
                                }
                            }
                        }
                    } else {
                        ConnectionCard()
                    }
                },
                right = {
                    InfoCard("Atualizações", Icons.Default.Notifications) {
                        var checking by remember { mutableStateOf(false) }
                        var msg by remember { mutableStateOf<String?>(null) }
                        InfoRow("Versão instalada", BuildConfig.VERSION_NAME)
                        WideOutlined(if (checking) "Verificando…" else "Verificar atualização", Icons.Default.Refresh) {
                            if (checking) return@WideOutlined
                            scope.launch {
                                checking = true
                                val r = withContext(Dispatchers.IO) { runCatching { Updater.check() } }
                                checking = false
                                r.onSuccess {
                                    if (it == null) {
                                        msg = "Você já está na versão mais recente."
                                    } else {
                                        msg = null
                                        onUpdateFound(it)
                                    }
                                }.onFailure { msg = "Não consegui verificar agora (sem internet?)." }
                            }
                        }
                        msg?.let { Muted(it) }
                    }
                    update?.let { UpdateCard(it) }
                    InfoCard("Sobre", Icons.Default.Info) {
                        Text("Androcar GPS ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                        Muted("Envia o GPS do celular para o tablet, para o Google Maps navegar no carro.")
                        InfoRow("Desenvolvedor", "Heitor Campos")
                        InfoRow("GitHub", "heitorcampos2")
                        WideOutlined("Abrir página no GitHub") { Intents.openUrl(ctx, Updater.REPO_URL) }
                    }
                },
            )
        }
    }
}

/** Celular: tempo entre um envio de posição e o próximo, em milissegundos. */
@Composable
private fun IntervalCard() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var current by remember { mutableStateOf(Prefs.intervalMs(ctx)) }
    var text by remember { mutableStateOf(current.toString()) }

    fun aplicar(ms: Long) {
        val v = ms.coerceIn(Prefs.INTERVAL_MIN, Prefs.INTERVAL_MAX)
        Prefs.setIntervalMs(ctx, v)
        current = v
        text = v.toString()
        focus.clearFocus()
        SenderService.applyInterval(ctx)
        Intents.toast(ctx, "Atualização a cada $v ms" + if (SenderService.isRunning) " (já aplicado)" else "")
    }

    InfoCard("Tempo de atualização", Icons.Default.Refresh) {
        Muted("De quanto em quanto tempo o celular envia a posição para o tablet.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(200L, 500L, 1000L, 2000L).forEach { ms ->
                val selected = current == ms
                OutlinedButton(
                    onClick = { aplicar(ms) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (selected) {
                        ButtonDefaults.outlinedButtonColors(containerColor = Azul, contentColor = Color.White)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    },
                ) { Text("$ms") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                label = { Text("Milissegundos (${Prefs.INTERVAL_MIN}–${Prefs.INTERVAL_MAX})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { text.toLongOrNull()?.let { aplicar(it) } }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { text.toLongOrNull()?.let { aplicar(it) } },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Aplicar") }
        }
        Muted("Padrão: 1000 ms. O GPS da maioria dos celulares atualiza até 1 vez por segundo; valores menores só fazem diferença em aparelhos com GPS mais rápido (ou com o Fake GPS).")
    }
}

/** Tablet: IP do celular forçado à mão (opcional). */
@Composable
private fun ConnectionCard() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val start = rememberStarter { ReceiverService.start(ctx) }
    var host by remember { mutableStateOf(Prefs.manualHost(ctx) ?: "") }
    val save = {
        focus.clearFocus()
        Prefs.setManualHost(ctx, host)
        when {
            (Prefs.receiverCode(ctx)?.length ?: 0) != 4 ->
                Intents.toast(ctx, "IP salvo. Falta o código de pareamento: volte e escaneie o QR code do celular.")
            ReceiverService.isRunning ->
                Intents.toast(ctx, if (host.isEmpty()) "Modo automático salvo. Conectando…" else "IP salvo. Conectando…")
            else -> {
                start()
                Intents.toast(ctx, "IP salvo. Conectando…")
            }
        }
    }
    var showIp by rememberSaveable { mutableStateOf(false) }
    InfoCard("Conexão", Icons.Default.Share) {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            label = { Text("IP do celular (opcional)") },
            placeholder = { Text("Automático") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            visualTransformation = if (showIp) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { EyeButton(showIp) { showIp = !showIp } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = save,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Salvar e conectar") }
        Muted("Deixe em branco para o modo automático (o QR code já traz os IPs do celular). Use só se precisar forçar um IP.")
    }
}

package com.heitorcampos.androcargps.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.heitorcampos.androcargps.Pairing
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heitorcampos.androcargps.AppState
import com.heitorcampos.androcargps.BuildConfig
import com.heitorcampos.androcargps.Net
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.UpdateInfo
import com.heitorcampos.androcargps.Updater
import com.heitorcampos.androcargps.service.ReceiverService
import com.heitorcampos.androcargps.service.SenderService
import kotlinx.coroutines.Dispatchers
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Androcar GPS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Muted(subtitle)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Configurações") }
    }
}

// ---------------------------------------------------------------- Escolha do modo

@Composable
private fun ModePicker(onChoose: (String) -> Unit) {
    ScreenColumn {
        Spacer(Modifier.height(36.dp))
        AppBadge(64)
        Text("Androcar GPS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Muted("Como este aparelho vai ser usado?")
        ModeOption("CELULAR", "Enviar GPS", "Lê o GPS deste aparelho e envia para o tablet.") {
            onChoose(Prefs.MODE_SEND)
        }
        ModeOption("TABLET", "Receber GPS", "Recebe o GPS do celular e entrega ao Google Maps como local fictício.") {
            onChoose(Prefs.MODE_RECEIVE)
        }
        Muted("Dá para trocar depois em Configurações.")
    }
}

@Composable
private fun ModeOption(tag: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tag, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Muted(desc)
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
    val start = rememberStarter { SenderService.start(ctx) }

    val fresh = st.lastFixAt > 0 && now - st.lastFixAt < 5000
    val gps = if (fresh) "GPS ok · precisão ±${st.accuracy?.roundToInt() ?: "?"} m" else "Buscando sinal de GPS…"

    ScreenColumn {
        update?.let { UpdateCard(it) }
        when {
            !st.running -> StatusHero(Cinza, "Parado", "Toque em Iniciar para enviar o GPS")
            st.clients > 0 -> StatusHero(Verde, "Enviando para o tablet", gps)
            else -> StatusHero(Amarelo, "Aguardando o tablet", gps)
        }
        st.error?.let { InfoCard("Atenção") { Text(it, color = Vermelho) } }
        BigToggle(st.running, onStart = start, onStop = { SenderService.stop(ctx) })

        // Atualiza os IPs a cada 5 s (o hotspot pode ser ligado depois de abrir o app)
        val ips by produceState(Net.localIps()) {
            while (true) {
                delay(5000)
                value = Net.localIps()
            }
        }
        val qr = remember(code, ips) { Pairing.qr(Pairing.uri(code, ips)) }

        InfoCard("Parear com o tablet") {
            Muted("No tablet, toque em \"Escanear QR code do celular\" e aponte a câmera para cá:")
            if (qr != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp),
                    ) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "QR code de pareamento",
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
            }
            Muted("Ou digite o código no tablet:")
            Text(
                code.toCharArray().joinToString("  "),
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
        }

        InfoCard("Rede") {
            InfoRow("IP deste celular", ips.joinToString("\n").ifEmpty { "sem rede" })
            Muted("Ligue o roteador (hotspot) do celular e conecte o tablet nele.")
        }

        XiaomiCard()
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

    val age = if (st.lastFixAt > 0) (now - st.lastFixAt) / 1000 else null
    val fresh = age != null && age < 4

    ScreenColumn {
        update?.let { UpdateCard(it) }

        // Sem pareamento: o pareamento vem primeiro, em destaque
        if (!paired) {
            PairingCard(code, highlight = true, onScan = scan) { d ->
                code = d
                Prefs.setReceiverCode(ctx, d)
            }
        }

        when {
            !st.running -> StatusHero(Cinza, "Parado", "Toque em Iniciar para receber o GPS do celular")
            st.mockError != null -> StatusHero(Vermelho, "Local fictício bloqueado", st.mockError)
            st.connected && fresh -> StatusHero(Verde, "Recebendo GPS do celular", "O Google Maps já pode navegar")
            st.connected -> StatusHero(Amarelo, "Conectado, aguardando GPS", "O celular ainda está buscando sinal de GPS")
            else -> StatusHero(if (st.isError) Vermelho else Amarelo, "Desconectado", st.message)
        }
        BigToggle(st.running, onStart = start, onStop = { ReceiverService.stop(ctx) })
        WideOutlined("Abrir Google Maps") { Intents.openMaps(ctx) }

        InfoCard("Posição") {
            InfoRow("Velocidade", st.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "—")
            InfoRow("Precisão", st.accuracy?.let { "±${it.roundToInt()} m" } ?: "—")
            InfoRow("Última posição", age?.let { if (it < 1) "agora" else "há $it s" } ?: "—")
            InfoRow(
                "Coordenadas",
                if (st.lat != null && st.lon != null) {
                    String.format(Locale.US, "%.5f, %.5f", st.lat, st.lon)
                } else {
                    "—"
                },
            )
            InfoRow("Celular", st.host ?: "—")
        }

        if (paired) {
            PairingCard(code, highlight = false, onScan = scan) { d ->
                code = d
                Prefs.setReceiverCode(ctx, d)
            }
        }

        InfoCard("Configuração única do tablet") {
            Step("1", "Ative o modo desenvolvedor: Configurações → Sobre o tablet → toque 7 vezes em \"Versão do SO\".")
            Step("2", "Em Opções do desenvolvedor → \"Selecionar app de local fictício\", escolha Androcar GPS (no lugar do Fake GPS).")
            Step("3", "Conecte o tablet no roteador (hotspot) do celular.")
            WideOutlined("Abrir Opções do desenvolvedor") { Intents.openDevOptions(ctx) }
        }

        XiaomiCard()
    }
}

@Composable
private fun PairingCard(code: String, highlight: Boolean, onScan: () -> Unit, onCode: (String) -> Unit) {
    InfoCard(if (highlight) "1º passo: parear com o celular" else "Pareamento") {
        if (highlight) Muted("No celular, abra o Androcar GPS em \"Enviar GPS\" e toque em Iniciar. Depois:")
        Button(
            onClick = onScan,
            modifier = Modifier.fillMaxWidth().height(if (highlight) 64.dp else 52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                if (highlight) "Escanear QR code do celular" else "Parear de novo (QR code)",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { v -> onCode(v.filter { it.isDigit() }.take(4)) },
            label = { Text("Ou digite o código de 4 dígitos do celular") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (code.length == 4) Muted("Código salvo ✓")
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
        ScreenColumn {
            InfoCard("Modo deste aparelho") {
                Text(if (send) "Celular — envia o GPS" else "Tablet — recebe o GPS")
                WideOutlined("Trocar modo", onChangeMode)
            }

            if (send) {
                InfoCard("Pareamento") {
                    var code by remember { mutableStateOf(Prefs.senderCode(ctx)) }
                    InfoRow("Código atual", code)
                    WideOutlined("Gerar novo código") {
                        code = Prefs.newSenderCode(ctx)
                        if (SenderService.isRunning) {
                            SenderService.stop(ctx)
                            Intents.toast(ctx, "Novo código gerado. Inicie o envio de novo e digite o código no tablet.")
                        } else {
                            Intents.toast(ctx, "Novo código gerado. Digite no tablet.")
                        }
                    }
                }
            } else {
                InfoCard("Conexão") {
                    var host by remember { mutableStateOf(Prefs.manualHost(ctx) ?: "") }
                    val focus = LocalFocusManager.current
                    val start = rememberStarter { ReceiverService.start(ctx) }
                    val save = {
                        focus.clearFocus()
                        Prefs.setManualHost(ctx, host)
                        when {
                            (Prefs.receiverCode(ctx)?.length ?: 0) != 4 ->
                                Intents.toast(ctx, "IP salvo. Falta o código de pareamento: volte e escaneie o QR code do celular.")
                            ReceiverService.isRunning ->
                                Intents.toast(ctx, if (host.isEmpty()) "Modo automático salvo. Conectando…" else "IP salvo. Conectando em $host…")
                            else -> {
                                start()
                                Intents.toast(ctx, "IP salvo. Conectando…")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("IP do celular (opcional)") },
                        placeholder = { Text("Automático") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
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

            InfoCard("Atualizações") {
                var checking by remember { mutableStateOf(false) }
                var msg by remember { mutableStateOf<String?>(null) }
                InfoRow("Versão instalada", BuildConfig.VERSION_NAME)
                WideOutlined(if (checking) "Verificando…" else "Verificar atualização") {
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

            InfoCard("Sobre") {
                Text("Androcar GPS ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                Muted("Envia o GPS do celular para o tablet, para o Google Maps navegar no carro.")
                Text("Desenvolvedor: Heitor Campos (GitHub heitorcampos2)")
                WideOutlined("Abrir página no GitHub") { Intents.openUrl(ctx, Updater.REPO_URL) }
            }
        }
    }
}

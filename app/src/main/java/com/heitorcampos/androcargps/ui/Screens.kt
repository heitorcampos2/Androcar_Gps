package com.heitorcampos.androcargps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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

        InfoCard("Código de pareamento") {
            Text(
                code.toCharArray().joinToString("  "),
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
            Muted("Digite este código no tablet (só na primeira vez).")
        }

        InfoCard("Rede") {
            val ips = remember(st.running) { Net.localIps() }
            InfoRow("IP deste celular", ips.joinToString("  ·  ").ifEmpty { "sem rede" })
            Muted("Ligue o roteador (hotspot) do celular e conecte o tablet nele: o tablet encontra o celular sozinho.")
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

    val age = if (st.lastFixAt > 0) (now - st.lastFixAt) / 1000 else null
    val fresh = age != null && age < 4

    ScreenColumn {
        update?.let { UpdateCard(it) }
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

        InfoCard("Código de pareamento") {
            OutlinedTextField(
                value = code,
                onValueChange = { v ->
                    val d = v.filter { it.isDigit() }.take(4)
                    code = d
                    Prefs.setReceiverCode(ctx, d)
                },
                label = { Text("Código de 4 dígitos mostrado no celular") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
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
                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it.trim()
                            Prefs.setManualHost(ctx, host)
                        },
                        label = { Text("IP do celular (opcional)") },
                        placeholder = { Text("Automático") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Muted("Deixe em branco: no roteador do celular o tablet acha o celular sozinho. Use só se os dois estiverem em outra rede Wi-Fi.")
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

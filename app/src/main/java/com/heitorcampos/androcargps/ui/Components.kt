package com.heitorcampos.androcargps.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heitorcampos.androcargps.Perms
import com.heitorcampos.androcargps.R
import com.heitorcampos.androcargps.UpdateInfo
import com.heitorcampos.androcargps.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun AppBadge(size: Int = 40) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_stat_gps), contentDescription = null,
            tint = Color.White, modifier = Modifier.size((size * 0.6f).dp),
        )
    }
}

@Composable
fun InfoCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun StatusHero(color: Color, title: String, subtitle: String?) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Muted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun BigToggle(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Button(
        onClick = { if (running) onStop() else onStart() },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = if (running) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2328), contentColor = Vermelho)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(if (running) "Parar" else "Iniciar", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun WideOutlined(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text)
    }
}

@Composable
fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(n, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Relógio que atualiza a cada segundo (para "há X s"). */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1000)
        value = System.currentTimeMillis()
    }
}

/** Pede as permissões (localização + notificações) e chama [onGranted] quando liberadas. */
@Composable
fun rememberStarter(onGranted: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val current by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Perms.hasLocation(ctx)) current() else Intents.toast(ctx, "Sem a permissão de localização o app não funciona")
    }
    return {
        if (Perms.hasLocation(ctx) && Perms.hasNotifications(ctx)) current() else launcher.launch(Perms.required())
    }
}

@Composable
fun XiaomiCard() {
    val ctx = LocalContext.current
    InfoCard("Aparelhos Xiaomi (HyperOS)") {
        Muted("Para o sistema não fechar o app no meio da viagem, em Informações do app:")
        Text("•  Energia → Sem restrições")
        Text("•  Início automático → ligado")
        Text("•  Pausar atividade do app quando sem uso → desligado")
        WideOutlined("Abrir informações do app") { Intents.openAppDetails(ctx) }
        WideOutlined("Liberar economia de bateria") { Intents.requestIgnoreBattery(ctx) }
    }
}

@Composable
fun UpdateCard(info: UpdateInfo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    InfoCard("Nova versão ${info.versionName} disponível") {
        if (info.notes.isNotBlank()) Muted(info.notes)
        val p = progress
        if (p != null) {
            LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
            Muted("Baixando… $p%")
        } else {
            Button(
                onClick = {
                    scope.launch {
                        progress = 0
                        val file = withContext(Dispatchers.IO) {
                            runCatching { Updater.download(ctx, info) { progress = it } }.getOrNull()
                        }
                        progress = null
                        when {
                            file == null -> Intents.toast(ctx, "Não consegui baixar a atualização")
                            !Updater.install(ctx, file) ->
                                Intents.toast(ctx, "Permita instalar apps deste app e toque em Atualizar de novo")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Baixar e instalar") }
        }
    }
}

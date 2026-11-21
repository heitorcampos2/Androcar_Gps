package com.heitorcampos.androcargps.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heitorcampos.androcargps.Perms
import com.heitorcampos.androcargps.R
import com.heitorcampos.androcargps.UpdateInfo
import com.heitorcampos.androcargps.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(24.dp)

/** Coluna rolável centralizada. [wide] = mais larga, para o tablet em paisagem. */
@Composable
fun ScreenColumn(wide: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (wide) 1240.dp else 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Duas colunas em tela larga (tablet em paisagem); uma coluna no celular. */
@Composable
fun AdaptiveColumns(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 860.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = left)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), content = right)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                left()
                right()
            }
        }
    }
}

@Composable
fun AppBadge(size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5AA2FF), Azul, Color(0xFF1F5FD1)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_stat_gps), contentDescription = null,
            tint = Color.White, modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}

@Composable
fun InfoCard(
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                .background(Azul.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = Azul, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            content()
        }
    }
}

/** Cartão de status com fundo na cor do estado e bolinha pulsando quando ativo. */
@Composable
fun StatusHero(color: Color, title: String, subtitle: String?, pulse: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
                .border(1.dp, color.copy(alpha = 0.35f), CardShape)
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseDot(color, pulse)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color, pulse: Boolean) {
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        if (pulse) {
            val t = rememberInfiniteTransition(label = "pulso")
            val s by t.animateFloat(
                1f, 2.2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "escala",
            )
            val a by t.animateFloat(
                0.55f, 0f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "alfa",
            )
            Box(
                Modifier.size(16.dp).scale(s).graphicsLayer { alpha = a }
                    .clip(CircleShape).background(color),
            )
        }
        Box(Modifier.size(16.dp).clip(CircleShape).background(color))
    }
}

/** Número grande com legenda (painel do carro). */
@Composable
fun RowScope.Metric(label: String, value: String, unit: String = "", big: Boolean = false) {
    Card(
        Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = if (big) 44.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = if (big) 9.dp else 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
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
        modifier = Modifier.fillMaxWidth().height(66.dp),
        shape = RoundedCornerShape(20.dp),
        colors = if (running) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFF3A2328), contentColor = Vermelho)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(if (running) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(if (running) "Parar" else "Iniciar", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun WideOutlined(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Azul.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text(n, fontWeight = FontWeight.Bold, color = Azul) }
        Spacer(Modifier.width(12.dp))
        Text(text, modifier = Modifier.padding(top = 3.dp))
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
    InfoCard("Aparelhos Xiaomi (HyperOS)", Icons.Default.Warning) {
        Muted("Para o sistema não fechar o app no meio da viagem, em Informações do app:")
        Step("1", "Energia → Sem restrições")
        Step("2", "Início automático → ligado")
        Step("3", "Pausar atividade do app quando sem uso → desligado")
        WideOutlined("Abrir informações do app") { Intents.openAppDetails(ctx) }
        WideOutlined("Liberar economia de bateria") { Intents.requestIgnoreBattery(ctx) }
    }
}

@Composable
fun UpdateCard(info: UpdateInfo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Azul.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nova versão ${info.versionName} disponível", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Baixar e instalar") }
            }
        }
    }
}

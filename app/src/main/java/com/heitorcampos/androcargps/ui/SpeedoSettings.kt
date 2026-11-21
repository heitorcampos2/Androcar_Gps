package com.heitorcampos.androcargps.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.heitorcampos.androcargps.Prefs
import com.heitorcampos.androcargps.service.ReceiverService
import com.heitorcampos.androcargps.service.SenderService
import com.heitorcampos.androcargps.speedo.SpeedOverlay
import com.heitorcampos.androcargps.speedo.SpeedometerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/** Configurações do velocímetro flutuante, com prévia ao vivo. */
@Composable
fun SpeedoCard() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(Prefs.speedo(ctx)) }
    var canDraw by remember { mutableStateOf(SpeedOverlay.canDraw(ctx)) }

    // Ao voltar da tela de permissão, confere de novo
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            canDraw = SpeedOverlay.canDraw(ctx)
            if (canDraw) SpeedOverlay.refresh(ctx)
        }
    }

    /** Salva; mantém a posição onde a bolinha foi arrastada, a não ser que [resetPos]. */
    fun save(n: Prefs.Speedo, resetPos: Boolean = false) {
        val cur = Prefs.speedo(ctx)
        val m = if (resetPos) n else n.copy(x = cur.x, y = cur.y)
        s = m
        Prefs.setSpeedo(ctx, m)
        SpeedOverlay.refresh(ctx)
    }

    fun askPermission() {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Intents.toast(ctx, "Ative \"Sobrepor a outros apps\" para o Androcar GPS e volte")
    }

    InfoCard(
        "Velocímetro flutuante",
        Icons.Default.Star,
        action = {
            Switch(
                checked = s.enabled,
                onCheckedChange = { on ->
                    if (on && !SpeedOverlay.canDraw(ctx)) askPermission()
                    save(s.copy(enabled = on))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Azul),
            )
        },
    ) {
        Muted("Bolinha com a velocidade do GPS por cima do Google Maps. Toque nela para mostrar os botões de mover e fechar.")

        if (s.enabled && !canDraw) {
            Text("Falta a permissão para aparecer sobre outros apps.", color = Amarelo)
            WideOutlined("Dar permissão") { askPermission() }
        }
        if (s.enabled && !SenderService.isRunning && !ReceiverService.isRunning) {
            Muted("Ele aparece quando o envio (celular) ou a recepção (tablet) estiver ligado.")
        }

        // Prévia ao vivo
        val demo by androidx.compose.runtime.produceState(0f) {
            var t = 0.0
            while (true) {
                t += 0.06
                value = (60 + 55 * sin(t)).toFloat().coerceAtLeast(0f)
                delay(120)
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val previewDp = s.sizeDp.coerceAtMost(200)
            AndroidView(
                factory = { SpeedometerView(it) },
                update = { v ->
                    v.style = s.style
                    v.theme = s.theme
                    v.maxKmh = s.maxKmh
                    v.accent = s.accent
                    v.alpha = s.opacity / 100f
                    v.setSpeed(demo)
                },
                modifier = Modifier.size(previewDp.dp),
            )
        }

        Text("Modelo", fontWeight = FontWeight.SemiBold)
        Choice(
            listOf(Prefs.STYLE_DIGITAL to "Digital", Prefs.STYLE_RING to "Anel", Prefs.STYLE_ANALOG to "Analógico"),
            s.style,
        ) { save(s.copy(style = it)) }
        Choice(
            listOf(Prefs.STYLE_SPORT to "Esportivo", Prefs.STYLE_NEON to "Neon", Prefs.STYLE_CHROME to "Cromado"),
            s.style,
        ) { save(s.copy(style = it)) }

        Text("Fundo", fontWeight = FontWeight.SemiBold)
        Choice(
            listOf(Prefs.THEME_LIGHT to "Claro", Prefs.THEME_DARK to "Escuro", Prefs.THEME_BLUE to "Colorido"),
            s.theme,
        ) { save(s.copy(theme = it)) }

        Text("Cor de destaque", fontWeight = FontWeight.SemiBold)
        AccentPicker(s.accent, onPreview = { s = s.copy(accent = it) }) { save(s.copy(accent = it)) }

        if (s.style != Prefs.STYLE_DIGITAL) {  // anel, analógico, esportivo, neon e cromado usam a escala
            Text("Velocidade máxima do mostrador", fontWeight = FontWeight.SemiBold)
            Choice(
                listOf("120" to "120", "160" to "160", "200" to "200", "240" to "240"),
                s.maxKmh.toString(),
            ) { save(s.copy(maxKmh = it.toInt())) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tamanho", fontWeight = FontWeight.SemiBold)
            Muted("${s.sizeDp} dp")
        }
        Slider(
            value = s.sizeDp.toFloat(),
            onValueChange = { s = s.copy(sizeDp = it.roundToInt()) },
            onValueChangeFinished = { save(s) },
            valueRange = 80f..260f,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Transparência", fontWeight = FontWeight.SemiBold)
            Muted("${s.opacity}% visível")
        }
        Slider(
            value = s.opacity.toFloat(),
            onValueChange = { s = s.copy(opacity = it.roundToInt()) },
            onValueChangeFinished = { save(s) },
            valueRange = 30f..100f,
        )

        WideOutlined("Voltar a bolinha para o canto") {
            save(s.copy(x = 40, y = 200), resetPos = true)
            Intents.toast(ctx, "Posição restaurada")
        }
    }
}

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            OutlinedButton(
                onClick = { onPick(value) },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (on) {
                    ButtonDefaults.outlinedButtonColors(containerColor = Azul, contentColor = Color.White)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) { Text(label, maxLines = 1) }
        }
    }
}


private val Swatches = listOf(
    0xFF2F7CF6, 0xFF00BCD4, 0xFF34C77B, 0xFFB5E61D, 0xFFF5B341,
    0xFFFF7A1A, 0xFFE53935, 0xFFFF4FA3, 0xFF9C5CFF, 0xFFFFFFFF,
).map { it.toInt() }

/** Paleta pronta + barra de matiz para qualquer cor. */
@Composable
private fun AccentPicker(color: Int, onPreview: (Int) -> Unit, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Swatches.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { c ->
                    val on = c == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else Color(0x33FFFFFF), CircleShape)
                            .clickable { onPick(c) },
                    )
                }
            }
        }
        // Matiz livre
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
        var hue by remember(color) { mutableStateOf(hsv[0]) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("Personalizada")
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(color)))
        }
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.85f, 1f) })),
        )
        Slider(
            value = hue,
            onValueChange = {
                hue = it
                onPreview(android.graphics.Color.HSVToColor(floatArrayOf(it, 0.85f, 1f)))
            },
            onValueChangeFinished = { onPick(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))) },
            valueRange = 0f..359f,
        )
    }
}

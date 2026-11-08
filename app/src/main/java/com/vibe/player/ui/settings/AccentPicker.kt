package com.vibe.player.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vibe.player.ui.theme.AccentPresets
import com.vibe.player.ui.theme.parseHex
import com.vibe.player.ui.theme.toHex

/** Preset swatches + a "custom" swatch that opens the HSV / hex picker. */
@Composable
fun AccentRow(current: Int, onPick: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val isPreset = AccentPresets.any { it.second == current }
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items(AccentPresets) { (name, argb) -> Swatch(Color(argb), selected = current == argb, label = name) { onPick(argb) } }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(if (!isPreset) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { custom = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isPreset) Box(Modifier.size(22.dp).clip(CircleShape).background(Color(current)))
                    else Icon(Icons.Default.Colorize, "Custom", Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Text("Custom", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (custom) AccentPickerDialog(current, onDismiss = { custom = false }) { onPick(it); custom = false }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = Color.Black.copy(alpha = 0.75f)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AccentPickerDialog(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    var hex by remember { mutableStateOf(argb.toHex()) }
    var hexError by remember { mutableStateOf(false) }

    fun syncHexFromSliders() { hex = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toHex(); hexError = false }

    val transparentTrack = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent, thumbColor = Color.White)
    AlertDialog(
        onDismissRequest = onDismiss,
        // Full-width dialog: the default one leaves the hue strip too short to pick precisely.
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Custom accent") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color(argb)).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        hex, { t -> hex = t; parseHex(t)?.let { p -> val a = FloatArray(3); android.graphics.Color.colorToHSV(p, a); hue = a[0]; sat = a[1]; value = a[2]; hexError = false } ?: run { hexError = true } },
                        label = { Text("Hex") }, singleLine = true, isError = hexError, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                // Tall rainbow strip with the slider laid over it, so the whole strip is the touch target.
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
                    Slider(hue, { hue = it; syncHexFromSliders() }, valueRange = 0f..360f, colors = transparentTrack, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))), Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value)))))))
                    Slider(sat, { sat = it; syncHexFromSliders() }, colors = transparentTrack, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))))))
                    Slider(value, { value = it; syncHexFromSliders() }, colors = transparentTrack, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(argb) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

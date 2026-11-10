package com.netpulse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Sky = Color(0xFF38BDF8)
val Emerald = Color(0xFF34D399)
val Violet = Color(0xFFA78BFA)
val Amber = Color(0xFFFBBF24)
val Rose = Color(0xFFFB7185)
val Slate = Color(0xFF94A3B8)

val NetColors = darkColorScheme(
    primary = Sky,
    secondary = Emerald,
    background = Color(0xFF0B1220),
    surface = Color(0xFF141C2B),
    surfaceVariant = Color(0xFF1F2A3C),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Slate,
)

@Composable
fun Section(title: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            value, Modifier.weight(0.58f), textAlign = TextAlign.End, fontSize = 14.sp,
            fontWeight = FontWeight.Medium, color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/** Simple signal bars 0..4. */
@Composable
fun SignalBars(level: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 4) {
            val on = i < level
            Box(
                Modifier
                    .width(6.dp)
                    .height((8 + i * 5).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) color else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatBox(label: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun signalColor(level: Int): Color = when (level) {
    4 -> Emerald
    3 -> Sky
    2 -> Amber
    1 -> Rose
    else -> Slate
}

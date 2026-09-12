package xyz.zongtech.campuswall

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.isActive

/** A native perspective projection: no HTML, JavaScript or WebView renderer. */
@Composable
fun ParticleHeart() {
    val context = LocalContext.current
    val motion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val points = remember {
        val random = Random(41)
        List(900) {
            val t = random.nextFloat() * 2 * PI
            val r = sqrt(random.nextFloat().toDouble())
            val x = 16 * sin(t).pow(3) * r
            val y = (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) * r
            val z = (random.nextFloat() - .5) * 12 * sqrt(1 - r * r)
            Triple(x, y, z)
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motion) {
        if (motion) {
            val start = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { time = (it - start) / 1_000_000_000f }
            }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val angle = sin(time * .3) * .35
        val pulse = 1 + sin(time * 2.1) * .035
        points
            .sortedBy { it.third }
            .forEach { (x, y, z) ->
                val rotatedX = x * cos(angle) + z * sin(angle)
                val depth = -x * sin(angle) + z * cos(angle)
                val perspective = 65 / (65 - depth)
                val scale = size.height / 37 * pulse
                drawCircle(
                    Color(0xFFC24F73)
                        .copy(alpha = ((depth + 12) / 28).toFloat().coerceIn(.2f, .9f)),
                    radius = (1.0 + perspective).toFloat(),
                    center =
                        Offset(
                            (size.width / 2 + rotatedX * scale * perspective).toFloat(),
                            (size.height / 2 - y * scale * perspective).toFloat(),
                        ),
                )
            }
    }
}

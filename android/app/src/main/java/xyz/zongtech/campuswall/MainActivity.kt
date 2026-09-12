package xyz.zongtech.campuswall

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val model: WallModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receive(intent)
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(this)
                .okHttpClient(model.api.client)
                .components { add(coil.decode.SvgDecoder.Factory()) }
                .build()
        )
        setContent {
            val dark =
                when (model.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
            val colors =
                if (Build.VERSION.SDK_INT >= 31 && model.dynamicColor) {
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                } else if (dark)
                    darkColorScheme(primary = Color(0xFF9DD4B5), secondary = Color(0xFFB8CCBD))
                else
                    lightColorScheme(
                        primary = Color(0xFF285C49),
                        secondary = Color(0xFF516457),
                        tertiary = Color(0xFF8E4A60),
                    )
            MaterialTheme(colorScheme = colors) { CampusWall(model, intent?.data) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        if (intent?.getBooleanExtra("open_notifications", false) == true)
            model.openNotifications = true
        val uri = intent?.data ?: return
        if (
            uri.scheme == "https" &&
                uri.host == "wall.zongtech.xyz" &&
                uri.path?.startsWith("/wall/message/") == true
        )
            uri.lastPathSegment
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let { model.pendingMessage = it.toString() }
        if (
            uri.scheme == "campuswall" &&
                uri.host == "captcha" &&
                model.captchaState.isNotEmpty() &&
                uri.getQueryParameter("state") == model.captchaState
        ) {
            model.captchaToken = uri.getQueryParameter("token").orEmpty()
            model.captchaState = ""
        }
    }
}

package xyz.zongtech.campuswall

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

class WallModel(application: Application) : AndroidViewModel(application) {
    val api = WallApi(application)
    var user by mutableStateOf<JSONObject?>(null)
    var community by mutableStateOf(JSONObject())
    var error by mutableStateOf<String?>(null)
    var revision by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var captchaToken by mutableStateOf("")
    var captchaState: String = ""
    val ownedPosts = mutableStateListOf<String>()
    var pendingMessage by mutableStateOf<String?>(null)
    var openNotifications by mutableStateOf(false)
    private val preferences = application.getSharedPreferences("preferences", 0)
    var themeMode by mutableStateOf(preferences.getString("theme", "system") ?: "system")
    var dynamicColor by mutableStateOf(preferences.getBoolean("dynamic_color", true))

    fun setAppearance(theme: String = themeMode, dynamic: Boolean = dynamicColor) {
        themeMode = theme
        dynamicColor = dynamic
        preferences.edit().putString("theme", theme).putBoolean("dynamic_color", dynamic).apply()
    }

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            try {
                user = api.request("/api/user/me").optJSONObject("user")
            } catch (_: Exception) {
                user = null
            }
            try {
                community =
                    api.request("/api/community/config").optJSONObject("community") ?: JSONObject()
            } catch (_: Exception) {}
        }
    }

    fun perform(success: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
                revision++
                success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "网络连接失败"
            } finally {
                busy = false
            }
        }
    }

    fun can(capability: String) = user?.strings("capabilities")?.contains(capability) == true
}

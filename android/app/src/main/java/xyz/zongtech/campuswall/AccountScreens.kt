package xyz.zongtech.campuswall

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.json.JSONObject

@Composable
fun LoginScreen(model: WallModel, back: () -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var admin by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var captcha by remember { mutableStateOf(JSONObject()) }
    var result by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            captcha =
                model.api.request("/api/user/captcha/config").optJSONObject("captcha")
                    ?: JSONObject()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            model.error = e.message
        }
    }
    val action = if (register) "register" else if (admin) "admin_login" else "login"
    val captchaRequired =
        captcha.optBoolean("enabled") &&
            (captcha.optJSONObject("protected_actions")?.optBoolean(action, true) ?: true)
    LaunchedEffect(action) { model.captchaToken = "" }
    Column {
        PageTitle(if (register) "加入校园墙" else "欢迎回来", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("观澜中学", style = MaterialTheme.typography.headlineLarge)
            Text("与网页共用账号，校园日常随身看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                account,
                { account = it },
                Modifier.fillMaxWidth(),
                label = { Text(if (admin) "管理账号" else "学号") },
                singleLine = true,
            )
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            if (register) {
                OutlinedTextField(
                    nickname,
                    { nickname = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("昵称") },
                )
                OutlinedTextField(
                    email,
                    { email = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("邮箱") },
                )
            }
            if (captchaRequired)
                OutlinedButton(
                    onClick = {
                        model.captchaState = UUID.randomUUID().toString()
                        CustomTabsIntent.Builder()
                            .build()
                            .launchUrl(
                                context,
                                Uri.parse(
                                    "https://wall.zongtech.xyz/native-captcha.html?action=$action&state=${model.captchaState}"
                                ),
                            )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (model.captchaToken.isEmpty()) "完成人机验证" else "验证已完成")
                }
            Button(
                onClick = {
                    model.perform {
                        val verificationToken = model.captchaToken
                        model.captchaToken = ""
                        val r =
                            model.api.request(
                                if (admin && !register) "/api/admin/login" else "/api/user/$action",
                                "POST",
                                fields =
                                    mapOf(
                                        "student_id" to account,
                                        "username" to account,
                                        "password" to password,
                                        "nickname" to nickname,
                                        "email" to email,
                                        "email_notify" to "true",
                                        "captcha_token" to verificationToken,
                                    ),
                            )
                        model.captchaToken = ""
                        password = ""
                        if (register) {
                            result = r.s("message", "注册已提交，等待审核")
                            register = false
                        } else {
                            model.refreshSession()
                            back()
                        }
                    }
                },
                enabled =
                    !model.busy &&
                        account.isNotBlank() &&
                        password.isNotBlank() &&
                        (!captchaRequired || model.captchaToken.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (register) "提交注册" else "登录")
            }
            if (result.isNotEmpty()) Text(result)
            TextButton(
                onClick = {
                    register = !register
                    admin = false
                }
            ) {
                Text(if (register) "已有账号，去登录" else "还没有账号？注册")
            }
            if (!register)
                TextButton(onClick = { admin = !admin }) { Text(if (admin) "学生登录" else "管理账号登录") }
        }
    }
}

@Composable
fun MeScreen(model: WallModel, go: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        PageTitle("我的")
        Surface(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    model.user?.s("nickname") ?: "你好，同学",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(model.user?.s("bio") ?: "登录后收藏、评论和分享校园生活")
                if (model.user == null) Button(onClick = { go("login") }) { Text("登录 / 注册") }
            }
        }
        listOf(
                "posts" to "我的发布",
                "comments" to "我的评论",
                "saved" to "我的收藏",
                "settings" to "账号与通知设置",
                "help" to "公告、社区规则与反馈",
            )
            .forEach { (path, title) ->
                ListItem(
                    headlineContent = { Text(title) },
                    modifier =
                        Modifier.clickable {
                            go(if (model.user == null && path != "help") "login" else path)
                        },
                )
            }
        if (model.user?.strings("capabilities")?.isNotEmpty() == true)
            ListItem(
                headlineContent = { Text("管理中心") },
                modifier = Modifier.clickable { go("admin") },
            )
        if (model.user != null)
            TextButton(
                onClick = {
                    model.perform {
                        model.api.request("/api/user/logout", "POST")
                        model.api.cookies.clear()
                        model.user = null
                        model.ownedPosts.clear()
                        NotificationWorker.disable(model.api.context)
                    }
                }
            ) {
                Text("退出登录")
            }
    }
}

@Composable
fun AccountScreen(model: WallModel, back: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(model.user?.s("nickname").orEmpty()) }
    var bio by rememberSaveable { mutableStateOf(model.user?.s("bio").orEmpty()) }
    var gender by rememberSaveable { mutableStateOf(model.user?.s("gender", "0") ?: "0") }
    var email by rememberSaveable { mutableStateOf("") }
    var old by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    var notifications by remember {
        mutableStateOf(
            context.getSharedPreferences("preferences", 0).getBoolean("notifications", false)
        )
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                notifications = true
                NotificationWorker.enable(context)
            }
        }
    val avatar =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                model.perform {
                    model.api.upload(uri, true)
                    model.refreshSession()
                }
        }
    Column {
        PageTitle("账号与设置", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, t) ->
                    FilterChip(
                        model.themeMode == v,
                        { model.setAppearance(theme = v) },
                        label = { Text(t) },
                    )
                }
            }
            Row {
                Text("使用系统动态配色", Modifier.weight(1f))
                Switch(model.dynamicColor, { model.setAppearance(dynamic = it) })
            }
            HorizontalDivider()
            OutlinedButton(onClick = { avatar.launch("image/*") }) { Text("更换头像") }
            OutlinedTextField(
                nickname,
                { nickname = it },
                Modifier.fillMaxWidth(),
                label = { Text("昵称") },
            )
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("个人简介") })
            Row {
                listOf("0" to "未设置", "1" to "男", "2" to "女").forEach { (v, t) ->
                    FilterChip(
                        selected = gender == v,
                        onClick = { gender = v },
                        label = { Text(t) },
                    )
                }
            }
            Button(
                onClick = {
                    model.perform {
                        model.api.request(
                            "/api/user/me/profile",
                            "PUT",
                            fields = mapOf("nickname" to nickname, "bio" to bio, "gender" to gender),
                        )
                        model.refreshSession()
                    }
                }
            ) {
                Text("保存资料")
            }
            HorizontalDivider()
            Text("邮箱", style = MaterialTheme.typography.titleMedium)
            Text(model.user?.s("email", "尚未绑定").orEmpty())
            OutlinedTextField(
                email,
                { email = it },
                Modifier.fillMaxWidth(),
                label = { Text("绑定或更换邮箱") },
            )
            OutlinedButton(
                onClick = {
                    model.perform {
                        model.api.request(
                            "/api/user/me/email",
                            "POST",
                            fields = mapOf("email" to email),
                        )
                        model.error = "验证邮件已发送，请前往邮箱完成验证"
                    }
                }
            ) {
                Text("发送验证邮件")
            }
            Row {
                Text("邮件通知", Modifier.weight(1f))
                Switch(
                    checked = model.user?.optBoolean("email_notify") == true,
                    onCheckedChange = { v ->
                        model.perform {
                            model.api.request(
                                "/api/user/me/email/notify",
                                "POST",
                                fields = mapOf("email_notify" to "$v"),
                            )
                            model.refreshSession()
                        }
                    },
                )
            }
            Row {
                Text("系统通知", Modifier.weight(1f))
                Switch(
                    notifications,
                    { v ->
                        if (v) {
                            if (android.os.Build.VERSION.SDK_INT >= 33)
                                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            else {
                                notifications = true
                                NotificationWorker.enable(context)
                            }
                        } else {
                            notifications = false
                            NotificationWorker.disable(context)
                        }
                    },
                )
            }
            Text("后台定期检查新消息；受系统省电策略影响，可能延迟。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("修改密码", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                old,
                { old = it },
                Modifier.fillMaxWidth(),
                label = { Text("当前密码") },
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth(),
                label = { Text("新密码") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    model.perform {
                        model.api.request(
                            "/api/user/me/password",
                            "POST",
                            fields = mapOf("current_password" to old, "new_password" to password),
                        )
                        old = ""
                        password = ""
                        model.refreshSession()
                    }
                }
            ) {
                Text("更新密码")
            }
        }
    }
}

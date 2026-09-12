package xyz.zongtech.campuswall

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun NotificationSettings(model: WallModel, response: JSONObject) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        response.objects("providers").forEach { provider ->
            var enabled by
                remember(provider.toString()) { mutableStateOf(provider.optBoolean("enabled")) }
            var webhook by remember { mutableStateOf("") }
            var secret by remember { mutableStateOf("") }
            var confirmClear by remember { mutableStateOf(false) }
            val path = "/api/admin/settings/notifications/${provider.s("id")}"
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(provider.s("label"), style = MaterialTheme.typography.titleMedium)
                    Text(if (provider.optBoolean("configured")) "已配置" else "尚未配置")
                    Row {
                        Text("启用", Modifier.weight(1f))
                        Switch(
                            enabled,
                            { enabled = it },
                            enabled = model.can("settings.notifications.update"),
                        )
                    }
                    OutlinedTextField(
                        webhook,
                        { webhook = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("新的机器人地址（留空保留）") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (provider.optBoolean("supports_signing_secret"))
                        OutlinedTextField(
                            secret,
                            { secret = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("新的签名密钥（留空保留）") },
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    Row {
                        if (model.can("settings.notifications.update"))
                            Button(
                                onClick = {
                                    model.perform {
                                        model.api.request(
                                            path,
                                            "PUT",
                                            json =
                                                JSONObject()
                                                    .put("enabled", enabled)
                                                    .put("webhook", webhook)
                                                    .put("secret", secret),
                                        )
                                        webhook = ""
                                        secret = ""
                                    }
                                }
                            ) {
                                Text("保存")
                            }
                        if (model.can("settings.notifications.test"))
                            TextButton(
                                onClick = {
                                    model.perform {
                                        model.api.request("$path/test", "POST")
                                        model.error = "测试消息已发送"
                                    }
                                }
                            ) {
                                Text("发送测试")
                            }
                        if (model.can("settings.notifications.update"))
                            TextButton(onClick = { confirmClear = true }) { Text("清除") }
                    }
                }
            }
            if (confirmClear)
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("清除该通知渠道？") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.perform {
                                    model.api.request(path, "DELETE")
                                    confirmClear = false
                                }
                            }
                        ) {
                            Text("清除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text("取消") }
                    },
                )
        }
    }
}

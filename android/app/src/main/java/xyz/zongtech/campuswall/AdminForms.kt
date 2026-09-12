package xyz.zongtech.campuswall

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject

data class FormField(
    val key: String,
    val label: String,
    val initial: String = "",
    val choices: List<Pair<String, String>> = emptyList(),
    val multiline: Boolean = false,
    val secret: Boolean = false,
)

data class AdminForm(
    val title: String,
    val path: String,
    val fields: List<FormField>,
    val method: String = "POST",
    val json: Boolean = false,
)

@Composable
fun AdminFormDialog(model: WallModel, form: AdminForm, close: () -> Unit) {
    var values by remember(form) { mutableStateOf(form.fields.associate { it.key to it.initial }) }
    Dialog(onDismissRequest = { if (!model.busy) close() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(form.title, style = MaterialTheme.typography.titleLarge)
                form.fields.forEach { field ->
                    if(field.key in listOf("muted_until","publish_at")) DateTimeField(field.label,values[field.key].orEmpty()){values=values+(field.key to it)}
                    else if (field.choices.isNotEmpty()) {
                        Text(field.label, style = MaterialTheme.typography.labelLarge)
                        Column {
                            field.choices.forEach { (v, t) ->
                                Row(Modifier.clickable { values = values + (field.key to v) }) {
                                    RadioButton(
                                        values[field.key] == v,
                                        { values = values + (field.key to v) },
                                    )
                                    Text(t, Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    } else
                        OutlinedTextField(
                            values[field.key].orEmpty(),
                            { values = values + (field.key to it) },
                            Modifier.fillMaxWidth(),
                            label = { Text(field.label) },
                            minLines = if (field.multiline) 3 else 1,
                            visualTransformation =
                                if (field.secret) PasswordVisualTransformation()
                                else VisualTransformation.None,
                        )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = close, enabled = !model.busy) { Text("取消") }
                    Button(
                        onClick = {
                            model.perform(close) {
                                if (form.json)
                                    model.api.request(
                                        form.path,
                                        form.method,
                                        json = JSONObject(values),
                                    )
                                else model.api.request(form.path, form.method, fields = values)
                            }
                        },
                        enabled = !model.busy,
                    ) {
                        Text("确认保存")
                    }
                }
            }
        }
    }
}

val roles =
    listOf("user" to "普通用户", "reviewer" to "审核员", "admin" to "管理员", "super_admin" to "超级管理员")

fun noticeForm(row: JSONObject? = null) =
    AdminForm(
        if (row == null) "发布公告" else "编辑公告",
        "/api/admin/notice" + (row?.let { "/${it.s("id")}" } ?: ""),
        listOf(
            FormField("title", "标题", row?.s("title").orEmpty()),
            FormField("summary", "摘要", row?.s("summary").orEmpty()),
            FormField("content", "正文", row?.s("content").orEmpty(), multiline = true),
            FormField(
                "priority",
                "优先级",
                row?.s("priority") ?: "normal",
                listOf("normal" to "普通", "important" to "重要", "urgent" to "紧急"),
            ),
            FormField(
                "status",
                "发布状态",
                row?.s("status") ?: "draft",
                listOf("draft" to "草稿", "published" to "公开", "archived" to "归档"),
            ),
            FormField("publish_at", "发布时间（可留空）", row?.s("publish_at").orEmpty()),
        ),
        if (row == null) "POST" else "PUT",
    )

@Composable
fun UserAdminActions(
    model: WallModel,
    row: JSONObject,
    show: (AdminForm) -> Unit,
    permissions: () -> Unit,
) {
    val base = "/api/admin/users/${row.s("id")}"
    val id = row.s("id")
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            if (model.can("users.profile.update"))
                TextButton(
                    onClick = {
                        show(
                            AdminForm(
                                "编辑资料",
                                base,
                                listOf(
                                    FormField("real_name", "真实姓名", row.s("real_name")),
                                    FormField("nickname", "昵称", row.s("nickname")),
                                    FormField(
                                        "gender",
                                        "性别",
                                        row.s("gender", "0"),
                                        listOf("0" to "未设置", "1" to "男", "2" to "女"),
                                    ),
                                    FormField("bio", "简介", row.s("bio"), multiline = true),
                                    FormField(
                                        "status",
                                        "账号状态",
                                        row.s("status", "active"),
                                        listOf("active" to "正常", "disabled" to "停用"),
                                    ),
                                ),
                                "PUT",
                            )
                        )
                    }
                ) {
                    Text("资料")
                }
            if (model.can("users.role.assign"))
                TextButton(
                    onClick = {
                        show(
                            AdminForm(
                                "调整角色",
                                "$base/role",
                                listOf(FormField("role", "角色", row.s("role"), roles)),
                                "PUT",
                                true,
                            )
                        )
                    }
                ) {
                    Text("角色")
                }
            if (model.can("users.permissions.assign"))
                TextButton(onClick = permissions) { Text("权限") }
            if (model.can("users.mute"))
                TextButton(
                    onClick = {
                        show(
                            AdminForm(
                                "禁言账号",
                                "$base/mute",
                                listOf(
                                    FormField("muted_until", "禁言截止时间"),
                                    FormField("reason", "原因", multiline = true),
                                ),
                            )
                        )
                    }
                ) {
                    Text("禁言")
                }
            if (model.can("users.password.reset"))
                TextButton(
                    onClick = {
                        show(
                            AdminForm(
                                "重置密码",
                                "$base/reset_password",
                                listOf(FormField("password", "新密码", secret = true)),
                            )
                        )
                    }
                ) {
                    Text("重置密码")
                }
        }
    }
}

@Composable
fun PermissionsDialog(model: WallModel, id: String, close: () -> Unit) {
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var catalog by remember { mutableStateOf(emptyList<JSONObject>()) }
    var values by remember { mutableStateOf(emptyMap<String, String>()) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        try {
            catalog = model.api.request("/api/admin/permissions").objects("permissions")
            state = model.api.request("/api/admin/users/$id/permissions")
            val overrides = state?.optJSONObject("overrides")
            values =
                catalog.associate {
                    val k = it.s("key")
                    k to
                        when (k) {
                            in overrides?.strings("allow").orEmpty() -> "allow"
                            in overrides?.strings("deny").orEmpty() -> "deny"
                            else -> "inherit"
                        }
                }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        }
    }
    Dialog(onDismissRequest = close) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp)) {
                Text("个人权限", style = MaterialTheme.typography.titleLarge)
                error?.let { Text(it) }
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())) {
                    catalog.forEach { p ->
                        val k = p.s("key")
                        Text(
                            p.s("label", p.s("name", k)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(p.s("description"), style = MaterialTheme.typography.bodySmall)
                        Row {
                            listOf("inherit" to "继承", "allow" to "允许", "deny" to "禁止").forEach {
                                (v, t) ->
                                FilterChip(
                                    values[k] == v,
                                    { values = values + (k to v) },
                                    label = { Text(t) },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    reason,
                    { reason = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("变更原因") },
                )
                Row {
                    TextButton(onClick = close) { Text("取消") }
                    Button(
                        onClick = {
                            model.perform(close) {
                                model.api.request(
                                    "/api/admin/users/$id/permissions",
                                    "PUT",
                                    json =
                                        JSONObject()
                                            .put(
                                                "allow",
                                                JSONArray(
                                                    values
                                                        .filterValues { it == "allow" }
                                                        .keys
                                                        .toList()
                                                ),
                                            )
                                            .put(
                                                "deny",
                                                JSONArray(
                                                    values
                                                        .filterValues { it == "deny" }
                                                        .keys
                                                        .toList()
                                                ),
                                            )
                                            .put(
                                                "permission_version",
                                                state!!.opt("permission_version"),
                                            )
                                            .put("reason", reason)
                                            .put("confirm", "REPLACE_PERMISSION_OVERRIDES"),
                                )
                            }
                        },
                        enabled = state != null && reason.isNotBlank() && !model.busy,
                    ) {
                        Text("保存权限")
                    }
                }
            }
        }
    }
}

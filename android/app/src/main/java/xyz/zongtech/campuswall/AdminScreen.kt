package xyz.zongtech.campuswall

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private data class AdminSection(
    val title: String,
    val endpoint: String,
    val capability: String,
    val key: String,
)

private val sections =
    listOf(
        AdminSection("概览", "/dashboard/stats", "dashboard.read", "stats"),
        AdminSection("动态审核", "/api/messages", "content.queue.read", "messages"),
        AdminSection("评论管理", "/comments", "content.comment.read", "comments"),
        AdminSection("用户管理", "/users", "users.read", "users"),
        AdminSection("公告", "/notice", "notice.read", "notices"),
        AdminSection("举报", "/report", "report.read", "reports"),
        AdminSection("举报处理记录", "/reports/history", "report.history.read", "items"),
        AdminSection("反馈", "/feedback", "feedback.read", "tickets"),
        AdminSection("回收站", "/trash", "content.trash.read", "messages"),
        AdminSection("审计记录", "/audit", "audit.read", "events"),
        AdminSection("社区设置", "/settings/community", "settings.read", "community"),
        AdminSection("人机验证", "/settings/captcha", "settings.read", "captcha"),
        AdminSection("内容审核设置", "/settings/ai", "settings.ai.read", "ai"),
        AdminSection("通知集成", "/settings/notifications", "settings.notifications.read", "providers"),
        AdminSection("管理日志", "/admin_log", "logs.legacy_admin.read", "logs"),
        AdminSection("错误日志", "/log", "logs.error.read", "logs"),
    )

@Composable
fun AdminScreen(model: WallModel, back: () -> Unit) {
    val allowed = sections.filter { model.can(it.capability) }
    var selected by remember { mutableStateOf<AdminSection?>(null) }
    var response by remember { mutableStateOf(JSONObject()) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var search by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var form by remember { mutableStateOf<AdminForm?>(null) }
    var permissionUser by remember { mutableStateOf<String?>(null) }
    var status by
        remember(selected) {
            mutableStateOf(if (selected?.endpoint == "/api/messages") "pending" else "all")
        }
    var scope by remember(selected) { mutableStateOf("all") }
    var checked by remember(selected, page, status, scope) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(selected, page, search, status, scope, model.revision, refresh) {
        selected?.let { s ->
            loading = true
            try {
                response =
                    model.api.request(
                        "/api/admin${s.endpoint}?page=$page&page_size=20&search=${search.pathSegment()}&q=${search.pathSegment()}&status=$status&scope=$scope"
                    )
                error = null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message
            } finally {
                loading = false
            }
        }
    }
    Column {
        PageTitle(
            selected?.title ?: "管理中心",
            {
                if (selected == null) back()
                else {
                    selected = null
                    page = 1
                    search = ""
                }
            },
        )
        if (selected == null) {
            LazyColumn {
                items(allowed) { s ->
                    ListItem(
                        headlineContent = { Text(s.title) },
                        modifier = Modifier.clickable { selected = s },
                    )
                }
                if (allowed.isEmpty()) item { Status("当前账号没有管理权限") }
            }
        } else {
            val s = selected!!
            val statuses =
                when (s.endpoint) {
                    "/api/messages" ->
                        listOf(
                            "pending" to "待审核",
                            "approved" to "已审核",
                            "awaiting_publication" to "待发布",
                        ) +
                            if (model.can("content.message.hide"))
                                listOf("all" to "全部", "visible" to "公开", "hidden" to "下架")
                            else emptyList()
                    "/comments" -> listOf("all" to "全部", "visible" to "公开", "hidden" to "下架")
                    "/users" ->
                        listOf(
                            "all" to "全部",
                            "active" to "正常",
                            "pending" to "待注册审核",
                            "disabled" to "停用",
                        )
                    "/feedback" ->
                        listOf(
                            "all" to "全部",
                            "pending" to "待处理",
                            "in_progress" to "处理中",
                            "resolved" to "已解决",
                            "closed" to "已关闭",
                        )
                    else -> emptyList()
                }
            Row(
                Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                statuses.forEach { (v, t) ->
                    FilterChip(
                        status == v,
                        {
                            status = v
                            page = 1
                        },
                        label = { Text(t) },
                    )
                }
            }
            if (s.endpoint == "/api/messages")
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("all" to "所有内容", "posts" to "校园动态", "confessions" to "表白墙").forEach {
                        (v, t) ->
                        FilterChip(
                            scope == v,
                            {
                                scope = v
                                page = 1
                            },
                            label = { Text(t) },
                        )
                    }
                }
            OutlinedTextField(
                search,
                {
                    search = it
                    page = 1
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("搜索") },
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Status(it) { refresh++ } }
            val rows =
                if (s.endpoint == "/report")
                    response
                        .optJSONObject("reports")
                        ?.let { reports ->
                            reports
                                .keys()
                                .asSequence()
                                .flatMap { id ->
                                    reports.objects(id).map {
                                        JSONObject(it.toString()).put("message_id", id)
                                    }
                                }
                                .toList()
                        }
                        .orEmpty()
                else
                    response
                        .objects(s.key)
                        .ifEmpty { response.objects("data") }
                        .ifEmpty { response.objects("items") }
                        .ifEmpty { response.objects("content") }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (s.endpoint in listOf("/api/messages", "/comments", "/trash"))
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(
                                onClick = {
                                    checked =
                                        if (checked.size == rows.size) emptySet()
                                        else rows.map { it.toString() }.toSet()
                                }
                            ) {
                                Text(
                                    if (checked.size == rows.size && rows.isNotEmpty()) "取消选择"
                                    else "选择本页"
                                )
                            }
                            val operations =
                                when (s.endpoint) {
                                    "/api/messages" ->
                                        listOf(
                                            "approve" to "批量通过",
                                            "return" to "退回待审",
                                            "hide" to "批量下架",
                                            "restore" to "批量上架",
                                        )
                                    "/comments" -> listOf("hide" to "批量下架", "restore" to "批量上架")
                                    else -> listOf("restore" to "批量恢复", "purge" to "永久删除")
                                }
                            operations.forEach { (operation, label) ->
                                TextButton(
                                    enabled = checked.isNotEmpty() && !model.busy,
                                    onClick = {
                                        val chosen = rows.filter { it.toString() in checked }
                                        action =
                                            "$label ${chosen.size} 项" to
                                                {
                                                    val payload =
                                                        JSONObject().put("action", operation)
                                                    val endpoint =
                                                        when (s.endpoint) {
                                                            "/api/messages" -> {
                                                                payload.put(
                                                                    "message_ids",
                                                                    org.json.JSONArray(
                                                                        chosen.map {
                                                                            it.optLong("id")
                                                                        }
                                                                    ),
                                                                )
                                                                "/messages/bulk-moderation"
                                                            }
                                                            "/comments" -> {
                                                                payload.put(
                                                                    "targets",
                                                                    org.json.JSONArray(
                                                                        chosen.map {
                                                                            JSONObject()
                                                                                .put(
                                                                                    "message_id",
                                                                                    it.opt(
                                                                                        "message_id"
                                                                                    ),
                                                                                )
                                                                                .put(
                                                                                    "comment_id",
                                                                                    it.s("id"),
                                                                                )
                                                                        }
                                                                    ),
                                                                )
                                                                "/comments/bulk-moderation"
                                                            }
                                                            else -> {
                                                                payload
                                                                    .put(
                                                                        "confirm",
                                                                        if (operation == "purge")
                                                                            "PURGE"
                                                                        else "",
                                                                    )
                                                                    .put(
                                                                        "targets",
                                                                        org.json.JSONArray(
                                                                            chosen.map {
                                                                                JSONObject()
                                                                                    .put(
                                                                                        "type",
                                                                                        it.s(
                                                                                            "type",
                                                                                            "message",
                                                                                        ),
                                                                                    )
                                                                                    .put(
                                                                                        "message_id",
                                                                                        it.opt(
                                                                                            "message_id"
                                                                                        )
                                                                                            ?: it
                                                                                                .opt(
                                                                                                    "id"
                                                                                                ),
                                                                                    )
                                                                                    .put(
                                                                                        "comment_id",
                                                                                        it.s(
                                                                                            "comment_id",
                                                                                            it.s(
                                                                                                "id"
                                                                                            ),
                                                                                        ),
                                                                                    )
                                                                            }
                                                                        ),
                                                                    )
                                                                "/trash/bulk"
                                                            }
                                                        }
                                                    val result =
                                                        model.api.request(
                                                            "/api/admin$endpoint",
                                                            "POST",
                                                            json = payload,
                                                        )
                                                    val failures =
                                                        result.objects("results").count {
                                                            !it.optBoolean("success")
                                                        }
                                                    checked = emptySet()
                                                    if (failures > 0)
                                                        model.error = "$failures 项未完成，请检查权限或内容状态"
                                                }
                                    },
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                if (s.endpoint == "/notice" && model.can("notice.create"))
                    item { Button(onClick = { form = noticeForm() }) { Text("创建公告") } }
                if (s.endpoint == "/users" && model.can("users.role.assign"))
                    item {
                        Button(
                            onClick = {
                                form =
                                    AdminForm(
                                        "新建管理账号",
                                        "/api/admin/users",
                                        listOf(
                                            FormField("username", "账号"),
                                            FormField("nickname", "昵称"),
                                            FormField("password", "初始密码", secret = true),
                                            FormField(
                                                "role",
                                                "角色",
                                                "reviewer",
                                                roles.filter { it.first != "user" },
                                            ),
                                        ),
                                        json = true,
                                    )
                            }
                        ) {
                            Text("新建管理账号")
                        }
                    }
                if (s.endpoint == "/settings/notifications")
                    item {
                        NotificationSettings(model, response.optJSONObject("settings") ?: response)
                    }
                else if (s.endpoint.startsWith("/settings/"))
                    item {
                        SettingsEditor(
                            model,
                            s.endpoint,
                            response.optJSONObject(s.key)
                                ?: response.optJSONObject("settings")
                                ?: response,
                        )
                    }
                if (s.endpoint == "/dashboard/stats")
                    item { ReadableObject(response.optJSONObject("stats") ?: response) }
                items(response.strings("log_content")) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
                items(rows) { row ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (s.endpoint in listOf("/api/messages", "/comments", "/trash"))
                                Checkbox(
                                    row.toString() in checked,
                                    { v ->
                                        checked =
                                            if (v) checked + row.toString()
                                            else checked - row.toString()
                                    },
                                )
                            val title =
                                row.s(
                                    "text",
                                    row.s(
                                        "title",
                                        row.s(
                                            "nickname",
                                            row.s("summary", row.s("action", "记录 ${row.s("id")}")),
                                        ),
                                    ),
                                )
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(row.s("status", row.s("moderation_status", row.s("timestamp"))))
                            if (s.endpoint == "/api/messages") {
                                Text(row.s("review_status"))
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf("approve" to "通过", "return" to "退回待审").forEach { (v, t)
                                        ->
                                        TextButton(
                                            onClick = {
                                                action =
                                                    t to
                                                        {
                                                            model.api.request(
                                                                "/api/admin/messages/${row.s("id")}/review",
                                                                "POST",
                                                                json = JSONObject().put("action", v),
                                                            )
                                                        }
                                            }
                                        ) {
                                            Text(t)
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "删除动态" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/delete_message/${row.s("id")}",
                                                            "POST",
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("删除")
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "切换置顶" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/messages/${row.s("id")}/moderation",
                                                            "POST",
                                                            json =
                                                                JSONObject()
                                                                    .put(
                                                                        "pinned",
                                                                        !row.optBoolean("pinned"),
                                                                    ),
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("置顶")
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "切换精华" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/messages/${row.s("id")}/moderation",
                                                            "POST",
                                                            json =
                                                                JSONObject()
                                                                    .put(
                                                                        "featured",
                                                                        !row.optBoolean("featured"),
                                                                    ),
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("精华")
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "切换下架状态" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/messages/${row.s("id")}/moderation",
                                                            "POST",
                                                            json =
                                                                JSONObject()
                                                                    .put(
                                                                        "hidden",
                                                                        row.s(
                                                                            "moderation_status"
                                                                        ) != "hidden",
                                                                    ),
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("上架 / 下架")
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "修复内容索引" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/repair_message/${row.s("id")}",
                                                            "POST",
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("修复")
                                    }
                                }
                            } else if (s.endpoint == "/comments")
                                Row {
                                    listOf("visible" to "公开", "hidden" to "隐藏").forEach { (v, t) ->
                                        TextButton(
                                            onClick = {
                                                action =
                                                    t to
                                                        {
                                                            model.api.request(
                                                                "/api/admin/comments/${row.s("message_id")}/${row.s("id").pathSegment()}/moderation",
                                                                "POST",
                                                                json =
                                                                    JSONObject()
                                                                        .put(
                                                                            "hidden",
                                                                            v == "hidden",
                                                                        ),
                                                            )
                                                        }
                                            }
                                        ) {
                                            Text(t)
                                        }
                                    }
                                }
                            else if (s.endpoint == "/users") {
                                UserAdminActions(
                                    model,
                                    row,
                                    { form = it },
                                    { permissionUser = row.s("id") },
                                )
                                Text(row.s("role"))
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf(
                                            "registration/approve" to "通过注册",
                                            "registration/reject" to "驳回注册",
                                            "unmute" to "解除禁言",
                                            "disable" to "停用账号",
                                        )
                                        .forEach { (v, t) ->
                                            TextButton(
                                                onClick = {
                                                    action =
                                                        t to
                                                            {
                                                                model.api.request(
                                                                    "/api/admin/users/${row.s("id")}/$v",
                                                                    "POST",
                                                                )
                                                            }
                                                }
                                            ) {
                                                Text(t)
                                            }
                                        }
                                }
                            } else if (s.endpoint == "/notice")
                                Row {
                                    TextButton(onClick = { form = noticeForm(row) }) { Text("编辑") }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "删除公告" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/notice/${row.s("id")}",
                                                            "DELETE",
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("删除")
                                    }
                                }
                            else if (s.endpoint == "/feedback") {
                                Text(row.s("email"))
                                TextButton(
                                    onClick = {
                                        form =
                                            AdminForm(
                                                "处理反馈",
                                                "/api/admin/feedback/${row.s("id")}",
                                                listOf(
                                                    FormField(
                                                        "status",
                                                        "状态",
                                                        row.s("status"),
                                                        listOf(
                                                            "pending" to "待处理",
                                                            "in_progress" to "处理中",
                                                            "resolved" to "已解决",
                                                            "closed" to "已关闭",
                                                        ),
                                                    ),
                                                    FormField(
                                                        "public_reply",
                                                        "公开回复",
                                                        row.s("public_reply"),
                                                        multiline = true,
                                                    ),
                                                    FormField(
                                                        "internal_note",
                                                        "内部备注",
                                                        row.s("internal_note"),
                                                        multiline = true,
                                                    ),
                                                ),
                                                "PUT",
                                                true,
                                            )
                                    }
                                ) {
                                    Text("处理工单")
                                }
                            } else if (s.endpoint == "/report") {
                                Text(row.s("category"))
                                TextButton(
                                    onClick = {
                                        form =
                                            AdminForm(
                                                "处理举报",
                                                "/api/admin/reports/${row.s("message_id")}/${row.s("id")}/resolve",
                                                listOf(
                                                    FormField(
                                                        "action",
                                                        "处理方式",
                                                        "dismiss",
                                                        listOf(
                                                            "dismiss" to "不予处理",
                                                            "delete_message" to "删除动态",
                                                        ) +
                                                            if (row.s("target_type") == "comment")
                                                                listOf("delete_comment" to "删除评论")
                                                            else emptyList(),
                                                    ),
                                                    FormField(
                                                        "public_reply",
                                                        "处理说明",
                                                        multiline = true,
                                                    ),
                                                ),
                                                json = true,
                                            )
                                    }
                                ) {
                                    Text("处理")
                                }
                            } else if (s.endpoint == "/trash")
                                Row {
                                    val target =
                                        if (row.s("type") == "comment")
                                            "comments/${row.s("message_id")}/${row.s("comment_id",row.s("id")).pathSegment()}"
                                        else "messages/${row.s("message_id",row.s("id"))}"
                                    TextButton(
                                        onClick = {
                                            action =
                                                "恢复内容" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/trash/$target/restore",
                                                            "POST",
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("恢复")
                                    }
                                    TextButton(
                                        onClick = {
                                            action =
                                                "永久删除（无法恢复）" to
                                                    {
                                                        model.api.request(
                                                            "/api/admin/trash/$target",
                                                            "DELETE",
                                                            json =
                                                                JSONObject().put("confirm", "PURGE"),
                                                        )
                                                    }
                                        }
                                    ) {
                                        Text("永久删除")
                                    }
                                }
                            else ReadableObject(row)
                        }
                    }
                }
                if (
                    rows.isEmpty() &&
                        !loading &&
                        !s.endpoint.startsWith("/settings/") &&
                        s.endpoint != "/dashboard/stats"
                )
                    item { Status("暂无记录") }
                item { Pager(page, response.optInt("total", rows.size), { page = it }) }
            }
        }
    }
    action?.let { (title, run) ->
        AlertDialog(
            onDismissRequest = { action = null },
            title = { Text("确认$title？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.perform {
                            run()
                            action = null
                        }
                    },
                    enabled = !model.busy,
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } },
        )
    }
    form?.let { AdminFormDialog(model, it) { form = null } }
    permissionUser?.let { PermissionsDialog(model, it) { permissionUser = null } }
}

private val labels =
    mapOf(
        "posting_enabled" to "允许发帖",
        "commenting_enabled" to "允许评论",
        "guest_posting_enabled" to "允许访客发帖",
        "guest_commenting_enabled" to "允许访客评论",
        "require_post_approval" to "发帖需要审核",
        "pause_reason" to "暂停说明",
        "community_rules" to "社区规则",
        "enabled" to "启用",
        "provider" to "服务商",
        "site_key" to "站点公钥",
        "secret_key" to "验证密钥",
        "protect_login" to "保护登录",
        "protect_register" to "保护注册",
        "protect_admin_login" to "保护管理登录",
        "model" to "模型",
        "base_url" to "服务地址",
        "api_key" to "接口密钥",
        "text" to "内容",
        "title" to "标题",
        "status" to "状态",
        "created_at" to "创建时间",
        "total" to "总计",
        "users" to "用户",
        "messages" to "动态",
        "comments" to "评论",
    )

@Composable
private fun ReadableObject(value: JSONObject) {
    value.keys().forEach { k ->
        if (k != "success" && !k.contains("secret") && !k.contains("token")) {
            val v = value.opt(k)
            if (v !is JSONObject && v !is org.json.JSONArray)
                Text(
                    "${labels[k]?:k}：${if(v==JSONObject.NULL)"—" else v}",
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
    }
}

@Composable
private fun SettingsEditor(model: WallModel, path: String, original: JSONObject) {
    var values by remember(original.toString()) { mutableStateOf(JSONObject(original.toString())) }
    var secret by remember { mutableStateOf("") }
    var clearSecret by remember { mutableStateOf(false) }
    val editable =
        when (path) {
            "/settings/community" -> model.can("settings.community.update")
            "/settings/captcha" -> model.can("settings.captcha.update")
            "/settings/ai" -> model.can("settings.ai.update")
            else -> false
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        original.keys().forEach { k ->
            val v = original.opt(k)
            if (k in labels && k != "success") {
                if (v is Boolean)
                    Row {
                        Text(labels[k] ?: k, Modifier.weight(1f))
                        Switch(
                            values.optBoolean(k),
                            { values = JSONObject(values.toString()).put(k, it) },
                            enabled = editable,
                        )
                    }
                else if (v !is JSONObject && v !is org.json.JSONArray)
                    OutlinedTextField(
                        values.s(k),
                        { values = JSONObject(values.toString()).put(k, it) },
                        Modifier.fillMaxWidth(),
                        label = { Text(labels[k] ?: k) },
                        enabled = editable,
                    )
            }
        }
        if (path == "/settings/community" || path == "/settings/captcha") {
            val key = if (path == "/settings/community") "sensitive_words" else "allowed_hostnames"
            var lines by
                remember(original.toString()) {
                    mutableStateOf(original.strings(key).joinToString("\n"))
                }
            OutlinedTextField(
                lines,
                {
                    lines = it
                    values =
                        JSONObject(values.toString())
                            .put(
                                key,
                                org.json.JSONArray(
                                    it.lines()
                                        .map { line -> line.trim() }
                                        .filter { line -> line.isNotEmpty() }
                                ),
                            )
                },
                Modifier.fillMaxWidth(),
                label = { Text(if (key == "sensitive_words") "敏感词，每行一个" else "允许的验证域名，每行一个") },
                minLines = 3,
                enabled = editable,
            )
        }
        if (path == "/settings/captcha" || path == "/settings/ai") {
            OutlinedTextField(
                secret,
                { secret = it },
                Modifier.fillMaxWidth(),
                label = { Text("替换密钥（留空保留原值）") },
                visualTransformation =
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                enabled = editable,
            )
            Row {
                Checkbox(clearSecret, { clearSecret = it }, enabled = editable)
                Text("清除已保存的密钥", Modifier.padding(top = 12.dp))
            }
        }
        Button(
            onClick = {
                model.perform {
                    val payload = JSONObject(values.toString())
                    if (path == "/settings/ai")
                        payload.put("api_key", secret).put("clear_api_key", clearSecret)
                    if (path == "/settings/captcha")
                        payload.put("secret_key", secret).put("clear_secret", clearSecret)
                    model.api.request("/api/admin$path", "PUT", json = payload)
                    secret = ""
                    clearSecret = false
                }
            },
            enabled = !model.busy && editable,
        ) {
            Text("保存设置")
        }
    }
}

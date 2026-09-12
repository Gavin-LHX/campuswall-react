package xyz.zongtech.campuswall

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun TopicsScreen(model: WallModel, go: (String) -> Unit) {
    var topics by remember { mutableStateOf(emptyList<JSONObject>()) }
    var q by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q, model.revision) {
        try {
            topics =
                model.api
                    .request("/api/topics?q=${q.pathSegment()}&start=0&end=200")
                    .objects("data")
            error = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        }
    }
    Column {
        PageTitle("发现话题")
        OutlinedTextField(
            q,
            { q = it },
            Modifier.fillMaxWidth().padding(16.dp),
            label = { Text("搜索话题") },
        )
        error?.let { Status(it) }
        LazyColumn {
            items(topics) { t ->
                ListItem(
                    headlineContent = { Text("# " + t.s("name", t.s("tag"))) },
                    supportingContent = {
                        Text("${t.optInt("count",t.optInt("message_count"))} 条动态")
                    },
                    modifier =
                        Modifier.clickable { go("tag/${t.s("name",t.s("tag")).pathSegment()}") },
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(model: WallModel, id: String, go: (String) -> Unit, back: () -> Unit) {
    var user by remember { mutableStateOf(JSONObject()) }
    LaunchedEffect(id) {
        try {
            user = model.api.request("/api/user/$id/profile").optJSONObject("user") ?: JSONObject()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            model.error = e.message
        }
    }
    Column {
        Text(user.s("bio"), Modifier.padding(16.dp))
        FeedScreen(
            model,
            user.s("nickname", "同学的主页"),
            go,
            path = "/api/user/$id/messages",
            back = back,
        )
    }
}

@Composable
fun LostFoundScreen(model: WallModel, go: (String) -> Unit, back: () -> Unit) {
    var compose by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("lost") }
    var item by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var resolved by rememberSaveable { mutableStateOf(false) }
    if (model.user == null) {
        Column {
            PageTitle("失物招领", back)
            Status("启事含有联系方式，登录后可查看。")
            Button(onClick = { go("login") }) { Text("去登录") }
        }
        return
    }
    Column {
        Row(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = { compose = !compose }) { Text(if (compose) "查看启事" else "发布启事") }
        }
        if (!compose) FeedScreen(model, "失物招领", go, path = "/api/user/lost-found", back = back)
        else {
            PageTitle("发布失物启事", back)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    FilterChip(kind == "lost", { kind = "lost" }, label = { Text("寻物") })
                    FilterChip(kind == "found", { kind = "found" }, label = { Text("招领") })
                }
                OutlinedTextField(
                    item,
                    { item = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("物品") },
                )
                OutlinedTextField(
                    location,
                    { location = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("地点") },
                )
                OutlinedTextField(
                    time,
                    { time = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("时间") },
                )
                OutlinedTextField(
                    details,
                    { details = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("特征与说明") },
                    minLines = 3,
                )
                OutlinedTextField(
                    contact,
                    { contact = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("联系方式") },
                )
                Row {
                    Checkbox(resolved, { resolved = it })
                    Text("已找回", Modifier.padding(top = 12.dp))
                }
                Button(
                    onClick = {
                        model.perform {
                            model.api.request(
                                "/api/user/lost-found",
                                "POST",
                                json =
                                    JSONObject()
                                        .put("kind", kind)
                                        .put("item", item)
                                        .put("location", location)
                                        .put("time", time)
                                        .put("details", details)
                                        .put("contact", contact)
                                        .put("resolved", resolved),
                            )
                            compose = false
                        }
                    },
                    enabled = item.isNotBlank() && location.isNotBlank() && !model.busy,
                ) {
                    Text("发布")
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(model: WallModel, go: (String) -> Unit) {
    var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var clearAll by remember { mutableStateOf(false) }
    LaunchedEffect(page, model.revision, model.user) {
        if (model.user != null)
            try {
                val r = model.api.request("/api/user/me/notifications?page=$page&page_size=20")
                rows = r.objects("notifications")
                total = r.optInt("total")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                model.error = e.message
            }
    }
    Column {
        PageTitle("消息")
        if (model.user == null) {
            Status("登录后查看评论、回复和审核通知")
            TextButton(onClick = { go("login") }) { Text("去登录") }
        } else {
            TextButton(onClick = { clearAll = true }, enabled = rows.isNotEmpty()) { Text("清空消息") }
            TextButton(
                onClick = {
                    model.perform {
                        model.api.request("/api/user/me/notifications/read-all", "POST")
                    }
                }
            ) {
                Text("全部已读")
            }
            LazyColumn {
                items(rows) { n ->
                    ListItem(
                        headlineContent = { Text(n.s("title", "校园墙新消息")) },
                        supportingContent = { Text(n.s("content", n.s("text"))) },
                        overlineContent = { Text(if (n.optBoolean("is_read")) "已读" else "未读") },
                        modifier =
                            Modifier.clickable {
                                model.perform({
                                    if (n.optLong("message_id") > 0)
                                        go("message/${n.s("message_id")}")
                                }) {
                                    model.api.request(
                                        "/api/user/me/notifications/${n.s("id")}/read",
                                        "POST",
                                    )
                                }
                            },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    model.perform {
                                        model.api.request(
                                            "/api/user/me/notifications/${n.s("id")}",
                                            "DELETE",
                                        )
                                    }
                                }
                            ) {
                                Text("删除")
                            }
                        },
                    )
                }
                item { Pager(page, total, { page = it }) }
            }
        }
    }
    if (clearAll)
        AlertDialog(
            onDismissRequest = { clearAll = false },
            title = { Text("清空所有消息？") },
            text = { Text("清空后无法恢复通知记录，动态和评论不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.perform {
                            model.api.request("/api/user/me/notifications", "DELETE")
                            clearAll = false
                            page = 1
                        }
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = { TextButton(onClick = { clearAll = false }) { Text("取消") } },
        )
}

@Composable
fun Pager(page: Int, total: Int, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text("上一页") }
        Text("第 $page 页")
        TextButton(onClick = { onPage(page + 1) }, enabled = page * 20 < total) { Text("下一页") }
    }
}

@Composable
fun MyCommentsScreen(model: WallModel, go: (String) -> Unit, back: () -> Unit) {
    var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(page, model.revision) {
        try {
            val r = model.api.request("/api/user/me/comments?page=$page&page_size=20")
            rows = r.objects("comments")
            total = r.optInt("total")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            model.error = e.message
        }
    }
    Column {
        PageTitle("我的评论", back)
        LazyColumn {
            items(rows) { c ->
                ListItem(
                    headlineContent = { Text(c.s("text")) },
                    supportingContent = { Text(c.s("moderation_status")) },
                    modifier = Modifier.clickable { go("message/${c.s("message_id")}") },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                model.perform {
                                    model.api.request(
                                        "/api/user/me/comments/${c.s("message_id")}/${c.s("id").pathSegment()}",
                                        "DELETE",
                                    )
                                }
                            }
                        ) {
                            Text("删除")
                        }
                    },
                )
            }
            item { Pager(page, total, { page = it }) }
        }
    }
}

@Composable
fun HelpScreen(model: WallModel, back: () -> Unit) {
    var notices by remember { mutableStateOf(emptyList<JSONObject>()) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("other") }
    LaunchedEffect(Unit) {
        try {
            val r = model.api.request("/api/notice")
            notices = r.objects("notices").ifEmpty { r.objects("content") }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            model.error = e.message
        }
    }
    Column {
        PageTitle("公告与帮助", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            notices.forEach {
                Text(it.s("title"), style = MaterialTheme.typography.titleMedium)
                Text(it.s("content", it.s("text")))
            }
            Text("社区规则", style = MaterialTheme.typography.titleLarge)
            Text(model.community.s("community_rules"))
            HorizontalDivider()
            Text("意见反馈", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text("标题") },
            )
            OutlinedTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth(),
                label = { Text("说明") },
                minLines = 4,
            )
            OutlinedTextField(
                email,
                { email = it },
                Modifier.fillMaxWidth(),
                label = { Text("联系邮箱") },
            )
            Button(
                onClick = {
                    model.perform {
                        val r =
                            model.api.request(
                                "/api/help/form",
                                "POST",
                                fields =
                                    mapOf(
                                        "title" to title,
                                        "text" to text,
                                        "email" to email,
                                        "category" to category,
                                    ),
                            )
                        text = ""
                        model.error = "反馈已提交，编号 ${r.s("ticket_id")}"
                    }
                },
                enabled = text.isNotBlank() && !model.busy,
            ) {
                Text("提交反馈")
            }
        }
    }
}

@Composable
fun ReportScreen(model: WallModel, id: String, back: () -> Unit) {
    var category by rememberSaveable { mutableStateOf("other") }
    var text by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    Column {
        PageTitle("举报内容", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                    "spam" to "垃圾信息",
                    "abuse" to "恶意行为",
                    "porn" to "低俗或违法信息",
                    "rumor" to "虚假信息",
                    "other" to "其他",
                )
                .forEach { (value, label) ->
                    Row {
                        RadioButton(category == value, { category = value })
                        Text(label, Modifier.padding(top = 12.dp))
                    }
                }
            OutlinedTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth(),
                label = { Text("举报理由") },
                minLines = 3,
            )
            OutlinedTextField(
                email,
                { email = it },
                Modifier.fillMaxWidth(),
                label = { Text("联系邮箱（可选）") },
            )
            Button(
                onClick = {
                    model.perform(back) {
                        require(id.matches(Regex("[0-9]+(?:/comment/[^/]+)?")))
                        model.api.request(
                            "/api/help/report/$id",
                            "POST",
                            fields = mapOf("category" to category, "text" to text, "email" to email),
                        )
                    }
                },
                enabled = text.isNotBlank() && !model.busy,
            ) {
                Text("提交举报")
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.zongtech.campuswall

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.Request
import org.json.JSONObject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun Attachment(file: String, api: WallApi? = null) {
    val url = "${BuildConfig.API_URL}/static/uploads/${file.pathSegment()}"
    val image =
        file.substringAfterLast('.').lowercase() in
            listOf("jpg", "jpeg", "png", "gif", "webp", "avif")
    val media =
        file.substringAfterLast('.').lowercase() in
            listOf("mp4", "webm", "mp3", "wav", "ogg", "m4a")
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (image)
        AsyncImage(
            model = url,
            contentDescription = "帖子图片，点击放大",
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clickable { open = true },
        )
    else
        OutlinedButton(
            onClick = {
                if (media) open = true
                else
                    scope.launch {
                        downloading = true
                        try {
                            val local =
                                withContext(Dispatchers.IO) {
                                    val dir =
                                        File(context.cacheDir, "attachments").apply { mkdirs() }
                                    val target =
                                        File(
                                            dir,
                                            file.substringAfterLast('/').substringAfterLast('\\'),
                                        )
                                    val client = api?.client ?: okhttp3.OkHttpClient()
                                    client
                                        .newCall(Request.Builder().url(url).build())
                                        .execute()
                                        .use { r ->
                                            check(r.isSuccessful) { "附件暂时无法读取" }
                                            r.body!!.byteStream().use { input ->
                                                target.outputStream().use { input.copyTo(it) }
                                            }
                                        }
                                    target
                                }
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    local,
                                )
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(
                                        uri,
                                        android.webkit.MimeTypeMap.getSingleton()
                                            .getMimeTypeFromExtension(file.substringAfterLast('.'))
                                            ?: "application/octet-stream",
                                    )
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error =
                                if (e is android.content.ActivityNotFoundException) "手机上没有可打开此文件的应用"
                                else e.message
                        } finally {
                            downloading = false
                        }
                    }
            },
            enabled = !downloading,
        ) {
            Text(if (downloading) "正在下载…" else if (media) "播放音视频" else "打开附件 · $file")
        }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (open)
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column {
                    if (image) {
                        var scale by remember { mutableFloatStateOf(1f) }
                        AsyncImage(
                            model = url,
                            contentDescription = "图片预览",
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(420.dp)
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                        }
                                    }
                                    .graphicsLayer(scaleX = scale, scaleY = scale),
                        )
                    } else {
                        val player =
                            remember(url) {
                                val cookies =
                                    api?.cookies
                                        ?.loadForRequest(url.toHttpUrl())
                                        .orEmpty()
                                        .joinToString("; ") { "${it.name}=${it.value}" }
                                val source =
                                    androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                        .setDefaultRequestProperties(mapOf("Cookie" to cookies))
                                ExoPlayer.Builder(context)
                                    .setMediaSourceFactory(
                                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                            source
                                        )
                                    )
                                    .build()
                                    .apply {
                                        setMediaItem(MediaItem.fromUri(url))
                                        prepare()
                                    }
                            }
                        DisposableEffect(player) { onDispose { player.release() } }
                        AndroidView(
                            factory = { PlayerView(it).apply { this.player = player } },
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                        )
                    }
                    TextButton(onClick = { open = false }) { Text("关闭") }
                }
            }
        }
}

@Composable
fun ComposeScreen(model: WallModel, initialTag: String = "", back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("draft.${model.user?.s("id")}", 0) }
    var text by rememberSaveable { mutableStateOf(prefs.getString("text", "").orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(initialTag) }
    var anonymous by rememberSaveable { mutableStateOf(true) }
    var official by rememberSaveable { mutableStateOf(false) }
    var question by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("") }
    var files by remember { mutableStateOf(emptyList<Uri>()) }
    var cameraUri by rememberSaveable { mutableStateOf("") }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            files = (files + it).distinct().take(20)
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
            if (it) files = (files + Uri.parse(cameraUri)).take(20)
        }
    LaunchedEffect(text) { prefs.edit().putString("text", text).apply() }
    Column {
        PageTitle("发布校园动态", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("分享日常、提问，或认真说一句心里话。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                text,
                { text = it },
                Modifier.fillMaxWidth(),
                label = { Text("正文") },
                minLines = 6,
            )
            OutlinedTextField(
                tags,
                { tags = it },
                Modifier.fillMaxWidth(),
                label = { Text("话题，以逗号分隔") },
            )
            Row {
                Checkbox(anonymous, { anonymous = it })
                Text("匿名发布", Modifier.padding(top = 12.dp))
            }
            if (model.can("content.publish.official"))
                Row {
                    Checkbox(official, { official = it })
                    Text("以官方身份发布", Modifier.padding(top = 12.dp))
                }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        picker.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf"))
                    }
                ) {
                    Text("添加附件")
                }
                OutlinedButton(
                    onClick = {
                        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                File.createTempFile("photo", ".jpg", dir),
                            )
                        cameraUri = uri.toString()
                        camera.launch(uri)
                    }
                ) {
                    Text("拍照")
                }
            }
            files.forEach { uri ->
                InputChip(
                    selected = true,
                    onClick = { files = files - uri },
                    label = { Text("${uri.lastPathSegment?.takeLast(25)} · 移除") },
                )
            }
            Text("投票（可选）", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                question,
                { question = it },
                Modifier.fillMaxWidth(),
                label = { Text("投票问题") },
            )
            if (question.isNotBlank()) {
                OutlinedTextField(
                    options,
                    { options = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("每行一个选项") },
                    minLines = 2,
                )
                DateTimeField("投票截止时间",deadline){deadline=it}
            }
            Button(
                onClick = {
                    model.perform(back) {
                        require(text.isNotBlank() || files.isNotEmpty() || question.isNotBlank()) {
                            "请填写正文或添加附件、投票"
                        }
                        val uploads = files.map { model.api.upload(it) }
                        val body =
                            MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("text", text)
                                .addFormDataPart("tags", tags.replace('，', ','))
                                .addFormDataPart("anonymous", anonymous.toString())
                                .addFormDataPart("post_as_admin", official.toString())
                        if (question.isNotBlank()) {
                            body.addFormDataPart("poll_question", question)
                            options
                                .lines()
                                .filter { it.isNotBlank() }
                                .forEach { body.addFormDataPart("poll_options", it) }
                            if (deadline.isNotBlank())
                                body.addFormDataPart("poll_closes_at", deadline)
                        }
                        uploads.forEach { body.addFormDataPart("filenames", it) }
                        val result =
                            model.api.request("/api/wall/submit", "POST", body = body.build())
                        prefs.edit().clear().apply()
                        model.error =
                            if (result.s("moderation_status") == "pending") "已提交，请等待审核" else "发布成功"
                    }
                },
                enabled =
                    !model.busy &&
                        (model.user != null ||
                            model.community.optBoolean("guest_posting_enabled")) &&
                        model.community.optBoolean("posting_enabled", true),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (model.busy) "正在上传与提交…" else "发布")
            }
            if (!model.community.optBoolean("posting_enabled", true))
                Text(model.community.s("pause_reason", "发帖暂时关闭"))
        }
    }
}

@Composable
fun DetailScreen(model: WallModel, id: String, go: (String) -> Unit, back: () -> Unit) {
    var post by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var comment by rememberSaveable { mutableStateOf("") }
    var refer by remember { mutableStateOf<JSONObject?>(null) }
    var saved by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    LaunchedEffect(id, model.revision, refresh) {
        try {
            post =
                model.api.request("/api/get_message_details/$id", "POST").optJSONObject("message")
            error = null
            if (model.user != null)
                saved = model.api.request("/api/user/me/favorites/ids").strings("ids").contains(id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        }
    }
    Column {
        PageTitle("动态详情", back)
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Status(it) { refresh++ } }
            post?.let { p ->
                PostCard(p, model, go, true)
                Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            model.perform {
                                model.api.request(
                                    "/api/user/me/favorites/$id",
                                    if (saved) "DELETE" else "POST",
                                )
                            }
                        }
                    ) {
                        Text(if (saved) "取消收藏" else "收藏")
                    }
                    TextButton(onClick = { go("report/$id") }) { Text("举报") }
                    if (
                        p.optBoolean("owned") ||
                            id in model.ownedPosts ||
                            p.s("user_id") == model.user?.s("id")
                    ) {
                        TextButton(
                            onClick = {
                                editText = p.s("text")
                                editing = true
                            }
                        ) {
                            Text("编辑")
                        }
                        TextButton(onClick = { deleting = true }) { Text("删除") }
                    }
                }
                Text(
                    "评论",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                p.objects("comments").forEach { c ->
                    ListItem(
                        headlineContent = { Text(c.s("text")) },
                        supportingContent = {
                            Text(
                                c.s("display_name_snapshot", c.s("nickname", "同学")) +
                                    " · " +
                                    c.s("timestamp")
                            )
                        },
                        trailingContent = {
                            Column {
                                TextButton(onClick = { refer = c }) { Text("回复") }
                                TextButton(
                                    onClick = {
                                        go("report/${("$id/comment/${c.s("id")}").pathSegment()}")
                                    }
                                ) {
                                    Text("举报")
                                }
                                if (c.optBoolean("owned"))
                                    TextButton(
                                        onClick = {
                                            model.perform {
                                                model.api.request(
                                                    "/api/user/me/comments/$id/${c.s("id").pathSegment()}",
                                                    "DELETE",
                                                )
                                            }
                                        }
                                    ) {
                                        Text("删除")
                                    }
                            }
                        },
                    )
                }
                if (model.user == null) TextButton(onClick = { go("login") }) { Text("登录后评论") }
                else
                    Column(Modifier.padding(16.dp)) {
                        refer?.let {
                            TextButton(onClick = { refer = null }) {
                                Text("回复 ${it.s("text").take(24)} · 取消")
                            }
                        }
                        OutlinedTextField(
                            comment,
                            { comment = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("友善交流，认真回应") },
                            minLines = 2,
                        )
                        Button(
                            onClick = {
                                model.perform {
                                    val fields = mutableMapOf("text" to comment)
                                    refer?.let {
                                        fields["refer_id"] = it.s("id")
                                        fields["refer"] = it.s("text")
                                    }
                                    model.api.request(
                                        "/api/wall/comment/$id",
                                        "POST",
                                        fields = fields,
                                    )
                                    comment = ""
                                    refer = null
                                }
                            },
                            enabled =
                                comment.isNotBlank() &&
                                    !model.busy &&
                                    model.community.optBoolean("commenting_enabled", true),
                        ) {
                            Text("发送评论")
                        }
                    }
            } ?: run { if (error == null) Status("正在加载…") }
        }
    }
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除这条动态？") },
            text = { Text("动态将从校园墙移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.perform(back) {
                            model.api.request("/api/user/me/messages/$id", "DELETE")
                            deleting = false
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
    if (editing)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑动态") },
            text = { OutlinedTextField(editText, { editText = it }, minLines = 4) },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.perform {
                            model.api.request(
                                "/api/user/me/messages/$id",
                                "PUT",
                                fields =
                                    mapOf(
                                        "text" to editText,
                                        "tags" to post!!.strings("tags").joinToString(","),
                                        "anonymous" to
                                            post!!.optBoolean("anonymous", true).toString(),
                                    ),
                            )
                            editing = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
}

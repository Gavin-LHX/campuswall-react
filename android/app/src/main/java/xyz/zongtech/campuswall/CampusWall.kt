@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.zongtech.campuswall

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import org.json.JSONObject

@Composable
fun CampusWall(model: WallModel, deepLink: Uri?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "feed"
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model.openNotifications) {
        if (model.openNotifications) {
            nav.navigate("notifications")
            model.openNotifications = false
        }
    }
    val tabs =
        listOf(
            Triple("feed", "校园", Icons.Default.Home),
            Triple("topics", "话题", Icons.Default.Tag),
            Triple("notifications", "消息", Icons.Default.Notifications),
            Triple("me", "我的", Icons.Default.Person),
        )
    LaunchedEffect(model.error) {
        model.error?.let {
            snackbar.showSnackbar(it)
            model.error = null
        }
    }
    LaunchedEffect(model.pendingMessage) {
        model.pendingMessage?.let {
            nav.navigate("message/$it")
            model.pendingMessage = null
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.first == route })
                NavigationBar {
                    tabs.forEach { (path, title, icon) ->
                        NavigationBarItem(
                            selected = route == path,
                            onClick = {
                                nav.navigate(path) {
                                    popUpTo("feed") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, title) },
                            label = { Text(title) },
                        )
                    }
                }
        },
        floatingActionButton = {
            if (route == "feed")
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(if (model.user == null) "login" else "compose") },
                    icon = { Icon(Icons.Default.Edit, null) },
                    text = { Text("写点什么") },
                )
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            NavHost(navController = nav, startDestination = "feed") {
                composable("feed") { FeedScreen(model, "校园墙", nav::navigate) }
                composable("topics") { TopicsScreen(model, nav::navigate) }
                composable("tag/{tag}") {
                    FeedScreen(
                        model,
                        it.arguments?.getString("tag").orEmpty(),
                        nav::navigate,
                        it.arguments?.getString("tag").orEmpty(),
                        back = { nav.popBackStack() },
                    )
                }
                composable("message/{id}") {
                    DetailScreen(model, it.arguments?.getString("id").orEmpty(), nav::navigate) {
                        nav.popBackStack()
                    }
                }
                composable("compose") { ComposeScreen(model) { nav.popBackStack() } }
                composable("compose/{tag}") {
                    ComposeScreen(model, it.arguments?.getString("tag").orEmpty()) {
                        nav.popBackStack()
                    }
                }
                composable("login") { LoginScreen(model) { nav.popBackStack() } }
                composable("me") { MeScreen(model, nav::navigate) }
                composable("notifications") { NotificationsScreen(model, nav::navigate) }
                composable("saved") {
                    FeedScreen(
                        model,
                        "我的收藏",
                        nav::navigate,
                        path = "/api/user/me/favorites",
                        back = { nav.popBackStack() },
                    )
                }
                composable("posts") {
                    FeedScreen(
                        model,
                        "我的发布",
                        nav::navigate,
                        path = "/api/user/me/messages",
                        back = { nav.popBackStack() },
                    )
                }
                composable("comments") {
                    MyCommentsScreen(model, nav::navigate) { nav.popBackStack() }
                }
                composable("lost") { LostFoundScreen(model, nav::navigate) { nav.popBackStack() } }
                composable("profile/{id}") {
                    ProfileScreen(model, it.arguments?.getString("id").orEmpty(), nav::navigate) {
                        nav.popBackStack()
                    }
                }
                composable("settings") { AccountScreen(model) { nav.popBackStack() } }
                composable("help") { HelpScreen(model) { nav.popBackStack() } }
                composable("report/{id}") {
                    ReportScreen(model, it.arguments?.getString("id").orEmpty()) {
                        nav.popBackStack()
                    }
                }
                composable("admin") { AdminScreen(model) { nav.popBackStack() } }
            }
            if (model.busy)
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun PageTitle(
    title: String,
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            back?.let {
                IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            }
        },
        actions = actions,
    )
}

@Composable
fun Status(message: String, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        retry?.let { OutlinedButton(onClick = it) { Text("重试") } }
    }
}

@Composable
fun FeedScreen(
    model: WallModel,
    title: String,
    go: (String) -> Unit,
    tag: String = "",
    path: String = "/api/get_messages",
    back: (() -> Unit)? = null,
) {
    var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var query by rememberSaveable { mutableStateOf("") }
    var applied by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("newest") }
    var contentFilter by rememberSaveable { mutableStateOf("all") }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(path, tag, page, applied, sort, contentFilter, model.revision, refresh) {
        loading = true
        error = null
        try {
            val suffix =
                if (path == "/api/get_messages")
                    "?start=${(page-1)*20}&end=${page*20}&s=$sort&w=${applied.pathSegment()}&tag=${tag.pathSegment()}&f=$contentFilter"
                else "?page=$page&page_size=20&filter=$contentFilter"
            val r = model.api.request(path + suffix)
            val incoming = r.objects("data").ifEmpty { r.objects("messages") }
            total = r.optInt("total", incoming.size)
            rows =
                if (path.matches(Regex("/api/user/[0-9]+/messages")))
                    incoming.drop((page - 1) * 20).take(20)
                else incoming
            rows
                .filter { it.optBoolean("owned") }
                .forEach { if (it.s("id") !in model.ownedPosts) model.ownedPosts.add(it.s("id")) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.message
        } finally {
            loading = false
        }
    }
    Column {
        PageTitle(title, back) {
            if (tag.isNotEmpty())
                IconButton(
                    onClick = {
                        go(
                            if (
                                model.user != null ||
                                    model.community.optBoolean("guest_posting_enabled")
                            )
                                "compose/${tag.pathSegment()}"
                            else "login"
                        )
                    }
                ) {
                    Icon(Icons.Default.Edit, "发布到此话题")
                }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "刷新") }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tag == "表白墙" || tag == "表白") item { ParticleHeart() }
            if(path=="/api/user/lost-found")item{Row(Modifier.padding(horizontal=16.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("all" to "全部","lost" to "寻物","found" to "招领","resolved" to "已找回").forEach{(v,t)->FilterChip(contentFilter==v,{contentFilter=v;page=1},label={Text(t)})}}}
            if (path == "/api/get_messages")
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(contentFilter=="files",{contentFilter=if(contentFilter=="files")"all"else"files";page=1},label={Text("附件")});FilterChip(contentFilter=="polls",{contentFilter=if(contentFilter=="polls")"all"else"polls";page=1},label={Text("投票")})}
                        if (tag.isEmpty()) {
                            Text(
                                "观澜中学 · 同学们的日常",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { go("tag/${"表白".pathSegment()}") },
                                    label = { Text("表白墙") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FavoriteBorder,
                                            null,
                                            Modifier.size(18.dp),
                                        )
                                    },
                                )
                                AssistChip(onClick = { go("lost") }, label = { Text("失物招领") })
                                AssistChip(onClick = { go("help") }, label = { Text("公告与帮助") })
                            }
                        }
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索校园里的新鲜事") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        applied = query
                                        page = 1
                                    }
                                ) {
                                    Icon(Icons.Default.Search, "搜索")
                                }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("newest" to "最新", "likes" to "热门", "dislikes" to "争议").forEach {
                                (value, label) ->
                                FilterChip(
                                    selected = sort == value,
                                    onClick = {
                                        sort = value
                                        page = 1
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Status(it) { refresh++ } } }
            if (!loading && error == null && rows.isEmpty()) item { Status("这里还很安静，来发第一条吧。") }
            items(rows, key = { it.s("id") }) { PostCard(it, model, go) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { page-- }, enabled = page > 1 && !loading) { Text("上一页") }
                    Text("第 $page 页")
                    TextButton(onClick = { page++ }, enabled = page * 20 < total && !loading) {
                        Text("下一页")
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: JSONObject, model: WallModel, go: (String) -> Unit, detail: Boolean = false) {
    val id = post.s("id")
    val anonymous = post.optBoolean("anonymous", true)
    val context = LocalContext.current
    ElevatedCard(
        onClick = { if (!detail) go("message/$id") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model =
                        "${BuildConfig.API_URL}/api/user/${if(anonymous) "0" else post.s("user_id")}/avatar",
                    contentDescription = null,
                    modifier =
                        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = !anonymous) {
                            go("profile/${post.s("user_id")}")
                        },
                )
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        if (anonymous) "匿名同学" else post.s("display_name_snapshot", "一位同学"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        post.s("timestamp"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (post.optBoolean("pinned"))
                    Icon(Icons.Default.PushPin, "置顶", Modifier.size(16.dp))
            }
            Text(
                post.s("text"),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (detail) Int.MAX_VALUE else 7,
            )
            if (post.strings("tags").isNotEmpty())
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    post.strings("tags").forEach { tag ->
                        SuggestionChip(
                            onClick = { go("tag/${tag.pathSegment()}") },
                            label = { Text("# $tag") },
                        )
                    }
                }
            post.strings("files").forEach { file -> Attachment(file, model.api) }
            post.optJSONObject("poll")?.let { poll ->
                Text(poll.s("question"), fontWeight = FontWeight.Medium)
                poll.objects("options").forEach { option ->
                    OutlinedButton(
                        onClick = {
                            model.perform {
                                model.api.request(
                                    "/api/wall/poll/$id/vote",
                                    "POST",
                                    json = JSONObject().put("option_id", option.opt("id")),
                                )
                            }
                        },
                        enabled =
                            !model.busy &&
                                !poll.optBoolean("has_voted") &&
                                !poll.optBoolean("is_closed"),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            option.s("text", option.s("label")) +
                                if (poll.optBoolean("has_voted") || poll.optBoolean("is_closed"))
                                    " · ${option.optInt("votes")} 票"
                                else ""
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { model.perform { model.api.request("/api/wall/like/$id", "POST") } }
                ) {
                    Icon(
                        if (post.optBoolean("liked")) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder,
                        "赞",
                        Modifier.size(18.dp),
                    )
                    Text(" ${post.optInt("likes")}")
                }
                TextButton(
                    onClick = {
                        model.perform { model.api.request("/api/wall/dislike/$id", "POST") }
                    }
                ) {
                    Icon(Icons.Default.ThumbDownOffAlt, "踩", Modifier.size(18.dp))
                    Text(" ${post.optInt("dislikes")}")
                }
                TextButton(onClick = { go("message/$id") }) {
                    Icon(Icons.Default.ChatBubbleOutline, "评论", Modifier.size(18.dp))
                    Text(" ${post.optJSONArray("comments")?.length() ?: 0}")
                }
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(
                                        Intent.EXTRA_TEXT,
                                        "https://wall.zongtech.xyz/wall/message/$id",
                                    ),
                                "分享校园墙",
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Share, "分享")
                }
            }
        }
    }
}

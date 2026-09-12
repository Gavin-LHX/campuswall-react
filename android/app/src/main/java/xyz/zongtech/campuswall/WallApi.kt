package xyz.zongtech.campuswall

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.s(key: String, default: String = "") =
    if (isNull(key)) default else optString(key, default)

fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }
        ?: emptyList()

fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

fun String.pathSegment(): String = Uri.encode(this)

/** Session cookies are encrypted with a non-exportable Android Keystore key. */
class SessionCookies(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val alias = "campuswall.session"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    private var cookies =
        runCatching {
                val encoded =
                    prefs.getString("cookies", null) ?: return@runCatching mutableListOf<Cookie>()
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
                )
                val values = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size))))
                (0 until values.length())
                    .mapNotNull {
                        Cookie.parse(
                            HttpUrl.Builder().scheme("https").host("api-wall.zongtech.xyz").build(),
                            values.getString(it),
                        )
                    }
                    .toMutableList()
            }
            .getOrElse { mutableListOf() }

    @Synchronized
    override fun loadForRequest(url: HttpUrl) =
        cookies.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { next ->
            this.cookies.removeAll {
                it.name == next.name && it.domain == next.domain && it.path == next.path
            }
            if (next.expiresAt > System.currentTimeMillis()) this.cookies.add(next)
        }
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes =
            cipher.iv +
                cipher.doFinal(
                    JSONArray(this.cookies.map { it.toString() }).toString().toByteArray()
                )
        prefs.edit().putString("cookies", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().clear().apply()
    }
}

class WallApi(val context: Context, private val baseUrl: String = BuildConfig.API_URL) {
    val cookies = SessionCookies(context)
    val client =
        OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    suspend fun request(
        path: String,
        method: String = "GET",
        fields: Map<String, String>? = null,
        json: JSONObject? = null,
        body: RequestBody? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val payload =
                body
                    ?: json?.toString()?.toRequestBody("application/json".toMediaType())
                    ?: fields?.let { values ->
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .apply { values.forEach { (k, v) -> addFormDataPart(k, v) } }
                            .build()
                    }
                    ?: if (method in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody()
                    else null
            require(path.startsWith("/api/") && !path.contains('\\')) { "无效的接口地址" }
            val request =
                Request.Builder()
                    .url(baseUrl.trimEnd('/') + path)
                    .header("Origin", "https://wall.zongtech.xyz")
                    .method(method, payload)
                    .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val result =
                    try {
                        if (raw.trimStart().startsWith("["))
                            JSONObject().put("data", JSONArray(raw))
                        else JSONObject(raw)
                    } catch (_: Exception) {
                        throw IllegalStateException("服务器暂时无法响应（${response.code}）")
                    }
                if (
                    !response.isSuccessful ||
                        (result.has("success") && !result.optBoolean("success"))
                )
                    throw IllegalStateException(result.s("error", "请求失败（${response.code}）"))
                result
            }
        }

    suspend fun upload(uri: Uri, avatar: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var name = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val key = UUID.randomUUID().toString()
            // Streaming to private temporary storage bounds memory while supporting large videos.
            val temp = java.io.File.createTempFile("upload", ".bin", context.cacheDir)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                } ?: error("无法读取附件")
                if (avatar) {
                    val body =
                        MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart(
                                "avatar",
                                name,
                                object : RequestBody() {
                                    override fun contentType() = mime.toMediaType()

                                    override fun contentLength() = temp.length()

                                    override fun writeTo(sink: okio.BufferedSink) {
                                        temp.inputStream().use { input ->
                                            val b = ByteArray(65536)
                                            var n = input.read(b)
                                            while (n >= 0) {
                                                sink.write(b, 0, n)
                                                n = input.read(b)
                                            }
                                        }
                                    }
                                },
                            )
                            .build()
                    request("/api/user/me/avatar", "POST", body = body)
                    return@withContext ""
                }
                val chunkSize = 512 * 1024
                val count = ((temp.length() + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
                temp.inputStream().use { input ->
                    repeat(count) { index ->
                        val buffer = ByteArray(chunkSize)
                        var length = 0
                        while (length < chunkSize) {
                            val read = input.read(buffer, length, chunkSize - length)
                            if (read < 0) break
                            length += read
                        }
                        val bytes = buffer.copyOf(length)
                        val body =
                            MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chunkIndex", "$index")
                                .addFormDataPart("totalChunks", "$count")
                                .addFormDataPart("fileKey", key)
                                .addFormDataPart("originalName", name)
                                .addFormDataPart(
                                    "chunk",
                                    name,
                                    bytes.toRequestBody(mime.toMediaType()),
                                )
                                .build()
                        request("/api/chunked_upload", "POST", body = body)
                    }
                }
                request(
                        "/api/merge_chunks",
                        "POST",
                        json =
                            JSONObject()
                                .put("fileKey", key)
                                .put("originalName", name)
                                .put("totalChunks", count),
                    )
                    .s("filename")
            } finally {
                temp.delete()
            }
        }
}

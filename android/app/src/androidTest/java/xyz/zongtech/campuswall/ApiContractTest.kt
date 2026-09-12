package xyz.zongtech.campuswall

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WallApi

    @Before
    fun setup() {
        server = MockWebServer().apply { start() }
        api = WallApi(ApplicationProvider.getApplicationContext(), server.url("/").toString())
        api.cookies.clear()
    }

    @After
    fun cleanup() {
        server.shutdown()
        api.cookies.clear()
    }

    @Test
    fun loginPreservesMultipartAndOriginContract() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"success\":true,\"user\":{\"id\":7}}"))
        val result =
            api.request(
                "/api/user/login",
                "POST",
                fields =
                    mapOf(
                        "student_id" to "1234567890",
                        "password" to "test-password",
                        "captcha_token" to "challenge",
                    ),
            )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/user/login", request.path)
        assertEquals("https://wall.zongtech.xyz", request.getHeader("Origin"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"captcha_token\""))
        assertTrue(body.contains("challenge"))
        assertEquals(7, result.getJSONObject("user").getInt("id"))
    }

    @Test
    fun rejectsApplicationErrorsEvenOnHttp200() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"success\":false,\"error\":\"请先登录\"}"))
        val error = runCatching { api.request("/api/user/me") }.exceptionOrNull()
        assertEquals("请先登录", error?.message)
    }

    @Test
    fun preservesAnonymousServerResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(
                    "{\"success\":true,\"message\":{\"id\":1,\"anonymous\":true,\"display_name_snapshot\":\"匿名用户\",\"files\":[\"photo.webp\"],\"comments\":[]}}"
                )
        )
        val post = api.request("/api/get_message_details/1", "POST").getJSONObject("message")
        assertFalse(post.has("user_id"))
        assertEquals(listOf("photo.webp"), post.strings("files"))
        assertTrue(post.objects("comments").isEmpty())
    }

    @Test
    fun sessionPersistsEncryptedAndStaysOnItsOrigin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "https://api-wall.zongtech.xyz/api/user/me".toHttpUrl()
        val cookie =
            Cookie.Builder()
                .name("user_session")
                .value("private-session-value")
                .hostOnlyDomain(url.host)
                .secure()
                .httpOnly()
                .expiresAt(System.currentTimeMillis() + 60000)
                .build()
        api.cookies.saveFromResponse(url, listOf(cookie))
        assertFalse(
            context
                .getSharedPreferences("session", 0)
                .getString("cookies", "")!!
                .contains("private-session-value")
        )
        val restored = SessionCookies(context)
        assertEquals("private-session-value", restored.loadForRequest(url).single().value)
        assertTrue(restored.loadForRequest("https://example.org/".toHttpUrl()).isEmpty())
        assertTrue(restored.loadForRequest("http://api-wall.zongtech.xyz/".toHttpUrl()).isEmpty())
    }
}

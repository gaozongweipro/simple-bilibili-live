package com.simplebilibili.live.auth

import com.simplebilibili.live.net.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AuthRepository(private val client: OkHttpClient) {
    fun requestQrToken(): QrLoginToken {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
            .header("User-Agent", HttpClientFactory.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("QR token request failed: HTTP ${response.code}")
            val data = JSONObject(response.body?.string().orEmpty()).getJSONObject("data")
            return QrLoginToken(
                url = data.getString("url"),
                qrcodeKey = data.getString("qrcode_key")
            )
        }
    }

    fun pollQrStatus(qrcodeKey: String): QrLoginStatus {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("qrcode_key", qrcodeKey)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClientFactory.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return QrLoginStatus.Failed("扫码状态请求失败：HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.getJSONObject("data")
            return when (data.optInt("code")) {
                0 -> QrLoginStatus.Success(extractCookie(response.headers("Set-Cookie")))
                86038 -> QrLoginStatus.Expired("二维码已过期")
                86090 -> QrLoginStatus.Scanned
                86101 -> QrLoginStatus.Waiting
                else -> QrLoginStatus.Failed(data.optString("message", "扫码登录失败"))
            }
        }
    }

    fun validateLogin(cookie: String): LoginValidation {
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/nav")
            .header("User-Agent", HttpClientFactory.USER_AGENT)
            .header("Cookie", cookie)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return LoginValidation(false, "登录校验失败：HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val loggedIn = root.optJSONObject("data")?.optBoolean("isLogin", false) == true
            return LoginValidation(loggedIn, if (loggedIn) "已登录" else "登录已失效")
        }
    }

    private fun extractCookie(setCookieHeaders: List<String>): String {
        return setCookieHeaders
            .map { it.substringBefore(";") }
            .filter { it.contains("=") }
            .joinToString("; ")
    }
}

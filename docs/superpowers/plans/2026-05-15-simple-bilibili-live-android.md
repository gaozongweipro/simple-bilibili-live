# Simple Bilibili Live Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Android 8 compatible landscape app for Xiaomi Xiaoai touchscreen speaker to scan-login and watch a specified Bilibili live room with native playback.

**Architecture:** Use a native Kotlin Android app with View/XML UI, OkHttp for Bilibili requests, SharedPreferences for small local state, and AndroidX Media3 ExoPlayer for live playback. Bilibili non-public API access is isolated in `biliapi`, login state in `auth`, persisted user settings in `storage`, playback in `player`, and the single-activity landscape UI in `ui`.

**Tech Stack:** Kotlin, Android Gradle Plugin, minSdk 26, target/compile SDK from installed Android SDK, OkHttp, org.json, AndroidX Media3 ExoPlayer, ZXing QR encoder.

---

## File Structure

- Create `settings.gradle.kts`: Gradle project name and `:app` module include.
- Create `build.gradle.kts`: root plugin versions.
- Create `app/build.gradle.kts`: Android app configuration and dependencies.
- Create `app/src/main/AndroidManifest.xml`: app permissions, landscape activity, clear app entry point.
- Create `app/src/main/res/values/strings.xml`: user-facing strings.
- Create `app/src/main/res/values/colors.xml`: restrained dark player colors.
- Create `app/src/main/res/values/styles.xml`: no-actionbar app theme.
- Create `app/src/main/res/layout/activity_main.xml`: 800x400 landscape-first layout with player surface, login panel, room panel, controls, and error overlay.
- Create `app/src/main/java/com/simplebilibili/live/MainActivity.kt`: single-activity UI orchestration.
- Create `app/src/main/java/com/simplebilibili/live/auth/AuthModels.kt`: QR login domain models.
- Create `app/src/main/java/com/simplebilibili/live/auth/AuthRepository.kt`: QR login request, polling, cookie extraction, login validation.
- Create `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiModels.kt`: room and stream internal models.
- Create `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiClient.kt`: room lookup and stream URL request.
- Create `app/src/main/java/com/simplebilibili/live/net/HttpClientFactory.kt`: shared OkHttp client and Bilibili headers.
- Create `app/src/main/java/com/simplebilibili/live/player/LivePlayer.kt`: Media3 ExoPlayer wrapper and retry behavior.
- Create `app/src/main/java/com/simplebilibili/live/storage/AppStorage.kt`: login cookie, room id, and quality preference persistence.
- Create `app/src/main/java/com/simplebilibili/live/ui/UiState.kt`: screen state types used by `MainActivity`.
- Create `app/src/test/java/com/simplebilibili/live/biliapi/BiliApiClientTest.kt`: JSON parsing tests.
- Create `app/src/test/java/com/simplebilibili/live/storage/AppStorageTest.kt`: persistence behavior tests.

## Task 1: Bootstrap Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/styles.xml`

- [ ] **Step 1: Create Gradle settings**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "simpleBilibiliLive"
include(":app")
```

- [ ] **Step 2: Create root build file**

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

- [ ] **Step 3: Create app build file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.simplebilibili.live"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simplebilibili.live"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
}
```

- [ ] **Step 4: Create manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:configChanges="keyboardHidden|orientation|screenSize"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 5: Create resources**

Create `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Simple Bilibili Live</string>
</resources>
```

Create `app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="bg_player">#080A0E</color>
    <color name="panel_dark">#20242B</color>
    <color name="text_primary">#F5F7FA</color>
    <color name="text_secondary">#AAB2BD</color>
    <color name="accent">#00A1D6</color>
</resources>
```

Create `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">@color/accent</item>
    </style>
</resources>
```

- [ ] **Step 6: Verify Gradle project loads**

Run: `./gradlew.bat tasks`

Expected: task list prints and the command exits successfully. If the wrapper does not exist, run `gradle wrapper --gradle-version 8.7`, then run `./gradlew.bat tasks`.

## Task 2: Add Domain Models and Persistence

**Files:**
- Create: `app/src/main/java/com/simplebilibili/live/auth/AuthModels.kt`
- Create: `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiModels.kt`
- Create: `app/src/main/java/com/simplebilibili/live/storage/AppStorage.kt`
- Create: `app/src/test/java/com/simplebilibili/live/storage/AppStorageTest.kt`

- [ ] **Step 1: Write storage test**

Create `app/src/test/java/com/simplebilibili/live/storage/AppStorageTest.kt`:

```kotlin
package com.simplebilibili.live.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AppStorageTest {
    private lateinit var storage: AppStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("simple_bilibili_live", Context.MODE_PRIVATE).edit().clear().commit()
        storage = AppStorage(context)
    }

    @Test
    fun savesAndClearsLoginCookie() {
        storage.saveLoginCookie("SESSDATA=abc; bili_jct=def")

        assertEquals("SESSDATA=abc; bili_jct=def", storage.loginCookie)

        storage.clearLogin()
        assertNull(storage.loginCookie)
    }

    @Test
    fun savesRoomAndQualityPreference() {
        storage.lastRoomId = 12345L
        storage.preferredQuality = 400

        assertEquals(12345L, storage.lastRoomId)
        assertEquals(400, storage.preferredQuality)
    }
}
```

- [ ] **Step 2: Run failing storage test**

Run: `./gradlew.bat testDebugUnitTest --tests com.simplebilibili.live.storage.AppStorageTest`

Expected: FAIL because `AppStorage` does not exist.

- [ ] **Step 3: Create models**

Create `app/src/main/java/com/simplebilibili/live/auth/AuthModels.kt`:

```kotlin
package com.simplebilibili.live.auth

data class QrLoginToken(
    val url: String,
    val qrcodeKey: String
)

sealed class QrLoginStatus {
    data object Waiting : QrLoginStatus()
    data object Scanned : QrLoginStatus()
    data class Success(val cookie: String) : QrLoginStatus()
    data class Expired(val message: String) : QrLoginStatus()
    data class Failed(val message: String) : QrLoginStatus()
}
```

Create `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiModels.kt`:

```kotlin
package com.simplebilibili.live.biliapi

data class LiveRoom(
    val inputRoomId: Long,
    val roomId: Long,
    val title: String,
    val liveStatus: LiveStatus
)

enum class LiveStatus {
    LIVE,
    OFFLINE,
    NOT_FOUND,
    RESTRICTED
}

data class StreamOption(
    val quality: Int,
    val description: String,
    val url: String
)

data class LiveStream(
    val roomId: Long,
    val options: List<StreamOption>
) {
    val bestForDevice: StreamOption?
        get() = options.sortedBy { it.quality }.firstOrNull()
}
```

- [ ] **Step 4: Implement storage**

Create `app/src/main/java/com/simplebilibili/live/storage/AppStorage.kt`:

```kotlin
package com.simplebilibili.live.storage

import android.content.Context

class AppStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var loginCookie: String?
        get() = prefs.getString(KEY_LOGIN_COOKIE, null)
        private set(value) {
            prefs.edit().putString(KEY_LOGIN_COOKIE, value).apply()
        }

    var lastRoomId: Long
        get() = prefs.getLong(KEY_LAST_ROOM_ID, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_ROOM_ID, value).apply()
        }

    var preferredQuality: Int
        get() = prefs.getInt(KEY_PREFERRED_QUALITY, 0)
        set(value) {
            prefs.edit().putInt(KEY_PREFERRED_QUALITY, value).apply()
        }

    fun saveLoginCookie(cookie: String) {
        loginCookie = cookie
    }

    fun clearLogin() {
        prefs.edit().remove(KEY_LOGIN_COOKIE).apply()
    }

    companion object {
        private const val PREFS_NAME = "simple_bilibili_live"
        private const val KEY_LOGIN_COOKIE = "login_cookie"
        private const val KEY_LAST_ROOM_ID = "last_room_id"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
    }
}
```

- [ ] **Step 5: Run storage test**

Run: `./gradlew.bat testDebugUnitTest --tests com.simplebilibili.live.storage.AppStorageTest`

Expected: PASS.

## Task 3: Add HTTP Client and Bilibili API Parsing

**Files:**
- Create: `app/src/main/java/com/simplebilibili/live/net/HttpClientFactory.kt`
- Create: `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiClient.kt`
- Create: `app/src/test/java/com/simplebilibili/live/biliapi/BiliApiClientTest.kt`

- [ ] **Step 1: Write parsing tests**

Create `app/src/test/java/com/simplebilibili/live/biliapi/BiliApiClientTest.kt`:

```kotlin
package com.simplebilibili.live.biliapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliApiClientTest {
    @Test
    fun parsesLiveRoomFromInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "room_id": 22889451,
                "title": "测试直播间",
                "live_status": 1
              }
            }
        """.trimIndent()

        val room = BiliApiClient.parseRoomInfo(123L, json)

        assertEquals(123L, room.inputRoomId)
        assertEquals(22889451L, room.roomId)
        assertEquals("测试直播间", room.title)
        assertEquals(LiveStatus.LIVE, room.liveStatus)
    }

    @Test
    fun parsesOfflineRoomFromInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "room_id": 88,
                "title": "未开播",
                "live_status": 0
              }
            }
        """.trimIndent()

        val room = BiliApiClient.parseRoomInfo(88L, json)

        assertEquals(LiveStatus.OFFLINE, room.liveStatus)
    }

    @Test
    fun parsesStreamUrlsFromPlayInfoResponse() {
        val json = """
            {
              "code": 0,
              "data": {
                "playurl_info": {
                  "playurl": {
                    "stream": [
                      {
                        "format": [
                          {
                            "codec": [
                              {
                                "current_qn": 400,
                                "base_url": "/live-bvc/test.flv",
                                "url_info": [
                                  {
                                    "host": "https://example.com",
                                    "extra": "?token=abc"
                                  }
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val stream = BiliApiClient.parsePlayInfo(22889451L, json)

        assertEquals(22889451L, stream.roomId)
        assertEquals(1, stream.options.size)
        assertEquals(400, stream.options.first().quality)
        assertTrue(stream.options.first().url.startsWith("https://example.com/live-bvc/test.flv"))
    }
}
```

- [ ] **Step 2: Run failing parsing tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.simplebilibili.live.biliapi.BiliApiClientTest`

Expected: FAIL because `BiliApiClient` does not exist.

- [ ] **Step 3: Create shared HTTP client**

Create `app/src/main/java/com/simplebilibili/live/net/HttpClientFactory.kt`:

```kotlin
package com.simplebilibili.live.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientFactory {
    fun create(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 8.0.0) SimpleBilibiliLive/0.1"
}
```

- [ ] **Step 4: Implement API client**

Create `app/src/main/java/com/simplebilibili/live/biliapi/BiliApiClient.kt`:

```kotlin
package com.simplebilibili.live.biliapi

import com.simplebilibili.live.net.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class BiliApiClient(
    private val client: OkHttpClient,
    private val cookieProvider: () -> String?
) {
    fun getRoomInfo(roomId: Long): LiveRoom {
        val url = "https://api.live.bilibili.com/room/v1/Room/get_info"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("room_id", roomId.toString())
            .build()
        val body = execute(url.toString())
        return parseRoomInfo(roomId, body)
    }

    fun getPlayInfo(roomId: Long, quality: Int): LiveStream {
        val url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("room_id", roomId.toString())
            .addQueryParameter("protocol", "0,1")
            .addQueryParameter("format", "0,1,2")
            .addQueryParameter("codec", "0,1")
            .addQueryParameter("qn", quality.takeIf { it > 0 }?.toString() ?: "400")
            .addQueryParameter("platform", "web")
            .addQueryParameter("ptype", "8")
            .build()
        val body = execute(url.toString())
        return parsePlayInfo(roomId, body)
    }

    private fun execute(url: String): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClientFactory.USER_AGENT)
            .header("Referer", "https://live.bilibili.com/")
        cookieProvider()?.let { requestBuilder.header("Cookie", it) }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) error("Bilibili request failed: HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        fun parseRoomInfo(inputRoomId: Long, json: String): LiveRoom {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) {
                return LiveRoom(inputRoomId, inputRoomId, "", LiveStatus.NOT_FOUND)
            }
            val data = root.getJSONObject("data")
            val status = when (data.optInt("live_status", 0)) {
                1 -> LiveStatus.LIVE
                0 -> LiveStatus.OFFLINE
                else -> LiveStatus.RESTRICTED
            }
            return LiveRoom(
                inputRoomId = inputRoomId,
                roomId = data.optLong("room_id", inputRoomId),
                title = data.optString("title", ""),
                liveStatus = status
            )
        }

        fun parsePlayInfo(roomId: Long, json: String): LiveStream {
            val root = JSONObject(json)
            val options = mutableListOf<StreamOption>()
            val streams = root
                .getJSONObject("data")
                .getJSONObject("playurl_info")
                .getJSONObject("playurl")
                .getJSONArray("stream")

            for (streamIndex in 0 until streams.length()) {
                val formats = streams.getJSONObject(streamIndex).getJSONArray("format")
                for (formatIndex in 0 until formats.length()) {
                    val codecs = formats.getJSONObject(formatIndex).getJSONArray("codec")
                    for (codecIndex in 0 until codecs.length()) {
                        val codec = codecs.getJSONObject(codecIndex)
                        val quality = codec.optInt("current_qn", 0)
                        val baseUrl = codec.optString("base_url", "")
                        val urlInfos = codec.getJSONArray("url_info")
                        for (urlIndex in 0 until urlInfos.length()) {
                            val info = urlInfos.getJSONObject(urlIndex)
                            val fullUrl = info.optString("host") + baseUrl + info.optString("extra")
                            if (fullUrl.startsWith("http")) {
                                options += StreamOption(
                                    quality = quality,
                                    description = "清晰度 $quality",
                                    url = fullUrl
                                )
                            }
                        }
                    }
                }
            }
            return LiveStream(roomId = roomId, options = options.distinctBy { it.url })
        }
    }
}
```

- [ ] **Step 5: Run parsing tests**

Run: `./gradlew.bat testDebugUnitTest --tests com.simplebilibili.live.biliapi.BiliApiClientTest`

Expected: PASS.

## Task 4: Add QR Login Repository

**Files:**
- Create: `app/src/main/java/com/simplebilibili/live/auth/AuthRepository.kt`
- Modify: `app/src/main/java/com/simplebilibili/live/auth/AuthModels.kt`

- [ ] **Step 1: Extend auth model with validation result**

Modify `app/src/main/java/com/simplebilibili/live/auth/AuthModels.kt` to include:

```kotlin
data class LoginValidation(
    val loggedIn: Boolean,
    val message: String
)
```

- [ ] **Step 2: Implement QR login repository**

Create `app/src/main/java/com/simplebilibili/live/auth/AuthRepository.kt`:

```kotlin
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
            if (!response.isSuccessful) return QrLoginStatus.Failed("扫码状态请求失败：HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.getJSONObject("data")
            return when (data.optInt("code")) {
                0 -> QrLoginStatus.Success(response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") })
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
            if (!response.isSuccessful) return LoginValidation(false, "登录校验失败：HTTP ${response.code}")
            val root = JSONObject(response.body?.string().orEmpty())
            val loggedIn = root.optJSONObject("data")?.optBoolean("isLogin", false) == true
            return LoginValidation(loggedIn, if (loggedIn) "已登录" else "登录已失效")
        }
    }
}
```

- [ ] **Step 3: Build after auth repository**

Run: `./gradlew.bat testDebugUnitTest`

Expected: PASS.

## Task 5: Build Landscape UI Shell

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/simplebilibili/live/ui/UiState.kt`
- Create: `app/src/main/java/com/simplebilibili/live/MainActivity.kt`

- [ ] **Step 1: Create UI state**

Create `app/src/main/java/com/simplebilibili/live/ui/UiState.kt`:

```kotlin
package com.simplebilibili.live.ui

sealed class UiState {
    data object Loading : UiState()
    data object Login : UiState()
    data object RoomEntry : UiState()
    data class Playing(val roomId: Long, val title: String) : UiState()
    data class Error(val message: String, val canRetry: Boolean) : UiState()
}
```

- [ ] **Step 2: Create landscape layout**

Create `app/src/main/res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_player">

    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="false" />

    <LinearLayout
        android:id="@+id/loginPanel"
        android:layout_width="360dp"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="20dp"
        android:visibility="gone">

        <ImageView
            android:id="@+id/qrImage"
            android:layout_width="180dp"
            android:layout_height="180dp"
            android:background="#FFFFFF"
            android:contentDescription="Bilibili 登录二维码" />

        <TextView
            android:id="@+id/loginStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="14dp"
            android:gravity="center"
            android:textColor="@color/text_primary"
            android:textSize="18sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/roomPanel"
        android:layout_width="360dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="@color/panel_dark"
        android:orientation="vertical"
        android:padding="18dp"
        android:visibility="gone">

        <EditText
            android:id="@+id/roomInput"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:hint="输入直播间号"
            android:inputType="number"
            android:singleLine="true"
            android:textColor="@color/text_primary"
            android:textColorHint="@color/text_secondary"
            android:textSize="18sp" />

        <Button
            android:id="@+id/startButton"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:layout_marginTop="12dp"
            android:text="进入直播间"
            android:textSize="18sp" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/controlBar"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_gravity="bottom"
        android:background="#AA000000"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="12dp"
        android:visibility="gone">

        <Button
            android:id="@+id/backButton"
            android:layout_width="88dp"
            android:layout_height="44dp"
            android:text="返回" />

        <TextView
            android:id="@+id/roomTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginHorizontal="12dp"
            android:layout_weight="1"
            android:ellipsize="end"
            android:singleLine="true"
            android:textColor="@color/text_primary"
            android:textSize="17sp" />

        <Button
            android:id="@+id/refreshButton"
            android:layout_width="88dp"
            android:layout_height="44dp"
            android:text="刷新" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/errorPanel"
        android:layout_width="420dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="@color/panel_dark"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="18dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/errorMessage"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:textColor="@color/text_primary"
            android:textSize="18sp" />

        <Button
            android:id="@+id/retryButton"
            android:layout_width="160dp"
            android:layout_height="44dp"
            android:layout_marginTop="12dp"
            android:text="重试" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 3: Create initial activity with panel switching**

Create `app/src/main/java/com/simplebilibili/live/MainActivity.kt`:

```kotlin
package com.simplebilibili.live

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.ui.PlayerView
import com.simplebilibili.live.ui.UiState

class MainActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var loginPanel: LinearLayout
    private lateinit var roomPanel: LinearLayout
    private lateinit var controlBar: LinearLayout
    private lateinit var errorPanel: LinearLayout
    private lateinit var roomInput: EditText
    private lateinit var roomTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var qrImage: ImageView
    private lateinit var loginStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        setContentView(R.layout.activity_main)
        bindViews()
        render(UiState.RoomEntry)
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        loginPanel = findViewById(R.id.loginPanel)
        roomPanel = findViewById(R.id.roomPanel)
        controlBar = findViewById(R.id.controlBar)
        errorPanel = findViewById(R.id.errorPanel)
        roomInput = findViewById(R.id.roomInput)
        roomTitle = findViewById(R.id.roomTitle)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)
        qrImage = findViewById(R.id.qrImage)
        loginStatus = findViewById(R.id.loginStatus)
    }

    private fun render(state: UiState) {
        loginPanel.visibility = View.GONE
        roomPanel.visibility = View.GONE
        controlBar.visibility = View.GONE
        errorPanel.visibility = View.GONE
        when (state) {
            UiState.Loading -> Unit
            UiState.Login -> {
                loginPanel.visibility = View.VISIBLE
                loginStatus.text = "请使用 Bilibili 手机客户端扫码登录"
            }
            UiState.RoomEntry -> roomPanel.visibility = View.VISIBLE
            is UiState.Playing -> {
                controlBar.visibility = View.VISIBLE
                roomTitle.text = state.title.ifBlank { "直播间 ${state.roomId}" }
            }
            is UiState.Error -> {
                errorPanel.visibility = View.VISIBLE
                errorMessage.text = state.message
                retryButton.visibility = if (state.canRetry) View.VISIBLE else View.GONE
            }
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
```

- [ ] **Step 4: Build UI shell**

Run: `./gradlew.bat assembleDebug`

Expected: PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

## Task 6: Wire QR Login UI

**Files:**
- Modify: `app/src/main/java/com/simplebilibili/live/MainActivity.kt`

- [ ] **Step 1: Add QR bitmap helper and login flow**

Modify `MainActivity.kt` to:

```kotlin
private fun createQrBitmap(content: String, size: Int): Bitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
```

Add fields:

```kotlin
private val mainHandler = Handler(Looper.getMainLooper())
private lateinit var storage: AppStorage
private lateinit var authRepository: AuthRepository
private lateinit var apiClient: BiliApiClient
```

Initialize in `onCreate` after `bindViews()`:

```kotlin
storage = AppStorage(this)
val httpClient = HttpClientFactory.create()
authRepository = AuthRepository(httpClient)
apiClient = BiliApiClient(httpClient) { storage.loginCookie }
if (storage.loginCookie == null) {
    startQrLogin()
} else {
    render(UiState.RoomEntry)
}
```

Add:

```kotlin
private fun startQrLogin() {
    render(UiState.Login)
    Thread {
        try {
            val token = authRepository.requestQrToken()
            val bitmap = createQrBitmap(token.url, 180)
            runOnUiThread {
                qrImage.setImageBitmap(bitmap)
                loginStatus.text = "请使用 Bilibili 手机客户端扫码登录"
            }
            pollQrLogin(token.qrcodeKey)
        } catch (error: Throwable) {
            runOnUiThread { render(UiState.Error("二维码获取失败：${error.message}", true)) }
        }
    }.start()
}

private fun pollQrLogin(qrcodeKey: String) {
    mainHandler.postDelayed({
        Thread {
            val status = try {
                authRepository.pollQrStatus(qrcodeKey)
            } catch (error: Throwable) {
                QrLoginStatus.Failed(error.message ?: "扫码状态请求失败")
            }
            runOnUiThread {
                when (status) {
                    QrLoginStatus.Waiting -> {
                        loginStatus.text = "等待扫码"
                        pollQrLogin(qrcodeKey)
                    }
                    QrLoginStatus.Scanned -> {
                        loginStatus.text = "已扫码，请在手机上确认"
                        pollQrLogin(qrcodeKey)
                    }
                    is QrLoginStatus.Success -> {
                        storage.saveLoginCookie(status.cookie)
                        render(UiState.RoomEntry)
                    }
                    is QrLoginStatus.Expired -> startQrLogin()
                    is QrLoginStatus.Failed -> render(UiState.Error(status.message, true))
                }
            }
        }.start()
    }, 1800L)
}
```

Required imports:

```kotlin
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.simplebilibili.live.auth.AuthRepository
import com.simplebilibili.live.auth.QrLoginStatus
import com.simplebilibili.live.biliapi.BiliApiClient
import com.simplebilibili.live.net.HttpClientFactory
import com.simplebilibili.live.storage.AppStorage
```

- [ ] **Step 2: Build login UI**

Run: `./gradlew.bat assembleDebug`

Expected: PASS.

## Task 7: Add Native Live Playback

**Files:**
- Create: `app/src/main/java/com/simplebilibili/live/player/LivePlayer.kt`
- Modify: `app/src/main/java/com/simplebilibili/live/MainActivity.kt`

- [ ] **Step 1: Create player wrapper**

Create `app/src/main/java/com/simplebilibili/live/player/LivePlayer.kt`:

```kotlin
package com.simplebilibili.live.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class LivePlayer(
    context: Context,
    private val playerView: PlayerView,
    private val onFatalError: (String) -> Unit
) {
    private val player = ExoPlayer.Builder(context).build()
    private var retryCount = 0
    private var currentUrl: String? = null

    init {
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount < 2 && currentUrl != null) {
                    retryCount += 1
                    play(currentUrl!!)
                } else {
                    onFatalError("播放失败：${error.message ?: "无法连接直播流"}")
                }
            }
        })
    }

    fun play(url: String) {
        currentUrl = url
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        retryCount = 0
        player.stop()
    }

    fun release() {
        playerView.player = null
        player.release()
    }
}
```

- [ ] **Step 2: Wire room button and playback**

Modify `MainActivity.kt`:

Add field:

```kotlin
private lateinit var livePlayer: LivePlayer
private var currentRoomId: Long = 0L
```

Initialize after repositories:

```kotlin
livePlayer = LivePlayer(this, playerView) { message ->
    runOnUiThread { render(UiState.Error(message, true)) }
}
findViewById<Button>(R.id.startButton).setOnClickListener {
    val roomId = roomInput.text.toString().toLongOrNull()
    if (roomId == null || roomId <= 0L) {
        render(UiState.Error("请输入正确的直播间号", true))
    } else {
        startPlayback(roomId)
    }
}
findViewById<Button>(R.id.refreshButton).setOnClickListener {
    if (currentRoomId > 0L) startPlayback(currentRoomId)
}
findViewById<Button>(R.id.backButton).setOnClickListener {
    livePlayer.stop()
    render(UiState.RoomEntry)
}
retryButton.setOnClickListener {
    if (currentRoomId > 0L) startPlayback(currentRoomId) else startQrLogin()
}
```

Add playback method:

```kotlin
private fun startPlayback(roomId: Long) {
    currentRoomId = roomId
    storage.lastRoomId = roomId
    render(UiState.Loading)
    Thread {
        try {
            val room = apiClient.getRoomInfo(roomId)
            if (room.liveStatus != LiveStatus.LIVE) {
                runOnUiThread { render(UiState.Error("直播间未开播或不可访问", true)) }
                return@Thread
            }
            val stream = apiClient.getPlayInfo(room.roomId, storage.preferredQuality)
            val option = stream.bestForDevice
            if (option == null) {
                runOnUiThread { render(UiState.Error("没有可用的直播流", true)) }
                return@Thread
            }
            runOnUiThread {
                render(UiState.Playing(room.roomId, room.title))
                livePlayer.play(option.url)
            }
        } catch (error: Throwable) {
            runOnUiThread { render(UiState.Error("进入直播间失败：${error.message}", true)) }
        }
    }.start()
}
```

Add imports:

```kotlin
import com.simplebilibili.live.biliapi.LiveStatus
import com.simplebilibili.live.player.LivePlayer
```

Release player:

```kotlin
override fun onDestroy() {
    livePlayer.release()
    super.onDestroy()
}
```

- [ ] **Step 3: Build playback app**

Run: `./gradlew.bat assembleDebug`

Expected: PASS.

## Task 8: Add Startup Resume and Error State Polish

**Files:**
- Modify: `app/src/main/java/com/simplebilibili/live/MainActivity.kt`

- [ ] **Step 1: Resume last room**

Modify startup logic:

```kotlin
if (storage.loginCookie == null) {
    startQrLogin()
} else if (storage.lastRoomId > 0L) {
    roomInput.setText(storage.lastRoomId.toString())
    startPlayback(storage.lastRoomId)
} else {
    render(UiState.RoomEntry)
}
```

- [ ] **Step 2: Keep controls lightweight**

Add control bar auto-hide:

```kotlin
private fun showControlsTemporarily() {
    controlBar.visibility = View.VISIBLE
    mainHandler.removeCallbacksAndMessages("hide_controls")
    mainHandler.postDelayed({ controlBar.visibility = View.GONE }, 5000L)
}
```

Set root click listener after `bindViews()`:

```kotlin
findViewById<View>(R.id.root).setOnClickListener {
    if (currentRoomId > 0L) showControlsTemporarily()
}
```

Adjust `render(UiState.Playing)` branch:

```kotlin
is UiState.Playing -> {
    roomTitle.text = state.title.ifBlank { "直播间 ${state.roomId}" }
    showControlsTemporarily()
}
```

- [ ] **Step 3: Build final debug APK**

Run: `./gradlew.bat assembleDebug`

Expected: PASS and debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

## Task 9: Device Verification

**Files:**
- Modify: `docs/superpowers/specs/2026-05-15-simple-bilibili-live-android-design.md` if verification discovers a requirement mismatch.

- [ ] **Step 1: Install debug APK**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Expected: `Success`.

- [ ] **Step 2: Launch app**

Run:

```powershell
adb shell monkey -p com.simplebilibili.live 1
```

Expected: app opens in landscape.

- [ ] **Step 3: Check 800x400 layout**

Run:

```powershell
adb shell wm size 800x400
adb shell screencap -p /sdcard/simple_bilibili_live.png
adb pull /sdcard/simple_bilibili_live.png .
```

Expected: no primary button, QR image, room input, or playback control is clipped.

- [ ] **Step 4: Verify login and playback**

Manual verification:

- QR code appears on first run.
- Bilibili mobile app can scan and confirm login.
- After login, room entry appears.
- Enter a live room id and tap `进入直播间`.
- Native player starts playback.
- Tap screen and verify control bar appears briefly.
- Tap `刷新` and verify playback restarts.
- Tap `返回` and verify room entry returns.

- [ ] **Step 5: Verify failure states**

Manual verification:

- Enter an invalid room id and confirm an error message appears.
- Enter an offline room id and confirm an offline/unavailable message appears.
- Disable network and confirm playback error appears.
- Restore network and tap `重试`.

## Self-Review

- Spec coverage: login, specified room playback, Android 8 minimum, landscape 800x400 layout, native playback, no danmaku, non-public API isolation, persistence, and failure states are covered by Tasks 1 through 9.
- Placeholder scan: this plan contains concrete file paths, code snippets, commands, and expected outcomes. It does not rely on deferred implementation language.
- Type consistency: `QrLoginToken`, `QrLoginStatus`, `LoginValidation`, `LiveRoom`, `LiveStatus`, `LiveStream`, `StreamOption`, `AppStorage`, `BiliApiClient`, `AuthRepository`, `LivePlayer`, and `UiState` names are consistent across tasks.


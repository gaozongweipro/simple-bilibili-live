package com.simplebilibili.live

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.SimpleAdapter
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.simplebilibili.live.auth.AuthRepository
import com.simplebilibili.live.auth.QrLoginStatus
import com.simplebilibili.live.biliapi.BiliApiClient
import com.simplebilibili.live.biliapi.LiveArea
import com.simplebilibili.live.biliapi.LiveRoomItem
import com.simplebilibili.live.biliapi.LiveStatus
import com.simplebilibili.live.player.AspectRatioSurfaceView
import com.simplebilibili.live.net.HttpClientFactory
import com.simplebilibili.live.player.LivePlayer
import com.simplebilibili.live.player.PlaybackRequestGate
import com.simplebilibili.live.storage.AppStorage
import com.simplebilibili.live.ui.UiState

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { controlBar.visibility = View.GONE }

    private lateinit var playerSurface: AspectRatioSurfaceView
    private lateinit var loadingText: TextView
    private lateinit var loginPanel: LinearLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var homeTitle: TextView
    private lateinit var homeList: ListView
    private lateinit var homeEmptyText: TextView
    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var roomPanel: LinearLayout
    private lateinit var controlBar: LinearLayout
    private lateinit var errorPanel: LinearLayout
    private lateinit var roomInput: EditText
    private lateinit var roomTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var qrImage: ImageView
    private lateinit var loginStatus: TextView
    private lateinit var storage: AppStorage
    private lateinit var authRepository: AuthRepository
    private lateinit var apiClient: BiliApiClient
    private lateinit var livePlayer: LivePlayer
    private lateinit var playbackRequestGate: PlaybackRequestGate

    private var currentRoomId: Long = 0L
    private var destroyed = false
    private var qrPolling = false
    private var homeMode: HomeMode = HomeMode.FOLLOW
    private var visibleRooms: List<LiveRoomItem> = emptyList()
    private var visibleAreas: List<LiveArea> = emptyList()
    private var currentSearchKeyword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        setContentView(R.layout.activity_main)
        bindViews()
        setupDependencies()
        setupActions()
        startInitialFlow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onDestroy() {
        destroyed = true
        qrPolling = false
        mainHandler.removeCallbacksAndMessages(null)
        livePlayer.release()
        super.onDestroy()
    }

    private fun bindViews() {
        playerSurface = findViewById(R.id.playerSurface)
        loadingText = findViewById(R.id.loadingText)
        loginPanel = findViewById(R.id.loginPanel)
        homePanel = findViewById(R.id.homePanel)
        homeTitle = findViewById(R.id.homeTitle)
        homeList = findViewById(R.id.homeList)
        homeEmptyText = findViewById(R.id.homeEmptyText)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
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

    private fun setupDependencies() {
        storage = AppStorage(this)
        val httpClient = HttpClientFactory.create()
        authRepository = AuthRepository(httpClient)
        apiClient = BiliApiClient(httpClient) { storage.loginCookie }
        playbackRequestGate = PlaybackRequestGate()
        livePlayer = LivePlayer(this, playerSurface) { message ->
            runOnUiThread { render(UiState.Error(message, true)) }
        }
    }

    private fun setupActions() {
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
            playbackRequestGate.nextRequest()
            livePlayer.stop()
            currentRoomId = 0L
            loadFollowedLives()
        }
        findViewById<Button>(R.id.manualRoomButton).setOnClickListener {
            playbackRequestGate.nextRequest()
            livePlayer.stop()
            render(UiState.RoomEntry)
        }
        findViewById<Button>(R.id.followModeButton).setOnClickListener {
            loadFollowedLives()
        }
        findViewById<Button>(R.id.categoryModeButton).setOnClickListener {
            loadLiveAreas()
        }
        findViewById<Button>(R.id.searchModeButton).setOnClickListener {
            render(UiState.SearchResults(currentSearchKeyword, emptyList()))
        }
        findViewById<Button>(R.id.reloadHomeButton).setOnClickListener {
            reloadHomeMode()
        }
        findViewById<Button>(R.id.searchSubmitButton).setOnClickListener {
            val keyword = searchInput.text.toString().trim()
            if (keyword.isBlank()) {
                render(UiState.SearchResults("", emptyList()))
            } else {
                searchLiveRooms(keyword)
            }
        }
        homeList.setOnItemClickListener { _, _, position, _ ->
            if (visibleAreas.isNotEmpty()) {
                visibleAreas.getOrNull(position)?.let { area ->
                    loadAreaLiveRooms(area)
                }
            } else {
                visibleRooms.getOrNull(position)?.let { room ->
                    startPlayback(room.roomId)
                }
            }
        }
        retryButton.setOnClickListener {
            if (currentRoomId > 0L) startPlayback(currentRoomId) else reloadHomeMode()
        }
        val controlClick = View.OnClickListener {
            if (currentRoomId > 0L) showControlsTemporarily()
        }
        findViewById<View>(R.id.root).setOnClickListener(controlClick)
        playerSurface.setOnClickListener(controlClick)
    }

    private fun startInitialFlow() {
        val lastRoomId = storage.lastRoomId
        if (storage.loginCookie == null) {
            startQrLogin()
        } else {
            if (lastRoomId > 0L) roomInput.setText(lastRoomId.toString())
            loadFollowedLives()
        }
    }

    private fun startQrLogin() {
        playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = 0L
        qrPolling = false
        render(UiState.Login)
        Thread {
            try {
                val token = authRepository.requestQrToken()
                val bitmap = createQrBitmap(token.url, QR_SIZE)
                runOnUiThread {
                    qrImage.setImageBitmap(bitmap)
                    loginStatus.text = "请使用 Bilibili 手机客户端扫码登录"
                }
                qrPolling = true
                pollQrLogin(token.qrcodeKey)
            } catch (error: Throwable) {
                runOnUiThread { render(UiState.Error("二维码获取失败：${error.message}", true)) }
            }
        }.start()
    }

    private fun pollQrLogin(qrcodeKey: String) {
        if (!qrPolling || destroyed) return
        mainHandler.postDelayed({
            if (!qrPolling || destroyed) return@postDelayed
            Thread {
                val status = try {
                    authRepository.pollQrStatus(qrcodeKey)
                } catch (error: Throwable) {
                    QrLoginStatus.Failed(error.message ?: "扫码状态请求失败")
                }
                runOnUiThread {
                    if (!qrPolling || destroyed) return@runOnUiThread
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
                            qrPolling = false
                            if (status.cookie.isBlank()) {
                                render(UiState.Error("登录成功但未获取到 Cookie，请重试", true))
                            } else {
                                storage.saveLoginCookie(status.cookie)
                                loadFollowedLives()
                            }
                        }
                        is QrLoginStatus.Expired -> startQrLogin()
                        is QrLoginStatus.Failed -> {
                            qrPolling = false
                            render(UiState.Error(status.message, true))
                        }
                    }
                }
            }.start()
        }, QR_POLL_INTERVAL_MS)
    }

    private fun startPlayback(roomId: Long) {
        qrPolling = false
        val requestId = playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = roomId
        storage.lastRoomId = roomId
        render(UiState.Loading)
        Thread {
            try {
                val room = apiClient.getRoomInfo(roomId)
                if (!playbackRequestGate.isLatest(requestId)) return@Thread
                if (room.liveStatus != LiveStatus.LIVE) {
                    runOnUiThread {
                        if (playbackRequestGate.isLatest(requestId)) {
                            render(UiState.Error("直播间未开播或不可访问", true))
                        }
                    }
                    return@Thread
                }
                val stream = apiClient.getPlayInfo(room.roomId, storage.preferredQuality)
                if (!playbackRequestGate.isLatest(requestId)) return@Thread
                val option = stream.bestForDevice
                if (option == null) {
                    runOnUiThread {
                        if (playbackRequestGate.isLatest(requestId)) {
                            render(UiState.Error("没有可用的直播流", true))
                        }
                    }
                    return@Thread
                }
                runOnUiThread {
                    if (playbackRequestGate.isLatest(requestId)) {
                        render(UiState.Playing(room.roomId, room.title))
                        livePlayer.play(option.url)
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (playbackRequestGate.isLatest(requestId)) {
                        render(UiState.Error("进入直播间失败：${error.message}", true))
                    }
                }
            }
        }.start()
    }

    private fun loadFollowedLives() {
        qrPolling = false
        playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = 0L
        homeMode = HomeMode.FOLLOW
        render(UiState.Loading)
        Thread {
            try {
                val validation = storage.loginCookie?.let { authRepository.validateLogin(it) }
                if (validation?.loggedIn != true) {
                    storage.clearLogin()
                    runOnUiThread { startQrLogin() }
                    return@Thread
                }
                val rooms = apiClient.getFollowedLiveRooms()
                    .map { LiveRoomItem(roomId = it.roomId, uname = it.uname, title = it.title) }
                runOnUiThread { render(UiState.FollowedLives(rooms)) }
            } catch (error: Throwable) {
                runOnUiThread {
                    render(UiState.Error("关注直播加载失败：${error.message}", true))
                }
            }
        }.start()
    }

    private fun loadLiveAreas() {
        qrPolling = false
        playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = 0L
        homeMode = HomeMode.CATEGORY
        render(UiState.Loading)
        Thread {
            try {
                val areas = apiClient.getLiveAreas()
                runOnUiThread { render(UiState.LiveAreas(areas)) }
            } catch (error: Throwable) {
                runOnUiThread { render(UiState.Error("分类加载失败：${error.message}", true)) }
            }
        }.start()
    }

    private fun loadAreaLiveRooms(area: LiveArea) {
        qrPolling = false
        playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = 0L
        homeMode = HomeMode.CATEGORY
        render(UiState.Loading)
        Thread {
            try {
                val rooms = apiClient.getAreaLiveRooms(area)
                runOnUiThread { render(UiState.AreaLiveRooms(area.displayName, rooms)) }
            } catch (error: Throwable) {
                runOnUiThread { render(UiState.Error("分类直播加载失败：${error.message}", true)) }
            }
        }.start()
    }

    private fun searchLiveRooms(keyword: String) {
        qrPolling = false
        playbackRequestGate.nextRequest()
        livePlayer.stop()
        currentRoomId = 0L
        homeMode = HomeMode.SEARCH
        currentSearchKeyword = keyword
        render(UiState.Loading)
        Thread {
            try {
                val rooms = apiClient.searchLiveRooms(keyword)
                runOnUiThread { render(UiState.SearchResults(keyword, rooms)) }
            } catch (error: Throwable) {
                runOnUiThread { render(UiState.Error("搜索失败：${error.message}", true)) }
            }
        }.start()
    }

    private fun reloadHomeMode() {
        when (homeMode) {
            HomeMode.FOLLOW -> loadFollowedLives()
            HomeMode.CATEGORY -> loadLiveAreas()
            HomeMode.SEARCH -> {
                val keyword = searchInput.text.toString().trim().ifBlank { currentSearchKeyword }
                if (keyword.isBlank()) {
                    render(UiState.SearchResults("", emptyList()))
                } else {
                    searchLiveRooms(keyword)
                }
            }
        }
    }

    private fun render(state: UiState) {
        hideSystemUi()
        loadingText.visibility = View.GONE
        loginPanel.visibility = View.GONE
        homePanel.visibility = View.GONE
        roomPanel.visibility = View.GONE
        controlBar.visibility = View.GONE
        errorPanel.visibility = View.GONE
        mainHandler.removeCallbacks(hideControlsRunnable)

        when (state) {
            UiState.Loading -> loadingText.visibility = View.VISIBLE
            UiState.Login -> {
                loginPanel.visibility = View.VISIBLE
                loginStatus.text = "请使用 Bilibili 手机客户端扫码登录"
            }
            is UiState.FollowedLives -> {
                homeMode = HomeMode.FOLLOW
                renderRoomList("关注直播", "暂无正在直播的关注主播", state.rooms, showSearch = false)
            }
            is UiState.LiveAreas -> {
                homeMode = HomeMode.CATEGORY
                renderAreaList(state.areas)
            }
            is UiState.AreaLiveRooms -> {
                homeMode = HomeMode.CATEGORY
                renderRoomList(state.title, "该分类暂无直播", state.rooms, showSearch = false)
            }
            is UiState.SearchResults -> {
                homeMode = HomeMode.SEARCH
                currentSearchKeyword = state.keyword
                searchInput.setText(state.keyword)
                renderRoomList("搜索直播", if (state.keyword.isBlank()) "请输入关键词搜索直播" else "没有搜索结果", state.rooms, showSearch = true)
            }
            UiState.RoomEntry -> roomPanel.visibility = View.VISIBLE
            is UiState.Playing -> {
                roomTitle.text = state.title.ifBlank { "直播间 ${state.roomId}" }
                showControlsTemporarily()
            }
            is UiState.Error -> {
                errorPanel.visibility = View.VISIBLE
                errorMessage.text = state.message
                retryButton.visibility = if (state.canRetry) View.VISIBLE else View.GONE
            }
        }
    }

    private fun renderRoomList(title: String, emptyText: String, rooms: List<LiveRoomItem>, showSearch: Boolean) {
        visibleRooms = rooms
        visibleAreas = emptyList()
        homePanel.visibility = View.VISIBLE
        homeTitle.text = title
        searchBar.visibility = if (showSearch) View.VISIBLE else View.GONE
        homeEmptyText.text = emptyText
        homeEmptyText.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
        homeList.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE
        homeList.adapter = SimpleAdapter(
            this,
            rooms.map { mapOf("uname" to it.uname, "title" to it.title) },
            R.layout.item_follow_live,
            arrayOf("uname", "title"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
    }

    private fun renderAreaList(areas: List<LiveArea>) {
        visibleAreas = areas
        visibleRooms = emptyList()
        homePanel.visibility = View.VISIBLE
        homeTitle.text = "直播分类"
        searchBar.visibility = View.GONE
        homeEmptyText.text = "分类加载为空"
        homeEmptyText.visibility = if (areas.isEmpty()) View.VISIBLE else View.GONE
        homeList.visibility = if (areas.isEmpty()) View.GONE else View.VISIBLE
        homeList.adapter = SimpleAdapter(
            this,
            areas.map { mapOf("uname" to it.parentName, "title" to it.areaName) },
            R.layout.item_follow_live,
            arrayOf("uname", "title"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
    }

    private fun showControlsTemporarily() {
        controlBar.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, CONTROL_AUTO_HIDE_MS)
    }

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

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        private const val QR_SIZE = 180
        private const val QR_POLL_INTERVAL_MS = 1800L
        private const val CONTROL_AUTO_HIDE_MS = 5000L
    }

    private enum class HomeMode {
        FOLLOW,
        CATEGORY,
        SEARCH
    }
}

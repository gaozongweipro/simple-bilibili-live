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
import com.simplebilibili.live.biliapi.FollowedLiveRoom
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
    private lateinit var followPanel: LinearLayout
    private lateinit var followList: ListView
    private lateinit var followEmptyText: TextView
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
    private var followedRooms: List<FollowedLiveRoom> = emptyList()

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
        followPanel = findViewById(R.id.followPanel)
        followList = findViewById(R.id.followList)
        followEmptyText = findViewById(R.id.followEmptyText)
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
        findViewById<Button>(R.id.reloadFollowButton).setOnClickListener {
            loadFollowedLives()
        }
        followList.setOnItemClickListener { _, _, position, _ ->
            followedRooms.getOrNull(position)?.let { room ->
                startPlayback(room.roomId)
            }
        }
        retryButton.setOnClickListener {
            if (currentRoomId > 0L) startPlayback(currentRoomId) else loadFollowedLives()
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
                runOnUiThread { render(UiState.FollowedLives(rooms)) }
            } catch (error: Throwable) {
                runOnUiThread {
                    render(UiState.Error("关注直播加载失败：${error.message}", true))
                }
            }
        }.start()
    }

    private fun render(state: UiState) {
        hideSystemUi()
        loadingText.visibility = View.GONE
        loginPanel.visibility = View.GONE
        followPanel.visibility = View.GONE
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
                followedRooms = state.rooms
                followPanel.visibility = View.VISIBLE
                followEmptyText.visibility = if (state.rooms.isEmpty()) View.VISIBLE else View.GONE
                followList.visibility = if (state.rooms.isEmpty()) View.GONE else View.VISIBLE
                followList.adapter = SimpleAdapter(
                    this,
                    state.rooms.map {
                        mapOf("uname" to it.uname, "title" to it.title)
                    },
                    R.layout.item_follow_live,
                    arrayOf("uname", "title"),
                    intArrayOf(android.R.id.text1, android.R.id.text2)
                )
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
}

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

    fun getFollowedLiveRooms(): List<FollowedLiveRoom> {
        val url = "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("page_size", "20")
            .build()
        val body = execute(url.toString())
        return parseFollowedLiveRooms(body)
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
            val data = root.optJSONObject("data") ?: return LiveStream(roomId, emptyList())
            val playUrlInfo = data.optJSONObject("playurl_info") ?: return LiveStream(roomId, emptyList())
            val playUrl = playUrlInfo.optJSONObject("playurl") ?: return LiveStream(roomId, emptyList())
            val streams = playUrl.optJSONArray("stream") ?: return LiveStream(roomId, emptyList())
            val options = mutableListOf<StreamOption>()

            for (streamIndex in 0 until streams.length()) {
                val formats = streams.optJSONObject(streamIndex)?.optJSONArray("format") ?: continue
                for (formatIndex in 0 until formats.length()) {
                    val codecs = formats.optJSONObject(formatIndex)?.optJSONArray("codec") ?: continue
                    for (codecIndex in 0 until codecs.length()) {
                        val codec = codecs.optJSONObject(codecIndex) ?: continue
                        val quality = codec.optInt("current_qn", 0)
                        val baseUrl = codec.optString("base_url", "")
                        val urlInfos = codec.optJSONArray("url_info") ?: continue
                        for (urlIndex in 0 until urlInfos.length()) {
                            val info = urlInfos.optJSONObject(urlIndex) ?: continue
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

        fun parseFollowedLiveRooms(json: String): List<FollowedLiveRoom> {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONObject("data") ?: return emptyList()
            val rooms = data.optJSONArray("rooms")
                ?: data.optJSONArray("list")
                ?: data.optJSONArray("items")
                ?: return emptyList()
            val result = mutableListOf<FollowedLiveRoom>()
            for (index in 0 until rooms.length()) {
                val item = rooms.optJSONObject(index) ?: continue
                if (item.optInt("live_status", 1) != 1) continue
                val roomId = when {
                    item.optLong("roomid", 0L) > 0L -> item.optLong("roomid")
                    item.optLong("room_id", 0L) > 0L -> item.optLong("room_id")
                    else -> 0L
                }
                if (roomId <= 0L) continue
                result += FollowedLiveRoom(
                    roomId = roomId,
                    uname = item.optString("uname", item.optString("name", "未命名主播")),
                    title = item.optString("title", "直播中")
                )
            }
            return result
        }
    }
}

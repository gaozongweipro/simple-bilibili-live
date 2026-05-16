package com.simplebilibili.live.biliapi

import com.simplebilibili.live.net.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

    fun getLiveAreas(): List<LiveArea> {
        val body = execute("https://api.live.bilibili.com/room/v1/Area/getList")
        return parseLiveAreas(body)
    }

    fun getAreaLiveRooms(area: LiveArea): List<LiveRoomItem> {
        val urls = listOf(
            areaLiveRoomsUrl(area.parentId, area.areaId, "sort_type_${area.areaId}"),
            areaLiveRoomsUrl(area.parentId, 0L, "sort_type_${area.areaId}"),
            areaLiveRoomsUrl(area.parentId, area.areaId, "online")
        )
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val rooms = parseLiveRoomItems(execute(url))
                if (rooms.isNotEmpty()) return rooms
            } catch (error: Throwable) {
                lastError = error
            }
        }
        lastError?.let { throw it }
        return emptyList()
    }

    fun searchLiveRooms(keyword: String): List<LiveRoomItem> {
        val url = "https://api.bilibili.com/x/web-interface/search/type"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("search_type", "live")
            .addQueryParameter("keyword", keyword)
            .build()
        val body = execute(url.toString())
        return parseSearchLiveRooms(body)
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

    private fun areaLiveRoomsUrl(parentAreaId: Long, areaId: Long, sortType: String): String {
        return "https://api.live.bilibili.com/xlive/web-interface/v1/second/getList"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("platform", "web")
            .addQueryParameter("parent_area_id", parentAreaId.toString())
            .addQueryParameter("area_id", areaId.toString())
            .addQueryParameter("sort_type", sortType)
            .addQueryParameter("page", "1")
            .addQueryParameter("page_size", "20")
            .build()
            .toString()
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
            return parseLiveRoomItems(json).map {
                FollowedLiveRoom(roomId = it.roomId, uname = it.uname, title = it.title)
            }
        }

        fun parseLiveRoomItems(json: String): List<LiveRoomItem> {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONObject("data") ?: return emptyList()
            val rooms = data.optJSONArray("rooms")
                ?: data.optJSONArray("list")
                ?: data.optJSONArray("items")
                ?: data.optJSONObject("result")?.optJSONArray("list")
                ?: data.optJSONObject("result")?.optJSONArray("live_room")
                ?: data.optJSONObject("list")?.optJSONArray("data")
                ?: return emptyList()
            return parseLiveRoomItemArray(rooms, onlyLive = true)
        }

        fun parseLiveAreas(json: String): List<LiveArea> {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONObject("data")
            val parentAreas = if (data != null) {
                data.optJSONArray("data")
                    ?: data.optJSONArray("list")
                    ?: data.optJSONArray("areas")
            } else {
                root.optJSONArray("data")
            } ?: return emptyList()
            val result = mutableListOf<LiveArea>()
            for (parentIndex in 0 until parentAreas.length()) {
                val parent = parentAreas.optJSONObject(parentIndex) ?: continue
                val parentId = parent.optLong("id", parent.optLong("parent_id", 0L))
                val parentName = parent.optString("name", parent.optString("parent_name", "分区"))
                val children = parent.optJSONArray("list")
                    ?: parent.optJSONArray("children")
                    ?: JSONArray().put(parent)
                for (childIndex in 0 until children.length()) {
                    val child = children.optJSONObject(childIndex) ?: continue
                    val areaId = child.optLong("id", child.optLong("area_id", 0L))
                    if (parentId <= 0L || areaId <= 0L) continue
                    result += LiveArea(
                        parentId = parentId,
                        areaId = areaId,
                        parentName = parentName,
                        areaName = child.optString("name", child.optString("area_name", "子分区"))
                    )
                }
            }
            return result
        }

        fun parseSearchLiveRooms(json: String): List<LiveRoomItem> {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 0) return emptyList()
            val data = root.optJSONObject("data") ?: return emptyList()
            val resultObject = data.optJSONObject("result")
            val rooms = resultObject?.optJSONArray("live_room")
                ?: resultObject?.optJSONArray("room")
                ?: data.optJSONArray("result")
                ?: data.optJSONArray("list")
                ?: return emptyList()
            return parseLiveRoomItemArray(rooms, onlyLive = false)
        }

        private fun parseLiveRoomItemArray(rooms: org.json.JSONArray, onlyLive: Boolean): List<LiveRoomItem> {
            val result = mutableListOf<LiveRoomItem>()
            for (index in 0 until rooms.length()) {
                val item = rooms.optJSONObject(index) ?: continue
                if (onlyLive && item.optInt("live_status", 1) != 1) continue
                val roomId = extractRoomId(item)
                if (roomId <= 0L) continue
                result += LiveRoomItem(
                    roomId = roomId,
                    uname = item.optString("uname", item.optString("name", "未命名主播")),
                    title = cleanTitle(item.optString("title", "直播中"))
                )
            }
            return result
        }

        private fun extractRoomId(item: JSONObject): Long {
            return when {
                item.optLong("roomid", 0L) > 0L -> item.optLong("roomid")
                item.optLong("room_id", 0L) > 0L -> item.optLong("room_id")
                item.optLong("roomid_str", 0L) > 0L -> item.optLong("roomid_str")
                item.optLong("id", 0L) > 0L -> item.optLong("id")
                else -> 0L
            }
        }

        private fun cleanTitle(value: String): String {
            return value
                .replace("<em class=\"keyword\">", "")
                .replace("</em>", "")
                .ifBlank { "直播中" }
        }
    }
}

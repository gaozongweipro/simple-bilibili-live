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

data class FollowedLiveRoom(
    val roomId: Long,
    val uname: String,
    val title: String
)

data class LiveRoomItem(
    val roomId: Long,
    val uname: String,
    val title: String
)

data class LiveArea(
    val parentId: Long,
    val areaId: Long,
    val parentName: String,
    val areaName: String
) {
    val displayName: String
        get() = "$parentName / $areaName"
}

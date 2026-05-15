package com.simplebilibili.live.ui

import com.simplebilibili.live.biliapi.FollowedLiveRoom
import com.simplebilibili.live.biliapi.LiveArea
import com.simplebilibili.live.biliapi.LiveRoomItem

sealed class UiState {
    object Loading : UiState()
    object Login : UiState()
    data class FollowedLives(val rooms: List<LiveRoomItem>) : UiState()
    data class LiveAreas(val areas: List<LiveArea>) : UiState()
    data class AreaLiveRooms(val title: String, val rooms: List<LiveRoomItem>) : UiState()
    data class SearchResults(val keyword: String, val rooms: List<LiveRoomItem>) : UiState()
    object RoomEntry : UiState()
    data class Playing(val roomId: Long, val title: String) : UiState()
    data class Error(val message: String, val canRetry: Boolean) : UiState()
}

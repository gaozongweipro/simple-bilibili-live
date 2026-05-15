package com.simplebilibili.live.ui

import com.simplebilibili.live.biliapi.FollowedLiveRoom

sealed class UiState {
    object Loading : UiState()
    object Login : UiState()
    data class FollowedLives(val rooms: List<FollowedLiveRoom>) : UiState()
    object RoomEntry : UiState()
    data class Playing(val roomId: Long, val title: String) : UiState()
    data class Error(val message: String, val canRetry: Boolean) : UiState()
}

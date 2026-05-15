package com.simplebilibili.live.auth

data class QrLoginToken(
    val url: String,
    val qrcodeKey: String
)

sealed class QrLoginStatus {
    object Waiting : QrLoginStatus()
    object Scanned : QrLoginStatus()
    data class Success(val cookie: String) : QrLoginStatus()
    data class Expired(val message: String) : QrLoginStatus()
    data class Failed(val message: String) : QrLoginStatus()
}

data class LoginValidation(
    val loggedIn: Boolean,
    val message: String
)

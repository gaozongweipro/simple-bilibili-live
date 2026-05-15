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
        get() = prefs.getInt(KEY_PREFERRED_QUALITY, DEFAULT_QUALITY)
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
        private const val DEFAULT_QUALITY = 400
    }
}

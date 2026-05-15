package com.simplebilibili.live.player

import java.util.concurrent.atomic.AtomicInteger

class PlaybackRequestGate {
    private val latestRequest = AtomicInteger(0)

    fun nextRequest(): Int = latestRequest.incrementAndGet()

    fun isLatest(requestId: Int): Boolean = latestRequest.get() == requestId
}

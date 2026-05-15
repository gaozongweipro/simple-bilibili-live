package com.simplebilibili.live.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestGateTest {
    @Test
    fun onlyLatestPlaybackRequestCanUpdateUi() {
        val gate = PlaybackRequestGate()

        val first = gate.nextRequest()
        val second = gate.nextRequest()

        assertFalse(gate.isLatest(first))
        assertTrue(gate.isLatest(second))
    }
}

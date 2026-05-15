package com.simplebilibili.live.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AspectRatioSizeResolverTest {
    @Test
    fun fitsWideVideoInsideNarrowerContainerWithoutStretching() {
        val size = AspectRatioSizeResolver.fit(
            containerWidth = 800,
            containerHeight = 400,
            videoWidth = 1920,
            videoHeight = 1080
        )

        assertEquals(SurfaceSize(711, 400), size)
    }

    @Test
    fun fitsFourByThreeVideoInsideWideContainerWithoutStretching() {
        val size = AspectRatioSizeResolver.fit(
            containerWidth = 800,
            containerHeight = 400,
            videoWidth = 640,
            videoHeight = 480
        )

        assertEquals(SurfaceSize(533, 400), size)
    }
}

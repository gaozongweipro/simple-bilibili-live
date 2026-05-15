package com.simplebilibili.live.player

data class SurfaceSize(
    val width: Int,
    val height: Int
)

object AspectRatioSizeResolver {
    fun fit(containerWidth: Int, containerHeight: Int, videoWidth: Int, videoHeight: Int): SurfaceSize {
        if (containerWidth <= 0 || containerHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return SurfaceSize(containerWidth.coerceAtLeast(0), containerHeight.coerceAtLeast(0))
        }
        val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()
        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        return if (videoRatio > containerRatio) {
            SurfaceSize(containerWidth, (containerWidth / videoRatio).toInt())
        } else {
            SurfaceSize((containerHeight * videoRatio).toInt(), containerHeight)
        }
    }
}

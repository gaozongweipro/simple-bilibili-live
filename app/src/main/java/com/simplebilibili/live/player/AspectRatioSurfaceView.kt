package com.simplebilibili.live.player

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView

class AspectRatioSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    fun setVideoSize(width: Int, height: Int) {
        if (videoWidth == width && videoHeight == height) return
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        val containerHeight = MeasureSpec.getSize(heightMeasureSpec)
        val size = AspectRatioSizeResolver.fit(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            videoWidth = videoWidth,
            videoHeight = videoHeight
        )
        setMeasuredDimension(size.width, size.height)
    }
}

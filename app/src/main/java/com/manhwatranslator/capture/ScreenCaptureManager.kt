package com.manhwatranslator.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics

/**
 * Wraps MediaProjection + ImageReader. Continuously keeps the most recently captured
 * frame in memory (cheap - just holds a bitmap reference, no processing) so the floating
 * button can grab an up-to-date screenshot on demand for tap-to-translate.
 */
class ScreenCaptureManager(
    private val projection: MediaProjection,
    private val metrics: DisplayMetrics
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false

    @Volatile private var latestFrame: Bitmap? = null

    fun start() {
        // Required by Android: a callback must be registered before createVirtualDisplay.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, handler)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "ManhwaTranslatorCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                latestFrame = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } finally {
                image.close()
            }
        }, handler)
    }

    /** Most recently captured frame, or null if nothing captured yet. */
    fun getLatestFrame(): Bitmap? = latestFrame

    fun stop() {
        if (stopped) return
        stopped = true
        virtualDisplay?.release()
        imageReader?.close()
        runCatching { projection.stop() }
    }
}

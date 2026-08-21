package com.manhwatranslator.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.manhwatranslator.R
import com.manhwatranslator.capture.ScreenCaptureManager
import com.manhwatranslator.pipeline.TapTranslationPipeline
import kotlinx.coroutines.launch

class TranslationService : LifecycleService() {

    interface StateListener {
        fun onStateChanged(running: Boolean)
    }

    companion object {
        const val CHANNEL_ID = "translation_channel"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.manhwatranslator.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: TranslationService? = null

        private val listeners = mutableListOf<StateListener>()

        fun addListener(listener: StateListener) {
            listeners.add(listener)
            listener.onStateChanged(isRunning)
        }

        fun removeListener(listener: StateListener) {
            listeners.remove(listener)
        }

        private fun setRunning(running: Boolean) {
            isRunning = running
            listeners.forEach { it.onStateChanged(running) }
        }

        fun captureCurrentFrame(): Bitmap? = instance?.captureManager?.getLatestFrame()

        suspend fun translateCrop(crop: Bitmap): String? = instance?.currentPipeline()?.translateCrop(crop)

        /** Shows/hides the bottom translation panel without losing its history. */
        fun setBottomPanelVisible(visible: Boolean) {
            instance?.currentPipeline()?.bottomPanel?.setVisible(visible)
        }
    }

    private var captureManager: ScreenCaptureManager? = null
    private lateinit var pipeline: TapTranslationPipeline

    private fun currentPipeline(): TapTranslationPipeline? = if (::pipeline.isInitialized) pipeline else null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, buildNotification())
        pipeline = TapTranslationPipeline(applicationContext)
        setRunning(true)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData: Intent? = intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultData != null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }

            captureManager = ScreenCaptureManager(projection, metrics)
            lifecycleScope.launch {
                pipeline.ensureReady()
                captureManager?.start()
            }
        }
        return START_STICKY
    }

    private val windowManager get() = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        captureManager?.stop()
        if (::pipeline.isInitialized) pipeline.stop()
        setRunning(false)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

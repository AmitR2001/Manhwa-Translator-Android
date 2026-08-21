package com.manhwatranslator.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.manhwatranslator.service.TranslationService
import com.manhwatranslator.ui.ScreenCaptureRequestActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draggable floating button, drives the tap-to-translate flow:
 * - Tap while stopped -> starts the session; the live tap-catcher (a small band) arms
 *   automatically once capture is ready (gray -> green), bottom panel visible.
 * - Tap while running -> toggles ARMED (green, bottom panel visible) vs PAUSED (amber,
 *   bottom panel hidden so Komikku/Android UI is fully reachable). Scrolling (volume
 *   keys, auto-scroll) is never affected either way.
 * - Long-press (~0.5s) while running -> stops the session entirely (back to gray).
 */
class FloatingButtonService : Service(), TranslationService.StateListener {

    private lateinit var windowManager: WindowManager
    private var buttonView: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tapCatcher: LiveTapCatcher? = null

    private var downX = 0f
    private var downY = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private var downTime = 0L
    private val longPressMs = 500L

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val button = TextView(this).apply {
            text = "T"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            background = makeCircleDrawable(TranslationService.isRunning, true)
        }
        buttonView = button

        params = WindowManager.LayoutParams(
            140, 140,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x.toFloat()
                    downY = params.y.toFloat()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > 12 || abs(dy) > 12) isDragging = true
                    params.x = (downX + dx).toInt()
                    params.y = (downY + dy).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val heldMs = System.currentTimeMillis() - downTime
                    if (!isDragging) {
                        if (heldMs >= longPressMs) onLongPress() else onButtonTapped()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(button, params)
        TranslationService.addListener(this)
    }

    private fun onButtonTapped() {
        if (TranslationService.isRunning) {
            val catcher = tapCatcher ?: return
            val newArmed = !catcher.isArmed()
            catcher.setArmed(newArmed)
            TranslationService.setBottomPanelVisible(newArmed)
            updateButtonColor()
        } else {
            val intent = Intent(this, ScreenCaptureRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private fun onLongPress() {
        if (TranslationService.isRunning) {
            tapCatcher?.hide()
            tapCatcher = null
            startService(Intent(this, TranslationService::class.java).setAction(TranslationService.ACTION_STOP))
        }
    }

    private fun setupCatcher() {
        val catcher = LiveTapCatcher(this)
        tapCatcher = catcher
        catcher.frameProvider = { TranslationService.captureCurrentFrame() }
        catcher.onTap = { crop, _, updateResult ->
            scope.launch {
                val text = TranslationService.translateCrop(crop)
                updateResult(text)
            }
        }
        catcher.show()
        updateButtonColor()
    }

    private fun autoArmWhenFrameReady() {
        scope.launch {
            repeat(20) {
                if (!TranslationService.isRunning) return@launch
                if (TranslationService.captureCurrentFrame() != null) {
                    setupCatcher()
                    return@launch
                }
                delay(150)
            }
        }
    }

    override fun onStateChanged(running: Boolean) {
        if (running) {
            autoArmWhenFrameReady()
        } else {
            tapCatcher?.hide()
            tapCatcher = null
        }
        updateButtonColor()
    }

    private fun updateButtonColor() {
        val running = TranslationService.isRunning
        val armed = tapCatcher?.isArmed() ?: true
        buttonView?.background = makeCircleDrawable(running, armed)
    }

    private fun makeCircleDrawable(running: Boolean, armed: Boolean): GradientDrawable {
        val color = when {
            !running -> "#555555"
            armed -> "#2ECC71"
            else -> "#F39C12"
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }
    }

    override fun onDestroy() {
        TranslationService.removeListener(this)
        tapCatcher?.hide()
        scope.cancel()
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

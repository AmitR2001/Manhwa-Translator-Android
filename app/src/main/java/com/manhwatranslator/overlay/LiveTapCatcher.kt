package com.manhwatranslator.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.manhwatranslator.pipeline.PipelineSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay limited to a fixed-height band in the vertical middle of the screen. Always
 * FLAG_NOT_FOCUSABLE (volume-key scroll etc. keep working). Uses REAL display metrics so
 * the visible box and the actual captured pixels stay aligned.
 *
 * Gestures:
 * - Quick tap (no dragging): default-size box (PipelineSettings.defaultCropWidthCm x
 *   defaultCropHeightCm), top edge at the tap point, horizontally centered. Translates
 *   on release.
 * - Double-tap then hold+drag (second tap doesn't lift, moves instead): draws a
 *   freeform selection rectangle from that second tap's point to wherever you drag.
 * - Drag starting on the BODY of the currently visible box: moves it (same size) to a
 *   new spot; releasing re-translates there.
 * - Drag starting on the small handle at the box's BOTTOM-RIGHT CORNER: resizes it
 *   (top-left stays fixed, the corner follows your finger); releasing re-translates
 *   with the new size.
 * Only one translation box is shown at a time.
 */
class LiveTapCatcher(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var metrics: DisplayMetrics

    private var armed = true
    private var bandTopPx = 0
    private var bandHeightPx = 0

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val moveSlopPx = 16

    private var lastUpTime = 0L
    private var lastUpX = 0f
    private var lastUpY = 0f
    private val doubleTapWindowMs = 300L
    private val doubleTapSlopPx = 60
    private val cornerHandleRadiusPx = 55
    private val minBoxPx = 80

    private enum class Mode { NONE, NEW_FREEFORM, MOVE, CORNER_RESIZE }
    private var mode = Mode.NONE

    private var previewOutline: View? = null
    private var previewLeft = 0
    private var previewTop = 0
    private var previewWidth = 0
    private var previewHeight = 0

    private var currentBoxRect: Rect? = null // local band coordinates
    private var currentGroup: View? = null

    var frameProvider: (() -> Bitmap?)? = null
    var onTap: ((crop: Bitmap, box: Rect, updateResult: (String?) -> Unit) -> Unit)? = null

    fun show() {
        if (rootView != null) return

        metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenHeight = metrics.heightPixels
        bandHeightPx = (PipelineSettings.tapBandHeightCm / 2.54f * metrics.ydpi).toInt()
        bandTopPx = ((screenHeight - bandHeightPx) / 2).coerceAtLeast(0)

        val container = FrameLayout(context)
        container.setOnTouchListener { _, event -> handleTouch(event) }
        rootView = container

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            bandHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }
        applyArmedState()
        windowManager.addView(container, params)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                mode = Mode.NONE

                val downTime = System.currentTimeMillis()
                val isSecondTapOfDoubleTap =
                    (downTime - lastUpTime) <= doubleTapWindowMs &&
                        abs(event.x - lastUpX) <= doubleTapSlopPx &&
                        abs(event.y - lastUpY) <= doubleTapSlopPx

                val box = currentBoxRect
                val isOnCornerHandle = box != null &&
                    abs(event.x - box.right) <= cornerHandleRadiusPx &&
                    abs(event.y - box.bottom) <= cornerHandleRadiusPx
                val isOnExistingBox = box != null &&
                    event.x >= box.left && event.x <= box.right &&
                    event.y >= box.top && event.y <= box.bottom

                when {
                    isSecondTapOfDoubleTap -> {
                        mode = Mode.NEW_FREEFORM
                        startFreeformPreview(downX.toInt(), downY.toInt())
                    }
                    isOnCornerHandle -> {
                        mode = Mode.CORNER_RESIZE
                        startCornerResizePreview(box!!)
                    }
                    isOnExistingBox -> {
                        mode = Mode.MOVE
                        startMovePreview(box!!)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > moveSlopPx || dy > moveSlopPx) moved = true

                when (mode) {
                    Mode.NEW_FREEFORM -> updateFreeformPreview(event.x.toInt(), event.y.toInt())
                    Mode.MOVE -> updateMovePreview(event.x.toInt(), event.y.toInt())
                    Mode.CORNER_RESIZE -> updateCornerResizePreview(event.x.toInt(), event.y.toInt())
                    Mode.NONE -> { /* stationary hold in progress */ }
                }
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.NEW_FREEFORM, Mode.MOVE, Mode.CORNER_RESIZE ->
                        finalizeTapRect(previewLeft, previewTop, previewWidth, previewHeight)
                    Mode.NONE -> {
                        if (!moved) {
                            val w = defaultCropWidthPx()
                            val h = defaultCropHeightPx()
                            finalizeTapRect(downX.toInt() - w / 2, downY.toInt(), w, h)
                        }
                        // moved without entering a recognized gesture -> ignore (accidental)
                    }
                }
                clearPreview()
                lastUpTime = System.currentTimeMillis()
                lastUpX = event.x
                lastUpY = event.y
                mode = Mode.NONE
            }
        }
        return true
    }

    private fun defaultCropWidthPx(): Int = (PipelineSettings.defaultCropWidthCm / 2.54f * metrics.xdpi).toInt()
    private fun defaultCropHeightPx(): Int = (PipelineSettings.defaultCropHeightCm / 2.54f * metrics.ydpi).toInt()

    private fun startFreeformPreview(startX: Int, startY: Int) {
        previewLeft = startX
        previewTop = startY
        previewWidth = minBoxPx
        previewHeight = minBoxPx
        drawPreviewOutline("#664CAF50", "#224CAF50")
    }

    private fun updateFreeformPreview(curX: Int, curY: Int) {
        val left = min(downX.toInt(), curX)
        val top = min(downY.toInt(), curY)
        val right = max(downX.toInt(), curX)
        val bottom = max(downY.toInt(), curY)
        previewLeft = left
        previewTop = top
        previewWidth = (right - left).coerceAtLeast(minBoxPx)
        previewHeight = (bottom - top).coerceAtLeast(minBoxPx)
        updatePreviewOutlineBounds()
    }

    private fun startMovePreview(box: Rect) {
        previewLeft = box.left
        previewTop = box.top
        previewWidth = box.width()
        previewHeight = box.height()
        drawPreviewOutline("#6603A9F4", "#2203A9F4")
    }

    private fun updateMovePreview(curX: Int, curY: Int) {
        val dx = curX - downX.toInt()
        val dy = curY - downY.toInt()
        val box = currentBoxRect ?: return
        previewLeft = box.left + dx
        previewTop = box.top + dy
        updatePreviewOutlineBounds()
    }

    private fun startCornerResizePreview(box: Rect) {
        previewLeft = box.left
        previewTop = box.top
        previewWidth = box.width()
        previewHeight = box.height()
        drawPreviewOutline("#66FF9800", "#22FF9800")
    }

    private fun updateCornerResizePreview(curX: Int, curY: Int) {
        val box = currentBoxRect ?: return
        previewLeft = box.left
        previewTop = box.top
        previewWidth = (curX - box.left).coerceAtLeast(minBoxPx)
        previewHeight = (curY - box.top).coerceAtLeast(minBoxPx)
        updatePreviewOutlineBounds()
    }

    private fun drawPreviewOutline(strokeColor: String, fillColor: String) {
        val root = rootView ?: return
        val outline = View(context).apply {
            background = GradientDrawable().apply {
                setStroke(3, Color.parseColor(strokeColor))
                setColor(Color.parseColor(fillColor))
            }
        }
        previewOutline = outline
        root.addView(
            outline,
            FrameLayout.LayoutParams(previewWidth, previewHeight).apply {
                leftMargin = previewLeft
                topMargin = previewTop
            }
        )
    }

    private fun updatePreviewOutlineBounds() {
        val outline = previewOutline ?: return
        val lp = outline.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = previewWidth
        lp.height = previewHeight
        lp.leftMargin = previewLeft
        lp.topMargin = previewTop
        outline.layoutParams = lp
    }

    private fun clearPreview() {
        previewOutline?.let { rootView?.removeView(it) }
        previewOutline = null
    }

    private fun applyArmedState() {
        params.flags = if (armed) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val root = rootView ?: return
        root.background = if (armed) {
            GradientDrawable().apply {
                setStroke(3, Color.parseColor("#88CCCCCC"))
                setColor(Color.parseColor("#22CCCCCC"))
            }
        } else {
            null
        }
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    fun setArmed(value: Boolean) {
        armed = value
        applyArmedState()
    }

    fun isArmed(): Boolean = armed

    private fun finalizeTapRect(localLeft: Int, localTop: Int, boxW: Int, boxH: Int) {
        val bitmap = frameProvider?.invoke() ?: return
        val root = rootView ?: return

        val fullLeft = localLeft
        val fullTop = bandTopPx + localTop

        val left = fullLeft.coerceIn(0, (bitmap.width - boxW).coerceAtLeast(0))
        val top = fullTop.coerceIn(0, (bitmap.height - boxH).coerceAtLeast(0))
        val width = boxW.coerceAtMost(bitmap.width - left)
        val height = boxH.coerceAtMost(bitmap.height - top)
        if (width <= 0 || height <= 0) return

        val box = Rect(left, top, left + width, top + height)
        val localBoxTop = box.top - bandTopPx
        val localBoxBottom = localBoxTop + box.height()

        currentGroup?.let { runCatching { root.removeView(it) } }

        val group = FrameLayout(context)
        val outline = View(context).apply {
            background = GradientDrawable().apply {
                setStroke(4, Color.parseColor("#FFEB3B"))
                setColor(Color.TRANSPARENT)
            }
        }
        group.addView(
            outline,
            FrameLayout.LayoutParams(box.width(), box.height()).apply {
                leftMargin = box.left
                topMargin = localBoxTop
            }
        )

        // Small visible handle at the bottom-right corner - grab and drag it to resize.
        val handleSize = 28
        val handle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFEB3B"))
                setStroke(2, Color.parseColor("#88000000"))
            }
        }
        group.addView(
            handle,
            FrameLayout.LayoutParams(handleSize, handleSize).apply {
                leftMargin = box.left + box.width() - handleSize / 2
                topMargin = localBoxBottom - handleSize / 2
            }
        )

        val bubble = TextView(context).apply {
            text = "..."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(16, 8, 16, 8)
            maxWidth = box.width().coerceAtLeast(300)
        }

        val boxCenterY = localBoxTop + box.height() / 2
        val showAbove = boxCenterY > bandHeightPx / 2

        val bubbleParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        if (showAbove) {
            bubbleParams.gravity = Gravity.BOTTOM or Gravity.START
            bubbleParams.leftMargin = box.left
            bubbleParams.bottomMargin = (bandHeightPx - localBoxTop) + 8
        } else {
            bubbleParams.gravity = Gravity.TOP or Gravity.START
            bubbleParams.leftMargin = box.left
            bubbleParams.topMargin = localBoxBottom + 8
        }
        group.addView(bubble, bubbleParams)

        root.addView(
            group,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        currentGroup = group
        currentBoxRect = Rect(box.left, localBoxTop, box.left + box.width(), localBoxBottom)

        val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
        onTap?.invoke(crop, box) { resultText ->
            bubble.text = resultText ?: "(no Korean text found here)"
        }
    }

    fun hide() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        currentGroup = null
        currentBoxRect = null
        previewOutline = null
    }
}

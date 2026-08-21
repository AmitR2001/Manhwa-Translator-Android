package com.manhwatranslator.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Strip pinned to the bottom of the screen, listing translations in order. setVisible()
 * toggles it on/off (e.g. hidden while paused, to free up screen space for Komikku/Android
 * UI) WITHOUT losing history - only hide() fully clears everything (used on session stop).
 */
class BottomTranslationPanel(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: ScrollView? = null
    private var listContainer: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var isAttached = false
    private val shownTexts = LinkedHashSet<String>()
    private val maxEntries = 50
    private val maxHeightPx = 260

    fun show() {
        if (rootView == null) {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
            }
            val scroll = ScrollView(context).apply {
                addView(container)
                setBackgroundColor(Color.parseColor("#CC000000"))
            }
            listContainer = container
            rootView = scroll
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                maxHeightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }
        }
        if (!isAttached) {
            windowManager.addView(rootView, params)
            isAttached = true
        }
    }

    /** Toggle visibility without losing history. */
    fun setVisible(visible: Boolean) {
        val root = rootView ?: return
        val p = params ?: return
        if (visible && !isAttached) {
            windowManager.addView(root, p)
            isAttached = true
        } else if (!visible && isAttached) {
            runCatching { windowManager.removeView(root) }
            isAttached = false
        }
    }

    fun addLine(translatedText: String) {
        val normalized = translatedText.trim()
        if (normalized.isEmpty() || normalized in shownTexts) return

        shownTexts.add(normalized)
        if (shownTexts.size > maxEntries) {
            val oldest = shownTexts.first()
            shownTexts.remove(oldest)
            listContainer?.let { if (it.childCount > 0) it.removeViewAt(0) }
        }

        val line = TextView(context).apply {
            text = normalized
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 4, 0, 4)
        }
        listContainer?.addView(line)
        rootView?.post { rootView?.fullScroll(View.FOCUS_DOWN) }
    }

    fun hide() {
        if (isAttached) runCatching { windowManager.removeView(rootView) }
        isAttached = false
        rootView = null
        listContainer = null
        shownTexts.clear()
    }
}

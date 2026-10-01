package com.bydmate.app.camping

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bydmate.app.R
import com.bydmate.app.util.appLocalizedContext

/**
 * The windows camping mode puts on screen. All calls on the main thread.
 *
 *  - Center screen, [CampingSettings.screenOff] on: a full-screen black window that also asks for
 *    the backlight to go off (window brightness override — no settings permission needed). Any
 *    tap or key shows the Stop panel and brings the backlight back; it goes dark again on its own.
 *  - Center screen, screen-off off: only a small «Stop camping» button in the corner.
 *  - Cluster, [CampingSettings.clusterOff] on: a black window on the cluster projection surface.
 *    It is only visible while the cluster compositor shows that surface, which
 *    [CampingController] takes care of.
 */
class CampingOverlay(
    context: Context,
    private val onStop: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val text = appContext.appLocalizedContext()
    private val handler = Handler(Looper.getMainLooper())
    private val hidePanel = Runnable { setPanelVisible(false) }

    private var mainView: FrameLayout? = null
    private var mainParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var statusView: TextView? = null
    private var clusterView: View? = null
    private var clusterWm: WindowManager? = null
    private var dark = false

    fun showMain(screenOff: Boolean) {
        if (mainView != null) return
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dark = screenOff
        val root = FrameLayout(appContext)
        val params: WindowManager.LayoutParams
        if (screenOff) {
            root.setBackgroundColor(Color.BLACK)
            root.isFocusable = true
            root.isFocusableInTouchMode = true
            root.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN && panel?.visibility != View.VISIBLE) {
                    setPanelVisible(true)
                    true
                } else {
                    false
                }
            }
            root.setOnKeyListener { _, _, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN) setPanelVisible(true)
                true
            }
            val p = buildPanel()
            root.addView(p, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            panel = p
            p.visibility = View.GONE
            @Suppress("DEPRECATION")
            root.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE,
            ).apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF }
        } else {
            root.addView(stopButton(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = dp(24)
                y = dp(24)
            }
        }
        try {
            wm.addView(root, params)
            mainView = root
            mainParams = params
            if (screenOff) root.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "main overlay failed: ${e.message}")
        }
    }

    /** Black window on the cluster display. False when the window could not be added. */
    fun showCluster(display: Display): Boolean {
        if (clusterView != null) return true
        return try {
            val displayContext = appContext.createDisplayContext(display)
            val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(displayContext).apply { setBackgroundColor(Color.BLACK) }
            wm.addView(v, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE,
            ))
            clusterView = v
            clusterWm = wm
            true
        } catch (e: Exception) {
            Log.e(TAG, "cluster overlay failed: ${e.message}")
            false
        }
    }

    fun updateStatus(line: String) {
        statusView?.text = line
    }

    fun hideAll() {
        handler.removeCallbacks(hidePanel)
        mainView?.let { v ->
            runCatching { (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        clusterView?.let { v -> runCatching { clusterWm?.removeView(v) } }
        mainView = null
        mainParams = null
        panel = null
        statusView = null
        clusterView = null
        clusterWm = null
    }

    private fun setPanelVisible(visible: Boolean) {
        val root = mainView ?: return
        val params = mainParams ?: return
        if (!dark) return
        panel?.visibility = if (visible) View.VISIBLE else View.GONE
        params.screenBrightness = if (visible) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        }
        runCatching {
            (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, params)
        }
        handler.removeCallbacks(hidePanel)
        if (visible) handler.postDelayed(hidePanel, PANEL_TIMEOUT_MS)
    }

    private fun buildPanel(): View {
        val column = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(28), dp(32), dp(28))
            background = GradientDrawable().apply {
                setColor(PANEL_COLOR)
                cornerRadius = dp(20).toFloat()
            }
            // Taps inside the panel must not fall through to the dark root.
            isClickable = true
        }
        column.addView(TextView(appContext).apply {
            setText(text.getString(R.string.camping_overlay_title))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        })
        val status = TextView(appContext).apply {
            setTextColor(STATUS_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }
        statusView = status
        column.addView(status)
        column.addView(stopButton(), LinearLayout.LayoutParams(dp(360), dp(72)))
        column.addView(Button(appContext).apply {
            setText(text.getString(R.string.camping_overlay_keep_dark))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), STATUS_COLOR)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { setPanelVisible(false) }
        }, LinearLayout.LayoutParams(dp(360), dp(56)).apply { topMargin = dp(12) })
        return column
    }

    private fun stopButton(): Button = Button(appContext).apply {
        setText(text.getString(R.string.camping_stop))
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        isAllCaps = false
        setPadding(dp(28), dp(12), dp(28), dp(12))
        background = GradientDrawable().apply {
            setColor(STOP_COLOR)
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onStop() }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), appContext.resources.displayMetrics).toInt()

    companion object {
        private const val TAG = "CampingOverlay"
        private const val PANEL_TIMEOUT_MS = 15_000L
        private val PANEL_COLOR = Color.rgb(0x16, 0x1E, 0x2E)
        private val STATUS_COLOR = Color.rgb(0x9A, 0xA7, 0xBD)
        private val STOP_COLOR = Color.rgb(0xD9, 0x3B, 0x3B)

        /**
         * The cluster surface visible to the app uid: the full-cluster XDJAScreenProjection mirror
         * («_0») first, the same family [com.bydmate.app.cluster.ClusterProjectionManager] uses.
         */
        fun findClusterDisplay(context: Context): Display? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val projection = dm.displays.filter { it.name.contains("XDJAScreenProjection", ignoreCase = true) }
            val name = com.bydmate.app.cluster.pickProjectionDisplayName(projection.map { it.name }, preferFull = true)
            return projection.firstOrNull { it.name == name }
        }
    }
}

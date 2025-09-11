package org.wikipedia.bitdriftdev

import android.app.Activity
import android.app.Application
import android.os.*
import android.view.*
import androidx.core.view.ViewCompat
import io.bitdrift.capture.Capture.Logger
import kotlin.math.abs

/**
 * Adds a one-finger long-press (~1s) listener to every Activity window.
 * Trigger area is a bottom-right "safe corner" to avoid clashes with other long-press UI.
 * When triggered, shows a simple dialog with:
 *  - Start Slow Leak
 *  - Force OOM Now
 *
 * The overlay does NOT consume touches (returns false), so the app stays usable.
 */
object GlobalDebugGesture : Application.ActivityLifecycleCallbacks {

    private const val PRESS_MS = 1000L

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        attachOverlay(activity)
    }

    private fun attachOverlay(activity: Activity) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>("bitdriftdev-overlay") != null) return

        val overlay = object : View(activity) {
            private var handler: Handler? = null
            private var armed = false
            private var downX = 0f
            private var downY = 0f
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 0.6f // stricter

            // Safe-corner geometry (bottom-right 96dp box with 24dp margin)
            private val density get() = resources.displayMetrics.density
            private val boxPx get() = (96 * density)
            private val marginPx get() = (24 * density)

            private fun isInSafeCorner(x: Float, y: Float): Boolean {
                val root = (parent as? ViewGroup) ?: return false
                val minX = root.width - boxPx - marginPx
                val minY = root.height - boxPx - marginPx
                return x >= minX && y >= minY
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Only arm if the DOWN occurs in the safe corner
                        if (!armed && isInSafeCorner(ev.x, ev.y)) {
                            armed = true
                            downX = ev.x
                            downY = ev.y
                            if (handler == null) handler = Handler(Looper.getMainLooper())
                            handler?.postDelayed({
                                if (armed) {
                                    // Disarm before showing to avoid stuck state if dialog steals focus
                                    armed = false
                                    showDialog(activity)
                                }
                            }, PRESS_MS)
                        } else {
                            armed = false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (armed) {
                            val dx = abs(ev.x - downX)
                            val dy = abs(ev.y - downY)
                            if (dx > touchSlop || dy > touchSlop) cancel()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
                }
                return false // don’t consume; app still gets touches
            }

            private fun cancel() {
                armed = false
                handler?.removeCallbacksAndMessages(null)
            }
        }

        overlay.tag = "bitdriftdev-overlay"
        overlay.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        overlay.isClickable = true
        overlay.isFocusable = false
        overlay.isHapticFeedbackEnabled = false
        ViewCompat.setImportantForAccessibility(overlay, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO)

        decor.addView(overlay)
    }

    private fun showDialog(activity: Activity) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("Debug Menu")
            .setItems(arrayOf("Start Slow Leak (+256 KB / 500ms)", "Force OOM Now")) { _, which ->
                if (which == 0) {
                    DebugLeaker.startSlowLeak()
                    Logger.logWarning(
                        mapOf(
                            "type" to "slowLeak",
                            "rate" to "256KB/500ms",
                            "screen" to (activity::class.java.simpleName ?: "UnknownActivity")
                        )
                    ) { "Memory Leak Simulation Started" }
                } else {
                    DebugLeaker.forceOutOfMemoryCrash()
                    Logger.logWarning(
                        mapOf(
                            "type" to "forceOOM",
                            "chunk" to "1MB/50ms",
                            "screen" to (activity::class.java.simpleName ?: "UnknownActivity")
                        )
                    ) { "Force OOM Simulation Started" }
                }
            }
            // Re-arm reliably even if the user taps outside the dialog
            .setOnDismissListener { /* no-op; overlay re-arms on next DOWN */ }
            .show()
    }

    // Unused callbacks:
    override fun onActivityCreated(a: Activity, s: android.os.Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}

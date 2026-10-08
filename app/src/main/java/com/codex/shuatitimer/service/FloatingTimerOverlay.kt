package com.codex.shuatitimer.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.preferences.FloatingCapsuleSettings
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.TimeUtils
import kotlin.math.abs

/**
 * A compact, user-authorized timer window displayed immediately below the status bar.
 * It is deliberately implemented with platform Views so it has no Activity lifecycle needs.
 */
internal class FloatingTimerOverlay(
    context: Context,
    private val onToggleRunning: () -> Unit,
    private val onFinish: () -> Unit
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val settings = FloatingCapsuleSettings(appContext)
    private val density = appContext.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private var root: LinearLayout? = null
    private var subjectView: TextView? = null
    private var timerView: TextView? = null
    private var statusDot: View? = null
    private var toggleButton: TextView? = null
    private var finishButton: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var added = false
    private var expanded = false
    private var latestSnapshot = TimerStateStore.Snapshot()

    fun sync(snapshot: TimerStateStore.Snapshot) {
        latestSnapshot = snapshot
        if (!snapshot.active || !settings.enabled || !Settings.canDrawOverlays(appContext)) {
            hide()
            return
        }
        ensureShown()
        render(snapshot)
    }

    fun hide() {
        val view = root ?: return
        if (added) {
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The system may already have removed the window after permission revocation.
            } catch (_: SecurityException) {
                // Permission may be revoked while the timer is running.
            }
        }
        added = false
        root = null
        subjectView = null
        timerView = null
        statusDot = null
        toggleButton = null
        finishButton = null
        layoutParams = null
    }

    private fun ensureShown() {
        if (added) return
        val view = buildView()
        val saved = settings.savedPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved?.x ?: defaultX()
            y = saved?.y ?: (statusBarHeight() + dp(8))
            windowAnimations = android.R.style.Animation_Dialog
        }
        try {
            windowManager.addView(view, params)
            root = view
            layoutParams = params
            added = true
            view.post { clampAndUpdatePosition() }
        } catch (_: WindowManager.BadTokenException) {
            hide()
        } catch (_: SecurityException) {
            hide()
        }
    }

    private fun buildView(): LinearLayout {
        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(8), dp(10), dp(8))
            background = roundedBackground(COLOR_PAPER, dp(22).toFloat(), COLOR_BORDER)
            elevation = dp(8).toFloat()
            minimumHeight = dp(44)
            contentDescription = "题刻悬浮计时胶囊，轻触展开"
        }

        statusDot = View(appContext).apply {
            background = roundedBackground(COLOR_GREEN, dp(6).toFloat())
        }.also {
            container.addView(it, LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(9)
            })
        }

        val copy = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        subjectView = TextView(appContext).apply {
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            visibility = View.GONE
        }.also(copy::addView)
        timerView = TextView(appContext).apply {
            setTextColor(COLOR_INK)
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
        }.also(copy::addView)
        container.addView(copy)

        toggleButton = capsuleButton("暂停") {
            onToggleRunning()
        }.also {
            it.visibility = View.GONE
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply {
                marginStart = dp(12)
            })
        }
        finishButton = capsuleButton("完成", accent = true) {
            onFinish()
        }.also {
            it.visibility = View.GONE
            container.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
            ).apply {
                marginStart = dp(7)
            })
        }

        installDragAndExpandGesture(container)
        return container
    }

    private fun capsuleButton(
        label: String,
        accent: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(appContext).apply {
        text = label
        gravity = Gravity.CENTER
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(if (accent) Color.WHITE else COLOR_GREEN)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = roundedBackground(
            if (accent) COLOR_VERMILION else COLOR_CONTROL,
            dp(17).toFloat()
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun installDragAndExpandGesture(view: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.isClickable = true
        view.setOnClickListener { setExpanded(!expanded) }
        view.setOnTouchListener { touchedView, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        clampAndUpdatePosition()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        settings.savePosition(params.x, params.y)
                    } else {
                        touchedView.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        subjectView?.visibility = if (value) View.VISIBLE else View.GONE
        toggleButton?.visibility = if (value && !latestSnapshot.completed) View.VISIBLE else View.GONE
        finishButton?.visibility = if (value) View.VISIBLE else View.GONE
        timerView?.textSize = if (value) 20f else 18f
        root?.contentDescription = if (value) {
            "题刻悬浮计时控制，拖动可移动，轻触空白处收起"
        } else {
            "题刻悬浮计时胶囊，轻触展开"
        }
        root?.post { clampAndUpdatePosition() }
    }

    private fun render(snapshot: TimerStateStore.Snapshot) {
        subjectView?.text = snapshot.subject.take(12)
        timerView?.text = when {
            snapshot.completed -> "已完成"
            snapshot.hasReachedTarget ->
                "超 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)}"
            snapshot.running -> TimeUtils.formatDuration(snapshot.displayDurationMs)
            else -> "Ⅱ  ${TimeUtils.formatDuration(snapshot.displayDurationMs)}"
        }
        statusDot?.background = roundedBackground(
            if (snapshot.running && !snapshot.hasReachedTarget) COLOR_GREEN else COLOR_VERMILION,
            dp(6).toFloat()
        )
        toggleButton?.apply {
            text = if (snapshot.running) "暂停" else "继续"
            contentDescription = text
            visibility = if (expanded && !snapshot.completed) View.VISIBLE else View.GONE
        }
        finishButton?.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun clampAndUpdatePosition() {
        if (!added) return
        val view = root ?: return
        val params = layoutParams ?: return
        val metrics = appContext.resources.displayMetrics
        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
        val minY = statusBarHeight() + dp(4)
        val maxY = (metrics.heightPixels - view.height - dp(16)).coerceAtLeast(minY)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(minY, maxY)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            added = false
        } catch (_: SecurityException) {
            hide()
        }
    }

    private fun defaultX(): Int = (
        appContext.resources.displayMetrics.widthPixels - dp(128) - dp(12)
        ).coerceAtLeast(0)

    private fun statusBarHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        } else {
            // Pre-Android 11 does not expose WindowMetrics to a service. 24dp is the platform
            // status-bar baseline; final coordinates remain user-adjustable and are persisted.
            dp(24)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private companion object {
        val COLOR_PAPER = Color.rgb(250, 247, 239)
        val COLOR_BORDER = Color.rgb(216, 207, 190)
        val COLOR_INK = Color.rgb(28, 52, 43)
        val COLOR_MUTED = Color.rgb(105, 115, 107)
        val COLOR_GREEN = Color.rgb(41, 97, 74)
        val COLOR_CONTROL = Color.rgb(229, 238, 232)
        val COLOR_VERMILION = Color.rgb(185, 72, 53)
    }
}

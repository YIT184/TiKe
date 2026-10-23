package com.codex.shuatitimer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.codex.shuatitimer.MainActivity
import com.codex.shuatitimer.R
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.LiveWindowExtras
import com.codex.shuatitimer.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: TimerStateStore
    private lateinit var notificationManager: NotificationManager
    private lateinit var floatingOverlay: FloatingTimerOverlay
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tickerJob: Job? = null
    private var liveWindowCreated = false

    override fun onCreate() {
        super.onCreate()
        store = TimerStateStore(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        floatingOverlay = FloatingTimerOverlay(
            context = this,
            onToggleRunning = ::toggleTimerFromOverlay,
            onFinish = ::openFinishScreen
        )
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_PAUSE -> { CountdownAlertNotifier.silence(this); store.pause() }
            ACTION_RESUME -> store.resume()
            ACTION_STOP -> {
                endLiveWindowAndStop(intent?.getBooleanExtra(EXTRA_CLEAR_TIMER, false) == true)
                return START_NOT_STICKY
            }
        }

        val snapshot = store.snapshot()
        if (!snapshot.active) {
            CountdownAlarmScheduler.cancel(this)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            TIMER_NOTIFICATION_ID,
            buildTimerNotification(
                snapshot,
                if (liveWindowCreated) LiveWindowExtras.Operation.UPDATE
                else LiveWindowExtras.Operation.CREATE
            ),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (snapshot.alarmRinging) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            else if (snapshot.alarmRinging) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        AlarmSoundPlayer.sync(this)
        liveWindowCreated = true
        CountdownAlarmScheduler.sync(this)
        syncFloatingOverlay(snapshot)
        startTicker()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AlarmSoundPlayer.stop()
        tickerJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        floatingOverlay.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            var ticksUntilNotificationRefresh = NOTIFICATION_REFRESH_SECONDS
            var showingOvertime = false
            while (isActive) {
                val snapshot = store.snapshot()
                if (!snapshot.alarmRinging) AlarmSoundPlayer.stop()
                if (!snapshot.active) {
                    stopSelf()
                    break
                }
                if (!snapshot.hasReachedTarget) showingOvertime = false
                val alertPosted = if (snapshot.running && snapshot.hasReachedTarget) {
                    CountdownAlertNotifier.notifyIfReached(this@TimerService)
                } else false
                if (snapshot.running && snapshot.hasReachedTarget && (!showingOvertime || alertPosted)) {
                    // Switch the system notification from its countdown chronometer to an
                    // overtime stopwatch without ending the user's timer.
                    notifyTimer(snapshot)
                    CountdownAlarmScheduler.cancel(this@TimerService)
                    showingOvertime = true
                    ticksUntilNotificationRefresh = NOTIFICATION_REFRESH_SECONDS
                } else if (snapshot.running && --ticksUntilNotificationRefresh <= 0) {
                    // SystemUI advances the visible chronometer itself. A low-frequency refresh
                    // keeps vendor notification extensions current without rebuilding every second.
                    notifyTimer(snapshot)
                    ticksUntilNotificationRefresh = NOTIFICATION_REFRESH_SECONDS
                }
                syncFloatingOverlay(snapshot)
                val remaining = if (snapshot.mode == TimerMode.COUNT_DOWN && !snapshot.hasReachedTarget) {
                    snapshot.displayDurationMs
                } else 1_000L
                delay(if (snapshot.running) remaining.coerceIn(1L, 1_000L) else 1_500L)
            }
        }
    }

    private fun notifyTimer(snapshot: TimerStateStore.Snapshot) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this).notify(
                TIMER_NOTIFICATION_ID,
                buildTimerNotification(snapshot, LiveWindowExtras.Operation.UPDATE)
            )
        } catch (_: SecurityException) {
            // The user may revoke notification access while a timer is running.
        }
    }

    private fun buildTimerNotification(
        snapshot: TimerStateStore.Snapshot,
        liveOperation: Int
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val finish = PendingIntent.getActivity(
            this,
            REQUEST_FINISH,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FINISH_FROM_NOTIFICATION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            snapshot.completed -> "目标完成，点击保存本次记录"
            snapshot.running && snapshot.mode == TimerMode.COUNT_DOWN && snapshot.hasReachedTarget ->
                "已超时 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)} · 继续计时"
            snapshot.running && snapshot.mode == TimerMode.COUNT_DOWN -> "倒计时进行中"
            snapshot.running -> "计时进行中"
            else -> "已暂停 · ${TimeUtils.formatDuration(snapshot.displayDurationMs)}"
        }
        val capsuleText = when {
            snapshot.completed -> "已完成"
            snapshot.mode == TimerMode.COUNT_DOWN -> TimeUtils.formatDuration(snapshot.displayDurationMs)
            else -> TimeUtils.formatDuration(snapshot.effectiveElapsedMs)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${snapshot.subject} · 题刻")
            .setContentText(statusText)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.rgb(41, 97, 74))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val chronometer = TimerNotificationChronometer.from(snapshot)
        if (chronometer.enabled) {
            builder
                .setWhen(chronometer.whenMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(chronometer.countDown)
        } else {
            builder
                .setShowWhen(false)
                .setUsesChronometer(false)
        }

        if (!snapshot.completed) {
            val toggleAction = if (snapshot.running) ACTION_PAUSE else ACTION_RESUME
            val toggleLabel = if (snapshot.running) "暂停" else "继续"
            val toggleIcon = if (snapshot.running) R.drawable.ic_pause else R.drawable.ic_play
            builder.addAction(
                toggleIcon,
                toggleLabel,
                servicePendingIntent(toggleAction, REQUEST_TOGGLE)
            )
        }
        builder.addAction(R.drawable.ic_stop, "完成", finish)

        LiveWindowExtras.apply(
            context = this,
            builder = builder,
            operation = liveOperation,
            title = snapshot.subject,
            content = capsuleText,
            iconResId = R.drawable.ic_stat_timer
        )
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun toggleTimerFromOverlay() {
        val before = store.snapshot()
        if (!before.active || before.completed) return
        CountdownAlertNotifier.silence(this)
        val updated = if (before.running) store.pause() else store.resume()
        CountdownAlarmScheduler.sync(this)
        notifyTimer(updated)
        syncFloatingOverlay(updated)
    }

    private fun openFinishScreen() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FINISH_FROM_NOTIFICATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    private fun syncFloatingOverlay(snapshot: TimerStateStore.Snapshot) {
        mainHandler.post { floatingOverlay.sync(snapshot) }
    }

    private fun endLiveWindowAndStop(clearTimer: Boolean) {
        CountdownAlertNotifier.silence(this)
        tickerJob?.cancel()
        CountdownAlarmScheduler.cancel(this)
        floatingOverlay.hide()
        val snapshot = store.snapshot()
        if (snapshot.active && canPostNotifications()) {
            try {
                NotificationManagerCompat.from(this).notify(
                    TIMER_NOTIFICATION_ID,
                    buildTimerNotification(snapshot, LiveWindowExtras.Operation.END)
                )
            } catch (_: SecurityException) {
                // Nothing to end visually when notification access has been revoked.
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
        notificationManager.cancel(CountdownAlertNotifier.NOTIFICATION_ID)
        if (clearTimer) store.clear()
        stopSelf()
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannels() {
        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            "题刻计时",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "显示后台、锁屏计时与 HarmonyOS 实况窗"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(timerChannel)
        CountdownAlertNotifier.createChannel(this)
    }

    companion object {
        const val ACTION_START = "com.codex.shuatitimer.action.START"
        const val ACTION_PAUSE = "com.codex.shuatitimer.action.PAUSE"
        const val ACTION_RESUME = "com.codex.shuatitimer.action.RESUME"
        const val ACTION_STOP = "com.codex.shuatitimer.action.STOP"
        const val ACTION_START_ALARM = "com.codex.shuatitimer.action.START_ALARM"
        private const val EXTRA_CLEAR_TIMER = "clear_timer"

        // A new ID is intentional: Android does not allow an installed channel's
        // importance to be raised from LOW to HIGH by an app update.
        internal const val CHANNEL_TIMER = "live_timer_v2"
        internal const val CHANNEL_COMPLETE = "countdown_alert_v1"
        private const val TIMER_NOTIFICATION_ID = 41001
        private const val REQUEST_OPEN = 41101
        private const val REQUEST_FINISH = 41102
        private const val REQUEST_TOGGLE = 41103
        private const val NOTIFICATION_REFRESH_SECONDS = 15

        fun intent(context: Context, action: String): Intent =
            Intent(context, TimerService::class.java).setAction(action)

        fun stopAndClearIntent(context: Context): Intent =
            intent(context, ACTION_STOP).putExtra(EXTRA_CLEAR_TIMER, true)
    }
}

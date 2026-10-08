package com.codex.shuatitimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.codex.shuatitimer.MainActivity
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore

/** One system wakeup alarm for the current countdown, independent of service ticks. */
object CountdownAlarmScheduler {
    internal const val ACTION_REACHED = "com.codex.shuatitimer.action.COUNTDOWN_REACHED"
    internal const val EXTRA_TIMER_STARTED = "timer_started"
    private const val REQUEST_ALARM = 41105
    private const val REQUEST_SHOW_TIMER = 41106

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun permissionIntent(context: Context): Intent =
        Intent(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Synchronized
    fun sync(context: Context) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val snapshot = TimerStateStore(context).snapshot(nowElapsed, nowWall)
        val target = snapshot.targetDurationMs
        if (!snapshot.active || !snapshot.running || snapshot.completed ||
            snapshot.mode != TimerMode.COUNT_DOWN || target == null || snapshot.countdownAlertSent
        ) {
            cancel(context)
            return
        }
        val remaining = target - snapshot.effectiveElapsedMs
        if (remaining <= 0L) {
            CountdownAlertNotifier.notifyIfReached(context)
            cancel(context)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            alarmIntent(context).putExtra(EXTRA_TIMER_STARTED, snapshot.startWallClock),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExactAlarms(context)) {
            val showTimer = PendingIntent.getActivity(
                context,
                REQUEST_SHOW_TIMER,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                // Alarm-clock alarms are not batched or throttled in Doze. Only the final
                // countdown deadline uses one; ordinary display refreshes remain wakeup-free.
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(nowWall + remaining, showTimer), operation)
                return
            } catch (_: SecurityException) {
                // Permission can change between the capability check and scheduling.
            }
        }
        // Keep an existing timer recoverable if exact-alarm access is later revoked.
        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nowElapsed + remaining, operation)
    }

    @Synchronized
    fun cancel(context: Context) {
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            alarmIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }

    private fun alarmIntent(context: Context) =
        Intent(context, CountdownAlarmReceiver::class.java).setAction(ACTION_REACHED)
}

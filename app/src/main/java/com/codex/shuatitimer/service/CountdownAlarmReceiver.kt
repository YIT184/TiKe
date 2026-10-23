package com.codex.shuatitimer.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.codex.shuatitimer.timer.TimerStateStore

class CountdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CountdownAlertNotifier.ACTION_SILENCE -> {
                val snapshot = TimerStateStore(context).snapshot()
                if (intent.getStringExtra(CountdownAlertNotifier.EXTRA_TIMER_ID) == snapshot.sessionId) {
                    CountdownAlertNotifier.silence(context)
                }
            }
            CountdownAlarmScheduler.ACTION_REACHED -> {
                val snapshot = TimerStateStore(context).snapshot()
                if (intent.getLongExtra(CountdownAlarmScheduler.EXTRA_TIMER_STARTED, -1L) != snapshot.startWallClock) return
                if (!CountdownAlertNotifier.notifyIfReached(context) && snapshot.active && snapshot.running) {
                    // A wall-clock adjustment can deliver an alarm before the elapsed-time
                    // countdown is actually due. Re-arm for the real remaining duration.
                    CountdownAlarmScheduler.sync(context)
                }
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> CountdownAlarmScheduler.sync(context)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            ) CountdownAlarmScheduler.sync(context)
        }
    }
}

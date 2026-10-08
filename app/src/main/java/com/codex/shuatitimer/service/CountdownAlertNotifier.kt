package com.codex.shuatitimer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.shuatitimer.MainActivity
import com.codex.shuatitimer.R
import com.codex.shuatitimer.data.model.CountdownReminderMode
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.TimeUtils

object CountdownAlertNotifier {
    internal const val NOTIFICATION_ID = 41002
    internal const val CHANNEL_ALARM = "countdown_alarm_v1"
    private const val REQUEST_OPEN_FINISH = 41104

    fun createChannel(context: Context) {
        val messages = NotificationChannel(TimerService.CHANNEL_COMPLETE, "倒计时消息提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "倒计时归零时提醒，计时继续直到手动完成"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
        val alarms = NotificationChannel(CHANNEL_ALARM, "倒计时闹钟提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "倒计时归零时使用闹钟提示音提醒"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(messages, alarms))
    }

    /** Both the alarm receiver and service fallback use one durable, once-only claim. */
    @Synchronized
    fun notifyIfReached(context: Context): Boolean {
        val store = TimerStateStore(context)
        val snapshot = store.snapshot()
        if (!snapshot.active || !snapshot.running || !snapshot.hasReachedTarget || snapshot.countdownAlertSent) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        createChannel(context)
        val openFinish = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_FINISH,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FINISH_FROM_NOTIFICATION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmMode = CountdownReminderSettings(context).mode == CountdownReminderMode.ALARM
        val notification = NotificationCompat.Builder(context, if (alarmMode) CHANNEL_ALARM else TimerService.CHANNEL_COMPLETE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${snapshot.subject}倒计时结束")
            .setContentText("计时仍在继续 · 已超时 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)}，点击完成并记录")
            .setContentIntent(openFinish)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (alarmMode) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
        if (!store.markCountdownAlertSent()) return false
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

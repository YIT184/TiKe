package com.codex.shuatitimer.service

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.model.CountdownReminderMode
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings
import com.codex.shuatitimer.timer.TimerStateStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CountdownAlarmTest {
    private lateinit var context: Context
    private lateinit var store: TimerStateStore
    private lateinit var alarms: AlarmManager
    private lateinit var notifications: NotificationManager

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        store = TimerStateStore(context)
        store.clear()
        CountdownAlertNotifier.dismiss(context)
        CountdownReminderSettings(context).mode = CountdownReminderMode.MESSAGE
        CountdownReminderSettings(context).setSound(null)
        alarms = context.getSystemService(AlarmManager::class.java)
        notifications = context.getSystemService(NotificationManager::class.java)
        ShadowAlarmManager.setAutoSchedule(false)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun schedulesTheExactDeadlineWithoutWaitingForAServiceTick() {
        store.start(TimerMode.COUNT_DOWN, "资料分析", 1_500L, null)
        val earliestDeadline = System.currentTimeMillis() + 1_500L
        CountdownAlarmScheduler.sync(context)
        val latestDeadline = System.currentTimeMillis() + 1_500L

        val alarm = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // elapsedRealtime is controlled by Robolectric; JVM wall time still advances
        // during setup. The exact deadline must fall inside the sampled wall-time bounds.
        assertTrue(alarm.triggerAtTime in earliestDeadline..latestDeadline)
        assertNotNull(alarm.alarmClockInfo)
        assertEquals(alarm.triggerAtTime, alarms.nextAlarmClock.triggerTime)

        ShadowSystemClock.advanceBy(Duration.ofMillis(1_499))
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
        assertNull(shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID))
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        alarm.operation.send()
        shadowOf(Looper.getMainLooper()).idle()

        val notification = shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertEquals("资料分析倒计时结束", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(TimerStateStore(context).snapshot().countdownAlertSent)
        assertTrue(store.snapshot().running)
        assertEquals(0L, store.snapshot().overtimeDurationMs)
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
        assertEquals(1, shadowOf(notifications).size())
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        assertEquals(2_100L, store.snapshot().effectiveElapsedMs)
    }

    @Test
    fun theAlarmReceiverCanNotifyDuringDeepSleepWithoutTheServiceRunning() {
        store.start(TimerMode.COUNT_DOWN, "资料分析", 2_000L, null)
        CountdownAlarmScheduler.sync(context)
        val alarm = shadowOf(alarms).scheduledAlarms.single()
        ShadowSystemClock.simulateDeepSleep(Duration.ofMillis(2_000))
        alarm.operation.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID))
        assertTrue(store.snapshot().running)
        assertTrue(store.snapshot().countdownAlertSent)
    }

    @Test
    fun pausedTimeMovesTheDeadlineAndOldCallbacksCannotNotify() {
        store.start(TimerMode.COUNT_DOWN, "判断推理", 2_000L, null)
        CountdownAlarmScheduler.sync(context)
        val oldAlarm = shadowOf(alarms).scheduledAlarms.single()
        ShadowSystemClock.advanceBy(Duration.ofMillis(400))
        store.pause()
        CountdownAlarmScheduler.sync(context)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        oldAlarm.operation.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, shadowOf(notifications).size())

        store.resume()
        val earliestDeadline = System.currentTimeMillis() + 1_600L
        CountdownAlarmScheduler.sync(context)
        val latestDeadline = System.currentTimeMillis() + 1_600L
        val resumedAlarm = shadowOf(alarms).scheduledAlarms.single()
        assertTrue(resumedAlarm.triggerAtTime in earliestDeadline..latestDeadline)
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
    }

    @Test
    fun replacingOrDiscardingATimerPreventsStaleAlerts() {
        val first = store.start(TimerMode.COUNT_DOWN, "资料分析", 1_000L, null)
        CountdownAlarmScheduler.sync(context)
        ShadowSystemClock.advanceBy(Duration.ofMillis(100))
        store.start(TimerMode.COUNT_DOWN, "言语理解", 2_000L, null)
        CountdownAlarmScheduler.sync(context)
        CountdownAlarmReceiver().onReceive(context,
            Intent(CountdownAlarmScheduler.ACTION_REACHED)
                .putExtra(CountdownAlarmScheduler.EXTRA_TIMER_STARTED, first.startWallClock))
        assertEquals(0, shadowOf(notifications).size())
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)

        store.clear()
        CountdownAlarmScheduler.sync(context)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
    }

    @Test
    fun countUpDoesNotScheduleAnEndAlert() {
        store.start(TimerMode.COUNT_UP, "资料分析", null, null)
        CountdownAlarmScheduler.sync(context)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
    }

    @Test
    @Config(sdk = [32])
    fun permissionGrantReplacesTheFallbackWithAnExactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        store.start(TimerMode.COUNT_DOWN, "资料分析", 2_000L, null)
        CountdownAlarmScheduler.sync(context)
        assertNull(shadowOf(alarms).scheduledAlarms.single().alarmClockInfo)
        assertFalse(CountdownAlarmScheduler.canScheduleExactAlarms(context))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        CountdownAlarmReceiver().onReceive(context,
            Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertNotNull(shadowOf(alarms).scheduledAlarms.single().alarmClockInfo)
    }

    @Test
    @Config(sdk = [34])
    fun notificationPermissionIsRespectedAndTheCompletionChannelCanPopUp() {
        store.start(TimerMode.COUNT_DOWN, "资料分析", 1_000L, null)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000))
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
        assertFalse(store.snapshot().countdownAlertSent)
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(CountdownAlertNotifier.notifyIfReached(context))
        val channel = notifications.getNotificationChannel(TimerService.CHANNEL_COMPLETE)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull(channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun savedAlarmChoiceUsesAlarmSoundInsteadOfTheMessageChannel() {
        val settings = CountdownReminderSettings(context)
        assertEquals(CountdownReminderMode.MESSAGE, settings.mode)
        settings.mode = CountdownReminderMode.ALARM
        assertEquals(CountdownReminderMode.ALARM, CountdownReminderSettings(context).mode)
        store.start(TimerMode.COUNT_DOWN, "资料分析", 1_000L, null)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000))
        assertTrue(CountdownAlertNotifier.notifyIfReached(context))

        val notification = shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID)
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(CountdownAlertNotifier.CHANNEL_ALARM, notification.channelId)
        val channel = notifications.getNotificationChannel(CountdownAlertNotifier.CHANNEL_ALARM)
        assertEquals(android.media.AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertNull(channel.sound)
        assertTrue(store.snapshot().alarmRinging)
        assertTrue(notification.actions.any { it.title == "关闭铃声" })
    }

    @Test fun quickSilenceCancelsAlarmWithoutStoppingTimerAndCannotSilenceAFutureTimer() {
        val settings = CountdownReminderSettings(context)
        settings.mode = CountdownReminderMode.ALARM
        settings.setSound("file:///private/chosen.ogg", "我的闹铃")
        assertEquals("我的闹铃", CountdownReminderSettings(context).soundName)
        assertEquals("file:///private/chosen.ogg", CountdownReminderSettings(context).soundUri)
        store.start(TimerMode.COUNT_DOWN, "资料分析", 1_000L, null)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000))
        assertTrue(CountdownAlertNotifier.notifyIfReached(context))
        val notification = shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID)
        val oldSilence = notification.actions.single { it.title == "关闭铃声" }.actionIntent
        oldSilence.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(store.snapshot().alarmRinging)
        assertTrue(store.snapshot().running)
        assertTrue(store.snapshot().countdownAlertSent)
        assertNull(shadowOf(notifications).getNotification(CountdownAlertNotifier.NOTIFICATION_ID))
        ShadowSystemClock.advanceBy(Duration.ofMillis(2_000))
        assertEquals(3_000L, store.snapshot().effectiveElapsedMs)
        assertFalse(CountdownAlertNotifier.notifyIfReached(context))
        val previousId = store.snapshot().sessionId
        store.start(TimerMode.COUNT_DOWN, "新计时", 1_000L, null)
        store.setAlarmRinging(true)
        CountdownAlarmReceiver().onReceive(context, Intent(CountdownAlertNotifier.ACTION_SILENCE)
            .putExtra(CountdownAlertNotifier.EXTRA_TIMER_ID, previousId))
        assertTrue(store.snapshot().alarmRinging)
        CountdownAlertNotifier.silence(context)
    }
}

package com.codex.shuatitimer.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings
import com.codex.shuatitimer.timer.TimerStateStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.util.DataSource
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AlarmSoundPlayerTest {
    private lateinit var context: Context
    private lateinit var store: TimerStateStore
    private val chosen = Uri.parse("file:///test/my-alarm.ogg")
    private val players = mutableListOf<MediaPlayer>()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = TimerStateStore(context)
        store.clear()
        AlarmSoundPlayer.stop()
        CountdownReminderSettings(context).setSound(chosen.toString(), "自选声音")
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        store.start(TimerMode.COUNT_DOWN, "资料分析", 1_000L, null)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        store.markCountdownAlertSent()
        store.setAlarmRinging(true)
    }
    @After fun close() { CountdownAlertNotifier.silence(context); ShadowMediaPlayer.setCreateListener(null) }

    @Test fun chosenSoundLoopsOnceAndQuickSilenceReleasesPlaybackWithoutStoppingTimer() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(context, chosen), ShadowMediaPlayer.MediaInfo(1_000, 0))
        AlarmSoundPlayer.sync(context)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, players.size)
        assertEquals(DataSource.toDataSource(context, chosen), shadowOf(players.single()).dataSource)
        assertTrue(players.single().isLooping)
        assertTrue(players.single().isPlaying)
        AlarmSoundPlayer.sync(context)
        assertEquals(1, players.size)
        CountdownAlertNotifier.silence(context)
        assertEquals("END", shadowOf(players.single()).state.name)
        assertTrue(store.snapshot().running)
        assertFalse(store.snapshot().alarmRinging)
        AlarmSoundPlayer.sync(context)
        assertEquals(1, players.size)
    }

    @Test fun aPendingAudioPreparationCannotRestartAfterTheUserSilencesIt() {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(context, chosen), ShadowMediaPlayer.MediaInfo(1_000, 100))
        AlarmSoundPlayer.sync(context)
        CountdownAlertNotifier.silence(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals("END", shadowOf(players.single()).state.name)
        assertFalse(store.snapshot().alarmRinging)
        assertTrue(store.snapshot().running)
    }
}

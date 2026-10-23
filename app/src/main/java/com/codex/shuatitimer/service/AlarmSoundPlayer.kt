package com.codex.shuatitimer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings
import com.codex.shuatitimer.timer.TimerStateStore

/** Sound is owned by the foreground timer service, with a synchronous stop path. */
object AlarmSoundPlayer {
    private var player: MediaPlayer? = null
    private var timerId: String? = null
    private var audioManager: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    @Synchronized
    fun sync(context: Context) {
        val snapshot = TimerStateStore(context).snapshot()
        if (!snapshot.active || !snapshot.alarmRinging || !snapshot.running) { stop(); return }
        if (timerId == snapshot.sessionId && player != null) return
        stop()
        timerId = snapshot.sessionId
        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) CountdownAlertNotifier.silence(context)
            }.build()
        audioManager = manager
        focus = request
        manager.requestAudioFocus(request)
        val uri = CountdownReminderSettings(context).soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        prepare(context.applicationContext, uri, allowFallback = true)
    }

    private fun prepare(context: Context, uri: Uri, allowFallback: Boolean) {
        val candidate = MediaPlayer()
        player = candidate
        fun failed() {
            if (player !== candidate) return
            candidate.release()
            player = null
            if (allowFallback && TimerStateStore(context).snapshot().alarmRinging) {
                prepare(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), false)
            } else {
                TimerStateStore(context).setAlarmRinging(false)
                stop()
            }
        }
        try {
            candidate.setAudioAttributes(attributes)
            candidate.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            candidate.setDataSource(context, uri)
            candidate.isLooping = true
            candidate.setOnPreparedListener {
                val state = TimerStateStore(context).snapshot()
                if (player === candidate && state.active && state.running && state.alarmRinging && state.sessionId == timerId) {
                    runCatching { candidate.start() }.onFailure { failed() }
                } else if (player === candidate) stop()
            }
            candidate.setOnErrorListener { _, _, _ -> failed(); true }
            candidate.prepareAsync()
        } catch (_: Exception) { failed() }
    }

    @Synchronized
    fun stop() {
        val old = player
        player = null
        timerId = null
        runCatching { old?.release() }
        focus?.let { runCatching { audioManager?.abandonAudioFocusRequest(it) } }
        focus = null
        audioManager = null
    }
}

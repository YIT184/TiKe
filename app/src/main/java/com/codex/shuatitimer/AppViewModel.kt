package com.codex.shuatitimer

import android.app.Application
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.Subject
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.model.TimerUiStyle
import com.codex.shuatitimer.data.model.CountdownReminderMode
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings
import com.codex.shuatitimer.data.preferences.UserPreferences
import com.codex.shuatitimer.data.repository.SessionRepository
import com.codex.shuatitimer.service.TimerService
import com.codex.shuatitimer.service.CountdownAlarmScheduler
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.CsvExporter
import com.codex.shuatitimer.util.StatisticsCalculator
import com.codex.shuatitimer.util.TimeUtils
import com.codex.shuatitimer.sync.HostDetails
import com.codex.shuatitimer.sync.LanSync
import com.codex.shuatitimer.sync.SyncBundle
import com.codex.shuatitimer.sync.SyncCodec
import com.codex.shuatitimer.sync.LegacyCsvImporter
import com.codex.shuatitimer.sync.DeviceSync
import com.codex.shuatitimer.sync.DeviceIdentity
import com.codex.shuatitimer.sync.NearbyDevice
import com.codex.shuatitimer.sync.NearbyDiscovery
import com.codex.shuatitimer.sync.PairingRequest
import com.codex.shuatitimer.sync.PairingStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimerSetupUiState(
    val mode: TimerMode = TimerMode.COUNT_UP,
    val selectedSubject: String = "",
    val modules: List<String> = emptyList(),
    val countdownMinutes: Int = 30,
    val plannedQuestions: Int? = null,
    val plannedQuestionsByModule: Map<String, Int> = emptyMap(),
    val timerUiStyle: TimerUiStyle = TimerUiStyle.CLASSIC,
    val keepScreenOn: Boolean = false,
    val countdownReminderMode: CountdownReminderMode = CountdownReminderMode.MESSAGE,
    val loaded: Boolean = false
) {
    val resolvedSubject: String
        get() = selectedSubject
}

data class FinishDialogState(
    val visible: Boolean = false,
    val snapshot: TimerStateStore.Snapshot = TimerStateStore.Snapshot(),
    val finishWallClock: Long = 0L,
    val resumeIfCancelled: Boolean = false
)

data class SubjectStatistics(
    val subject: String,
    val durationMs: Long,
    val sessionCount: Int,
    val questionCount: Long,
    val correctCount: Long,
    val gradedQuestionCount: Long = questionCount,
    val timedQuestionDurationMs: Long = durationMs
) {
    val accuracy: Double?
        get() = if (gradedQuestionCount > 0) correctCount.toDouble() / gradedQuestionCount else null
    val avgSecondsPerQuestion: Double?
        get() = if (questionCount > 0) timedQuestionDurationMs / 1000.0 / questionCount else null
}

data class SyncUiState(
    val busy: Boolean = false,
    val isLan: Boolean = false,
    val host: HostDetails? = null,
    val message: String = "将两台设备连接到同一 Wi-Fi，即可同步。",
    val searching: Boolean = false,
    val deviceName: String = "",
    val nearby: List<NearbyDevice> = emptyList(),
    val paired: List<DeviceIdentity> = emptyList(),
    val pairingRequest: PairingRequest? = null,
    val waitingForPairing: PairingRequest? = null,
    val pendingImport: SyncBundle? = null,
    val legacyImport: Boolean = false
)

data class StatisticsUiState(
    val overall: StatisticsCalculator.OverallStats = StatisticsCalculator.OverallStats(),
    val daily: List<StatisticsCalculator.DailySummary> = emptyList(),
    val subjects: List<SubjectStatistics> = emptyList()
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object SessionSaved : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = SessionRepository(AppDatabase.getInstance(appContext))
    private val preferences = UserPreferences(appContext)
    private val timerStore = TimerStateStore(appContext)
    private val countdownReminderSettings = CountdownReminderSettings(appContext)
    private val pairings = PairingStore(appContext.noBackupFilesDir, android.os.Build.MODEL)

    val sessions: StateFlow<List<Session>> = repository.allSessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _setup = MutableStateFlow(TimerSetupUiState())
    val setup = _setup.asStateFlow()
    private val _sync = MutableStateFlow(SyncUiState(deviceName = pairings.identity().name,
        paired = pairings.devices().map { DeviceIdentity(it.id, it.name) }))
    val sync = _sync.asStateFlow()
    private var syncJob: Job? = null
    private var lan: DeviceSync? = null
    private var receiver: DeviceSync? = null
    private var hostJob: Job? = null
    private var discovery: NearbyDiscovery? = null
    private var pairingApproval: CompletableDeferred<Boolean>? = null
    @Volatile private var networkGeneration = 0
    private var incomingActive = false

    private val _timer = MutableStateFlow(timerStore.snapshot())
    val timer = _timer.asStateFlow()

    private val _finishDialog = MutableStateFlow(FinishDialogState())
    val finishDialog = _finishDialog.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val statistics: StateFlow<StatisticsUiState> = sessions.map(::calculateStatistics).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StatisticsUiState()
    )

    init {
        viewModelScope.launch {
            val lastSubject = preferences.lastSubject.first()
            repository.preserveLegacyModule(lastSubject)
            val names = repository.activeModules.first().map { it.name }
            val plans = preferences.plannedQuestionsByModule.first().toMutableMap()
            val legacyPlan = preferences.lastPlannedQuestions.first()
            if (lastSubject !in plans && legacyPlan != null) {
                plans[lastSubject] = legacyPlan
                preferences.setPlannedQuestionsForModule(lastSubject, legacyPlan)
            }
            _setup.value = TimerSetupUiState(
                selectedSubject = lastSubject.takeIf { it in names } ?: names.firstOrNull().orEmpty(),
                modules = names,
                countdownMinutes = preferences.lastCountdownMinutes.first().coerceIn(5, 360),
                plannedQuestions = plans[lastSubject.takeIf { it in names } ?: names.firstOrNull().orEmpty()],
                plannedQuestionsByModule = plans,
                timerUiStyle = preferences.timerUiStyle.first(),
                keepScreenOn = preferences.keepScreenOn.first(),
                countdownReminderMode = countdownReminderSettings.mode,
                loaded = true
            )
            repository.activeModules.collect { modules ->
                val updatedNames = modules.map { it.name }
                _setup.update {
                    it.copy(modules = updatedNames, selectedSubject = it.selectedSubject.takeIf { name -> name in updatedNames }
                        ?: updatedNames.firstOrNull().orEmpty())
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                _timer.value = timerStore.snapshot()
                delay(if (_timer.value.active && _timer.value.running) 250L else 750L)
            }
        }
    }

    fun setMode(mode: TimerMode) = _setup.update { it.copy(mode = mode) }

    fun setSubject(subject: String) {
        if (subject !in _setup.value.modules) return
        _setup.update {
            it.copy(selectedSubject = subject, plannedQuestions = it.plannedQuestionsByModule[subject])
        }
        viewModelScope.launch { preferences.setLastSubject(subject) }
    }

    fun setTimerUiStyle(style: TimerUiStyle) {
        _setup.update { it.copy(timerUiStyle = style) }
        viewModelScope.launch { preferences.setTimerUiStyle(style) }
    }

    fun addModule(name: String) {
        viewModelScope.launch {
            val error = repository.addModule(name)
            if (error == null) {
                _setup.update { it.copy(selectedSubject = name.trim()) }
                preferences.setLastSubject(name.trim())
            }
            showMessage(error ?: "模块已保存")
        }
    }

    fun deleteModule(name: String) {
        viewModelScope.launch {
            repository.deleteModule(name)
            showMessage("模块已删除，历史记录已保留")
        }
    }

    fun setCountdownMinutes(value: Int) {
        val minutes = value.coerceIn(5, 360)
        _setup.update { it.copy(countdownMinutes = minutes) }
        viewModelScope.launch { preferences.setLastCountdownMinutes(minutes) }
    }

    fun setPlannedQuestions(value: Int?) {
        val count = value?.takeIf { it > 0 }?.coerceAtMost(9_999)
        val module = _setup.value.selectedSubject
        _setup.update { state ->
            val plans = state.plannedQuestionsByModule.toMutableMap().apply {
                if (count == null) remove(module) else put(module, count)
            }
            state.copy(plannedQuestions = count, plannedQuestionsByModule = plans)
        }
        if (module.isNotBlank()) viewModelScope.launch {
            preferences.setPlannedQuestionsForModule(module, count)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _setup.update { it.copy(keepScreenOn = enabled) }
        viewModelScope.launch { preferences.setKeepScreenOn(enabled) }
    }

    fun setCountdownReminderMode(mode: CountdownReminderMode) {
        countdownReminderSettings.mode = mode
        _setup.update { it.copy(countdownReminderMode = mode) }
    }

    fun startTimer(): String? {
        val setupState = _setup.value
        if (!setupState.loaded || setupState.resolvedSubject !in setupState.modules) {
            return "请先添加并选择一个模块"
        }
        if (setupState.mode == TimerMode.COUNT_DOWN && setupState.countdownMinutes <= 0) {
            return "倒计时时长必须大于 0"
        }
        if (setupState.mode == TimerMode.COUNT_DOWN && !CountdownAlarmScheduler.canScheduleExactAlarms(appContext)) {
            try {
                appContext.startActivity(CountdownAlarmScheduler.permissionIntent(appContext))
            } catch (_: android.content.ActivityNotFoundException) {
                return "请在系统设置中为题刻允许「闹钟和提醒」，以便准时提醒"
            }
            return "请允许「闹钟和提醒」，返回后再开始倒计时"
        }
        val target = if (setupState.mode == TimerMode.COUNT_DOWN) {
            setupState.countdownMinutes * 60_000L
        } else null
        val snapshot = timerStore.start(
            mode = setupState.mode,
            subject = setupState.resolvedSubject,
            targetDurationMs = target,
            plannedQuestionCount = setupState.plannedQuestions
        )
        _timer.value = snapshot
        CountdownAlarmScheduler.sync(appContext)
        viewModelScope.launch {
            preferences.setLastSubject(setupState.resolvedSubject)
            preferences.setLastCountdownMinutes(setupState.countdownMinutes)
        }
        ContextCompat.startForegroundService(
            appContext,
            TimerService.intent(appContext, TimerService.ACTION_START)
        )
        return null
    }

    fun pauseTimer() {
        _timer.value = timerStore.pause()
        CountdownAlarmScheduler.sync(appContext)
        appContext.startService(TimerService.intent(appContext, TimerService.ACTION_PAUSE))
    }

    fun resumeTimer() {
        _timer.value = timerStore.resume()
        CountdownAlarmScheduler.sync(appContext)
        appContext.startService(TimerService.intent(appContext, TimerService.ACTION_RESUME))
    }

    fun requestFinish() {
        val before = timerStore.snapshot()
        if (!before.active || _finishDialog.value.visible) return
        val captured = if (before.running) timerStore.pause() else before
        CountdownAlarmScheduler.sync(appContext)
        if (before.running) {
            appContext.startService(TimerService.intent(appContext, TimerService.ACTION_PAUSE))
        }
        _timer.value = captured
        _finishDialog.value = FinishDialogState(
            visible = true,
            snapshot = captured,
            finishWallClock = System.currentTimeMillis(),
            resumeIfCancelled = before.running && !before.completed
        )
    }

    fun cancelFinish() {
        val dialog = _finishDialog.value
        _finishDialog.value = FinishDialogState()
        if (dialog.resumeIfCancelled) resumeTimer()
    }

    fun saveFinishedSession(
        questionText: String,
        correctText: String,
        note: String
    ): String? {
        val questions = questionText.trim().toIntOrNull()
            ?: return "请填写实际题数（可以填 0）"
        val correct = correctText.trim().toIntOrNull()
            ?: return "请填写正确题数（可以填 0）"
        if (questions < 0 || correct < 0) return "题数不能为负数"
        if (correct > questions) return "正确题数不能大于实际题数"
        val dialog = _finishDialog.value
        if (!dialog.visible || !dialog.snapshot.active) return "当前没有可保存的计时"
        val snapshot = dialog.snapshot
        val session = Session(
            startWallClock = snapshot.startWallClock,
            endWallClock = dialog.finishWallClock,
            effectiveElapsedMs = snapshot.effectiveElapsedMs,
            accumulatedPausedMs = snapshot.accumulatedPausedMs,
            mode = snapshot.mode.name,
            targetDurationMs = snapshot.targetDurationMs,
            plannedQuestionCount = snapshot.plannedQuestionCount,
            subject = snapshot.subject,
            questionCount = questions,
            correctCount = correct,
            note = note.trim().takeIf { it.isNotBlank() }
        )
        _finishDialog.value = FinishDialogState()
        _timer.value = TimerStateStore.Snapshot()
        CountdownAlarmScheduler.cancel(appContext)
        viewModelScope.launch {
            repository.insert(session)
            appContext.startService(TimerService.stopAndClearIntent(appContext))
            _events.emit(UiEvent.SessionSaved)
        }
        return null
    }

    fun discardTimer() {
        _finishDialog.value = FinishDialogState()
        _timer.value = TimerStateStore.Snapshot()
        CountdownAlarmScheduler.cancel(appContext)
        appContext.startService(TimerService.stopAndClearIntent(appContext))
        _events.tryEmit(UiEvent.Message("本次计时已丢弃"))
    }

    fun updateSession(
        session: Session,
        questionText: String,
        correctText: String,
        note: String
    ): String? {
        val questions = questionText.trim().toIntOrNull()
            ?: return "请填写实际题数（可以填 0）"
        val correct = correctText.trim().toIntOrNull()
            ?: return "请填写正确题数（可以填 0）"
        if (questions < 0 || correct < 0 || correct > questions) {
            return "正确题数需在 0 到实际题数之间"
        }
        viewModelScope.launch {
            repository.update(
                session.copy(
                    questionCount = questions,
                    correctCount = correct,
                    note = note.trim().takeIf { it.isNotBlank() }
                )
            )
            _events.emit(UiEvent.Message("记录已更新"))
        }
        return null
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.delete(session)
            _events.emit(UiEvent.Message("记录已删除"))
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            repository.deleteAll()
            _events.emit(UiEvent.Message("全部记录已清空"))
        }
    }

    fun exportCsv(): String = CsvExporter.export(sessions.value)

    fun showMessage(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    private fun calculateStatistics(all: List<Session>): StatisticsUiState {
        val todayStart = TimeUtils.startOfTodayMs()
        val sevenDayStart = TimeUtils.startOfDaysAgoMs(6)
        val today = all.filter { it.startWallClock >= todayStart }
        val lastSeven = all.filter { it.startWallClock >= sevenDayStart }
        val totalQuestions = all.sumOf { (it.questionCount ?: 0).toLong() }
        val totalCorrect = all.sumOf { (it.correctCount ?: 0).toLong() }
        val totalDuration = all.sumOf { it.effectiveElapsedMs }
        val overall = StatisticsCalculator.computeOverallStats(
            todaySessions = today,
            last7DaysSessions = lastSeven,
            allTimeTotalCount = all.size,
            allTimeTotalDuration = totalDuration,
            allTimeTotalQuestions = totalQuestions,
            allTimeTotalCorrect = totalCorrect
        )
        val subjects = StatisticsCalculator.computeSubjectStats(all)
        return StatisticsUiState(
            overall = overall,
            daily = StatisticsCalculator.computeDailySummaries(lastSeven, 7),
            subjects = subjects
        )
    }

    private suspend fun mergeWithBackup(incoming: SyncBundle): Int = withContext(Dispatchers.IO) {
        val backup = File(appContext.noBackupFilesDir, "sync-backups").apply { mkdirs() }
        val content = SyncCodec.encode(repository.snapshot())
        val original = File(backup, "before-first-sync.json")
        if (!original.exists()) original.writeText(content)
        // Keep the first snapshot untouched and update a separate pre-merge recovery copy.
        val temp = File(backup, "before-latest-sync.tmp")
        temp.writeText(content)
        java.nio.file.Files.move(temp.toPath(), File(backup, "before-latest-sync.json").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        repository.merge(incoming)
    }

    fun startHosting() {
        if (_sync.value.busy) return
        stopSync()
        val generation = networkGeneration
        val transport = DeviceSync(pairings)
        receiver = transport
        _sync.update { it.copy(searching = true, message = "正在搜索附近的题刻设备…") }
        hostJob = viewModelScope.launch {
            try {
                transport.host(
                    ready = { port -> viewModelScope.launch {
                        if (generation == networkGeneration) {
                            _sync.update { it.copy(host = HostDetails(LanSync.localAddresses(), port, ""), message = "请选择要同步的设备。") }
                            discovery = NearbyDiscovery(appContext, pairings.identity()).also { search ->
                                search.start(port,
                                    changed = { devices -> if (generation == networkGeneration) _sync.update { it.copy(nearby = devices) } },
                                    failed = { message -> if (generation == networkGeneration) _sync.update { it.copy(message = message) } })
                            }
                        }
                    } },
                    approve = { request -> withContext(Dispatchers.Main) {
                        if (generation != networkGeneration || _sync.value.busy || _sync.value.pendingImport != null) false
                        else {
                            incomingActive = true
                            val answer = CompletableDeferred<Boolean>()
                            pairingApproval = answer
                            _sync.update { it.copy(busy = true, isLan = true, pairingRequest = request, message = "${request.name} 请求配对") }
                            var approved = false
                            try { approved = answer.await(); approved }
                            finally {
                                if (generation == networkGeneration) {
                                    pairingApproval = null
                                    if (!approved) incomingActive = false
                                    _sync.update { it.copy(pairingRequest = null, busy = approved, isLan = approved,
                                        message = if (approved) "正在同步…" else "已拒绝或取消配对") }
                                }
                            }
                        }
                    } },
                    exchange = { incoming ->
                        withContext(Dispatchers.Main) {
                            check(generation == networkGeneration) { "连接已关闭" }
                            check(incomingActive || (!_sync.value.busy && _sync.value.pendingImport == null)) { "本机正在处理其他操作，请稍后重试" }
                            incomingActive = true
                            _sync.update { it.copy(busy = true, isLan = true, message = "正在同步…") }
                        }
                        mergeWithBackup(incoming)
                        repository.snapshot()
                    },
                    completed = { name -> viewModelScope.launch {
                        if (generation == networkGeneration) {
                            incomingActive = false
                            _sync.update { it.copy(busy = false, isLan = false, message = "已与 $name 同步", paired = pairedNames()) }
                        }
                    } },
                    failed = { error -> viewModelScope.launch {
                        if (generation == networkGeneration && (incomingActive || !_sync.value.busy)) {
                            incomingActive = false
                            syncFailed(error)
                        }
                    } }
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive && generation == networkGeneration) { stopSync(); syncFailed(e) } }
            finally { transport.stop() }
        }
    }

    private fun pairedNames() = pairings.devices().map { DeviceIdentity(it.id, it.name) }

    fun answerPairing(accept: Boolean) { pairingApproval?.complete(accept) }

    fun stopSync() {
        networkGeneration++
        if (_sync.value.isLan) syncJob?.cancel()
        hostJob?.cancel()
        pairingApproval?.cancel()
        pairingApproval = null
        receiver?.stop()
        lan?.stop()
        discovery?.close()
        discovery = null
        incomingActive = false
        _sync.update { it.copy(busy = if (it.isLan) false else it.busy, isLan = false, host = null,
            searching = false, nearby = emptyList(), pairingRequest = null, waitingForPairing = null,
            message = if (it.isLan || it.searching) "搜索已停止" else it.message) }
    }

    fun connectDevice(device: NearbyDevice) {
        if (_sync.value.busy) return
        val generation = networkGeneration
        val transport = DeviceSync(pairings)
        lan = transport
        _sync.update { it.copy(busy = true, isLan = true, message = "正在连接 ${device.name}…") }
        syncJob = viewModelScope.launch {
            try {
                val incoming = transport.connect(device, repository.snapshot()) { request ->
                    _sync.update { if (generation == networkGeneration && it.isLan)
                        it.copy(waitingForPairing = request, message = "等待 ${device.name} 同意配对…") else it }
                }
                mergeWithBackup(incoming)
                _sync.update { it.copy(busy = false, isLan = false, waitingForPairing = null,
                    paired = pairedNames(), message = "已与 ${device.name} 同步") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive) syncFailed(e) }
            finally { transport.stop() }
        }
    }

    fun forgetDevice(id: String) {
        if (_sync.value.busy) return
        val restart = _sync.value.searching
        stopSync()
        pairings.forget(id)
        _sync.update { it.copy(paired = pairedNames(), message = "已取消配对") }
        if (restart) startHosting()
    }

    fun renameDevice(name: String) {
        if (_sync.value.busy) return
        runCatching { pairings.rename(name) }.onSuccess {
            _sync.update { it.copy(deviceName = pairings.identity().name) }
            if (_sync.value.searching) startHosting()
        }.onFailure { showMessage(it.message ?: "无法保存设备名称") }
    }

    fun exportSync(uri: Uri) {
        if (_sync.value.busy) return
        _sync.update { it.copy(busy = true, message = "正在导出…") }
        syncJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val data = SyncCodec.encode(repository.snapshot())
                    checkNotNull(appContext.contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }
                        .use { it.write(data.toByteArray(Charsets.UTF_8)) }
                }
                _sync.update { it.copy(busy = false, message = "同步文件已导出") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive) syncFailed(e) }
        }
    }

    fun prepareImport(uri: Uri, legacyCsv: Boolean = false) {
        if (_sync.value.busy) return
        _sync.update { it.copy(busy = true, message = "正在读取文件…") }
        syncJob = viewModelScope.launch {
            try {
                val incoming = withContext(Dispatchers.IO) {
                    checkNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取文件" }.use {
                        if (legacyCsv) LegacyCsvImporter.decode(SyncCodec.readText(it)) else SyncCodec.read(it)
                    }
                }
                _sync.update { it.copy(busy = false, pendingImport = incoming, legacyImport = legacyCsv, message = "请选择是否导入") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive) syncFailed(e) }
        }
    }

    fun cancelImport() { _sync.update { it.copy(pendingImport = null, message = "已取消导入") } }

    fun confirmImport() {
        val incoming = _sync.value.pendingImport ?: return
        _sync.update { it.copy(busy = true, pendingImport = null, message = "正在导入…") }
        syncJob = viewModelScope.launch {
            try {
                mergeWithBackup(incoming)
                _sync.update { it.copy(busy = false, message = "导入完成") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive) syncFailed(e) }
        }
    }

    private fun syncFailed(e: Exception) {
        val message = when (e) {
            is java.net.SocketTimeoutException -> "连接超时，请让对方保持同步页面开启后重试。"
            is java.net.ConnectException -> "设备已离线，请重新搜索。"
            is java.io.EOFException, is javax.crypto.AEADBadTagException -> "连接中断或配对已失效，请重新连接。"
            is org.json.JSONException -> "不是有效的题刻同步文件，文件可能损坏或版本不兼容。"
            is IllegalArgumentException, is IllegalStateException -> e.message ?: "同步失败，请重试"
            else -> "同步未完成，请检查网络或文件后重试。"
        }
        _sync.update { it.copy(busy = false, isLan = false, pairingRequest = null, waitingForPairing = null, message = message) }
    }

    override fun onCleared() {
        stopSync()
        super.onCleared()
    }
}

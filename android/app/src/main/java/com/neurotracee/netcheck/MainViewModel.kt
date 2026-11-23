package com.neurotracee.netcheck

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neurotracee.netcheck.data.HistoryEntry
import com.neurotracee.netcheck.data.HistoryStore
import com.neurotracee.netcheck.data.LogStore
import com.neurotracee.netcheck.data.ReportStore
import com.neurotracee.netcheck.model.NetEnv
import com.neurotracee.netcheck.model.RunResult
import com.neurotracee.netcheck.model.TargetResult
import com.neurotracee.netcheck.net.Checker
import com.neurotracee.netcheck.net.Targets
import com.neurotracee.netcheck.platform.NetEnvReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Phase { Idle, Running, Done }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val envReader = NetEnvReader(app)
    private val store = ReportStore(app)
    private val historyStore = HistoryStore(app)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var checkerRef: Checker? = null
    private var runId = 0
    private var startedAt = 0L

    var phase by mutableStateOf(Phase.Idle)
        private set
    var results by mutableStateOf<List<TargetResult>>(emptyList())
        private set
    var run by mutableStateOf<RunResult?>(null)
        private set
    var env by mutableStateOf(NetEnv())
        private set
    var progressCurrent by mutableStateOf(0)
        private set
    var progressTotal by mutableStateOf(0)
        private set
    var currentName by mutableStateOf("")
        private set
    var quickMode by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            env = withContext(Dispatchers.IO) { envReader.read() }
            history = withContext(Dispatchers.IO) { historyStore.load() }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            history = withContext(Dispatchers.IO) { historyStore.clear() }
        }
    }

    fun refreshEnv() {
        viewModelScope.launch {
            env = withContext(Dispatchers.IO) { envReader.read() }
        }
    }

    fun start(quick: Boolean) {
        if (phase == Phase.Running) return
        val targets = if (quick) Targets.quickSubset() else Targets.all
        quickMode = quick
        results = emptyList()
        run = null
        message = null
        phase = Phase.Running
        progressCurrent = 0
        progressTotal = targets.size
        currentName = "准备中"
        startedAt = System.currentTimeMillis()
        val thisRun = ++runId
        LogStore.i("RUN", "开始自检 #$thisRun · 模式=${if (quick) "quick" else "full"} · 点位=${targets.size}")

        job = viewModelScope.launch {
            val began = System.currentTimeMillis()
            try {
                val checker = Checker()
                checkerRef = checker
                val rateInfo = withContext(Dispatchers.IO) {
                    checker.run(
                        targets = targets,
                        onStart = { index, total, target ->
                            main.post {
                                if (thisRun == runId) {
                                    progressCurrent = index
                                    progressTotal = total
                                    currentName = target.name
                                }
                            }
                        },
                        onResult = { result ->
                            // 停止之后旧回调可能还在排队，用运行编号把它们丢掉
                            if (!result.ok) {
                                LogStore.w("PROBE", "${result.name} ${result.host} → ${result.label}" +
                                    "（DNS ${result.dnsMs} / TCP ${result.tcpMs} / TLS ${result.tlsMs} / TTFB ${result.ttfbMs}）" +
                                    if (result.error.isNotBlank()) " · ${result.error}" else "")
                            }
                            main.post { if (thisRun == runId) results = results + result }
                        }
                    )
                }
                // 被用户按下停止：不再写结果，直接回首页
                if (checker.canceled || thisRun != runId) return@launch
                val elapsed = ((System.currentTimeMillis() - began) / 1000).toInt()
                val envNow = withContext(Dispatchers.IO) { envReader.read() }
                env = envNow
                val completed = RunResult(
                    results = results,
                    startedAtMillis = startedAt,
                    elapsedSec = elapsed,
                    env = envNow,
                    rateInfo = rateInfo,
                    quick = quick
                )
                run = completed
                phase = Phase.Done
                LogStore.i("RUN", "完成 #$thisRun · 可连通 ${completed.okCount}/${completed.total} · 耗时 ${elapsed}s" +
                    " · 平均建连 ${completed.avgTcp}ms" + (if (completed.rateInfo.isNotBlank()) " · ${completed.rateInfo}" else ""))
                // 一次跑完就落一条历史，最多留 100 条
                val entry = HistoryEntry.from(completed, startedAt, quick)
                history = withContext(Dispatchers.IO) { historyStore.add(entry) }
            } catch (e: CancellationException) {
                phase = Phase.Idle
                message = "已停止本次自检"
                LogStore.i("RUN", "被用户停止 #$thisRun · 已测 ${results.size}/${progressTotal}")
                throw e
            } catch (t: Throwable) {
                phase = Phase.Idle
                message = "自检中断：${t.message ?: t.javaClass.simpleName}"
                LogStore.e("RUN", "自检异常中断 #$thisRun", t)
            }
        }
    }

    fun cancel() {
        // 先把「这一次」存进历史：测了几个算几个，一个都没测也要留一条，
        // 不然用户点了开始又马上停止，历史里什么都没有，反而像是没跑过
        val done = results
        val planned = if (progressTotal > 0) progressTotal else Targets.all.size
        if (startedAt > 0) {
            val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
            val entry = HistoryEntry.partial(
                startedAtMillis = startedAt,
                plannedTotal = planned,
                elapsedSec = elapsed,
                results = done,
                quick = quickMode
            )
            viewModelScope.launch {
                history = withContext(Dispatchers.IO) { historyStore.add(entry) }
            }
        }
        // 再让阻塞中的探测在下一个检查点退出，并把界面立刻切回首页
        checkerRef?.cancel()
        job?.cancel()
        job = null
        runId++
        phase = Phase.Idle
        results = emptyList()
        run = null
        progressCurrent = 0
        currentName = ""
        message = "已停止本次自检"
    }

    fun exportHtml(): File? {
        val r = run ?: return null
        return runCatching { store.saveHtml(r) }.getOrNull()
    }

    fun exportCsv(): File? {
        val r = run ?: return null
        return runCatching { store.saveCsv(r) }.getOrNull()
    }

    fun store(): ReportStore = store

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
    }
}

package com.neurotracee.netcheck

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null
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

    init {
        viewModelScope.launch {
            env = withContext(Dispatchers.IO) { envReader.read() }
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

        job = viewModelScope.launch {
            val began = System.currentTimeMillis()
            try {
                val checker = Checker()
                val rateInfo = withContext(Dispatchers.IO) {
                    checker.run(
                        targets = targets,
                        onStart = { index, total, target ->
                            main.post {
                                progressCurrent = index
                                progressTotal = total
                                currentName = target.name
                            }
                        },
                        onResult = { result ->
                            main.post { results = results + result }
                        }
                    )
                }
                val elapsed = ((System.currentTimeMillis() - began) / 1000).toInt()
                val envNow = withContext(Dispatchers.IO) { envReader.read() }
                env = envNow
                run = RunResult(
                    results = results,
                    startedAtMillis = startedAt,
                    elapsedSec = elapsed,
                    env = envNow,
                    rateInfo = rateInfo,
                    quick = quick
                )
                phase = Phase.Done
            } catch (e: CancellationException) {
                phase = Phase.Idle
                message = "已停止本次自检"
                throw e
            } catch (t: Throwable) {
                phase = Phase.Idle
                message = "自检中断：${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
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

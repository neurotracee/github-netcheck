package com.neurotracee.netcheck.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neurotracee.netcheck.BuildConfig
import com.neurotracee.netcheck.MainViewModel
import com.neurotracee.netcheck.Phase
import com.neurotracee.netcheck.data.ReportBuilder
import com.neurotracee.netcheck.model.Levels
import com.neurotracee.netcheck.model.NetEnv
import com.neurotracee.netcheck.model.RunResult
import com.neurotracee.netcheck.model.TargetResult
import com.neurotracee.netcheck.model.Verdict
import com.neurotracee.netcheck.net.Targets

private const val PROJECT_URL = "https://github.com/neurotracee/github-netcheck"
private const val MIRROR_URL = "https://gitee.com/bio234/github-netcheck"

@Composable
fun NetCheckScreen(
    prefs: com.neurotracee.netcheck.data.Prefs,
    onThemeChanged: (Int) -> Unit,
    onLangChanged: (com.neurotracee.netcheck.ui.Lang) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf<String?>(null) }
    // 0 = 正常；1 = 异常·全部（默认，因为异常才要处理）；2 = 多；3 = 中；4 = 少
    var viewIdx by remember { mutableStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // 点击回调、后台消息这类非 Composable 上下文里也要按当前语言出字，
    // 所以这里先取一次语言，再用普通函数拼字符串（t() 只能在 Composable 里调用）。
    val isEn = LocalLang.current == com.neurotracee.netcheck.ui.Lang.EN
    fun s(zh: String, en: String) = if (isEn) en else zh

    // 异常与正常各成一组，异常再按严重程度分三档：完全不通 / 部分阶段失败 / 通但很慢
    val issueList = vm.results.filter { !it.ok }
    val okList = vm.results.filter { it.ok }
    val severeList = issueList.filter { !it.tcpOk || it.level == Levels.DOWN }
    val mediumList = issueList.filter { it.tcpOk && it.level != Levels.DOWN }
    val mildList = okList.filter { it.level == Levels.SLOW || it.level == Levels.LIMIT }
    val visibleResults = when (viewIdx) {
        0 -> okList
        1 -> issueList
        2 -> severeList
        3 -> mediumList
        else -> mildList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
    ) {
        // 三种页面状态之间用弹性滑动切换：旧页向下流出，新页从上方带过冲落进来，
        // dampingRatio 调低就是为了那一下「果冻」回弹，而不是硬切一帧
        val stage = when {
            vm.phase == Phase.Running -> 0
            vm.phase == Phase.Idle && vm.results.isEmpty() -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = stage,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.42f, stiffness = 300f),
                        initialOffsetY = { h -> -h / 5 }
                    ) + fadeIn(animationSpec = tween(220))
                    ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(200),
                        targetOffsetY = { h -> h / 9 }
                    ) + fadeOut(animationSpec = tween(140))
                    )
            },
            label = "stage"
        ) { st ->
            when (st) {
            0 -> {
            // 运行中不跟列表抢位置：整块留在屏幕中央，配一条流动的纠缠曲线
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    HeroCard(
                        p = p,
                        tag = t("正在自检", "CHECKING"),
                        status = if (vm.progressTotal > 0) "${vm.progressCurrent} / ${vm.progressTotal}" else t("准备中", "Preparing"),
                        big = if (vm.progressTotal > 0) "${vm.progressCurrent} / ${vm.progressTotal}" else "—",
                        sub = if (vm.currentName.isNotEmpty()) vm.currentName else t("正在逐个建连…", "Connecting…"),
                        progress = if (vm.progressTotal > 0) vm.progressCurrent.toFloat() / vm.progressTotal else 0f,
                        primaryLabel = t("停止", "Stop"),
                        onPrimary = { vm.cancel() },
                        secondaryLabel = null,
                        onSecondary = null,
                        wave = true
                    )
                }
            }
            }
            1 -> {
            // 首页：整块居中，不再把一行行小字贴着屏幕顶部排
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
            ) {
                IdleHero(
                    p = p,
                    env = vm.env,
                    endpointCount = Targets.all.size,
                    message = vm.message,
                    onDismissMessage = { vm.message = null },
                    onSettings = { showSettings = true },
                    onRun = { vm.start(false) },
                    onQuick = { vm.start(true) }
                )
            }
            }
            else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 左右滑动也能在「异常 / 正常」之间翻，和点上面的分段按钮等价
                    .pointerInput(vm.phase, vm.results.size) {
                        var acc = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acc = 0f },
                            onDragEnd = { acc = 0f },
                            onDragCancel = { acc = 0f }
                        ) { _, dragAmount ->
                            acc += dragAmount
                            if (acc <= -140.dp.toPx()) {
                                if (viewIdx < 4) viewIdx += 1
                                acc = 0f
                            } else if (acc >= 140.dp.toPx()) {
                                if (viewIdx > 0) viewIdx -= 1
                                acc = 0f
                            }
                        }
                    },
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    RunHero(
                        p = p,
                        phase = vm.phase,
                        run = vm.run,
                        current = vm.progressCurrent,
                        total = vm.progressTotal,
                        name = vm.currentName,
                        onRun = { vm.start(false) },
                        onQuick = { vm.start(true) },
                        onCancel = { vm.cancel() }
                    )
                }

                if (vm.phase == Phase.Done) {
                    item {
                        ResultActions(
                            p = p,
                            onRun = { vm.start(false) },
                            onQuick = { vm.start(true) },
                            onExportHtml = {
                                val file = vm.exportHtml()
                                if (file != null) {
                                    val tree = prefs.saveTreeUri
                                    val saved = tree?.let {
                                        vm.store().saveToTree(android.net.Uri.parse(it), file.name, "text/html", file.readBytes())
                                    }
                                    if (saved != null) {
                                        vm.message = s("报告已存到你选的文件夹：${file.name}", "Report saved to the folder you picked: ${file.name}")
                                    } else {
                                        context.startActivity(
                                            vm.store().shareIntent(file, "text/html", s("GitHub 网络自检报告", "GitHub Network Check report"))
                                        )
                                    }
                                }
                            },
                            onExportCsv = {
                                val file = vm.exportCsv()
                                if (file != null) {
                                    val tree = prefs.saveTreeUri
                                    val saved = tree?.let {
                                        vm.store().saveToTree(android.net.Uri.parse(it), file.name, "text/csv", file.readBytes())
                                    }
                                    if (saved != null) {
                                        vm.message = s("CSV 已存到你选的文件夹：${file.name}", "CSV saved to the folder you picked: ${file.name}")
                                    } else {
                                        context.startActivity(
                                            vm.store().shareIntent(file, "text/csv", s("GitHub 网络自检数据", "GitHub Network Check data"))
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                vm.message?.let { msg ->
                    item { MessageBanner(p, msg) { vm.message = null } }
                }

                if (vm.phase == Phase.Done && vm.results.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SegmentedTabs(
                                p = p,
                                issuesSelected = viewIdx != 0,
                                issueCount = issueList.size,
                                okCount = okList.size,
                                onIssues = { viewIdx = 1 },
                                onOk = { viewIdx = 0 }
                            )
                            if (viewIdx != 0) {
                                Spacer(Modifier.height(9.dp))
                                SeverityTabs(
                                    p = p,
                                    viewIdx = viewIdx,
                                    allCount = issueList.size,
                                    manyCount = severeList.size,
                                    midCount = mediumList.size,
                                    fewCount = mildList.size
                                ) { viewIdx = it }
                            }
                        }
                    }
                }

                if (visibleResults.isEmpty() && vm.results.isNotEmpty()) {
                    item {
                        Text(
                            when (viewIdx) {
                                0 -> t("本次没有正常点位", "Nothing came back OK")
                                1 -> t("本次没有异常点位", "No issues this run")
                                2 -> t("没有完全不通的点位", "Nothing was fully blocked")
                                3 -> t("没有部分阶段失败的点位", "No partial-stage failures")
                                else -> t("没有通但很慢的点位", "Nothing was merely slow")
                            },
                            fontSize = 13.sp,
                            color = p.sub,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        )
                    }
                }

                items(visibleResults, key = { it.name + it.host }) { r ->
                    ResultRow(
                        r = r,
                        expanded = expanded == r.name,
                        onClick = { expanded = if (expanded == r.name) null else r.name }
                    )
                }
            }
            }
            }
            }

        Footer(
            p = p,
            onHistory = { showHistory = true },
            onCopy = { clipboard.setText(AnnotatedString(it)) }
        )
    }

    if (showSettings) {
        SettingsSheet(
            prefs = prefs,
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showSettings = false },
            onOpenAbout = { showSettings = false; showAbout = true },
            onToast = { vm.message = it },
            onThemeChanged = onThemeChanged,
            onLangChanged = onLangChanged
        )
    }
    if (showHistory) {
        HistorySheet(
            entries = vm.history,
            onDismiss = { showHistory = false },
            onClear = { vm.clearHistory() },
            onToast = { vm.message = it }
        )
    }
    if (showAbout) {
        AboutSheet(
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showAbout = false },
            onToast = { vm.message = it }
        )
    }

    // 启动时静默查一次更新：查到新版才弹提示，应用内下载安装，浏览器 / 复制链接作为退路
    var updateInfo by remember { mutableStateOf<com.neurotracee.netcheck.data.UpdateChecker.Info?>(null) }
    LaunchedEffect(Unit) {
        if (!prefs.autoCheckUpdate) return@LaunchedEffect
        val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.neurotracee.netcheck.data.UpdateChecker.check(prefs.updateSource, BuildConfig.VERSION_NAME).getOrNull()
        }
        if (info != null && com.neurotracee.netcheck.data.UpdateChecker.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
            updateInfo = info
        }
    }
    updateInfo?.let { info ->
        UpdateDialog(info = info, onClose = { updateInfo = null }, onToast = { vm.message = it })
    }
}

@Composable
private fun IdleHero(
    p: Palette,
    env: NetEnv,
    endpointCount: Int,
    message: String?,
    onDismissMessage: () -> Unit,
    onSettings: () -> Unit,
    onRun: () -> Unit,
    onQuick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        // 顶部一行：标题 + 点位数胶囊 + 右上齿轮，不加边框，和下面的卡片留出呼吸感
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("GitHub 网络自检", "GitHub Network Check"),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink
            )
            Spacer(Modifier.width(8.dp))
            Pill(p, t("$endpointCount 个点位", "$endpointCount endpoints"), false)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", fontSize = 20.sp, color = p.sub)
            }
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            MessageBanner(p, message, onDismissMessage)
        }
        Spacer(Modifier.height(16.dp))
        HeroCard(
            p = p,
            tag = t("本机自检", "ON-DEVICE"),
            status = t("待机", "Idle"),
            big = t("还没测过", "Not run yet"),
            sub = t("四段分别计时，结果只留在本机", "Four stages, timed separately. Results stay on this device."),
            progress = null,
            primaryLabel = t("开始自检", "Start check"),
            onPrimary = onRun,
            secondaryLabel = t("只测 GitHub 系（更快）", "GitHub-only (faster)"),
            onSecondary = onQuick
        )
        Spacer(Modifier.height(14.dp))
        if (env.networkName.isNotEmpty()) {
            InfoCard(
                p = p,
                icon = "📶",
                title = t("当前网络", "Network"),
                sub = env.networkName + if (env.vpnActive) t(" · VPN 已启用", " · VPN on") else "",
                chip = t("就绪", "Ready"),
                chipWarn = false
            )
        }
        if (env.hostsHits > 0) {
            Spacer(Modifier.height(12.dp))
            InfoCard(
                p = p,
                icon = "🗂",
                title = t("本地 hosts", "Local hosts"),
                sub = t("${env.hostsHits} 条映射会一并生效", "${env.hostsHits} mappings apply too"),
                chip = t("${env.hostsHits} 条", "${env.hostsHits}"),
                chipWarn = true
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/** 运行中与结果态共用同一张卡：进行中显示进度，跑完显示结论 */
@Composable
private fun RunHero(
    p: Palette,
    phase: Phase,
    run: RunResult?,
    current: Int,
    total: Int,
    name: String,
    onRun: () -> Unit,
    onQuick: () -> Unit,
    onCancel: () -> Unit
) {
    if (phase == Phase.Running) {
        val frac = if (total <= 0) 0f else current.toFloat() / total
        HeroCard(
            p = p,
            tag = t("正在自检", "CHECKING"),
            status = if (total > 0) "$current / $total" else t("准备中", "Preparing"),
            big = if (total > 0) "$current / $total" else "—",
            sub = if (name.isNotEmpty()) name else t("正在逐个建连…", "Connecting…"),
            progress = frac,
            primaryLabel = t("停止", "Stop"),
            onPrimary = onCancel,
            secondaryLabel = null,
            onSecondary = null
        )
    } else if (run != null) {
        HeroCard(
            p = p,
            tag = t("本次结果", "THIS RUN"),
            status = t("用时 ${run.elapsedSec} 秒", "${run.elapsedSec}s"),
            big = "${run.okCount} / ${run.total}",
            sub = t(
                "可连通 · 平均 ${run.avgTcp}ms · 异常 ${run.failCount} 项",
                "reachable · avg ${run.avgTcp}ms · ${run.failCount} issues"
            ),
            progress = null,
            primaryLabel = t("重新自检", "Run again"),
            onPrimary = onRun,
            secondaryLabel = t("只测 GitHub 系（更快）", "GitHub-only (faster)"),
            onSecondary = onQuick
        )
    }
}

/** 大色块卡片：一张卡讲完一件事，主按钮压在卡里，视觉重心不再散 */
@Composable
private fun HeroCard(
    p: Palette,
    tag: String,
    status: String?,
    big: String,
    sub: String,
    progress: Float?,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    wave: Boolean = false
) {
    val dark = p.ink.luminance() > 0.55f
    val grad = if (dark) {
        listOf(Color(0xFF14532F), Color(0xFF1C7746))
    } else {
        listOf(Color(0xFF0E9A4A), Color(0xFF46C97B))
    }
    // 卡片落地时纵向压一下再弹回去，配合上面的滑入做成「果冻」感
    val jelly = remember { Animatable(0.90f) }
    LaunchedEffect(Unit) {
        jelly.animateTo(1f, animationSpec = spring(dampingRatio = 0.34f, stiffness = 240f))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleY = jelly.value
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(grad))
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tag,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.weight(1f))
                if (status != null) {
                    Text(
                        status,
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                big,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 41.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                sub,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.84f),
                lineHeight = 19.sp
            )
            if (wave) {
                Spacer(Modifier.height(14.dp))
                WaveField()
            }
            if (progress != null) {
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.24f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (primaryLabel != null && onPrimary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                        .clickable { onPrimary() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(primaryLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            if (secondaryLabel != null && onSecondary != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable { onSecondary() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        secondaryLabel,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.padding(vertical = 9.dp)
                    )
                }
            }
        }
    }
}

/** 三条相位与速度互不相同的正弦曲线交织流动，做出「正在抓包」的动势，纯 Canvas 绘制 */
@Composable
private fun WaveField() {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        for (i in 0 until 3) {
            val amp = h * 0.26f * (1f - i * 0.16f)
            val freq = 2.0f + i * 0.85f
            val dir = if (i % 2 == 0) 1f else -1f
            val path = Path()
            var x = 0f
            while (x <= w) {
                val y = cy + kotlin.math.sin((x / w) * freq * 2f * Math.PI.toFloat() + phase * dir + i * 1.1f) * amp
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 4f
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.13f + i * 0.11f),
                style = Stroke(width = 2.2.dp.toPx())
            )
        }
    }
}

/** 白卡信息行：左边一块图标底，中间标题与说明，右边一个小胶囊 */
@Composable
private fun InfoCard(p: Palette, icon: String, title: String, sub: String, chip: String, chipWarn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(p.card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(p.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            chip,
            fontSize = 11.5.sp,
            color = if (chipWarn) p.warnText else p.good,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (chipWarn) p.warnBg else p.accentSoft)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun Header(p: Palette, run: RunResult?, idleHint: String, onSettings: () -> Unit) {
    // 只留一行状态：跑过就显示时间与用时，没跑就显示点位数。原来那两行说明已合并掉
    val meta = if (run != null) {
        t(
            "${ReportBuilder.timeStamp(run.startedAtMillis)}　·　用时 ${run.elapsedSec} 秒",
            "${ReportBuilder.timeStamp(run.startedAtMillis)}　·　${run.elapsedSec}s"
        )
    } else {
        idleHint
    }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                t("GITHUB NET CHECK · 本机自检", "GITHUB NET CHECK · ON-DEVICE"),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = p.sub,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                t("GitHub 网络自检", "GitHub Network Check"),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(meta, fontSize = 12.5.sp, color = p.sub, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.card)
                .border(1.dp, p.line, RoundedCornerShape(12.dp))
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", fontSize = 18.sp, color = p.sub)
        }
    }
}

@Composable
private fun EnvPills(p: Palette, env: NetEnv, centered: Boolean = false) {
    // 只在「值得知道」的异常项上出胶囊：正常状态一律不占位，避免一排小字糊满屏幕
    fun enName(raw: String) = when (raw) {
        "移动数据" -> "Mobile data"
        "以太网" -> "Ethernet"
        "关闭" -> "Off"
        "自动" -> "Auto"
        else -> raw
    }

    val items = mutableListOf<Pair<String, Boolean>>()
    if (env.networkName.isNotEmpty()) {
        items += (enName(env.networkName) to false)
    }
    if (env.proxyEnabled) items += (t("系统代理", "Proxy") to true)
    if (env.vpnActive) items += (t("VPN", "VPN") to true)
    if (env.privateDns.isNotEmpty() && env.privateDns != "关闭") {
        items += (t("私有 DNS", "Private DNS") to false)
    }
    if (env.hostsHits > 0) {
        items += (t("hosts ${env.hostsHits} 条映射", "hosts: ${env.hostsHits}") to true)
    }

    if (items.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (text, warn) ->
            Box(Modifier.padding(horizontal = 3.dp)) { Pill(p, text, warn) }
        }
    }
}

@Composable
private fun Pill(p: Palette, text: String, warn: Boolean) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = if (warn) p.warnText else p.sub,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (warn) p.warnBg else p.card)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun StartButtons(p: Palette, onRun: () -> Unit, onQuick: () -> Unit) {
    // 一个主按钮承担全部视觉重量，次选项降成文字按钮，不再两个大按钮平起平坐
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onRun,
            modifier = Modifier.width(232.dp).height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.good, contentColor = Color.White)
        ) {
            Text(t("开始自检", "Start check"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onQuick) {
            Text(t("只测 GitHub 系（更快）", "GitHub-only (faster)"), fontSize = 12.5.sp, color = p.sub)
        }
    }
}

@Composable
private fun ResultActions(
    p: Palette,
    onRun: () -> Unit,
    onQuick: () -> Unit,
    onExportHtml: () -> Unit,
    onExportCsv: () -> Unit
) {
    // 重跑与快速模式已经在大卡里了，这里只留两个导出入口，避免同一屏出现两份相同按钮
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onExportHtml,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink),
            border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
        ) {
            Text(t("导出报告", "Export report"), fontSize = 13.sp)
        }
        OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink),
            border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
        ) {
            Text(t("分享 CSV", "Share CSV"), fontSize = 13.sp)
        }
    }
}

@Composable
private fun RunningCard(p: Palette, current: Int, total: Int, name: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (total > 0) t("正在测试 $current / $total", "Testing $current / $total") else t("准备中", "Preparing"),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) {
                Text(t("停止", "Stop"), fontSize = 12.sp, color = p.sub)
            }
        }
        Text(name, fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        val fraction = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = p.good,
            trackColor = p.track
        )
    }
}

@Composable
private fun MessageBanner(p: Palette, text: String, onDismiss: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = p.warnText,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.warnBg)
            .clickable { onDismiss() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SummaryGrid(p: Palette, run: RunResult) {
    // 原来 2×2 四张大卡 + 一句总结，占了半屏；现在压成一条四格横排
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryMetric(
            p = p,
            label = t("可连通", "Reachable"),
            value = "${run.okCount}/${run.total}",
            modifier = Modifier.weight(1f),
            accent = if (run.failCount > 0) p.warnText else p.ink
        )
        VertSep(p)
        SummaryMetric(
            p = p,
            label = t("平均延迟", "Avg connect"),
            value = "${run.avgTcp}ms",
            modifier = Modifier.weight(1f)
        )
        VertSep(p)
        SummaryMetric(
            p = p,
            label = t("最快", "Fastest"),
            value = (run.fastest?.tcpMs?.toString() ?: "-") + "ms",
            modifier = Modifier.weight(1f)
        )
        VertSep(p)
        SummaryMetric(
            p = p,
            label = t("异常", "Issues"),
            value = "${run.failCount}",
            modifier = Modifier.weight(1f),
            accent = if (run.failCount > 0) p.down else p.ink
        )
    }
}

@Composable
private fun VertSep(p: Palette) {
    Box(Modifier.width(1.dp).height(24.dp).background(p.line))
}

@Composable
private fun SummaryMetric(
    p: Palette,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = accent ?: p.ink, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.5.sp, color = p.sub, maxLines = 1)
    }
}

@Composable
private fun ResultRow(r: TargetResult, expanded: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val levelColor = p.level(r.level)

    // 收起状态只留三样：名称、评级、四段耗时。host / IP / DoH / 描述全部收进展开区，
    // 这样一屏能看的目标从 3 个变成 6 个左右
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .drawBehind {
                drawRect(color = levelColor, size = Size(4.dp.toPx(), size.height))
            }
            .clickable { onClick() }
            .padding(start = 18.dp, end = 16.dp, top = 15.dp, bottom = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                r.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = levelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(levelColor.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth()) {
            MetricCell(p, "DNS", if (r.dnsOk) r.dnsMs.toString() else "—", r.dnsOk, Modifier.weight(1f))
            MetricCell(p, "TCP", if (r.tcpOk) r.tcpMs.toString() else "—", r.tcpOk, Modifier.weight(1f))
            MetricCell(p, "TLS", if (r.ok) r.tlsMs.toString() else "—", r.ok, Modifier.weight(1f))
            MetricCell(p, "HTTP", if (r.ok) r.ttfbMs.toString() else "—", r.ok, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(p.track)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(r.barPercent / 100f)
                    .fillMaxHeight()
                    .background(levelColor.copy(alpha = if (r.level == Levels.DOWN) 0.6f else 1f))
            )
        }
        if (r.hint.isNotEmpty() || !r.ok) {
            Spacer(Modifier.height(9.dp))
            Text(
                listOf(r.hint, if (!r.ok) r.error else "").filter { it.isNotEmpty() }.joinToString("　"),
                fontSize = 11.5.sp,
                color = p.down,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.line)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                r.host,
                fontSize = 11.5.sp,
                color = p.sub,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(levelColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(r.ip, fontSize = 11.5.sp, color = p.sub)
                if (r.dohIp.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (r.dohIp == r.ip) t("DoH 一致", "DoH: same") else t("DoH 真实解析 ${r.dohIp}", "DoH resolves to ${r.dohIp}"),
                        fontSize = 11.5.sp,
                        color = if (r.dohIp == r.ip) p.good else p.warnText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(r.desc, fontSize = 12.sp, color = p.sub, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            DetailLine(p, t("评级依据", "Basis"), "${Verdict.text(r.level)}　·　${r.label}")
            DetailLine(p, t("HTTP 状态", "HTTP status"), if (r.status > 0) r.status.toString() else "—")
            DetailLine(p, t("本机解析", "Local DNS"), r.ip)
            DetailLine(p, t("DoH 对照", "DoH result"), r.dohIp.ifEmpty { t("未取到", "n/a") })
            DetailLine(p, t("DNS 耗时", "DNS time"), if (r.dnsOk) "${r.dnsMs} ms" else t("解析失败", "failed"))
            DetailLine(p, t("TCP 建连", "TCP connect"), if (r.tcpOk) "${r.tcpMs} ms" else t("未建立", "not opened"))
            DetailLine(p, t("TLS 握手", "TLS handshake"), if (r.ok) "${r.tlsMs} ms" else t("未完成", "incomplete"))
            DetailLine(p, t("HTTP 首字节", "HTTP first byte"), if (r.ok) "${r.ttfbMs} ms" else t("未收到", "none"))
            DetailLine(p, t("用途说明", "What it does"), r.desc)
        }
    }
}

@Composable
private fun MetricCell(p: Palette, label: String, value: String, hasData: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasData) p.ink else p.sub.copy(alpha = 0.55f)
        )
        Text(
            label,
            fontSize = 9.5.sp,
            color = p.sub,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun DetailLine(p: Palette, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = p.sub, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 11.sp, color = p.ink, modifier = Modifier.weight(1f))
    }
}

/** 结果页顶部居中的分段切换：异常 / 正常。异常用红、正常用绿，一眼对上语义 */
@Composable
private fun SegmentedTabs(
    p: Palette,
    issuesSelected: Boolean,
    issueCount: Int,
    okCount: Int,
    onIssues: () -> Unit,
    onOk: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Segment(p, t("异常 $issueCount", "Issues $issueCount"), issuesSelected, p.down, onIssues)
        Segment(p, t("正常 $okCount", "OK $okCount"), !issuesSelected, p.good, onOk)
    }
}

/** 异常内部再按严重程度分档：严重＝完全不通，中等＝部分阶段失败，轻微＝通但很慢 */
@Composable
private fun SeverityTabs(
    p: Palette,
    viewIdx: Int,
    allCount: Int,
    manyCount: Int,
    midCount: Int,
    fewCount: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.card)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        MiniSegment(p, t("全部 $allCount", "All $allCount"), viewIdx == 1) { onSelect(1) }
        MiniSegment(p, t("严重 $manyCount", "Severe $manyCount"), viewIdx == 2) { onSelect(2) }
        MiniSegment(p, t("中等 $midCount", "Moderate $midCount"), viewIdx == 3) { onSelect(3) }
        MiniSegment(p, t("轻微 $fewCount", "Mild $fewCount"), viewIdx == 4) { onSelect(4) }
    }
}

@Composable
private fun MiniSegment(p: Palette, label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) p.ink else p.sub,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) p.bg else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
private fun Segment(p: Palette, label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) Color.White else p.sub,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) color else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

@Composable
private fun Footer(p: Palette, onHistory: () -> Unit, onCopy: (String) -> Unit) {
    // 底栏：一个小的历史记录入口 + 版本行 + 两个仓库入口，保持轻盈
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            t("历史记录", "History"),
            fontSize = 12.5.sp,
            color = p.fair,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(p.card)
                .clickable { onHistory() }
                .padding(horizontal = 16.dp, vertical = 7.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            t(
                "v${BuildConfig.VERSION_NAME}　·　Apache-2.0　·　不上传数据",
                "v${BuildConfig.VERSION_NAME}　·　Apache-2.0　·　nothing uploaded"
            ),
            fontSize = 10.5.sp,
            color = p.sub,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "★ GitHub",
                fontSize = 11.sp,
                color = p.fair,
                modifier = Modifier.clickable { onCopy(PROJECT_URL) }.padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Text("·", fontSize = 11.sp, color = p.sub)
            Text(
                "★ Gitee",
                fontSize = 11.sp,
                color = p.fair,
                modifier = Modifier.clickable { onCopy(MIRROR_URL) }.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun Legend(p: Palette, color: Color, label: String, threshold: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text("$label $threshold", fontSize = 10.5.sp, color = p.sub, maxLines = 1)
    }
}

@Composable
private fun LinkChip(p: Palette, text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = p.fair,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, p.line, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

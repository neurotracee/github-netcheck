package com.neurotracee.netcheck.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.neurotracee.netcheck.data.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val PROJECT_URL = "https://github.com/neurotracee/github-netcheck"
private const val MIRROR_URL = "https://gitee.com/bio234/github-netcheck"
private const val ISSUE_URL = "$PROJECT_URL/issues/new/choose"
private const val MIRROR_ISSUE_URL = "$MIRROR_URL/issues"

/**
 * 应用日志页：本次启动 / 按日期查历史 / 复制 / 存 TXT / 按区间清除 / 反馈模板。
 *
 * 交互刻意保持「看得见范围」：每次删除都会先把「哪几天、多少天、多大」念一遍，
 * 再让用户确认两次，不做那种一按就清空、事后没法救的设计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    versionName: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    var tab by remember { mutableStateOf(0) }                       // 0 本次启动 · 1 历史
    var session by remember { mutableStateOf(LogStore.sessionText()) }
    var fromText by remember { mutableStateOf(fmt.format(LocalDate.now())) }
    var toText by remember { mutableStateOf(fmt.format(LocalDate.now())) }
    var fromTime by remember { mutableStateOf("") }
    var toTime by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(LogStore.availableDays()) }
    var pending by remember { mutableStateOf(LogStore.pendingCrash()) }
    var confirmStage by remember { mutableStateOf(0) }
    var confirmText by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf(0 to 0L) }
    var timeTarget by remember { mutableStateOf(-1) }   // -1 关 · 0 起始时间 · 1 结束时间
    var dateTarget by remember { mutableStateOf(-1) }   // -1 关 · 0 起始日期 · 1 结束日期

    fun toast(s: String) = onToast(s)

    fun parseRange(): Pair<LocalDate, LocalDate>? {
        val f = runCatching { LocalDate.parse(fromText.trim(), fmt) }.getOrNull()
        val t = runCatching { LocalDate.parse(toText.trim(), fmt) }.getOrNull()
        if (f == null || t == null) { toast(tr(context, "日期格式要写成 2026-11-20", "Dates must look like 2026-11-20")); return null }
        if (t.isBefore(f)) { toast(tr(context, "结束日期不能早于起始日期", "The end date is before the start date")); return null }
        return f to t
    }

    fun validTime(s: String): Boolean =
        s.isBlank() || Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(s.trim())

    fun reload() {
        val r = parseRange() ?: return
        if (!validTime(fromTime) || !validTime(toTime)) {
            toast(tr(context, "时间要写成 09:30 这种 24 小时制", "Times must use 24-hour HH:mm"))
            return
        }
        scope.launch {
            preview = withContext(Dispatchers.IO) {
                LogStore.readRange(r.first, r.second, fromTime.trim().ifBlank { null }, toTime.trim().ifBlank { null })
            }
            if (preview.isEmpty()) toast(tr(context, "这段时间没有日志", "No logs in that range"))
        }
    }

    fun refreshAll() {
        session = LogStore.sessionText()
        days = LogStore.availableDays()
        pending = LogStore.pendingCrash()
        preview = ""
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val body = if (tab == 0) session else preview
        if (body.isBlank()) { toast(tr(context, "没有可保存的内容", "Nothing to save")); return@rememberLauncherForActivityResult }
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }.isSuccess
        toast(if (ok) tr(context, "已保存为 TXT", "Saved as TXT") else tr(context, "保存失败，换个目录试试", "Save failed — try another folder"))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.bg, dragHandle = null) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 28.dp)
        ) {
            // 顶部：只留标题和一句说明
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("应用日志", "App logs"), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(t("最近 365 天 · 只在本机", "Last 365 days · stays on this device"),
                        fontSize = 11.sp, color = p.sub)
                }
                Text(t("完成", "Done"), fontSize = 14.sp, color = p.fair, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }

            // 上次异常退出：压缩成一行，点「查看」直接跳到本次启动
            if (pending != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.warnBg)
                        .border(1.dp, p.warnBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t("上次运行异常退出", "Last run ended unexpectedly"), fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold, color = p.warnText)
                        Spacer(Modifier.height(2.dp))
                        Text(t("堆栈就在本次启动的日志里", "The stack trace is in this launch's log"),
                            fontSize = 11.sp, color = p.warnText)
                    }
                    Text(t("查看", "View"), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.warnText,
                        modifier = Modifier
                            .clickable {
                                LogStore.dismissCrash(); pending = null; tab = 0; session = LogStore.sessionText()
                            }
                            .padding(6.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Segmented(p, listOf(t("本次启动", "This launch"), t("历史日志", "History")), tab) { i ->
                tab = i
                if (i == 1) reload()
            }

            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
                // 本次启动：一块卡片，数字 + 日志 + 三个动作
                SheetCard(p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("本次启动", "This launch"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                        Spacer(Modifier.weight(1f))
                        val sLines = lineCount(session)
                        MetaText(p, t("$sLines 行", "$sLines lines"))
                    }
                    Spacer(Modifier.height(10.dp))
                    val sessionHint = t("这次启动还没有日志", "No log lines yet")
                    LogBox(p, session.ifBlank { sessionHint },
                        Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 380.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val copiedMsg = t("日志已复制", "Log copied")
                        PillButton(p, t("复制", "Copy"), primary = true) { copyText(clipboard, session, copiedMsg, { s -> toast(s) }) }
                        PillButton(p, t("存为 TXT", "Save TXT")) { saveLauncher.launch(LogStore.exportFileName()) }
                        PillButton(p, t("刷新", "Refresh")) { session = LogStore.sessionText() }
                    }
                }
            } else {
                // 时间范围卡片：日期、时间都是点一下弹选择器，不再一排文本框
                SheetCard(p) {
                    SectionHead(p, t("时间范围", "Time range"), t("点一下就能选", "Tap to pick"))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EdgeLabel(p, t("起", "From"))
                        Column(Modifier.weight(1f)) {
                            Text(t("日期", "Date"), fontSize = 11.sp, color = p.sub)
                            Spacer(Modifier.height(4.dp))
                            DateChip(p, fromText, Modifier.fillMaxWidth()) { dateTarget = 0 }
                        }
                        TimeField(p, t("时间", "Time"), fromTime, Modifier.weight(0.85f),
                            onPick = { timeTarget = 0 }, onClear = { fromTime = "" })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EdgeLabel(p, t("止", "To"))
                        Column(Modifier.weight(1f)) {
                            Text(t("日期", "Date"), fontSize = 11.sp, color = p.sub)
                            Spacer(Modifier.height(4.dp))
                            DateChip(p, toText, Modifier.fillMaxWidth()) { dateTarget = 1 }
                        }
                        TimeField(p, t("时间", "Time"), toTime, Modifier.weight(0.85f),
                            onPick = { timeTarget = 1 }, onClear = { toTime = "" })
                    }

                    if (days.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(t("有日志的日期", "Days with logs"), fontSize = 10.5.sp, color = p.sub)
                        Spacer(Modifier.height(7.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            days.take(30).forEach { d ->
                                val s = fmt.format(d)
                                MiniChip(p, fmt.format(d).substring(5), selected = s == fromText && s == toText) {
                                    fromText = s; toText = s; fromTime = ""; toTime = ""; reload()
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PillButton(p, t("查询", "Search"), primary = true, fill = true) { reload() }
                }

                Spacer(Modifier.height(10.dp))
                // 查询结果卡片
                SheetCard(p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("查询结果", "Result"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                        Spacer(Modifier.weight(1f))
                        val rLines = lineCount(preview).toString()
                        val rKb = (preview.toByteArray().size / 1024).toString()
                        MetaText(p, if (preview.isBlank()) t("未查询", "Not queried") else t("$rLines 行 · $rKb KB", "$rLines lines · $rKb KB"))
                    }
                    Spacer(Modifier.height(10.dp))
                    val previewHint = t("选好范围后点「查询」", "Pick a range, then tap Search")
                    LogBox(p, preview.ifBlank { previewHint },
                        Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 320.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val copiedMsg = t("已复制", "Copied")
                        PillButton(p, t("复制", "Copy"), primary = true) {
                            if (preview.isBlank()) toast(tr(context, "先查一下再复制", "Search first"))
                            else copyText(clipboard, preview, copiedMsg, { s -> toast(s) })
                        }
                        PillButton(p, t("存为 TXT", "Save TXT")) {
                            if (preview.isBlank()) toast(tr(context, "先查一下再保存", "Search first"))
                            else saveLauncher.launch(LogStore.exportFileName())
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    t("清除这段日期的日志", "Delete logs in this range"),
                    fontSize = 12.5.sp, color = p.down,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val r = parseRange() ?: return@clickable
                            stats = LogStore.rangeStats(r.first, r.second)
                            confirmText = "${fmt.format(r.first)} → ${fmt.format(r.second)}"
                            confirmStage = 1
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            FeedbackCard(p, versionName, context, clipboard, onToast)
        }
    }

    // 日期选择器
    if (dateTarget >= 0) {
        val seedMs = runCatching { LocalDate.parse(if (dateTarget == 0) fromText.trim() else toText.trim(), fmt) }
            .getOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = seedMs)
        DatePickerDialog(
            onDismissRequest = { dateTarget = -1 },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = p.paper),
            confirmButton = {
                TextButton(onClick = {
                    val ms = pickerState.selectedDateMillis
                    if (ms != null) {
                        val s = fmt.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                        if (dateTarget == 0) fromText = s else toText = s
                    }
                    dateTarget = -1
                }) { Text(t("确定", "OK"), color = p.fair) }
            },
            dismissButton = { TextButton(onClick = { dateTarget = -1 }) { Text(t("取消", "Cancel"), color = p.sub) } }
        ) {
            DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
        }
    }

    // 时间滚轮
    if (timeTarget >= 0) {
        val initial = if (timeTarget == 0) fromTime else toTime
        TimePickerDialog(
            title = if (timeTarget == 0) t("起始时间", "From time") else t("结束时间", "To time"),
            initial = initial,
            onDismiss = { timeTarget = -1 },
            onConfirm = { v ->
                if (timeTarget == 0) fromTime = v else toTime = v
                timeTarget = -1
            }
        )
    }

    // 两段确认：先念清楚删哪几天，再问一次「确定吗」
    if (confirmStage == 1) {
        AlertDialog(
            onDismissRequest = { confirmStage = 0 },
            containerColor = p.paper,
            titleContentColor = p.ink,
            textContentColor = p.ink,
            title = { Text(t("删除前的确认", "Before deleting")) },
            text = {
                Text(
                    t("将要删除 $confirmText 的日志，共 ${stats.first} 天、约 ${stats.second / 1024} KB。\n删除后无法恢复。",
                        "This will delete logs for $confirmText — ${stats.first} file(s), about ${stats.second / 1024} KB.\nDeleted logs cannot be recovered."),
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmStage = 2 }) {
                    Text(t("继续", "Continue"), color = p.down)
                }
            },
            dismissButton = { TextButton(onClick = { confirmStage = 0 }) { Text(t("取消", "Cancel")) } }
        )
    }
    if (confirmStage == 2) {
        AlertDialog(
            onDismissRequest = { confirmStage = 0 },
            containerColor = p.paper,
            titleContentColor = p.ink,
            textContentColor = p.ink,
            title = { Text(t("再确认一次", "Confirm once more")) },
            text = {
                Text(t("确定永久删除 $confirmText 这些日志吗？这一步不能撤销。",
                    "Permanently delete the logs for $confirmText? This cannot be undone."), fontSize = 13.5.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = runCatching { LocalDate.parse(fromText.trim(), fmt) to LocalDate.parse(toText.trim(), fmt) }.getOrNull()
                    if (r != null) {
                        val n = LogStore.clearRange(r.first, r.second, fromTime.trim().ifBlank { null }, toTime.trim().ifBlank { null })
                        toast(tr(context, "已删除 ${n} 天的日志", "Deleted logs for $n day(s)"))
                    }
                    confirmStage = 0
                    refreshAll()
                    reload()
                }) { Text(t("永久删除", "Delete"), color = p.down) }
            },
            dismissButton = { TextButton(onClick = { confirmStage = 0 }) { Text(t("取消", "Cancel")) } }
        )
    }
}

// ---------------- 卡片与小组件 ----------------

@Composable
private fun SheetCard(
    p: Palette,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun SectionHead(p: Palette, title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        Spacer(Modifier.weight(1f))
        Text(hint, fontSize = 10.5.sp, color = p.sub)
    }
}

@Composable
private fun MetaText(p: Palette, text: String) {
    Text(text, fontSize = 10.5.sp, color = p.sub)
}

/** 两个页签做成一个整体胶囊，比并排两个方块干净。 */
@Composable
private fun Segmented(p: Palette, items: List<String>, index: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.track)
            .padding(4.dp)
    ) {
        items.forEachIndexed { i, s ->
            val active = i == index
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) p.card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s,
                    fontSize = 12.5.sp,
                    color = if (active) p.ink else p.sub,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    p: Palette,
    text: String,
    primary: Boolean = false,
    fill: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(11.dp)
    val base = if (fill) Modifier.fillMaxWidth() else Modifier
    Box(
        base
            .clip(shape)
            .background(if (primary) p.fair else p.card)
            .border(1.dp, if (primary) p.fair else p.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.5.sp, color = if (primary) Color.White else p.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EdgeLabel(p: Palette, text: String) {
    Text(text, fontSize = 10.5.sp, color = p.sub, modifier = Modifier.width(14.dp))
}

/** 日期格子：点一下弹日历，省掉一排手打日期的输入框。 */
@Composable
private fun DateChip(p: Palette, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier
            .clip(shape)
            .background(p.paper)
            .border(1.dp, p.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(value, fontSize = 13.sp, color = p.ink)
    }
}

/** 有日志的日期小胶囊，横向滑，不占纵向版面。 */
@Composable
private fun MiniChip(p: Palette, text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) p.fair else p.card)
            .border(1.dp, if (selected) p.fair else p.line, shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 11.5.sp, color = if (selected) Color.White else p.ink)
    }
}

@Composable
private fun LogBox(p: Palette, text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.paper)
            .border(1.dp, p.line, RoundedCornerShape(12.dp))
    ) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
            Text(
                text,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = p.ink
            )
        }
    }
}

private fun lineCount(text: String): Int = text.lineSequence().count { it.isNotBlank() }

private fun copyText(
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    text: String,
    message: String,
    toast: (String) -> Unit
) {
    clipboard.setText(AnnotatedString(text))
    toast(message)
}

/** 反馈卡：默认只露一行说明，模板折叠起来，想看再展开。 */
@Composable
private fun FeedbackCard(
    p: Palette,
    versionName: String,
    context: android.content.Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    toast: (String) -> Unit
) {
    val device = LogStore.deviceLine.ifBlank { t("在手机上自动获取", "Collected on your phone") }
    val template = t(
        """版本号：v$versionName
机型系统：$device
问题一句话：打开设置页再返回首页，顶部卡片被标题栏盖住一截

复现步骤：
1. 打开 App，等首页加载完
2. 点右上角齿轮进设置，停 2 秒
3. 按返回键回到首页
   结果：首页顶部那张卡片的上边缘被压住，露出半行字

实际看到：卡片上沿缺了一截，标题压在卡片里
预期结果：回到首页时卡片完整，标题不与卡片重叠

日志：已随本 Issue 附上 TXT（复制时可用「复制日志」按钮，或在日志页「存为 TXT」）""",
        """Version: v$versionName
Device: $device
In one sentence: the top card is clipped after returning from Settings

Steps to reproduce:
1. Open the app and wait for the home screen to load
2. Tap the gear in the top right, stay 2 seconds
3. Press back to the home screen
   Result: the top edge of the card on the home screen is cut off

What I saw: the card's top edge is missing and the title sits inside it
What I expected: the card should be complete and the title should not overlap it

Log: attach the TXT exported from the log screen"""
    )
    var expanded by remember { mutableStateOf(false) }

    SheetCard(p) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("有问题要反馈？", "Reporting a bug?"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text(t("版本号与机型已自动填好", "Version and device are pre-filled"),
                    fontSize = 11.sp, color = p.sub)
            }
            Text(
                if (expanded) t("收起", "Hide") else t("看模板", "Template"),
                fontSize = 11.5.sp, color = p.fair, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { expanded = !expanded }.padding(6.dp)
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.paper)
                    .border(1.dp, p.line, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(template, fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace, color = p.ink)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(p, t("复制模板", "Copy template"), primary = true) {
                clipboard.setText(AnnotatedString(template))
                toast(tr(context, "模板已复制，记得把日志一起附上", "Template copied — attach the log as well"))
            }
            PillButton(p, "GitHub") { openLogUrl(context, ISSUE_URL, toast) }
            PillButton(p, "Gitee") { openLogUrl(context, MIRROR_ISSUE_URL, toast) }
        }
    }
}

private fun openLogUrl(context: android.content.Context, url: String, toast: (String) -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(tr(context, "没有能打开浏览器的应用", "No app can open a browser")) }
}

// ---------------- 时间选择：双滚轮（时 / 分） ----------------

private val WHEEL_HOURS = (0..23).toList()
private val WHEEL_MINUTES = (0..59).toList()

/** 时间字段：显示 `HH:mm` 或「整天」，点了打开滚轮；右侧 × 清空。 */
@Composable
private fun TimeField(
    p: Palette,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = p.sub)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(p.paper)
                .border(1.dp, p.line, RoundedCornerShape(10.dp))
                .clickable { onPick() }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (value.isBlank()) t("整天", "Whole day") else value,
                fontSize = 13.sp,
                color = if (value.isBlank()) p.sub else p.ink,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotBlank()) {
                Text("×", fontSize = 15.sp, color = p.sub,
                    modifier = Modifier.clickable { onClear() }.padding(4.dp))
            } else {
                Text("▾", fontSize = 12.sp, color = p.sub)
            }
        }
    }
}

/** 时分选择窗：左轮是时、右轮是分，右下角「确定」。 */
@Composable
private fun TimePickerDialog(
    title: String,
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val p = LocalPalette.current
    val seed = remember(initial) {
        val parts = initial?.trim()?.split(":")
        if (parts != null && parts.size == 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) h to m else null
        } else null
    }
    var hour by remember(initial) { mutableStateOf(seed?.first ?: 9) }
    var minute by remember(initial) { mutableStateOf(seed?.second ?: 0) }
    // 分默认锁着：时没被动过之前是灰的、推不动；已经带着旧值进来时直接可用
    var minuteEnabled by remember(initial) { mutableStateOf(initial != null && initial.isNotBlank() && seed != null) }
    var inputTarget by remember { mutableStateOf(-1) }   // -1 无 · 0 时 · 1 分
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.paper,
        titleContentColor = p.ink,
        textContentColor = p.ink,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.ink) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeWheel(
                        p = p,
                        values = WHEEL_HOURS,
                        selected = hour,
                        enabled = true,
                        onSelect = {
                            hour = it
                            if (!minuteEnabled) minuteEnabled = true   // 推过一次时，分就解锁
                        },
                        onClickInput = { inputTarget = 0; inputText = two(hour) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    TimeWheel(
                        p = p,
                        values = WHEEL_MINUTES,
                        selected = minute,
                        enabled = minuteEnabled,
                        onSelect = { minute = it },
                        onClickInput = { inputTarget = 1; inputText = two(minute) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (minuteEnabled) t("上下推动滚轮；轻点滚轮可以打字输入", "Drag the wheels up or down; tap one to type a value")
                    else t("先推一下「时」，分的滚轮就会解锁", "Push the hour wheel first to unlock minutes"),
                    fontSize = 11.sp, color = p.sub, lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(two(hour) + ":" + two(minute)) }) {
                Text(t("确定", "OK"), color = p.fair, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("取消", "Cancel"), color = p.sub) }
        }
    )

    // 轻点滚轮后的小输入框：确定即生效，键盘输入没被删掉
    if (inputTarget >= 0) {
        val max = if (inputTarget == 0) 23 else 59
        val ok = inputText.toIntOrNull()?.let { it in 0..max } == true
        AlertDialog(
            onDismissRequest = { inputTarget = -1 },
            containerColor = p.paper,
            titleContentColor = p.ink,
            textContentColor = p.ink,
            title = { Text(if (inputTarget == 0) t("输入小时", "Type the hour") else t("输入分钟", "Type the minute"), fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { s -> inputText = s.filter { it.isDigit() }.take(2) },
                    singleLine = true,
                    label = { Text(if (inputTarget == 0) t("0–23", "0-23") else t("0–59", "0-59"), fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputText.isNotEmpty() && !ok,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    val v = inputText.toInt()
                    if (inputTarget == 0) {
                        hour = v
                        if (!minuteEnabled) minuteEnabled = true
                    } else {
                        minute = v
                    }
                    inputTarget = -1
                }) { Text(t("确定", "OK"), color = if (ok) p.fair else p.sub) }
            },
            dismissButton = { TextButton(onClick = { inputTarget = -1 }) { Text(t("取消", "Cancel")) } }
        )
    }
}

private fun two(v: Int): String = if (v < 10) "0$v" else v.toString()

/**
 * 滚轮：手指上下推、每过一格就更新值；松手后 180ms 吸附到最近一格。
 * 中间一格的数字同字号、加粗，外加一圈高亮框；上下相邻的字透明度递减，
 * 始终能看到相邻数字，不会滚出空白。
 */
@Composable
private fun TimeWheel(
    p: Palette,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onClickInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemH = 44.dp
    val boxH = itemH * 5
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val scope = rememberCoroutineScope()
    var pos by remember { mutableStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }

    // 外部改值（例如打字输入）时，滚轮跟着归位；自己拖动期间不回写，免得打架
    LaunchedEffect(selected) {
        if (!dragging && !settling) pos = selected.toFloat()
    }

    // 松手 / 取消都要回弹，抽成一份，两处共用
    fun settle() {
        dragging = false
        scope.launch {
            settling = true
            val from = pos
            val target = from.roundToInt().coerceIn(0, values.lastIndex)
            animate(0f, 1f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { t, _ ->
                pos = from + (target - from) * t
            }
            pos = target.toFloat()
            if (target != selected) onSelect(target)
            settling = false
        }
    }

    Box(
        modifier
            .height(boxH)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) p.paper else p.track)
            .border(1.dp, p.line, RoundedCornerShape(14.dp))
            .clipToBounds()
            .pointerInput(enabled, values.size) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        val next = (pos - delta / itemPx)
                            .coerceIn(-0.55f, values.lastIndex.toFloat() + 0.55f)
                        pos = next
                        val idx = next.roundToInt().coerceIn(0, values.lastIndex)
                        if (idx != selected) onSelect(idx)
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onClickInput() }
            }
    ) {
        // 中间选中格：同字号、加粗、外框高亮
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemH)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Transparent)
                .border(1.5.dp, if (enabled) p.fair else p.line, RoundedCornerShape(10.dp))
        )

        values.forEach { v ->
            val dist = abs(v - pos)
            if (dist <= 2.6f) {
                Text(
                    two(v),
                    fontSize = 20.sp,
                    fontWeight = if (dist < 0.5f) FontWeight.Bold else FontWeight.Normal,
                    color = if (enabled) {
                        p.ink.copy(alpha = (1f - dist * 0.26f).coerceIn(0.25f, 1f))
                    } else {
                        p.sub.copy(alpha = (0.45f - dist * 0.12f).coerceAtLeast(0.08f))
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(0, ((v - pos) * itemPx).roundToInt()) }
                )
            }
        }
    }
}
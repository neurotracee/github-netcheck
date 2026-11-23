package com.neurotracee.netcheck.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurotracee.netcheck.data.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
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
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("应用日志", "App logs"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Spacer(Modifier.weight(1f))
                Text(t("完成", "Done"), fontSize = 15.sp, color = p.fair,
                    modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                t("保留最近 365 天，一天一个文件；不上传、不出本机。", "Kept for 365 days, one file per day. Nothing is uploaded."),
                fontSize = 11.5.sp, color = p.sub
            )

            // 上次异常退出的提示：直接把用户引到这段日志
            if (pending != null) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.warnBg)
                        .border(1.dp, p.warnBorder, RoundedCornerShape(12.dp)).padding(12.dp)
                ) {
                    Text(t("上次运行异常退出", "Last run ended unexpectedly"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.warnText)
                    Spacer(Modifier.height(4.dp))
                    Text(pending!!, fontSize = 11.5.sp, color = p.warnText, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("本次启动的日志已包含崩溃堆栈，往下滑即可看到", "This session's log contains the stack trace — scroll down to see it"),
                            fontSize = 11.sp, color = p.warnText, modifier = Modifier.weight(1f))
                        Text(t("知道了", "Got it"), fontSize = 12.sp, color = p.warnText, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                LogStore.dismissCrash(); pending = null
                            }.padding(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(p, t("本次启动", "This launch"), tab == 0) { tab = 0 }
                TabChip(p, t("历史日志", "History"), tab == 1) { tab = 1; reload() }
            }

            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
                LogBox(p, session, Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(p, t("复制日志", "Copy")) {
                        clipboard.setText(AnnotatedString(session))
                        toast(tr(context, "本次启动的日志已复制", "This session's log is copied"))
                    }
                    ActionButton(p, t("存为 TXT", "Save TXT")) {
                        saveLauncher.launch(LogStore.exportFileName())
                    }
                    ActionButton(p, t("刷新", "Refresh"), soft = true) { session = LogStore.sessionText() }
                }
            } else {
                // 日期与时间范围
                RangeField(p, t("起始日期", "From"), fromText) { fromText = it }
                Spacer(Modifier.height(6.dp))
                RangeField(p, t("结束日期", "To"), toText) { toText = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeField(p, t("起始时间（可空）", "From time (optional)"), fromTime, Modifier.weight(1f)) { fromTime = it }
                    RangeField(p, t("结束时间（可空）", "To time (optional)"), toTime, Modifier.weight(1f)) { toTime = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(p, t("查询", "Search")) { reload() }
                    ActionButton(p, t("复制", "Copy"), soft = true) {
                        if (preview.isBlank()) toast(tr(context, "先查一下再复制", "Search first")) else {
                            clipboard.setText(AnnotatedString(preview)); toast(tr(context, "已复制", "Copied"))
                        }
                    }
                    ActionButton(p, t("存为 TXT", "Save TXT"), soft = true) {
                        if (preview.isBlank()) toast(tr(context, "先查一下再保存", "Search first")) else saveLauncher.launch(LogStore.exportFileName())
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(t("有日志的日期（点一下填进起始与结束）", "Days with logs (tap to fill the range)"),
                    fontSize = 11.sp, color = p.sub)
                Spacer(Modifier.height(4.dp))
                if (days.isEmpty()) {
                    Text(t("暂时没有历史日志", "No historical logs yet"), fontSize = 12.sp, color = p.sub)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.take(12).forEach { d ->
                            val s = fmt.format(d)
                            Text(
                                "$s　" + t("点我选这一天", "tap to select"),
                                fontSize = 12.sp, color = p.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(p.card)
                                    .border(1.dp, p.line, RoundedCornerShape(8.dp))
                                    .clickable { fromText = s; toText = s; fromTime = ""; toTime = ""; reload() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        if (days.size > 12) {
                            Text(t("只列出最近 12 天，其余按日期直接填", "Only the 12 newest days are listed — type a date for older ones"),
                                fontSize = 11.sp, color = p.sub)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                LogBox(p, preview.ifBlank { t("还没有查询结果。选好日期后点「查询」。", "No result yet — pick dates and tap Search.") },
                    Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp))

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card)
                        .border(1.dp, p.line, RoundedCornerShape(10.dp))
                        .clickable {
                            val r = parseRange() ?: return@clickable
                            stats = LogStore.rangeStats(r.first, r.second)
                            confirmText = "${fmt.format(r.first)} → ${fmt.format(r.second)}"
                            confirmStage = 1
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("清除这段日期的日志", "Delete logs in this range"), fontSize = 13.5.sp, color = p.down, modifier = Modifier.weight(1f))
                    Text("›", fontSize = 16.sp, color = p.sub)
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Spacer(Modifier.height(12.dp))
            FeedbackCard(p, versionName, context, clipboard, onToast)
        }
    }

    // 两段确认：先念清楚删哪几天，再问一次「确定吗」
    if (confirmStage == 1) {
        AlertDialog(
            onDismissRequest = { confirmStage = 0 },
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

@Composable
private fun TabChip(p: Palette, text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) p.ink else p.card)
            .border(1.dp, if (active) p.ink else p.line, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 12.5.sp, color = if (active) p.bg else p.ink, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun ActionButton(p: Palette, text: String, soft: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (soft) p.card else p.fair)
            .border(1.dp, if (soft) p.line else p.fair, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, fontSize = 12.5.sp, color = if (soft) p.ink else Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RangeField(p: Palette, label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        modifier = modifier
    )
}

@Composable
private fun LogBox(p: Palette, text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.card)
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

/** 反馈引导：模板 + 两个仓库的 Issue 入口。 */
@Composable
private fun FeedbackCard(
    p: Palette,
    versionName: String,
    context: android.content.Context,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    toast: (String) -> Unit
) {
    val device = LogStore.deviceLine.ifBlank { t("机型系统：在你的手机上自动获取", "Device info is collected on your phone") }
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(t("要提反馈？照这个写就行", "Reporting a bug? Copy this"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            t("一句话说清发生了什么，再写三步复现，最后补上「实际看到 / 预期结果」，日志作为 TXT 附上。版本号与机型系统已经自动填好。",
                "One sentence on what happened, three reproduction steps, then what you saw versus what you expected, with the log attached as TXT. The version and device line are pre-filled."),
            fontSize = 11.5.sp, color = p.sub, lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(10.dp)).padding(10.dp)
        ) {
            Text(template, fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace, color = p.ink)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(p, t("复制模板", "Copy template"), soft = true) {
                clipboard.setText(AnnotatedString(template))
                toast(tr(context, "模板已复制，记得把日志一起附上", "Template copied — attach the log as well"))
            }
            ActionButton(p, "GitHub Issue", soft = true) { openLogUrl(context, ISSUE_URL, toast) }
            ActionButton(p, "Gitee Issue", soft = true) { openLogUrl(context, MIRROR_ISSUE_URL, toast) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            t("两个仓库都开着 Issue，哪个打得开就提哪个；打不开 GitHub 时用 Gitee 就行。",
                "Both repos accept issues — use whichever opens. If GitHub will not load, use Gitee."),
            fontSize = 11.sp, color = p.sub
        )
    }
}

private fun openLogUrl(context: android.content.Context, url: String, toast: (String) -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(tr(context, "没有能打开浏览器的应用", "No app can open a browser")) }
}

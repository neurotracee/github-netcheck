package com.neurotracee.netcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurotracee.netcheck.data.HistoryEntry
import com.neurotracee.netcheck.data.HistoryItem
import com.neurotracee.netcheck.data.historyTimeText
import com.neurotracee.netcheck.model.Levels

/**
 * 历史记录：默认列出最近 100 次，点某一条进详情。
 * 刻意做成卡片列表而不是表格，避免又变回工程日志的样子。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onToast: (String) -> Unit
) {
    val p = LocalPalette.current
    val isEn = LocalLang.current == Lang.EN
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<HistoryEntry?>(null) }
    // 详情里默认看全部，0 = 全部，1 = 只看异常
    var detailTab by remember(selected?.startedAt) { mutableStateOf(0) }
    // 列表整体筛选：0 全部 / 1 正常 / 2 异常全部 / 3 严重 / 4 中等 / 5 轻微
    var listIdx by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    // t() 只能在 Composable 上下文调用，按钮回调里的文案先在这里取好
    val clearedToast = t("已清空历史记录", "History cleared")

    // 整体统计：次数口径，正常 / 异常（严重＋中等＋轻微）
    val normalCount = entries.count { entrySeverity(it) == 0 }
    val severeCount = entries.count { entrySeverity(it) == 1 }
    val midCount = entries.count { entrySeverity(it) == 2 }
    val mildCount = entries.count { entrySeverity(it) == 3 }
    val issueCount = severeCount + midCount + mildCount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.bg,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected == null) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t("历史记录", "History"),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.ink
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t("保留最近 ${entries.size} 次 · 最多 100 次", "${entries.size} kept · up to 100"),
                            fontSize = 11.5.sp,
                            color = p.sub
                        )
                    }
                    if (entries.isNotEmpty()) {
                        Text(
                            t("清空", "Clear"),
                            fontSize = 12.5.sp,
                            color = p.sub,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { confirmClear = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Text(
                        "←",
                        fontSize = 20.sp,
                        color = p.ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selected = null }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            historyTimeText(selected!!.startedAt, isEn),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = p.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (selected!!.interrupted) {
                                t(
                                    "中途停止 · 已测 ${selected!!.items.size}/${selected!!.total} 个点位",
                                    "Stopped early · ${selected!!.items.size}/${selected!!.total} tested"
                                )
                            } else {
                                t(
                                    "${selected!!.okCount}/${selected!!.total} 可连通 · 平均 ${selected!!.avgTcp}ms · 用时 ${selected!!.elapsedSec} 秒",
                                    "${selected!!.okCount}/${selected!!.total} reachable · avg ${selected!!.avgTcp}ms · ${selected!!.elapsedSec}s"
                                )
                            },
                            fontSize = 11.5.sp,
                            color = p.sub
                        )
                    }
                }
            }

            if (selected != null) {
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(p.card)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MiniTab(
                            p = p,
                            label = t("全部 ${selected!!.items.size}", "All ${selected!!.items.size}"),
                            active = detailTab == 0
                        ) { detailTab = 0 }
                        MiniTab(
                            p = p,
                            label = t(
                                "异常 ${selected!!.items.count { !it.ok }}",
                                "Issues ${selected!!.items.count { !it.ok }}"
                            ),
                            active = detailTab == 1
                        ) { detailTab = 1 }
                    }
                }
            }

            if (selected == null && entries.isNotEmpty()) {
                Spacer(Modifier.height(11.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(p.card)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MiniTab(p, t("全部 ${entries.size}", "All ${entries.size}"), listIdx == 0) { listIdx = 0 }
                        MiniTab(p, t("正常 $normalCount", "OK $normalCount"), listIdx == 1) { listIdx = 1 }
                        MiniTab(p, t("异常 $issueCount", "Issues $issueCount"), listIdx == 2) { listIdx = 2 }
                    }
                }
                if (listIdx >= 2) {
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(p.card)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            MiniTab(p, t("全部 $issueCount", "All $issueCount"), listIdx == 2) { listIdx = 2 }
                            MiniTab(p, t("严重 $severeCount", "Severe $severeCount"), listIdx == 3) { listIdx = 3 }
                            MiniTab(p, t("中等 $midCount", "Moderate $midCount"), listIdx == 4) { listIdx = 4 }
                            MiniTab(p, t("轻微 $mildCount", "Mild $mildCount"), listIdx == 5) { listIdx = 5 }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selected == null) {
                if (entries.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t("还没有记录，跑一次自检就会留在这里", "Nothing yet — run a check and it will show up here"),
                            fontSize = 13.sp,
                            color = p.sub,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val shown = when (listIdx) {
                        1 -> entries.filter { entrySeverity(it) == 0 }
                        2 -> entries.filter { entrySeverity(it) >= 1 }
                        3 -> entries.filter { entrySeverity(it) == 1 }
                        4 -> entries.filter { entrySeverity(it) == 2 }
                        5 -> entries.filter { entrySeverity(it) == 3 }
                        else -> entries
                    }
                    if (shown.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when (listIdx) {
                                    1 -> t("这 ${entries.size} 次里没有全部正常的记录", "None of these ${entries.size} runs was fully OK")
                                    2 -> t("这 ${entries.size} 次里没有异常的记录", "None of these ${entries.size} runs had issues")
                                    3 -> t("没有出现完全不通的记录", "No run had a fully blocked endpoint")
                                    4 -> t("没有出现部分阶段失败的记录", "No run had a partial-stage failure")
                                    else -> t("没有只是偏慢的记录", "No run was merely slow")
                                },
                                fontSize = 13.sp,
                                color = p.sub,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                // 左右滑动依次切：全部 → 正常 → 异常 → 严重 → 中等 → 轻微
                                .pointerInput(listIdx) {
                                    var acc = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { acc = 0f },
                                        onDragEnd = { acc = 0f },
                                        onDragCancel = { acc = 0f }
                                    ) { _, dragAmount ->
                                        acc += dragAmount
                                        if (acc <= -140.dp.toPx()) {
                                            listIdx = (listIdx + 1).coerceAtMost(5)
                                            acc = 0f
                                        } else if (acc >= 140.dp.toPx()) {
                                            listIdx = (listIdx - 1).coerceAtLeast(0)
                                            acc = 0f
                                        }
                                    }
                                },
                            contentPadding = PaddingValues(bottom = 26.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(shown, key = { it.startedAt }) { e ->
                                HistoryCard(p = p, e = e, isEn = isEn, onClick = { selected = e })
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        // 左右滑动也能在「全部 / 异常」之间翻，和点上面的分段等价
                        .pointerInput(selected?.startedAt) {
                            var acc = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { acc = 0f },
                                onDragEnd = { acc = 0f },
                                onDragCancel = { acc = 0f }
                            ) { _, dragAmount ->
                                acc += dragAmount
                                if (acc <= -140.dp.toPx()) {
                                    detailTab = 1
                                    acc = 0f
                                } else if (acc >= 140.dp.toPx()) {
                                    detailTab = 0
                                    acc = 0f
                                }
                            }
                        },
                    contentPadding = PaddingValues(bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val detailItems = if (detailTab == 0) {
                        selected!!.items
                    } else {
                        selected!!.items.filter { !it.ok }
                    }
                    if (detailItems.isEmpty()) {
                        item {
                            Text(
                                if (detailTab == 0) {
                                    t("这一次没有任何点位记录", "No endpoints recorded this time")
                                } else {
                                    t("这一次没有异常点位", "No issues this time")
                                },
                                fontSize = 13.sp,
                                color = p.sub,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                            )
                        }
                    }
                    items(detailItems, key = { it.name }) { item ->
                        HistoryItemCard(p = p, item = item)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(t("清空历史记录？", "Clear all history?"), fontSize = 16.sp, color = p.ink) },
            text = {
                Text(
                    t("只删本机这 ${entries.size} 条，已经导出的报告不受影响。", "Only these ${entries.size} entries on this device are removed. Exported reports are untouched."),
                    fontSize = 13.sp,
                    color = p.sub
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear(); onToast(clearedToast) }) {
                    Text(t("清空", "Clear"), color = p.down)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(t("取消", "Cancel"), color = p.sub) }
            },
            containerColor = p.card
        )
    }
}

@Composable
private fun MiniTab(p: Palette, label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) p.ink else p.sub,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) p.bg else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

/**
 * 一条记录归到哪一档（次数口径，取已测点位里最严重的那一档）：
 * 0 正常、1 严重（有完全不通的点）、2 中等（有半通的点）、3 轻微（都通但偏慢）。
 */
private fun entrySeverity(e: HistoryEntry): Int {
    val items = e.items
    if (items.any { !it.ok && (it.tcpMs <= 0L || it.level == Levels.DOWN) }) return 1
    if (items.any { !it.ok }) return 2
    if (items.any { it.ok && (it.level == Levels.SLOW || it.level == Levels.LIMIT) }) return 3
    return 0
}

@Composable
private fun HistoryCard(p: Palette, e: HistoryEntry, isEn: Boolean, onClick: () -> Unit) {
    val issues = e.total - e.okCount
    val chipColor = when {
        e.interrupted -> p.warnText
        issues > 0 -> p.down
        else -> p.good
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                historyTimeText(e.startedAt, isEn),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (e.interrupted) {
                    // 中途停止的那一条，如实说明测到第几个
                    t(
                        "中途停止 · 已测 ${e.items.size}/${e.total} 个点位",
                        "Stopped early · ${e.items.size}/${e.total} tested"
                    )
                } else {
                    t(
                        "${e.okCount}/${e.total} 可连通 · 平均 ${e.avgTcp}ms · ${e.elapsedSec} 秒",
                        "${e.okCount}/${e.total} reachable · avg ${e.avgTcp}ms · ${e.elapsedSec}s"
                    )
                },
                fontSize = 11.5.sp,
                color = p.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                e.interrupted -> t("未测完", "Incomplete")
                issues > 0 -> t("异常 $issues", "$issues issues")
                else -> t("全部正常", "All OK")
            },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = chipColor,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(chipColor.copy(alpha = 0.12f))
                .padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun HistoryItemCard(p: Palette, item: HistoryItem) {
    val levelColor = p.level(item.level)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                item.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = levelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(levelColor.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HistoryMetric(p, "DNS", if (item.dnsMs > 0) item.dnsMs.toString() else "—")
            HistoryDot(p)
            HistoryMetric(p, "TCP", if (item.tcpMs > 0) item.tcpMs.toString() else "—")
            HistoryDot(p)
            HistoryMetric(p, "TLS", if (item.tlsMs > 0) item.tlsMs.toString() else "—")
            HistoryDot(p)
            HistoryMetric(p, "HTTP", if (item.ttfbMs > 0) item.ttfbMs.toString() else "—")
            if (!item.ok && item.status > 0) {
                Spacer(Modifier.width(8.dp))
                Text("HTTP ${item.status}", fontSize = 11.sp, color = p.down)
            }
        }
    }
}

@Composable
private fun HistoryMetric(p: Palette, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = p.sub)
        Spacer(Modifier.width(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
    }
}

@Composable
private fun HistoryDot(p: Palette) {
    Box(
        Modifier
            .padding(horizontal = 9.dp)
            .width(3.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(p.line)
    )
}

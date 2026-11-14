package com.neurotracee.netcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

private const val PROJECT_URL = "https://github.com/neurotracee/netcheck"
private const val MIRROR_URL = "https://gitee.com/bio234/netcheck"

@Composable
fun NetCheckScreen(vm: MainViewModel = viewModel()) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Header(
                    p = p,
                    run = vm.run,
                    idleHint = "${Targets.all.size} 个目标 · 逐项测 DNS / TCP / TLS / HTTP 四段耗时"
                )
            }

            item { EnvPills(p, vm.env, vm.run?.rateInfo.orEmpty()) }

            item {
                when (vm.phase) {
                    Phase.Running -> RunningCard(
                        p = p,
                        current = vm.progressCurrent,
                        total = vm.progressTotal,
                        name = vm.currentName,
                        onCancel = { vm.cancel() }
                    )
                    Phase.Idle -> StartButtons(p, onRun = { vm.start(false) }, onQuick = { vm.start(true) })
                    Phase.Done -> ResultActions(
                        p = p,
                        onRun = { vm.start(false) },
                        onQuick = { vm.start(true) },
                        onExportHtml = {
                            val file = vm.exportHtml()
                            if (file != null) {
                                context.startActivity(
                                    vm.store().shareIntent(file, "text/html", "网络连通性自检报告")
                                )
                            }
                        },
                        onExportCsv = {
                            val file = vm.exportCsv()
                            if (file != null) {
                                context.startActivity(
                                    vm.store().shareIntent(file, "text/csv", "网络连通性自检数据")
                                )
                            }
                        }
                    )
                }
            }

            vm.message?.let { msg ->
                item { MessageBanner(p, msg) { vm.message = null } }
            }

            vm.run?.let { run ->
                item { SummaryGrid(p, run) }
            }

            items(vm.results, key = { it.name + it.host }) { r ->
                val index = vm.results.indexOf(r)
                ResultRow(
                    r = r,
                    expanded = expanded == index,
                    onClick = { expanded = if (expanded == index) null else index }
                )
            }

            item { Footer(p, onCopy = { clipboard.setText(AnnotatedString(it)) }) }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun Header(p: Palette, run: RunResult?, idleHint: String) {
    val meta = if (run != null) {
        "${ReportBuilder.timeStamp(run.startedAtMillis)}　·　${run.total} 个目标　·　用时 ${run.elapsedSec} 秒"
    } else {
        idleHint
    }
    Column(Modifier.padding(bottom = 2.dp)) {
        Text(
            "NET CHECK · 本机自检",
            fontSize = 10.5.sp,
            letterSpacing = 2.sp,
            color = p.sub,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text("网络连通性自检", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(meta, fontSize = 12.sp, color = p.sub)
        Spacer(Modifier.height(2.dp))
        Text("全部测试在本机完成，不上传任何数据", fontSize = 12.sp, color = p.sub)
    }
}

@Composable
private fun EnvPills(p: Palette, env: NetEnv, rateInfo: String) {
    val items = mutableListOf<Pair<String, Boolean>>()
    if (env.networkName.isNotEmpty()) items += ("当前网络 ${env.networkName}" to env.vpnActive)
    if (env.proxyEnabled) items += ("系统代理 ${env.proxyServer}" to true) else items += ("系统代理未开启" to false)
    if (env.vpnActive) items += ("VPN 已启用" to true)
    if (env.privateDns.isNotEmpty() && env.privateDns != "关闭") items += ("私有 DNS ${env.privateDns}" to true)
    val hostsLabel = if (env.hostsHits > 0) "hosts ${env.hostsHits} 条本地映射" else "hosts 无本地映射"
    items += (hostsLabel to (env.hostsHits > 0))
    if (env.dnsServers.isNotEmpty()) items += ("DNS ${env.dnsServers.joinToString(", ")}" to false)
    if (rateInfo.isNotEmpty()) items += (rateInfo to false)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (text, warn) -> Pill(p, text, warn) }
    }
}

@Composable
private fun Pill(p: Palette, text: String, warn: Boolean) {
    Text(
        text,
        fontSize = 11.sp,
        color = if (warn) p.warnText else p.sub,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (warn) p.warnBg else p.card)
            .border(1.dp, if (warn) p.warnBorder else p.line, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun StartButtons(p: Palette, onRun: () -> Unit, onQuick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRun,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.good, contentColor = Color.White)
        ) {
            Text("开始自检", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onQuick,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = p.sub),
            border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
        ) {
            Text("只测 GitHub 系（更快）", fontSize = 13.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRun,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.good, contentColor = Color.White)
            ) {
                Text("重新自检", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onQuick,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.sub),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
            ) {
                Text("只测 GitHub 系", fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onExportHtml,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
            ) {
                Text("导出报告", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.ink),
                border = androidx.compose.foundation.BorderStroke(1.dp, p.line)
            ) {
                Text("分享 CSV", fontSize = 13.sp)
            }
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
                if (total > 0) "正在测试 $current / $total" else "准备中",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) {
                Text("停止", fontSize = 12.sp, color = p.sub)
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(p, "可连通", "${run.okCount}", "/ ${run.total}", Modifier.weight(1f))
            SummaryCard(p, "平均建连延迟", "${run.avgTcp}", "ms", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(p, "最快", run.fastest?.tcpMs?.toString() ?: "-", "ms", Modifier.weight(1f))
            SummaryCard(p, "异常目标", "${run.failCount}", "项", Modifier.weight(1f))
        }
        Text(
            ReportBuilder.headline(run),
            fontSize = 11.5.sp,
            color = p.sub
        )
    }
}

@Composable
private fun SummaryCard(p: Palette, label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = p.sub)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Text(unit, fontSize = 11.sp, color = p.sub, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
        }
    }
}

@Composable
private fun ResultRow(r: TargetResult, expanded: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val levelColor = p.level(r.level)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .drawBehind {
                drawRect(color = levelColor, size = Size(4.dp.toPx(), size.height))
            }
            .border(1.dp, p.line, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.ink,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            if (r.hint.isNotEmpty()) {
                Text(
                    r.hint,
                    fontSize = 10.5.sp,
                    color = p.warnText,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(p.warnBg)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Text(
                r.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = levelColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(levelColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            r.host,
            fontSize = 11.sp,
            color = p.sub,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(levelColor)
            )
            Spacer(Modifier.width(5.dp))
            Text(r.ip, fontSize = 11.sp, color = p.sub)
            if (r.dohIp.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (r.dohIp == r.ip) "DoH 一致" else "DoH 真实解析 ${r.dohIp}",
                    fontSize = 10.5.sp,
                    color = if (r.dohIp == r.ip) p.good else p.warnText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth()) {
            MetricCell(p, "DNS", if (r.dnsOk) r.dnsMs.toString() else "—", r.dnsOk, Modifier.weight(1f))
            MetricCell(p, "TCP", if (r.tcpOk) r.tcpMs.toString() else "—", r.tcpOk, Modifier.weight(1f))
            MetricCell(p, "TLS", if (r.ok) r.tlsMs.toString() else "—", r.ok, Modifier.weight(1f))
            MetricCell(p, "HTTP", if (r.ok) r.ttfbMs.toString() else "—", r.ok, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
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
        Spacer(Modifier.height(7.dp))
        Text(r.desc, fontSize = 11.sp, color = p.sub)
        if (!r.ok) {
            Spacer(Modifier.height(3.dp))
            Text(r.error, fontSize = 11.sp, color = p.down)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.line)
            )
            Spacer(Modifier.height(8.dp))
            DetailLine(p, "评级依据", "${Verdict.text(r.level)}　·　${r.label}")
            DetailLine(p, "HTTP 状态", if (r.status > 0) r.status.toString() else "—")
            DetailLine(p, "本机解析", r.ip)
            DetailLine(p, "DoH 对照", r.dohIp.ifEmpty { "未取到" })
            DetailLine(p, "DNS 耗时", if (r.dnsOk) "${r.dnsMs} ms" else "解析失败")
            DetailLine(p, "TCP 建连", if (r.tcpOk) "${r.tcpMs} ms" else "未建立")
            DetailLine(p, "TLS 握手", if (r.ok) "${r.tlsMs} ms" else "未完成")
            DetailLine(p, "HTTP 首字节", if (r.ok) "${r.ttfbMs} ms" else "未收到")
            DetailLine(p, "用途说明", r.desc)
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasData) p.ink else p.sub.copy(alpha = 0.65f)
        )
        Text(
            label,
            fontSize = 9.5.sp,
            color = p.sub,
            letterSpacing = 0.4.sp
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

@Composable
private fun Footer(p: Palette, onCopy: (String) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "测试方法：DNS 解析 → TCP 443 建连 → TLS 握手 → 发送 HTTP 请求测首字节，四段分别计时。",
            fontSize = 11.sp,
            color = p.sub,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "评级按 TCP 建连延迟：≤120ms 优秀，≤400ms 良好，≤1000ms 一般，>1000ms 较慢，失败为不通。",
            fontSize = 11.sp,
            color = p.sub,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "DoH 对照用阿里公共 DNS 的加密查询，用来发现 hosts 改写或 DNS 污染。",
            fontSize = 11.sp,
            color = p.sub,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Spacer(Modifier.height(10.dp))
        Text("版本 ${BuildConfig.VERSION_NAME}　·　Apache License 2.0", fontSize = 11.sp, color = p.sub)
        Spacer(Modifier.height(3.dp))
        Text(
            "原项目地址：$PROJECT_URL",
            fontSize = 11.sp,
            color = p.fair,
            modifier = Modifier.padding(vertical = 2.dp).clickable { onCopy(PROJECT_URL) }
        )
        Text(
            "镜像（Gitee）：$MIRROR_URL",
            fontSize = 11.sp,
            color = p.fair,
            modifier = Modifier.padding(vertical = 2.dp).clickable { onCopy(MIRROR_URL) }
        )
        Spacer(Modifier.height(3.dp))
        Text("点地址可复制到剪贴板", fontSize = 10.5.sp, color = p.sub)
    }
}

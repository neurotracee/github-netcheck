package com.neurotracee.netcheck.net

import com.neurotracee.netcheck.model.HttpsOutcome
import com.neurotracee.netcheck.model.Target
import com.neurotracee.netcheck.model.TargetResult
import com.neurotracee.netcheck.model.Verdict

/**
 * 串行跑完所有点位。
 *
 * 为什么串行：并发会互相抢带宽与 NAT 会话，建连延迟就不准了。桌面端也是串行。
 * 14 个点位、每个最多 3 秒，最坏情况接近 40 秒，正常网络 15～25 秒。
 *
 * 这里刻意写成普通阻塞函数 + 普通回调，不套 suspend lambda：
 * Kotlin 1.9.24 的 JVM 后端在「suspend 回调 + 内联高阶函数 + withContext」组合下会
 * 报 Backend Internal error（IR lowering 阶段 fixStack 分析空指针），
 * 换成普通循环后彻底绕开，行为完全一致。
 */
class Checker(private val timeoutMs: Int = 3000) {

    fun run(
        targets: List<Target>,
        onStart: (index: Int, total: Int, target: Target) -> Unit,
        onResult: (TargetResult) -> Unit
    ): String {
        var rateInfo = ""
        for (i in targets.indices) {
            val target = targets[i]
            onStart(i + 1, targets.size, target)

            val dns = Probe.resolve(target.host, timeoutMs)
            var https = HttpsOutcome(ok = false, tcpOk = false)
            var doh = ""

            if (dns.ok) {
                https = Probe.https(
                    host = target.host,
                    path = target.path,
                    connectIp = dns.ip,
                    timeoutMs = timeoutMs
                )
                doh = Probe.doh(target.host)
                if (target.host == "api.github.com" && https.ok && https.body.isNotEmpty()) {
                    val parsed = parseRateInfo(https.body)
                    if (parsed != null) rateInfo = parsed
                }
            } else {
                https = HttpsOutcome(
                    ok = false,
                    tcpOk = false,
                    error = "DNS 解析失败，未继续连接"
                )
            }

            val verdict = Verdict.of(https)
            val hint = when {
                dns.ip == "127.0.0.1" || dns.ip == "::1" -> "本地反代"
                https.ok && https.status >= 500 -> "服务端异常"
                else -> ""
            }

            onResult(
                TargetResult(
                    name = target.name,
                    host = target.host,
                    desc = target.desc,
                    ip = dns.ip.ifEmpty { "—" },
                    dohIp = doh,
                    dnsMs = dns.ms,
                    tcpMs = https.tcpMs,
                    tlsMs = https.tlsMs,
                    ttfbMs = https.ttfbMs,
                    status = https.status,
                    ok = https.ok,
                    tcpOk = https.tcpOk,
                    dnsOk = dns.ok,
                    level = verdict.first,
                    label = verdict.second,
                    error = https.error,
                    hint = hint
                )
            )
        }
        return rateInfo
    }

    /** 从 api.github.com/rate_limit 的响应里读出配额，桌面端也是这么显示的。 */
    private fun parseRateInfo(body: String): String? {
        val remaining = Regex("\"remaining\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)
        val limit = Regex("\"limit\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)
        return if (remaining != null && limit != null) "API 配额 $remaining / $limit" else null
    }
}

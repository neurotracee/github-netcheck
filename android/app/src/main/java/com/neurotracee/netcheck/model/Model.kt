package com.neurotracee.netcheck.model

/** 评级用的色阶 key，与 HTML 报告里的 CSS 类名一一对应（g/g2/y/o/r）。 */
object Levels {
    const val GOOD = "g"      // 优秀：TCP ≤ 120ms
    const val FAIR = "g2"     // 良好：≤ 400ms
    const val SLOW = "y"      // 一般：≤ 1000ms
    const val LIMIT = "o"     // 较慢 / 受限
    const val DOWN = "r"      // 不通

    const val NO_DATA = "n"
}

/** 一个测试点位。和 Windows 端 netcheck.ps1 的 $Targets 完全对应。 */
data class Target(
    val name: String,
    val host: String,
    val path: String = "/",
    val desc: String,
    val group: Group = Group.GITHUB
) {
    enum class Group(val label: String) {
        BASE("基准"),
        GITHUB("GitHub"),
        AI("AI / 模型"),
        CONTAINER("容器镜像")
    }
}

data class DnsOutcome(
    val ok: Boolean,
    val ms: Long,
    val ip: String,
    val error: String = ""
)

data class HttpsOutcome(
    val ok: Boolean,
    val tcpOk: Boolean,
    val tcpMs: Long = 0,
    val tlsMs: Long = 0,
    val ttfbMs: Long = 0,
    val totalMs: Long = 0,
    val status: Int = 0,
    val body: String = "",
    val error: String = ""
)

/** 一个点位的最终结果。字段与 Windows 端 CSV 的列一一对应。 */
data class TargetResult(
    val name: String,
    val host: String,
    val desc: String,
    val ip: String = "—",
    val dohIp: String = "",
    val dnsMs: Long = 0,
    val tcpMs: Long = 0,
    val tlsMs: Long = 0,
    val ttfbMs: Long = 0,
    val status: Int = 0,
    val ok: Boolean = false,
    val tcpOk: Boolean = false,
    val dnsOk: Boolean = false,
    val level: String = Levels.DOWN,
    val label: String = "不通",
    val error: String = "",
    val hint: String = ""
) {
    val barPercent: Int
        get() = when {
            !tcpOk -> 100
            else -> ((tcpMs / 500.0) * 100).toInt().coerceIn(4, 100)
        }
}

data class NetEnv(
    val proxyEnabled: Boolean = false,
    val proxyServer: String = "",
    val vpnActive: Boolean = false,
    val privateDns: String = "",
    val dnsServers: List<String> = emptyList(),
    val hostsHits: Int = 0,
    val networkName: String = ""
)

data class RunResult(
    val results: List<TargetResult>,
    val startedAtMillis: Long,
    val elapsedSec: Int,
    val env: NetEnv,
    val rateInfo: String = "",
    val quick: Boolean = false
) {
    val okCount: Int get() = results.count { it.ok }
    val total: Int get() = results.size
    val avgTcp: Int
        get() {
            val list = results.filter { it.ok }.map { it.tcpMs }
            return if (list.isEmpty()) 0 else (list.average().toInt())
        }
    val fastest: TargetResult? get() = results.filter { it.ok }.minByOrNull { it.tcpMs }
    val failCount: Int get() = results.count { !it.ok }
    val failList: List<TargetResult> get() = results.filter { !it.ok }
}

/** 评级：与 Windows 端 Get-Verdict 的判定顺序和阈值完全一致。 */
object Verdict {
    fun of(https: HttpsOutcome): Pair<String, String> = when {
        !https.tcpOk -> Levels.DOWN to "不通"
        !https.ok -> Levels.LIMIT to "受限"
        https.tcpMs <= 120 -> Levels.GOOD to "优秀"
        https.tcpMs <= 400 -> Levels.FAIR to "良好"
        https.tcpMs <= 1000 -> Levels.SLOW to "一般"
        else -> Levels.LIMIT to "较慢"
    }

    fun text(level: String): String = when (level) {
        Levels.GOOD -> "很快"
        Levels.FAIR -> "正常"
        Levels.SLOW -> "偏慢"
        Levels.LIMIT -> "很慢"
        else -> "连不上"
    }
}

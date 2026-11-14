package com.neurotracee.netcheck

import com.neurotracee.netcheck.data.ReportBuilder
import com.neurotracee.netcheck.model.HttpsOutcome
import com.neurotracee.netcheck.model.Levels
import com.neurotracee.netcheck.model.NetEnv
import com.neurotracee.netcheck.model.RunResult
import com.neurotracee.netcheck.model.Target
import com.neurotracee.netcheck.model.TargetResult
import com.neurotracee.netcheck.model.Verdict
import com.neurotracee.netcheck.net.Probe
import com.neurotracee.netcheck.net.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class VerdictTest {

    private fun https(tcpOk: Boolean, ok: Boolean, tcpMs: Long) =
        HttpsOutcome(ok = ok, tcpOk = tcpOk, tcpMs = tcpMs)

    @Test
    fun `tcp 失败一律判不通`() {
        assertEquals(Levels.DOWN to "不通", Verdict.of(https(tcpOk = false, ok = false, tcpMs = 0)))
        assertEquals(Levels.DOWN to "不通", Verdict.of(https(tcpOk = false, ok = true, tcpMs = 30)))
    }

    @Test
    fun `tcp 通但握手失败判受限`() {
        assertEquals(Levels.LIMIT to "受限", Verdict.of(https(tcpOk = true, ok = false, tcpMs = 80)))
    }

    @Test
    fun `阈值边界与桌面端一致`() {
        assertEquals(Levels.GOOD to "优秀", Verdict.of(https(true, true, 120)))
        assertEquals(Levels.FAIR to "良好", Verdict.of(https(true, true, 121)))
        assertEquals(Levels.FAIR to "良好", Verdict.of(https(true, true, 400)))
        assertEquals(Levels.SLOW to "一般", Verdict.of(https(true, true, 401)))
        assertEquals(Levels.SLOW to "一般", Verdict.of(https(true, true, 1000)))
        assertEquals(Levels.LIMIT to "较慢", Verdict.of(https(true, true, 1001)))
    }

    @Test
    fun `进度条百分比范围`() {
        val slow = TargetResult(name = "x", host = "x", desc = "", tcpOk = true, tcpMs = 2000)
        assertEquals(100, slow.barPercent)
        val fast = TargetResult(name = "x", host = "x", desc = "", tcpOk = true, tcpMs = 1)
        assertEquals(4, fast.barPercent)
        val dead = TargetResult(name = "x", host = "x", desc = "", tcpOk = false)
        assertEquals(100, dead.barPercent)
    }
}

class TargetsTest {

    @Test
    fun `点位数与桌面端脚本一致`() {
        assertEquals(14, Targets.all.size)
        assertEquals(11, Targets.quickSubset().size)
    }

    @Test
    fun `快捷模式只留基准站与 github 系`() {
        assertTrue(Targets.quickSubset().all { it.host.contains("github") || it.host == "www.baidu.com" })
        assertTrue(Targets.quickSubset().none { it.host.contains("huggingface") })
    }

    @Test
    fun `必测点位存在且域名正确`() {
        val hosts = Targets.all.map { it.host }
        listOf(
            "www.baidu.com",
            "api.github.com",
            "raw.githubusercontent.com",
            "github.com",
            "codeload.github.com",
            "github.githubassets.com",
            "avatars.githubusercontent.com",
            "gist.githubusercontent.com",
            "objects.githubusercontent.com",
            "github.io",
            "huggingface.co",
            "registry-1.docker.io",
            "hub.docker.com"
        ).forEach { assertEquals(true, hosts.contains(it)) }
    }

    @Test
    fun `API 接口点位带上 rate_limit 路径`() {
        val api = Targets.all.first { it.host == "api.github.com" }
        assertEquals("/rate_limit", api.path)
    }

    @Test
    fun `每个点位都有说明与分组`() {
        assertTrue(Targets.all.all { it.desc.isNotBlank() })
        assertTrue(Targets.all.all { it.group in Target.Group.values() })
    }
}

class ReportBuilderTest {

    private fun result(
        name: String = "GitHub 主站",
        host: String = "github.com",
        ok: Boolean = true,
        level: String = Levels.GOOD,
        label: String = "优秀",
        error: String = ""
    ) = TargetResult(
        name = name,
        host = host,
        desc = "网页 / 仓库首页",
        ip = "20.205.243.166",
        dohIp = "20.205.243.166",
        dnsMs = 12,
        tcpMs = 80,
        tlsMs = 40,
        ttfbMs = 120,
        status = 200,
        ok = ok,
        tcpOk = ok,
        dnsOk = true,
        level = level,
        label = label,
        error = error
    )

    private fun run(vararg results: TargetResult) = RunResult(
        results = results.toList(),
        startedAtMillis = 1_700_000_000_000,
        elapsedSec = 23,
        env = NetEnv(networkName = "Wi-Fi", dnsServers = listOf("192.168.1.1"), hostsHits = 0),
        rateInfo = "API 配额 42 / 60"
    )

    @Test
    fun `html 里带汇总数字与逐条区块`() {
        val html = ReportBuilder.html(run(result()))
        assertTrue(html.contains("网络连通性报告"))
        assertTrue(html.contains("1 个目标"))
        assertTrue(html.contains("class=\"row lv-g\""))
        assertTrue(html.contains("DoH 一致"))
        assertTrue(html.contains("API 配额 42 / 60"))
        assertTrue(html.contains("Android 客户端"))
        assertTrue(html.contains("原项目"))  // 只校验页脚文案存在性（模板里没有时这条会失败，见下）
    }

    @Test
    fun `失败项会写出错误与建议`() {
        val html = ReportBuilder.html(
            run(result(ok = false, level = Levels.DOWN, label = "不通", error = "连接被重置（疑似被阻断）"))
        )
        assertTrue(html.contains("连接被重置"))
        assertTrue(html.contains("诊断与建议"))
    }

    @Test
    fun `全部连通时给一句好消息`() {
        val html = ReportBuilder.html(run(result()))
        assertTrue(html.contains("全部目标均可连通"))
    }

    @Test
    fun `点位名里的尖括号会被转义`() {
        val html = ReportBuilder.html(run(result(name = "<script>x</script>")))
        assertTrue(!html.contains("<script>x</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `csv 表头与桌面端列序一致`() {
        val csv = ReportBuilder.csv(run(result()))
        val header = csv.lineSequence().first()
        assertEquals("Name,Host,Ip,DohIp,DnsMs,TcpMs,TlsMs,TtfbMs,Status,Ok,Label", header)
        assertTrue(csv.contains("True"))
    }

    @Test
    fun `csv 里的逗号会被加引号`() {
        val csv = ReportBuilder.csv(run(result(name = "a,b")))
        assertTrue(csv.contains("\"a,b\""))
    }
}

class FriendlyErrorTest {

    @Test
    fun `域名解析失败`() {
        assertEquals("域名解析失败", Probe.friendly(UnknownHostException("x"), false))
    }

    @Test
    fun `未建连时超时措辞`() {
        assertEquals("超时（对方无响应）", Probe.friendly(SocketTimeoutException("connect timed out"), false))
    }

    @Test
    fun `已建连后超时说明链路被掐断`() {
        assertEquals("读取响应超时（链路被掐断）", Probe.friendly(SocketTimeoutException("read timed out"), true))
    }

    @Test
    fun `对端关闭连接判为被阻断`() {
        assertEquals(
            "连接被重置（疑似被阻断）",
            Probe.friendly(javax.net.ssl.SSLException("SSLException: Connection closed by peer"), true)
        )
    }
}

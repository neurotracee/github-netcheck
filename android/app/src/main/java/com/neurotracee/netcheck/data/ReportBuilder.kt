package com.neurotracee.netcheck.data

import com.neurotracee.netcheck.model.Levels
import com.neurotracee.netcheck.model.RunResult
import com.neurotracee.netcheck.model.TargetResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报告生成。HTML 模板与 Windows 端 netcheck.ps1 里那份**逐字一致**（同一套 CSS、同一套区块），
 * 这样电脑上跑出来的报告和手机上跑出来的报告可以直接对着看，不用重新适应一套排版。
 * 只在顶部环境条里多出安卓端才有的信息（VPN / 私有 DNS）。
 */
object ReportBuilder {

    fun timeStamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))

    fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date(millis))

    // ------------------------------------------------------------------ HTML

    fun html(run: RunResult, version: String = "1.0.0.0"): String {
        val rows = run.results.joinToString("\n") { row(it) }
        val pills = pills(run)
        val advice = advice(run)

        return TEMPLATE
            .replace("__DATE__", timeStamp(run.startedAtMillis))
            .replace("__COUNT__", run.total.toString())
            .replace("__ELAPSED__", run.elapsedSec.toString())
            .replace("__ENV__", pills)
            .replace("__OKCOUNT__", run.okCount.toString())
            .replace("__AVG__", run.avgTcp.toString())
            .replace("__FAST__", run.fastest?.tcpMs?.toString() ?: "-")
            .replace("__SLOW__", run.failCount.toString())
            .replace("__ROWS__", rows)
            .replace("__ADVICE__", advice)
            .replace("__VERSION__", version)
    }

    private fun row(r: TargetResult): String {
        val ipText = r.ip.ifEmpty { "—" }
        val dohTag = when {
            r.dohIp.isNotEmpty() && r.dohIp != r.ip ->
                "<span class=\"doh\">DoH 真实解析 " + esc(r.dohIp) + "</span>"
            r.dohIp.isNotEmpty() -> "<span class=\"doh ok\">DoH 一致</span>"
            else -> ""
        }
        val hintTag = if (r.hint.isNotEmpty()) "<span class=\"chip\">" + esc(r.hint) + "</span>" else ""
        val errLine = if (!r.ok) "<div class=\"err\">" + esc(r.error) + "</div>" else ""

        val dnsTxt = if (r.dnsOk) r.dnsMs.toString() else "—"
        val tcpTxt = if (r.tcpOk) r.tcpMs.toString() else "—"
        val tlsTxt = if (r.ok) r.tlsMs.toString() else "—"
        val httpTxt = if (r.ok) r.ttfbMs.toString() else "—"

        return """
<div class="row lv-${r.level}">
  <div class="info">
    <div class="nm">${esc(r.name)}$hintTag</div>
    <div class="hst">${esc(r.host)}</div>
    <div class="ipline"><span class="ipdot"></span>$ipText$dohTag</div>
  </div>
  <div class="metrics">
    <div class="m"><b>$dnsTxt</b><i>DNS</i></div>
    <div class="m"><b>$tcpTxt</b><i>TCP</i></div>
    <div class="m"><b>$tlsTxt</b><i>TLS</i></div>
    <div class="m"><b>$httpTxt</b><i>HTTP</i></div>
  </div>
  <div class="verdict">
    <span class="tag">${esc(r.label)}</span>
    <div class="bar"><i style="width:${r.barPercent}%;"></i></div>
    <div class="small">${esc(r.desc)}</div>
    $errLine
  </div>
</div>
""".trimIndent()
    }

    private fun pills(run: RunResult): String {
        val env = run.env
        val sb = StringBuilder()
        val mode = if (env.networkName.isNotEmpty()) env.networkName else "直连"
        sb.append("<span class=\"pill\">当前网络 " + esc(mode) + "</span>")
        if (env.proxyEnabled) {
            sb.append("<span class=\"pill warn\">系统代理开启 " + esc(env.proxyServer) + "</span>")
        } else {
            sb.append("<span class=\"pill\">系统代理未开启</span>")
        }
        if (env.vpnActive) sb.append("<span class=\"pill warn\">VPN 已启用</span>")
        if (env.privateDns.isNotEmpty() && env.privateDns != "关闭") {
            sb.append("<span class=\"pill warn\">私有 DNS " + esc(env.privateDns) + "</span>")
        }
        if (env.hostsHits > 0) {
            sb.append("<span class=\"pill warn\">hosts 有 " + env.hostsHits + " 条本地映射</span>")
        } else {
            sb.append("<span class=\"pill\">hosts 无本地映射</span>")
        }
        if (env.dnsServers.isNotEmpty()) {
            sb.append("<span class=\"pill\">DNS " + esc(env.dnsServers.joinToString(", ")) + "</span>")
        }
        if (run.rateInfo.isNotEmpty()) sb.append("<span class=\"pill\">" + esc(run.rateInfo) + "</span>")
        sb.append("<span class=\"pill\">Android 客户端</span>")
        return sb.toString()
    }

    private fun advice(run: RunResult): String {
        if (run.failCount == 0) {
            return "<div class=\"adv-item good\"><b>全部目标均可连通。</b>当前网络对 GitHub 系和 Hugging Face、Docker 的访问正常，不需要额外加速。</div>"
        }
        val sb = StringBuilder()
        for (r in run.failList) {
            var msg = "该站点当前无法建立连接。"
            when {
                r.host.contains("huggingface") -> msg = "Hugging Face 国内直连经常超时，开启加速或改用镜像站可改善。"
                r.host.contains("docker") -> msg = "Docker 官方仓库国内不稳定，可在 Docker 配置里改用国内镜像加速地址。"
                r.host.contains("github") -> msg = "GitHub 相关域名不稳定是常见现象，开启本地反代或代理后重测对比即可判断是否生效。"
            }
            msg = when {
                r.ip == "127.0.0.1" -> "域名被 hosts 指向本机 127.0.0.1，说明加速工具的本地反代没在正常转发，或反代已退出但 hosts 没清理。"
                r.tcpOk && !r.ok -> "端口能连上但 TLS 握手失败，常见原因是 SNI 被干扰或证书被中间设备替换，开启本地反代后通常会恢复。"
                else -> msg
            }
            sb.append("<div class=\"adv-item\"><b>" + esc(r.name) + "</b>：" + esc(msg) + "</div>")
        }
        return sb.toString()
    }

    private fun esc(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    // ------------------------------------------------------------------- CSV

    /** 列顺序与桌面端一致：Name,Host,Ip,DohIp,DnsMs,TcpMs,TlsMs,TtfbMs,Status,Ok,Label */
    fun csv(run: RunResult): String {
        val sb = StringBuilder()
        sb.append("Name,Host,Ip,DohIp,DnsMs,TcpMs,TlsMs,TtfbMs,Status,Ok,Label\r\n")
        run.results.forEach { r ->
            sb.append(csvCell(r.name)).append(',')
                .append(csvCell(r.host)).append(',')
                .append(csvCell(r.ip)).append(',')
                .append(csvCell(r.dohIp)).append(',')
                .append(r.dnsMs).append(',')
                .append(r.tcpMs).append(',')
                .append(r.tlsMs).append(',')
                .append(r.ttfbMs).append(',')
                .append(r.status).append(',')
                .append(if (r.ok) "True" else "False").append(',')
                .append(csvCell(r.label)).append("\r\n")
        }
        return sb.toString()
    }

    private fun csvCell(v: String): String {
        val needsQuote = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /** 汇总用的短文本，通知 / 分享标题用。 */
    fun headline(run: RunResult): String =
        "可连通 ${run.okCount} / ${run.total}　·　平均建连 ${run.avgTcp} ms　·　用时 ${run.elapsedSec} 秒"

    private val TEMPLATE = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>网络连通性自检报告</title>
<style>
:root{
  --bg:#f4f6f8; --card:#ffffff; --ink:#1a2027; --sub:#6b7785; --line:#e5e9ee;
  --g:#12a150; --g2:#3b82c4; --y:#c8870c; --o:#d9601a; --r:#d0342c; --n:#96a2ae;
}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--ink);font:15px/1.6 system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}
.wrap{max-width:1060px;margin:0 auto;padding:36px 22px 60px}
header{margin-bottom:22px}
.kicker{font-size:12px;letter-spacing:.22em;color:var(--sub);text-transform:uppercase}
h1{font-size:26px;font-weight:650;letter-spacing:-.2px;margin:8px 0 6px}
.meta{font-size:13px;color:var(--sub)}
.pills{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
.pill{font-size:12px;padding:4px 10px;border-radius:999px;background:#fff;border:1px solid var(--line);color:var(--sub)}
.pill.warn{background:#fff8e8;border-color:#f0dfb8;color:#9a6a08}
.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:22px 0 26px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px}
.card .lb{font-size:12px;color:var(--sub)}
.card .v{font-size:26px;font-weight:650;margin-top:2px;letter-spacing:-.5px}
.card .v small{font-size:13px;font-weight:400;color:var(--sub);margin-left:3px}
.list{display:flex;flex-direction:column;gap:10px}
.row{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px 18px;display:grid;grid-template-columns:1.35fr 1.15fr .85fr;gap:10px;align-items:center;border-left:4px solid var(--n)}
.row.lv-g{border-left-color:var(--g)} .row.lv-g2{border-left-color:var(--g2)}
.row.lv-y{border-left-color:var(--y)} .row.lv-o{border-left-color:var(--o)}
.row.lv-r{border-left-color:var(--r)}
.nm{font-weight:600;font-size:14.5px;display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.hst{font-size:12px;color:var(--sub);font-family:ui-monospace,Consolas,monospace}
.ipline{font-size:12px;color:var(--sub);margin-top:3px;display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.ipdot{width:6px;height:6px;border-radius:50%;background:var(--n);display:inline-block}
.row.lv-g .ipdot,.row.lv-g2 .ipdot{background:var(--g)}
.row.lv-r .ipdot{background:var(--r)}
.doh{font-size:11px;color:#a06a05}
.doh.ok{color:var(--g)}
.chip{font-size:11px;font-weight:500;padding:1px 6px;border-radius:5px;background:#fff3e0;color:#a15c0a}
.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;width:100%;max-width:400px;justify-self:center}
.m{text-align:center}
.m b{display:block;font-size:16px;font-weight:600;letter-spacing:-.3px}
.m i{display:block;font-style:normal;font-size:10.5px;color:var(--sub);letter-spacing:.05em}
.row.lv-r .m b{color:var(--n)}
.verdict{text-align:right}
.tag{display:inline-block;font-size:12px;padding:2px 9px;border-radius:6px;font-weight:600}
.row.lv-g .tag{background:#e8f7ee;color:#0c7a3c}
.row.lv-g2 .tag{background:#eaf2fb;color:#2a6a9e}
.row.lv-y .tag{background:#fdf4e3;color:#8a6107}
.row.lv-o .tag{background:#fdeee5;color:#a2480f}
.row.lv-r .tag{background:#fdecea;color:#a92a23}
.bar{height:4px;background:#eef1f4;border-radius:3px;margin-top:8px;overflow:hidden}
.bar i{display:block;height:100%;border-radius:3px;background:var(--g)}
.row.lv-g2 .bar i{background:var(--g2)} .row.lv-y .bar i{background:var(--y)}
.row.lv-o .bar i{background:var(--o)} .row.lv-r .bar i{background:var(--r);opacity:.6}
.small{font-size:11.5px;color:var(--sub);margin-top:6px}
.err{font-size:11.5px;color:#a92a23;margin-top:4px;text-align:right}
.sec{margin-top:28px}
h2{font-size:16px;font-weight:600;margin-bottom:10px}
.adv{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:6px 18px}
.adv-item{padding:10px 0;border-bottom:1px dashed var(--line);font-size:13.5px}
.adv-item:last-child{border-bottom:none}
.adv-item.good{color:#0c7a3c}
.adv-item b{font-weight:600}
.foot{margin-top:26px;font-size:12px;color:var(--sub);line-height:1.9}
.foot code{background:#eceff3;padding:1px 5px;border-radius:4px;font-size:11.5px}
@media (max-width:760px){
  .wrap{padding:24px 14px 44px}
  h1{font-size:20px}
  .cards{grid-template-columns:repeat(2,1fr)}
  .card .v{font-size:22px}
  .row{grid-template-columns:1fr;gap:12px;padding:14px}
  .verdict{text-align:left}
  .bar{margin-top:10px}
  .err{text-align:left}
}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="kicker">Net Check · 本机自检</div>
    <h1>网络连通性报告</h1>
    <div class="meta">__DATE__ 　·　 __COUNT__ 个目标　·　 用时 __ELAPSED__ 秒　·　 全部测试在本机完成，不上传任何数据</div>
    <div class="pills">__ENV__</div>
  </header>

  <div class="cards">
    <div class="card"><div class="lb">可连通</div><div class="v">__OKCOUNT__<small>/ __COUNT__</small></div></div>
    <div class="card"><div class="lb">平均建连延迟</div><div class="v">__AVG__<small>ms</small></div></div>
    <div class="card"><div class="lb">最快</div><div class="v">__FAST__<small>ms</small></div></div>
    <div class="card"><div class="lb">异常目标</div><div class="v">__SLOW__<small>项</small></div></div>
  </div>

  <div class="list">
__ROWS__
  </div>

  <div class="sec">
    <h2>诊断与建议</h2>
    <div class="adv">
__ADVICE__
    </div>
  </div>

  <div class="foot">
    测试方法：DNS 解析 → TCP 443 建连 → TLS 握手 → 发送 HTTP 请求测首字节时间，四段分别计时。<br>
    评级按 TCP 建连延迟：≤120ms 优秀，≤400ms 良好，≤1000ms 一般，&gt;1000ms 较慢，失败为不通。<br>
    <code>DoH 真实解析</code> 用阿里公共 DNS 加密查询对照，用来发现 hosts 改写或 DNS 污染。<br>
    报告中若某域名 IP 显示为 127.0.0.1，说明它被 hosts 指向本机，实际流量走的是本地反代而不是直连。<br>
    <br>
    版本 __VERSION__　·　Apache License 2.0　·　测试全程在本机完成，不上传任何数据<br>
    原项目地址：https://github.com/neurotracee/netcheck　（镜像：https://gitee.com/bio234/netcheck）
  </div>
</div>
</body>
</html>
"""
}

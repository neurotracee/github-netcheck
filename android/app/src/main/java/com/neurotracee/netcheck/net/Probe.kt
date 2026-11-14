package com.neurotracee.netcheck.net

import com.neurotracee.netcheck.model.DnsOutcome
import com.neurotracee.netcheck.model.HttpsOutcome
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 纯 Java Socket 实现的探测内核，与 Windows 端 netcheck.ps1 的 Test-Dns / Invoke-Https / Get-DohIp 同构：
 *
 *   DNS 解析 → TCP 建连 → TLS 握手 → 发送请求测首字节，四段分别计时。
 *
 * 这里刻意不使用 OkHttp / HttpURLConnection：
 *  1) 它们会把 DNS、建连、握手混在一起，分不出卡在哪一步；
 *  2) 它们会走系统代理，测不到真实直连状况；
 *  3) 零第三方依赖，APK 更小，构建也不需要联网拉库。
 */
object Probe {

    private const val MAX_BODY_CHARS = 8192

    /** 为了测纯连通性，证书校验被忽略（与桌面端一致，已在文档里写明这条限制）。 */
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
    }

    // ---------------------------------------------------------------- DNS

    /**
     * 解析耗时。InetAddress 的解析本身不可中断，所以放到独立线程里 join 一个上限，
     * 超时就当作失败——和桌面端 GetHostAddressesAsync + Wait 的做法一致。
     */
    fun resolve(host: String, timeoutMs: Int): DnsOutcome {
        val holder = AtomicReference<Array<InetAddress>?>()
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                holder.set(InetAddress.getAllByName(host))
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        worker.isDaemon = true
        val started = System.nanoTime()
        worker.start()
        worker.join(timeoutMs.toLong())
        val elapsed = (System.nanoTime() - started) / 1_000_000

        if (worker.isAlive) {
            return DnsOutcome(ok = false, ms = elapsed, ip = "", error = "DNS 解析超时")
        }
        failure.get()?.let {
            return DnsOutcome(ok = false, ms = elapsed, ip = "", error = "域名解析失败")
        }
        val ipv4 = holder.get()?.firstOrNull { it is java.net.Inet4Address }?.hostAddress
            ?: return DnsOutcome(ok = false, ms = elapsed, ip = "", error = "DNS 未返回 IPv4 地址")
        return DnsOutcome(ok = true, ms = elapsed, ip = ipv4)
    }

    // ------------------------------------------------------- TCP/TLS/HTTP

    /**
     * 一次连接测出三段耗时。ip 已知时直接连 IP（避免第二次 DNS 影响 TCP 计时）。
     */
    fun https(
        host: String,
        path: String,
        connectIp: String? = null,
        port: Int = 443,
        timeoutMs: Int
    ): HttpsOutcome {
        val totalStart = System.nanoTime()
        var socket: Socket? = null
        try {
            // ---- TCP
            val tcp = Socket()
            socket = tcp
            val tcpStart = System.nanoTime()
            val address = InetAddress.getByName(connectIp ?: host)
            tcp.connect(InetSocketAddress(address, port), timeoutMs)
            val tcpMs = (System.nanoTime() - tcpStart) / 1_000_000

            // ---- TLS（固定 TLS 1.2，和桌面端一致；不校验证书）
            val ssl = (sslContext.socketFactory as javax.net.ssl.SSLSocketFactory)
                .createSocket(tcp, host, port, true) as SSLSocket
            if (ssl.supportedProtocols.contains("TLSv1.2")) {
                ssl.enabledProtocols = arrayOf("TLSv1.2")
            }
            ssl.soTimeout = timeoutMs
            val tlsStart = System.nanoTime()
            ssl.startHandshake()
            val tlsMs = (System.nanoTime() - tlsStart) / 1_000_000

            // ---- HTTP 首字节
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append("\r\n")
                append("User-Agent: NetCheck-Android/1.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n\r\n")
            }
            val ttfbStart = System.nanoTime()
            ssl.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            ssl.outputStream.flush()

            val buffer = ByteArray(4096)
            var read = ssl.inputStream.read(buffer, 0, buffer.size)
            val ttfbMs = (System.nanoTime() - ttfbStart) / 1_000_000

            val text = StringBuilder()
            var totalBytes = 0
            if (read > 0) {
                text.append(String(buffer, 0, read, Charsets.UTF_8))
                totalBytes += read
            }
            while (totalBytes < MAX_BODY_CHARS && read > 0) {
                read = try {
                    ssl.inputStream.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
                if (read <= 0) break
                text.append(String(buffer, 0, read, Charsets.UTF_8))
                totalBytes += read
            }

            val raw = text.toString()
            val status = Regex("HTTP/\\d(?:\\.\\d)?\\s+(\\d{3})")
                .find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val headerEnd = raw.indexOf("\r\n\r\n")
            val body = if (headerEnd >= 0) raw.substring(headerEnd + 4) else ""

            val totalMs = (System.nanoTime() - totalStart) / 1_000_000
            return HttpsOutcome(
                ok = status > 0,
                tcpOk = true,
                tcpMs = tcpMs,
                tlsMs = tlsMs,
                ttfbMs = ttfbMs,
                totalMs = totalMs,
                status = status,
                body = body,
                error = if (status > 0) "" else "未收到合法 HTTP 响应"
            )
        } catch (t: Throwable) {
            val totalMs = (System.nanoTime() - totalStart) / 1_000_000
            return HttpsOutcome(
                ok = false,
                tcpOk = socket?.isConnected == true,
                totalMs = totalMs,
                error = friendly(t, socket?.isConnected == true)
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------------ DoH

    /**
     * 用阿里公共 DNS 的加密查询拿到「真实解析」，和本机解析结果对照。
     *
     * 与桌面端相比多做了一件事：优先直连 223.5.5.5 这个 IP，SNI 仍写 dns.alidns.com。
     * 这样即使本机 DNS 已经被改坏，对照值本身也还能取到（桌面端取不到就留空）。
     * 直连失败再退回域名解析。
     */
    fun doh(host: String, timeoutMs: Int = 4000): String {
        dohOnce(host, "223.5.5.5", timeoutMs)?.let { return it }
        return dohOnce(host, null, timeoutMs) ?: ""
    }

    private fun dohOnce(host: String, connectIp: String?, timeoutMs: Int): String? {
        var socket: Socket? = null
        try {
            val tcp = Socket()
            socket = tcp
            tcp.connect(InetSocketAddress(InetAddress.getByName(connectIp ?: "dns.alidns.com"), 443), timeoutMs)
            val ssl = (sslContext.socketFactory as javax.net.ssl.SSLSocketFactory)
                .createSocket(tcp, "dns.alidns.com", 443, true) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()

            val query = "/resolve?name=$host&type=A"
            val request = buildString {
                append("GET ").append(query).append(" HTTP/1.1\r\n")
                append("Host: dns.alidns.com\r\n")
                append("User-Agent: NetCheck-Android/1.0\r\n")
                append("Accept: application/dns-json\r\n")
                append("Connection: close\r\n\r\n")
            }
            ssl.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            ssl.outputStream.flush()

            val buffer = ByteArray(4096)
            val read = ssl.inputStream.read(buffer, 0, buffer.size)
            if (read <= 0) return null
            val raw = String(buffer, 0, read, Charsets.UTF_8)
            val headerEnd = raw.indexOf("\r\n\r\n")
            if (headerEnd < 0) return null
            val body = raw.substring(headerEnd + 4)
            if (!body.contains("\"Status\":0") && !body.contains("\"Status\": 0")) return null

            // 不引 JSON 库：Answer 里的 A 记录就是第一个出现的 IPv4 字面量
            val ip = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").find(body)?.value ?: return null
            if (ip == "0.0.0.0") return null
            return ip
        } catch (_: Throwable) {
            return null
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    // -------------------------------------------------------------- 错误翻译

    /** 把 Java 异常翻成一眼能看懂的结论，措辞与桌面端保持一致。 */
    fun friendly(t: Throwable, tcpOk: Boolean): String {
        val msg = (t.message ?: "").lowercase()
        return when {
            t is UnknownHostException -> "域名解析失败"
            t is SocketTimeoutException ->
                if (tcpOk) "读取响应超时（链路被掐断）" else "超时（对方无响应）"
            t is ConnectException ->
                if (msg.contains("refused")) "端口拒绝连接" else "连接失败（疑似被阻断）"
            t is SSLException -> when {
                msg.contains("closed by peer") || msg.contains("by peer") -> "连接被重置（疑似被阻断）"
                msg.contains("handshake") || msg.contains("protocol") -> "TLS 握手失败（SNI 干扰或协议不匹配）"
                else -> "TLS 握手失败（疑似被阻断）"
            }
            msg.contains("reset") || msg.contains("broken pipe") || msg.contains("econnreset") ->
                "连接被重置（疑似被阻断）"
            msg.contains("timeout") || msg.contains("timed out") -> "超时（对方无响应）"
            msg.isBlank() -> "未知错误"
            else -> msg.replace(Regex("\\s+"), " ").take(70)
        }
    }
}

package com.neurotracee.netcheck.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查：只读一个公开的版本号，不传任何本机信息。
 * Gitee 是国内默认源，GitHub 是海外源；某个源连不上时自动换另一个再试一次。
 */
object UpdateChecker {

    data class Info(val tag: String, val url: String)

    private const val GITEE_URL = "https://gitee.com/api/v5/repos/bio234/netcheck/releases/latest"
    private const val GITHUB_URL = "https://api.github.com/repos/neurotracee/netcheck/releases/latest"

    /** source: 0 Gitee · 1 GitHub。返回 Result，失败时带一句人话。 */
    fun check(source: Int, timeoutMs: Int = 8000): Result<Info> {
        val order = if (source == 1) listOf(GITHUB_URL, GITEE_URL) else listOf(GITEE_URL, GITHUB_URL)
        var last: String = "拿不到更新信息"
        for (url in order) {
            try {
                return Result.success(fetch(url, timeoutMs))
            } catch (t: Throwable) {
                last = friendly(url, t)
            }
        }
        return Result.failure(IllegalStateException(last))
    }

    private fun fetch(url: String, timeoutMs: Int): Info {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "NetCheck-Android")
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val tag = obj.optString("tag_name")
            val html = obj.optString("html_url")
            if (tag.isBlank()) throw IllegalStateException("没有版本号")
            return Info(tag.trim(), html.ifBlank { url })
        } finally {
            conn.disconnect()
        }
    }

    private fun friendly(url: String, t: Throwable): String = when {
        t is java.net.SocketTimeoutException -> "连接超时（换一个更新源再试）"
        t is java.net.UnknownHostException -> "域名解析失败（可能没网或被拦）"
        t is javax.net.ssl.SSLException -> "握手失败（链路被重置）"
        else -> t.message ?: "读取失败"
    }

    /** 把 v1.2.3.4 之类统一成可比较的四段，缺位补 0。 */
    fun normalize(tag: String): List<Int> {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".", "-", "+").mapNotNull { it.toIntOrNull() }
        return (parts + List(4) { 0 }).take(4)
    }

    /** 远端是否比本机新。 */
    fun isNewer(remote: String, local: String): Boolean {
        val r = normalize(remote)
        val l = normalize(local)
        for (i in 0 until 4) {
            if (r[i] > l[i]) return true
            if (r[i] < l[i]) return false
        }
        return false
    }
}

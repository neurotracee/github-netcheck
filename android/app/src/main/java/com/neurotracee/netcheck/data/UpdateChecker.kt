package com.neurotracee.netcheck.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查：只读一个公开的版本号，不传任何本机信息。
 * 先读仓库里的 version.json（按平台分开写，安卓只认 android 那一段，
 * 这样只发 Windows 更新时安卓不会被误报「有新版本」）；
 * 拿不到再退回 release（发布版本）的 tag。
 * Gitee 是国内默认源，GitHub 是海外源；某个源连不上时自动换另一个再试一次。
 */
object UpdateChecker {

    data class Info(val tag: String, val url: String)

    private const val GITEE_VER = "https://gitee.com/bio234/github-netcheck/raw/main/version.json"
    private const val GITHUB_VER = "https://raw.githubusercontent.com/neurotracee/github-netcheck/main/version.json"
    private const val GITEE_URL = "https://gitee.com/api/v5/repos/bio234/github-netcheck/releases/latest"
    private const val GITHUB_URL = "https://api.github.com/repos/neurotracee/github-netcheck/releases/latest"

    /** source: -1 自动 · 0 Gitee · 1 GitHub。返回 Result，失败时带一句人话。 */
    fun check(source: Int, timeoutMs: Int = 8000): Result<Info> {
        val resolved = resolveSource(source)
        val order = if (resolved == 1) listOf(GITHUB_VER, GITEE_VER) else listOf(GITEE_VER, GITHUB_VER)
        val fallback = if (resolved == 1) listOf(GITHUB_URL, GITEE_URL) else listOf(GITEE_URL, GITHUB_URL)
        var last: String = "拿不到更新信息"
        for (url in order) {
            try {
                return Result.success(fetchVersionFile(url, timeoutMs))
            } catch (t: Throwable) {
                last = friendly(url, t)
            }
        }
        for (url in fallback) {
            try {
                return Result.success(fetchRelease(url, timeoutMs))
            } catch (t: Throwable) {
                last = friendly(url, t)
            }
        }
        return Result.failure(IllegalStateException(last))
    }

    /** version.json 形如 {"android":{"version":"1.1.1.0","url":"..."},"windows":{...}} */
    private fun fetchVersionFile(url: String, timeoutMs: Int): Info {
        val obj = JSONObject(get(url, timeoutMs))
        val self = obj.optJSONObject("android")
            ?: throw IllegalStateException("version.json 里没有 android 段")
        val ver = self.optString("version")
        if (ver.isBlank()) throw IllegalStateException("没有版本号")
        val page = self.optString("url")
        return Info(ver.trim(), page.ifBlank { url })
    }

    private fun fetchRelease(url: String, timeoutMs: Int): Info {
        val obj = JSONObject(get(url, timeoutMs))
        val tag = obj.optString("tag_name")
        val html = obj.optString("html_url")
        if (tag.isBlank()) throw IllegalStateException("没有版本号")
        return Info(tag.trim(), html.ifBlank { url })
    }

    private fun get(url: String, timeoutMs: Int): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", "NetCheck-Android")
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 更新源判定：默认 Gitee。
     * 只有出现「明确证据」说明用户大概率不在中国大陆时，才默认 GitHub：
     * 界面语言不是中文，且系统时区偏移不是 UTC+8。
     * 任何一条无法确认（语言是中文 / 时区是 +8 / 读不到）→ 仍然走 Gitee。
     * 用户在设置里明确选了 Gitee 或 GitHub 时，直接照用户的选择。
     */
    fun resolveSource(pref: Int): Int {
        if (pref == 0 || pref == 1) return pref
        val lang = java.util.Locale.getDefault().language
        val zone = java.util.TimeZone.getDefault()
        val offsetHours = zone.getRawOffset() / 3_600_000
        val inChinaZone = offsetHours == 8
        val chineseUi = lang == "zh"
        return if (!chineseUi && !inChinaZone) 1 else 0
    }

    private fun friendly(url: String, t: Throwable): String = when {        t is java.net.SocketTimeoutException -> "连接超时（换一个更新源再试）"
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

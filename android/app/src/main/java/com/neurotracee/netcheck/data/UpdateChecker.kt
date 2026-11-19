package com.neurotracee.netcheck.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查：只读公开的版本号与发布说明，不传任何本机信息。
 *
 * 两条数据各司其职：
 * - version.json（按平台分开写，安卓只认 android 那一段）→ 「本平台最新版本号」的权威来源，
 *   只发 Windows 更新时安卓不会被误报「有新版本」；
 * - releases 列表 → 发布说明正文，用来在弹窗里显示「这一版改了什么」，
 *   并收集「落后几个版本」时每版的说明（最多 4 版）。
 *
 * version.json 拿不到时退回 releases 的 tag；两边都拿不到才算失败。
 * Gitee 是国内默认源，GitHub 是海外源；某个源连不上时自动换另一个再试一次。
 */
object UpdateChecker {

    /**
     * tag = 远端版本号；url = 发布页；apk = 本平台安装包直链（可能为空，退化为发布页）；
     * body = 最新版发布正文（用于摘要，可能为空）；pending = 未更新各版的说明（旧 → 新）。
     */
    data class Info(
        val tag: String,
        val url: String,
        val apk: String = "",
        val body: String = "",
        val pending: List<Pending> = emptyList()
    )

    /** 一个「还没装上、但已有说明」的版本 */
    data class Pending(val ver: String, val body: String)

    private data class Channel(
        val name: String,
        val verUrl: String,
        val apiUrl: String,
        val page: String
    )

    private val GITEE = Channel(
        "Gitee",
        "https://gitee.com/bio234/github-netcheck/raw/main/version.json",
        "https://gitee.com/api/v5/repos/bio234/github-netcheck/releases?per_page=20",
        "https://gitee.com/bio234/github-netcheck/releases/latest"
    )
    private val GITHUB = Channel(
        "GitHub",
        "https://raw.githubusercontent.com/neurotracee/github-netcheck/main/version.json",
        "https://api.github.com/repos/neurotracee/github-netcheck/releases?per_page=20",
        "https://github.com/neurotracee/github-netcheck/releases/latest"
    )

    /** 下载直链只认这几个域名，镜像站 / 被接管的接口返回陌生地址时只给发布页 */
    private val ALLOWED_HOSTS = listOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "codeload.github.com",
        "gitee.com"
    )

    /** 弹窗里最多列几个版本的说明：落后再多也只翻最近这几版，再多就去发布页看 */
    const val MAX_PENDING = 4

    fun allowedDownload(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = try {
            URL(url).host?.lowercase() ?: return false
        } catch (e: Exception) {
            return false
        }
        return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * source: -1 自动 · 0 Gitee · 1 GitHub。local 是本机当前版本，用来算「落后几个版本」。
     * 返回 Result，失败时带一句人话。
     */
    fun check(source: Int, local: String, timeoutMs: Int = 8000): Result<Info> {
        val resolved = resolveSource(source)
        val order = if (resolved == 1) listOf(GITHUB, GITEE) else listOf(GITEE, GITHUB)
        var last: String = "拿不到更新信息"

        for (ch in order) {
            // ① 版本号：version.json（只认本平台那一段）
            var vj: Info? = null
            try {
                vj = fetchVersionFile(ch.verUrl, timeoutMs)
            } catch (t: Throwable) {
                last = friendly(ch.verUrl, t)
            }
            // ② 说明：发布列表（拿不到也不影响更新本身）
            var rel: Releases? = null
            try {
                rel = fetchReleases(ch.apiUrl, local, timeoutMs, ch.page)
            } catch (t: Throwable) {
                last = friendly(ch.apiUrl, t)
            }
            if (vj == null && rel == null) continue

            val vjVer = vj?.tag ?: ""
            val relVer = rel?.bestVer ?: ""
            // 两边都给出本平台版本时取较大的；只有一个有就用它
            val target = when {
                vjVer.isBlank() -> relVer
                relVer.isBlank() -> vjVer
                isNewer(relVer, vjVer) -> relVer
                else -> vjVer
            }
            if (target.isBlank()) continue

            // 直链：优先「与 target 版本对得上」的那一条
            val apk = when {
                rel != null && rel.bestVer == target && rel.bestApk.isNotBlank() -> rel.bestApk
                vj != null && vj.tag == target && vj.apk.isNotBlank() -> vj.apk
                rel != null && rel.bestApk.isNotBlank() && rel.bestVer == target -> rel.bestApk
                else -> ""
            }
            // 正文：target 那一版在发布列表里就用它的；否则空着（弹窗回到模板话术）
            val body = when {
                rel == null -> ""
                rel.bestVer == target -> rel.bestBody
                else -> rel.bodies[target] ?: ""
            }

            return Result.success(
                Info(
                    tag = target.trim(),
                    url = vj?.url?.takeIf { it.isNotBlank() } ?: ch.page,
                    apk = apk.trim(),
                    body = body,
                    pending = rel?.pending ?: emptyList()
                )
            )
        }
        return Result.failure(IllegalStateException(last))
    }

    /** version.json 形如 {"android":{"version":"1.1.1.0","url":"...","apk":"..."},"windows":{...}} */
    private fun fetchVersionFile(url: String, timeoutMs: Int): Info {
        val obj = JSONObject(get(url, timeoutMs))
        val self = obj.optJSONObject("android")
            ?: throw IllegalStateException("version.json 里没有 android 段")
        val ver = self.optString("version")
        if (ver.isBlank()) throw IllegalStateException("没有版本号")
        val page = self.optString("url")
        val apk = self.optString("apk")
        return Info(ver.trim(), page.ifBlank { url }, apk.trim())
    }

    /** 从发布列表里挑出来的东西 */
    private class Releases(
        val bestVer: String,
        val bestApk: String,
        val bestBody: String,
        val page: String,
        /** 每个版本对应的发布正文，给「落后好几版」时按版本号取用 */
        val bodies: Map<String, String>,
        /** 还没更新、且仓库里有安装包的各版，旧 → 新，最多 MAX_PENDING 条 */
        val pending: List<Pending>
    )

    /**
     * 解析一段 releases JSON 数组。
     *
     * 只认「这个版本真的发了本平台安装包」：只给另一端发包的版本一律不计入，
     * 也就不会误报更新——这正是之前单端发版误报的根因。
     */
    private fun fetchReleases(url: String, local: String, timeoutMs: Int, pageFallback: String): Releases {
        val arr = JSONArray(get(url, timeoutMs))
        var bestVer = ""
        var bestApk = ""
        var bestBody = ""
        var bestPage = pageFallback
        val bodies = HashMap<String, String>()
        // 版本号 → (安装包直链, 正文)，最后按版本排序筛出「未更新」的
        val found = HashMap<String, Pair<String, String>>()

        for (i in 0 until arr.length()) {
            val rel = arr.optJSONObject(i) ?: continue
            if (rel.optBoolean("draft")) continue
            if (rel.optBoolean("prerelease")) continue
            val body = rel.optString("body").orEmpty()
            val page = rel.optString("html_url").ifBlank { pageFallback }
                .let { if (allowedDownload(it)) it else pageFallback }

            val assets = rel.optJSONArray("assets") ?: continue
            var apkForThis = ""
            var verForThis = ""
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val name = asset.optString("name").orEmpty()
                if (!name.lowercase().endsWith(".apk")) continue
                val raw = asset.optString("browser_download_url").orEmpty()
                val url2 = if (allowedDownload(raw)) raw else ""
                val v = versionFromName(name).ifBlank { rel.optString("tag_name") }
                if (v.isBlank()) continue
                apkForThis = url2.ifBlank { page }
                verForThis = v
                break
            }
            // 这一版没有给安卓发包 → 与本平台无关，跳过（不占用版本号、不进 pending）
            if (verForThis.isBlank()) continue

            bodies[verForThis] = body
            found[verForThis] = apkForThis to body
            if (bestVer.isBlank() || isNewer(verForThis, bestVer)) {
                bestVer = verForThis
                bestApk = apkForThis
                bestBody = body
                bestPage = page
            }
        }

        // 未更新的各版：版本 > 本机，按版本升序；超过 MAX_PENDING 时只留最近的那几版
        val newer = found.keys
            .filter { isNewer(it, local) }
            .sortedWith { a, b -> compareVer(a, b) }
        val kept = if (newer.size > MAX_PENDING) newer.takeLast(MAX_PENDING) else newer
        val pending = kept.map { Pending(it, found[it]?.second ?: "") }

        return Releases(bestVer, bestApk, bestBody, bestPage, bodies, pending)
    }

    /** 安装包文件名里的版本号：NetCheck-1.1.4.1.apk → 1.1.4.1 */
    private fun versionFromName(name: String): String {
        val m = Regex("""(\d+(?:\.\d+){1,3})""").find(name) ?: return ""
        return m.groupValues[1]
    }

    /** 两个版本号比较大小：-1 / 0 / 1 */
    private fun compareVer(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        for (i in 0 until 4) {
            if (x[i] != y[i]) return if (x[i] > y[i]) 1 else -1
        }
        return 0
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

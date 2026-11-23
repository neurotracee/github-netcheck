package com.neurotracee.netcheck.data

import android.content.Context
import android.os.Build
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 应用运行日志：按天落到应用私有目录 logs/ 下，一天一个文件，最多保留 365 天。
 *
 * 设计取舍（都是自己写的，没有引入任何第三方库）：
 *  - 行首统一打「日期 时间」前缀，所以复制出来直接能按时间对号入座，不需要再解析别的格式；
 *  - 内存里另存一份「本次启动」的环形缓冲，打开日志页第一眼看到的就是这一次打开之后的记录，
 *    不用去翻文件、也不会被昨天以前的噪声淹没；
 *  - 写文件前先判断当天文件是否已经超上限，超了就停在那一行并留一句说明，
 *    避免某个死循环把手机存储写满（这类事故在别的工具里出现过）；
 *  - 崩溃按「先落盘、再交给系统默认处理器」的顺序处理，否则系统不会弹「应用已停止」，
 *    用户只会觉得点了没反应。
 *
 * 结构上的参考（只借鉴思路，未复制任何代码，均为可再分发协议）：
 *  - Timber（Apache-2.0）：级别前缀 + tag 的扁平行格式；
 *  - ACRA（Apache-2.0）：未捕获异常先写盘、再调用原 handler 的顺序；
 *  - XLog（MIT）：按天分文件 + 过期清理的思路。
 *  这三位都属于宽松协议，即便直接搬也不构成传染；本项目仍然选择自己实现，
 *  是为了守住「零第三方依赖」这条卖点（APK 里没有 OkHttp、没有统计 SDK）。
 */
object LogStore {

    private const val TAG_FILE_PREFIX = "log-"
    private const val KEEP_DAYS = 365
    private const val MAX_BYTES = 512 * 1024      // 单日文件上限
    private const val RING_MAX = 600              // 本次启动保留的行数

    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val lock = Any()

    private var dir: File? = null
    private var sessionStart = 0L
    private var sessionLines = ArrayList<String>(RING_MAX)
    private val cappedDays = HashSet<String>()

    /** 设备信息：只写进日志头，供提交 issue 时直接抄 */
    var deviceLine: String = ""
        private set

    private fun now(): String = TimeText.stamp(System.currentTimeMillis())
    private fun today(): LocalDate = LocalDate.now()

    private var version: String = "unknown"

    /** 在 Application.onCreate 里调一次即可，重复调用不会重复装崩溃处理器。 */
    fun init(context: Context, versionName: String) {
        synchronized(lock) {
            if (dir != null) return
            val d = File(context.filesDir, "logs")
            if (!d.exists() && !d.mkdirs()) return
            dir = d
            version = versionName
            sessionStart = System.currentTimeMillis()
            deviceLine = describeDevice()
            pruneLocked()
            i("APP", "启动 v$versionName · $deviceLine")
            installCrashHandler()
        }
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String) = write("E", tag, msg)
    fun e(tag: String, msg: String, t: Throwable) = write("E", tag, msg + stackText(t))

    private fun write(level: String, tag: String, msg: String) {
        synchronized(lock) {
            val d = dir ?: return
            val stamp = now()
            val line = "$stamp [$level] [$tag] $msg"
            appendRing(line)
            appendDailyLocked(d, stamp.substring(0, 10), line)
        }
    }

    private fun appendRing(line: String) {
        if (sessionLines.size >= RING_MAX) sessionLines.removeAt(0)
        sessionLines.add(line)
    }

    private fun appendDailyLocked(d: File, day: String, text: String) {
        val f = File(d, TAG_FILE_PREFIX + day + ".txt")
        if (f.length() >= MAX_BYTES) {
            if (cappedDays.add(day)) {
                runCatching {
                    f.appendText("$text [已到单日上限 ${MAX_BYTES / 1024} KB，之后今天的日志不再写入]\n")
                }
            }
            return
        }
        runCatching { f.appendText(text + "\n") }
    }

    /** 本次启动以来的全部日志（含崩溃在内的最近 600 行）。 */
    fun sessionText(): String = synchronized(lock) {
        val head = "本次启动：${TimeText.stamp(sessionStart)} · v$version · $deviceLine"
        if (sessionLines.isEmpty()) head else head + "\n" + sessionLines.joinToString("\n")
    }

    /** 有日志的日期，从新到旧。 */
    fun availableDays(): List<LocalDate> = synchronized(lock) {
        val d = dir ?: return emptyList()
        d.listFiles()?.mapNotNull { parseDay(it.name) }?.sortedDescending() ?: emptyList()
    }

    fun totalBytes(): Long = synchronized(lock) {
        dir?.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * 读取区间。fromTime / toTime 是 "HH:mm"，留空表示整天；两端都含。
     * 只按行首时间戳过滤，非时间戳的续行（异常堆栈）跟随上一行。
     */
    fun readRange(from: LocalDate, to: LocalDate, fromTime: String? = null, toTime: String? = null): String =
        synchronized(lock) {
            val d = dir ?: return ""
            val list = (d.listFiles() ?: return "").sortedBy { it.name }
            val sb = StringBuilder()
            for (f in list) {
                val day = parseDay(f.name) ?: continue
                if (day.isBefore(from) || day.isAfter(to)) continue
                val content = runCatching { f.readText() }.getOrNull() ?: continue
                val filtered = filterByTime(content, fromTime, toTime)
                if (filtered.isEmpty()) continue
                sb.append("----- ").append(dayFormatter.format(day)).append(" -----\n").append(filtered).append("\n")
            }
            sb.toString()
        }

    /** 统计区间内的天数与字节数，供「删除前先告诉用户删了什么」。 */
    fun rangeStats(from: LocalDate, to: LocalDate): Pair<Int, Long> = synchronized(lock) {
        val d = dir ?: return 0 to 0L
        var days = 0
        var bytes = 0L
        val list = d.listFiles() ?: return 0 to 0L
        for (f in list) {
            val day = parseDay(f.name) ?: continue
            if (day.isBefore(from) || day.isAfter(to)) continue
            days++
            bytes += f.length()
        }
        days to bytes
    }

    /** 删除区间；整天覆盖的直接删文件，只覆盖一部分的按行重写。返回删掉的天数。 */
    fun clearRange(from: LocalDate, to: LocalDate, fromTime: String? = null, toTime: String? = null): Int =
        synchronized(lock) {
            val d = dir ?: return 0
            var days = 0
            val list = d.listFiles() ?: return 0
            for (f in list) {
                val day = parseDay(f.name) ?: continue
                if (day.isBefore(from) || day.isAfter(to)) continue
                if (fromTime.isNullOrBlank() && toTime.isNullOrBlank()) {
                    runCatching { f.delete() }
                    days++
                } else {
                    val kept = keepOutside(content = runCatching { f.readText() }.getOrNull() ?: "", fromTime, toTime)
                    if (kept.isEmpty()) {
                        runCatching { f.delete() }
                        days++
                    } else {
                        runCatching { f.writeText(kept) }
                        days++
                    }
                }
            }
            // 同一次删除也把本次启动缓冲里被删掉的行去掉，避免列表删了但页面还显示
            sessionLines.removeAll { line -> lineShouldDrop(line, from, to, fromTime, toTime) }
            days
        }

    /** 崩溃标记：下次启动时可以提示「上次异常退出，可以在关于里看日志」。 */
    fun pendingCrash(): String? = synchronized(lock) {
        val d = dir ?: return null
        val f = File(d, "last-crash.txt")
        if (!f.exists()) return null
        runCatching { f.readText().trim() }.getOrNull()?.ifEmpty { null }
    }

    fun dismissCrash() = synchronized(lock) {
        dir?.let { runCatching { File(it, "last-crash.txt").delete() } }
    }

    fun exportFileName(): String = "netcheck-log-" + TimeText.compact(System.currentTimeMillis()) + ".txt"

    /** 删除超过 365 天的文件。 */
    private fun pruneLocked() {
        val d = dir ?: return
        val cutoff = today().minusDays((KEEP_DAYS - 1).toLong())
        d.listFiles()?.forEach { f ->
            val day = parseDay(f.name) ?: return@forEach
            if (day.isBefore(cutoff)) runCatching { f.delete() }
        }
    }

    private fun parseDay(name: String): LocalDate? {
        if (!name.startsWith(TAG_FILE_PREFIX) || !name.endsWith(".txt")) return null
        return runCatching { LocalDate.parse(name.removePrefix(TAG_FILE_PREFIX).removeSuffix(".txt")) }.getOrNull()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                synchronized(lock) {
                    val stamp = now()
                    val body = "$stamp [F] [CRASH] 未捕获异常（线程 ${thread.name}）：${throwable.javaClass.name}: ${throwable.message}" +
                        stackText(throwable)
                    appendRing(body)
                    dir?.let { appendDailyLocked(it, stamp.substring(0, 10), body) }
                    dir?.let { d ->
                        File(d, "last-crash.txt").writeText(
                            "$stamp · v$version · ${throwable.javaClass.simpleName}: ${throwable.message ?: "无描述"}"
                        )
                    }
                }
            }
            // 交回系统默认处理器，让系统照常弹「应用已停止」，而不是被我们吞掉
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun filterByTime(content: String, fromTime: String?, toTime: String?): String {
        if (fromTime.isNullOrBlank() && toTime.isNullOrBlank()) return content.trimEnd()
        val out = StringBuilder()
        var keepCurrent = false
        for (line in content.split("\n")) {
            val hm = timeOf(line)
            if (hm != null) {
                keepCurrent = inWindow(hm, fromTime, toTime)
                if (keepCurrent) out.append(line).append("\n")
            } else if (keepCurrent && line.isNotBlank()) {
                out.append(line).append("\n")
            }
        }
        return out.toString().trimEnd()
    }

    private fun keepOutside(content: String, fromTime: String?, toTime: String?): String {
        if (fromTime.isNullOrBlank() && toTime.isNullOrBlank()) return ""
        val out = StringBuilder()
        var keepCurrent = false
        for (line in content.split("\n")) {
            val hm = timeOf(line)
            if (hm != null) {
                keepCurrent = !inWindow(hm, fromTime, toTime)
                if (keepCurrent) out.append(line).append("\n")
            } else if (keepCurrent && line.isNotBlank()) {
                out.append(line).append("\n")
            }
        }
        return out.toString()
    }

    private fun lineShouldDrop(line: String, from: LocalDate, to: LocalDate, fromTime: String?, toTime: String?): Boolean {
        if (line.length < 16) return false                 // 非时间戳行，交给整天删除时的清空处理
        val day = runCatching { LocalDate.parse(line.substring(0, 10)) }.getOrNull() ?: return false
        if (day.isBefore(from) || day.isAfter(to)) return false
        val hm = line.substring(11, 16)
        if (fromTime.isNullOrBlank() && toTime.isNullOrBlank()) return true
        return inWindow(hm, fromTime, toTime)
    }

    private fun timeOf(line: String): String? {
        if (line.length < 16) return null
        if (line[4] != '-' || line[7] != '-') return null
        val hm = line.substring(11, 16)
        return if (hm.length == 5 && hm[2] == ':') hm else null
    }

    private fun inWindow(hm: String, fromTime: String?, toTime: String?): Boolean {
        val f = if (fromTime.isNullOrBlank()) "00:00" else fromTime
        val t = if (toTime.isNullOrBlank()) "23:59" else toTime
        return hm >= f && hm <= t
    }

    private fun stackText(t: Throwable): String {
        val sb = StringBuilder()
        t.stackTrace.forEach { sb.append("\n    at ").append(it) }
        var cause = t.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append("\n  Caused by: ").append(cause)
            cause.stackTrace.forEach { sb.append("\n    at ").append(it) }
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    private fun describeDevice(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "-"
        return "机型 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· ABIs $abi"
    }
}

/** 时间文案统一走这里，24 小时制，不出现 AM/PM。 */
object TimeText {
    private val line = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
    private val compactFmt = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)

    fun stamp(millis: Long): String = synchronized(this) { line.format(java.util.Date(millis)) }
    fun compact(millis: Long): String = synchronized(this) { compactFmt.format(java.util.Date(millis)) }
}

package com.neurotracee.netcheck.data

import android.content.Context
import com.neurotracee.netcheck.model.RunResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 一条历史里的单个点位。只存展示要用的字段，主机名、IP、DoH 这类细节不落盘，省空间 */
data class HistoryItem(
    val name: String,
    val label: String,
    val level: String,
    val dnsMs: Long,
    val tcpMs: Long,
    val tlsMs: Long,
    val ttfbMs: Long,
    val ok: Boolean,
    val status: Int
)

/** 一次自检的历史记录。interrupted 为真表示是中途停止、点位没测完的那一次 */
data class HistoryEntry(
    val startedAt: Long,
    val okCount: Int,
    val total: Int,
    val avgTcp: Int,
    val elapsedSec: Int,
    val quick: Boolean,
    val items: List<HistoryItem>,
    val interrupted: Boolean = false
) {
    companion object {
        fun from(run: RunResult, startedAtMillis: Long, quick: Boolean): HistoryEntry = HistoryEntry(
            startedAt = startedAtMillis,
            okCount = run.okCount,
            total = run.total,
            avgTcp = run.avgTcp,
            elapsedSec = run.elapsedSec,
            quick = quick,
            items = run.results.map { it.toHistoryItem() }
        )

        /**
         * 中途停止：能测多少算多少，一个没测也照样留一条，防止「点了开始又立刻停止」这种情况
         * 在历史里完全没有痕迹。
         */
        fun partial(
            startedAtMillis: Long,
            plannedTotal: Int,
            elapsedSec: Int,
            results: List<com.neurotracee.netcheck.model.TargetResult>,
            quick: Boolean
        ): HistoryEntry {
            val ok = results.filter { it.ok }
            return HistoryEntry(
                startedAt = startedAtMillis,
                okCount = ok.size,
                total = plannedTotal,
                avgTcp = if (ok.isEmpty()) 0 else ok.map { it.tcpMs }.average().toInt(),
                elapsedSec = elapsedSec,
                quick = quick,
                items = results.map { it.toHistoryItem() },
                interrupted = true
            )
        }

        private fun com.neurotracee.netcheck.model.TargetResult.toHistoryItem() = HistoryItem(
            name = name,
            label = label,
            level = level,
            dnsMs = dnsMs,
            tcpMs = tcpMs,
            tlsMs = tlsMs,
            ttfbMs = ttfbMs,
            ok = ok,
            status = status
        )
    }
}

/**
 * 历史记录落在一个 JSON 文件里，最多 100 条，最新的在最前。
 * 一条记录用数组存点位（不用对象键名），100 条满打满算也就几十 KB，不做数据库。
 */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val limit = 100

    fun load(): List<HistoryEntry> = runCatching {
        if (!file.exists()) emptyList() else parse(file.readText())
    }.getOrDefault(emptyList())

    fun add(entry: HistoryEntry): List<HistoryEntry> {
        val list = load().toMutableList()
        list.add(0, entry)
        while (list.size > limit) list.removeAt(list.size - 1)
        runCatching { file.writeText(serialize(list)) }
        return list
    }

    fun clear(): List<HistoryEntry> {
        runCatching { file.delete() }
        return emptyList()
    }

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    private fun serialize(list: List<HistoryEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            o.put("t", e.startedAt)
            o.put("ok", e.okCount)
            o.put("n", e.total)
            o.put("avg", e.avgTcp)
            o.put("s", e.elapsedSec)
            o.put("q", e.quick)
            if (e.interrupted) o.put("x", 1)
            val items = JSONArray()
            e.items.forEach { i ->
                items.put(
                    JSONArray()
                        .put(i.name).put(i.label).put(i.level)
                        .put(i.dnsMs).put(i.tcpMs).put(i.tlsMs).put(i.ttfbMs)
                        .put(if (i.ok) 1 else 0).put(i.status)
                )
            }
            o.put("i", items)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun parse(text: String): List<HistoryEntry> {
        val arr = JSONArray(text)
        val out = ArrayList<HistoryEntry>(arr.length())
        for (k in 0 until arr.length()) {
            val o = arr.optJSONObject(k) ?: continue
            val itemsArr = o.optJSONArray("i") ?: JSONArray()
            val items = ArrayList<HistoryItem>(itemsArr.length())
            for (j in 0 until itemsArr.length()) {
                val a = itemsArr.optJSONArray(j) ?: continue
                items.add(
                    HistoryItem(
                        name = a.optString(0),
                        label = a.optString(1),
                        level = a.optString(2),
                        dnsMs = a.optLong(3),
                        tcpMs = a.optLong(4),
                        tlsMs = a.optLong(5),
                        ttfbMs = a.optLong(6),
                        ok = a.optInt(7) == 1,
                        status = a.optInt(8)
                    )
                )
            }
            out.add(
                HistoryEntry(
                    startedAt = o.optLong("t"),
                    okCount = o.optInt("ok"),
                    total = o.optInt("n"),
                    avgTcp = o.optInt("avg"),
                    elapsedSec = o.optInt("s"),
                    quick = o.optBoolean("q"),
                    interrupted = o.optInt("x") == 1,
                    items = items
                )
            )
        }
        return out
    }
}

/**
 * 时间写法：中文「2026年11月20日 下午 10:12:33」，英文「Nov 20, 2026 10:12 PM」。
 * 用 Calendar 判上午下午，避免 24 小时制那种机器味。
 */
fun historyTimeText(millis: Long, en: Boolean): String {
    val date = Date(millis)
    return if (en) {
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(date)
    } else {
        val cal = Calendar.getInstance().apply { time = date }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val half = if (hour < 12) "上午" else "下午"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val body = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(date)
        val clock = SimpleDateFormat("mm:ss", Locale.CHINA).format(date)
        "$body $half $h12:$clock"
    }
}

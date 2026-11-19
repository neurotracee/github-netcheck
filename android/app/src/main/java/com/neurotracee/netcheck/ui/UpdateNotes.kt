package com.neurotracee.netcheck.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurotracee.netcheck.data.ReleaseDigest
import com.neurotracee.netcheck.data.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 更新弹窗里的说明区（与 PDF 隐私打码工具的 UpdateNotes 同源）。
 *
 * 两种情况：
 * - 只落后一个版本（绝大多数情况）：平铺这一版的摘要 + 分平台条目；
 * - 落后好几个版本（例如当前 1.1.2.0，仓库已有 1.1.4.0、1.1.4.1）：变成可翻页的
 *   说明浏览器，一页一版，顶部先说明「共 N 个版本未更新」。
 *
 * 翻页方向刻意做成「相反逻辑」：**向右滑 → 上一页（更早的版本）**，
 * 向左滑 → 下一页（更新的版本）。页面顺序是「旧 → 新」，默认停在最后一页。
 *
 * 翻页只影响「看哪一版说明」：下载始终指向最新版，底部那行绿框也固定写最新版版本号。
 */
@Composable
internal fun UpdateNotesBlock(
    info: UpdateChecker.Info,
    accent: Color,
    onOpenPage: () -> Unit
) {
    val en = LocalLang.current == Lang.EN
    // 每个版本解析一次 Markdown，翻页时不再重复解析
    val pages = remember(info) {
        if (info.pending.size > 1) {
            info.pending.map { it.ver to ReleaseDigest.of(it.body) }
        } else {
            listOf(info.tag to ReleaseDigest.of(info.body))
        }
    }
    val hasAny = pages.any { !it.second.isEmpty() }

    if (pages.size > 1) {
        VersionPages(info, pages, accent, onOpenPage, en)
        if (hasAny) MoreLink(onOpenPage)
        return
    }

    NotesBody(pages.first().second, accent, en)
    if (hasAny) MoreLink(onOpenPage)
}

/**
 * 跨版本说明浏览器：一页一版，默认停在最新版那一页（最后一页）。
 * 下载目标与翻页完全解耦：不管停在哪一页，底部那行都写着最新版版本号。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionPages(
    info: UpdateChecker.Info,
    pages: List<Pair<String, ReleaseDigest.Digest>>,
    accent: Color,
    onOpenPage: () -> Unit,
    en: Boolean
) {
    val pager = rememberPagerState(initialPage = pages.lastIndex) { pages.size }
    val scope = rememberCoroutineScope()
    val idx = pager.currentPage.coerceIn(0, pages.lastIndex)
    val curVer = pages[idx].first

    // ① 先告诉用户「你落后了几个版本」
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Text(
                t(
                    "共 ${pages.size} 个版本未更新（当前 v${com.neurotracee.netcheck.BuildConfig.VERSION_NAME} → 最新 v${info.tag}）",
                    "${pages.size} versions behind (v${com.neurotracee.netcheck.BuildConfig.VERSION_NAME} → v${info.tag})"
                ),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                t(
                    "下面一页一版，正在看 v$curVer（第 ${idx + 1} / ${pages.size} 页）· 向右滑看更早的版本，向左滑看更新的版本",
                    "One version per page — viewing v$curVer (${idx + 1} / ${pages.size}) · swipe right for older, left for newer"
                ),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    // ② 可左右滑的说明页
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxWidth(),
        pageSpacing = 12.dp
    ) { page ->
        Column(Modifier.fillMaxWidth()) {
            NotesBody(pages[page].second, accent, en)
        }
    }

    // ③ 页码 + 箭头（方向与手势一致：左边=更早，右边=更新）
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
            },
            enabled = pager.currentPage > 0
        ) {
            Text(t("‹ 更早版本", "‹ Older"), fontSize = 11.5.sp)
        }
        Text(
            "${idx + 1} / ${pages.size}",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = {
                scope.launch {
                    pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex))
                }
            },
            enabled = pager.currentPage < pages.lastIndex
        ) {
            Text(t("更新版本 ›", "Newer ›"), fontSize = 11.5.sp)
        }
    }

    // ④ 固定写明下载目标：翻到旧版本也不会改变
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF2FBF4),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Text(
                t(
                    "下载目标始终是：最新版 v${info.tag}",
                    "Download target is always v${info.tag}, the newest"
                ),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14501F)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                t(
                    "左右滑动只是换着看说明，不会改变要装的版本；翻到 v$curVer 这一页点下载，装上的仍然是 v${info.tag}。",
                    "Swipe only changes which notes you read. Tapping download while viewing v$curVer still installs v${info.tag}."
                ),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = Color(0xFF2C6B3B)
            )
        }
    }

    // ⑤ 当前这版的发布时间页（详细说明在那边）
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(onClick = onOpenPage, modifier = Modifier.fillMaxWidth()) {
            Text(
                t("这一版的完整说明在 Release 页面 →", "Full notes for this version →"),
                fontSize = 11.sp
            )
        }
    }
}

/** 一版说明的正文：摘要 + 按平台归类的条目。没有内容时什么也不渲染。 */
@Composable
private fun NotesBody(digest: ReleaseDigest.Digest, accent: Color, en: Boolean) {
    val n = digest.pick(en) ?: return
    if (n.brief.isBlank() && n.items.isEmpty()) return

    if (n.brief.isNotBlank()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = 0.07f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(
                    n.brief,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (n.items.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        n.items.forEachIndexed { i, item ->
            if (i > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                )
            }
            Column(Modifier.padding(vertical = 7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformTag(item.platform, en)
                    Text(
                        item.title,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                if (item.lines.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.lines.joinToString("\n"),
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLink(onOpenPage: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onOpenPage) {
            Text(
                t("具体详见 Release 页面 →", "Full notes on the release page →"),
                fontSize = 11.5.sp
            )
        }
    }
}

/** Windows / 安卓 / 两端 的小标签，颜色只用来区分平台，不做好坏暗示 */
@Composable
private fun PlatformTag(platform: String, en: Boolean) {
    val (label, fg, bg) = when (platform) {
        "win" -> Triple("Windows", Color(0xFF2B5BD0), Color(0xFFEEF4FF))
        "android" -> Triple(if (en) "Android" else "安卓", Color(0xFF1A7F45), Color(0xFFEAF7EE))
        else -> Triple(if (en) "Both" else "两端", Color(0xFF6B3FC4), Color(0xFFF3EEFE))
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            label,
            Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

package com.neurotracee.netcheck.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** 界面语言。英文界面只翻译界面骨架，报告正文与点位说明仍是中文（工具面向中文用户，署名与判读都以中文为准）。 */
enum class Lang { ZH, EN }

val LocalLang = compositionLocalOf { Lang.ZH }

/** 在 Composable 里按当前语言取一句话。 */
@Composable
fun t(zh: String, en: String): String = if (LocalLang.current == Lang.EN) en else zh

/** 非 Composable 上下文（点击回调、后台线程、工具函数）里按设置项取一句话。 */
fun tr(ctx: Context, zh: String, en: String): String =
    if (com.neurotracee.netcheck.data.Prefs(ctx).langMode == 1) en else zh

package com.neurotracee.netcheck.data

import android.content.Context

/**
 * 设置项落盘（SharedPreferences），不涉及任何账号、不上传。
 * 全部是本地开关，改完立刻生效，不需要重启。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("netcheck_settings", Context.MODE_PRIVATE)

    /** 0 跟随系统 · 1 浅色 · 2 深色 */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME, 0)
        set(v) = sp.edit().putInt(KEY_THEME, v).apply()

    /** 0 中文 · 1 English */
    var langMode: Int
        get() = sp.getInt(KEY_LANG, 0)
        set(v) = sp.edit().putInt(KEY_LANG, v).apply()

    /** 启动时自动查一次更新（没网就不响） */
    var autoCheckUpdate: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    /** -1 自动（默认 Gitee，只有明确判定在境外才走 GitHub）· 0 Gitee · 1 GitHub */
    var updateSource: Int
        get() = sp.getInt(KEY_SOURCE, -1)
        set(v) = sp.edit().putInt(KEY_SOURCE, v).apply()

    /** 报告保存目录（SAF 授权的目录 Uri，空表示未设置：导出时走系统分享面板） */
    var saveTreeUri: String?
        get() = sp.getString(KEY_SAVE_DIR, null)
        set(v) = sp.edit().putString(KEY_SAVE_DIR, v).apply()

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANG = "lang_mode"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_SOURCE = "update_source"
        private const val KEY_SAVE_DIR = "save_tree_uri"
    }
}

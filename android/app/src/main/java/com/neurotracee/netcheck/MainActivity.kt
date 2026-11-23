package com.neurotracee.netcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.neurotracee.netcheck.data.LogStore
import com.neurotracee.netcheck.data.Prefs
import com.neurotracee.netcheck.ui.Lang
import com.neurotracee.netcheck.ui.LocalLang
import com.neurotracee.netcheck.ui.NetCheckScreen
import com.neurotracee.netcheck.ui.NetCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        LogStore.i("UI", "MainActivity.onCreate · 语言=${Prefs(applicationContext).langMode} · 主题=${Prefs(applicationContext).themeMode}")
        val prefs = Prefs(applicationContext)
        setContent {
            // 设置改动马上生效：主题与语言都提到这一层，改完立刻重绘
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            var lang by remember { mutableStateOf(if (prefs.langMode == 1) Lang.EN else Lang.ZH) }
            NetCheckTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalLang provides lang) {
                    NetCheckScreen(
                        prefs = prefs,
                        onThemeChanged = { themeMode = it },
                        onLangChanged = { lang = it }
                    )
                }
            }
        }
    }
}

package com.neurotracee.netcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.neurotracee.netcheck.ui.NetCheckScreen
import com.neurotracee.netcheck.ui.NetCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NetCheckTheme {
                NetCheckScreen()
            }
        }
    }
}

package com.neurotracee.netcheck

import android.app.Application
import com.neurotracee.netcheck.data.LogStore

/**
 * 应用级入口：日志与崩溃捕获必须在 Activity 之前装好，
 * 否则「一打开就崩」这种情况会一条都留不下来。
 */
class NetCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogStore.init(this, BuildConfig.VERSION_NAME)
    }
}

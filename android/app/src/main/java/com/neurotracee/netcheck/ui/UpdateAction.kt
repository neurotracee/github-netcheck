package com.neurotracee.netcheck.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.neurotracee.netcheck.BuildConfig
import com.neurotracee.netcheck.data.UpdateApk
import com.neurotracee.netcheck.data.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

/**
 * 「立即更新」这一块：应用内下载 + 交给系统安装器。
 *
 * 以前是把下载地址扔给浏览器，Gitee 的 APK 附件带着 `application/zip` 的 MIME，
 * 浏览器就把文件存成 `xxx.apk.zip`，用户点了装不上。现在一律先自己下，
 * 下完核对包名和版本号，再拉起系统安装器；浏览器那条路留着作为最后的退路。
 *
 * 启动时的自动检查、设置里的手动检查共用这一个组件，两处行为一致。
 */
@Composable
internal fun UpdateActionBlock(
    info: UpdateChecker.Info,
    accent: Color,
    onOpenBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onDone: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // 没有本平台安装包直链（兜底到发布页）时，应用内下载走不通，只能走浏览器
    val canInApp = info.apk.isNotBlank()

    var downloading by remember(info) { mutableStateOf(false) }
    var percent by remember(info) { mutableStateOf(0) }
    var needPerm by remember(info) { mutableStateOf(false) }
    var started by remember(info) { mutableStateOf(false) }
    var error by remember(info) { mutableStateOf("") }
    var apk by remember(info) { mutableStateOf<File?>(null) }
    // 下载进度：total <= 0 表示还不知道总大小（连接中或服务器没给长度）
    var gotBytes by remember(info) { mutableStateOf(0L) }
    var totalBytes by remember(info) { mutableStateOf(0L) }
    // 上次是不是用备用源下的，重试时沿用，免得来回跳
    var usingAlt by remember(info) { mutableStateOf(false) }

    fun begin(alt: Boolean) {
        val url = if (alt) info.altApk else info.apk
        if (url.isBlank()) return
        usingAlt = alt
        downloading = true
        percent = 0
        gotBytes = 0
        totalBytes = 0
        error = ""
        needPerm = false
        scope.launch {
            val f = UpdateApk.fetchAndInstall(
                ctx = ctx,
                url = url,
                version = info.tag
            ) { done, total ->
                gotBytes = done
                totalBytes = total
                percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
            }
            apk = f.apk
            downloading = false
            when (val o = f.outcome) {
                is UpdateApk.Outcome.Started -> { started = true; onDone() }
                is UpdateApk.Outcome.NeedPermission -> needPerm = true
                is UpdateApk.Outcome.Failed -> error = o.reason
            }
        }
    }

    fun resume() {
        val f = apk ?: run { begin(usingAlt); return }
        when (val o = UpdateApk.retryInstall(ctx, f)) {
            is UpdateApk.Outcome.Started -> { started = true; needPerm = false; onDone() }
            is UpdateApk.Outcome.NeedPermission -> needPerm = true
            is UpdateApk.Outcome.Failed -> error = o.reason
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            // 用户去系统设置开了「安装应用」再回来：不用再点一次「去允许安装」
            if (event == Lifecycle.Event.ON_RESUME && needPerm && UpdateApk.canInstall(ctx)) {
                needPerm = false
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxWidth()) {
        // ---- 状态说明 ----
        Text(
            when {
                downloading && totalBytes <= 0L && gotBytes == 0L -> t(
                    "正在连接 v${info.tag} 的下载地址…（这一步最容易卡，连不上会自动换一次源，不用关页面）",
                    "Connecting to the v${info.tag} download… (this step is the slowest; it retries on its own, keep this open)"
                )
                downloading && totalBytes <= 0L -> t(
                    "已连上，正在接收 v${info.tag}… 已收 ${mb(gotBytes)}（服务器没给总大小，进度条只表示「在动」）",
                    "Receiving v${info.tag}… ${mb(gotBytes)} so far (no total size from the server, the bar just means “moving”)"
                )
                downloading -> t(
                    "正在下载 v${info.tag}… $percent%（不用离开这个页面，下完直接弹出安装）",
                    "Downloading v${info.tag}… $percent% — stay here, the installer opens by itself"
                )
                needPerm -> t(
                    "安装包已经下好了，但系统还没允许本应用「安装应用」。点「继续安装」会带你去开那个开关，" +
                        "开完回到这里再点一次就好。",
                    "The file is downloaded, but Android has not allowed this app to install apps yet. " +
                        "“Continue” takes you to that switch; turn it on and tap again when you come back."
                )
                started -> t(
                    "已经打开系统安装界面。装完覆盖旧版即可，不用先卸载。",
                    "The system installer is open. Install over the old version — no need to uninstall first."
                )
                error.isNotBlank() -> error
                else -> t(
                    "点「立即更新」直接在这里下载并安装，不用经过浏览器，也不用自己去文件夹里找文件。",
                    "Tap “Update now” and the app downloads and installs it here — no browser, no hunting for the file."
                )
            },
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = if (error.isNotBlank() && !downloading) Color(0xFFE5484D)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (downloading) {
            Spacer(Modifier.height(8.dp))
            if (totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent
                )
            } else {
                // 总大小还没拿到：用不确定态，至少让用户看到它在动
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accent
                )
            }
        }

        // ---- 按钮 ----
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (canInApp) {
                Button(
                    enabled = !downloading,
                    onClick = {
                        when {
                            needPerm && UpdateApk.canInstall(ctx) -> resume()
                            needPerm -> {
                                UpdateApk.openInstallPermission(ctx)
                                Toast.makeText(
                                    ctx,
                                    tr(ctx, "打开开关后回到这里点「继续安装」", "Turn it on, then come back and tap “Continue”"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            apk != null -> resume()
                            else -> begin(usingAlt)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(
                        when {
                            downloading && totalBytes <= 0L && gotBytes == 0L -> t("连接中…", "Connecting…")
                            downloading && totalBytes <= 0L -> t("已收 ${mb(gotBytes)}", "${mb(gotBytes)}")
                            downloading -> t("下载中 $percent%", "Downloading $percent%")
                            needPerm -> t("继续安装", "Continue")
                            apk != null -> t("继续安装", "Continue")
                            error.isNotBlank() -> t("重试", "Retry")
                            else -> t("立即更新", "Update now")
                        },
                        fontSize = 12.5.sp
                    )
                }
            }
            TextButton(enabled = !downloading, onClick = onOpenBrowser) {
                Text(t("用浏览器下载", "Browser"), fontSize = 12.sp)
            }
            TextButton(onClick = onCopyLink) {
                Text(t("复制链接", "Copy link"), fontSize = 12.sp)
            }
            // 安装器抽风时的退路：把已下好的包另存到「下载」，用文件管理器自己点一下装
            val saved = apk
            if (!downloading && saved != null && saved.exists()) {
                TextButton(onClick = {
                    val name = UpdateApk.saveToDownloads(ctx, saved)
                    Toast.makeText(
                        ctx,
                        if (name != null) tr(ctx, "已存到「下载」：$name，用文件管理器点它就能装", "Saved to Downloads: $name — open it from your file manager")
                        else tr(ctx, "这台手机的「下载」目录不让写，改用浏览器下载", "This phone blocks writing to Downloads; use the browser instead"),
                        Toast.LENGTH_LONG
                    ).show()
                }) {
                    Text(t("另存到下载", "Save to Downloads"), fontSize = 12.sp)
                }
            }
            // 主源下不动时给一次「换另一个仓库下」的机会，不用让人只靠浏览器
            if (!downloading && error.isNotBlank() && info.altApk.isNotBlank() && !usingAlt) {
                TextButton(onClick = { begin(true) }) {
                    Text(t("换源再试", "Other source"), fontSize = 12.sp)
                }
            }
        }

        // ---- 走浏览器那条退路的提醒 ----
        if (error.isNotBlank() || started) {
            Text(
                t(
                    "如果改用浏览器下载：Gitee 有时会把文件存成「…apk.zip」，把结尾的 .zip 删掉就能正常安装。",
                    "If you use a browser instead: Gitee sometimes saves the file as “…apk.zip” — delete the " +
                        "trailing .zip and it installs fine."
                ),
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** 字节数写成 MB，给「总大小未知」时的进度文字用 */
private fun mb(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)

/** 用系统默认浏览器打开；打不开返回 false，交给界面给备选路径 */
internal fun openUrlInBrowser(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: Exception) {
    false
}

internal fun copyLinkToClipboard(ctx: Context, url: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("releases", url))
    Toast.makeText(
        ctx,
        tr(ctx, "链接已复制，粘到浏览器里打开", "Link copied — paste it into your browser"),
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * 发现新版本时的窗口。启动自动检查、设置手动检查共用这一个：
 * 正文说明 + 应用内下载安装块 + 浏览器 / 复制链接退路。
 */
@Composable
internal fun UpdateDialog(
    info: UpdateChecker.Info,
    onClose: () -> Unit,
    onToast: (String) -> Unit
) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val downloadUrl = info.apk.ifBlank { info.url }
    val zipHint = downloadUrl.contains("gitee.com")

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Text(
                    t("发现新版本 v${info.tag}", "New version v${info.tag}"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(9.dp))
                UpdateNotesBlock(
                    info = info,
                    accent = accent,
                    onOpenPage = {
                        val page = info.url.ifBlank { downloadUrl }
                        if (!openUrlInBrowser(ctx, page)) {
                            copyLinkToClipboard(ctx, page)
                            onToast(tr(ctx, "没有能打开浏览器的应用，已复制链接", "No browser found; link copied"))
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    t("当前是 v${BuildConfig.VERSION_NAME}。不更新也一模一样用，只是新版顺手修了几个小毛病；下次打开还会再提醒一次。",
                        "You're on v${BuildConfig.VERSION_NAME}. Skipping is fine — the app keeps working; you'll be reminded next launch."),
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    t("安卓 16（含 OPPO / vivo / 小米 / 荣耀等机型的侧载校验）：安装时若提示「开发者身份未验证」，勾选「已知悉应用风险」再点「仍然安装」，个别机型还要输一次锁屏密码。",
                        "Android may ask you to confirm installing from an unknown source: tick “I understand the risk”, then “Install anyway”."),
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (zipHint) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("Gitee 浏览器下载会把 .apk 存成 .zip（服务器按文件类型发的），下完把后缀改回 .apk 就能装；嫌麻烦去 GitHub 页下载。",
                            "Gitee's download may save the APK as .zip; rename it back to .apk. GitHub doesn't have this issue."),
                        fontSize = 11.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                UpdateActionBlock(
                    info = info,
                    accent = accent,
                    onOpenBrowser = {
                        if (!openUrlInBrowser(ctx, downloadUrl)) {
                            copyLinkToClipboard(ctx, downloadUrl)
                            onToast(tr(ctx, "没有能打开浏览器的应用，已复制链接", "No browser found; link copied"))
                        }
                    },
                    onCopyLink = {
                        copyLinkToClipboard(ctx, downloadUrl)
                        onToast(tr(ctx, "下载链接已复制", "Download link copied"))
                    },
                    onDone = { onClose() }
                )

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(t("暂不更新", "Not now"), fontSize = 13.sp) }
                }
            }
        }
    }
}

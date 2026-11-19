package com.neurotracee.netcheck.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neurotracee.netcheck.BuildConfig
import com.neurotracee.netcheck.data.Prefs
import com.neurotracee.netcheck.data.UpdateChecker
import android.os.Handler
import android.os.Looper

private const val PROJECT_URL = "https://github.com/neurotracee/github-netcheck"
private const val MIRROR_URL = "https://gitee.com/bio234/github-netcheck"

/** 设置面板：外观 / 语言 / 报告保存位置 / 更新检查 / 版本与更新 / 关于。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    prefs: Prefs,
    versionName: String,
    onDismiss: () -> Unit,
    onOpenAbout: () -> Unit,
    onToast: (String) -> Unit,
    onThemeChanged: (Int) -> Unit,
    onLangChanged: (Lang) -> Unit
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var themeMode by remember { mutableStateOf(prefs.themeMode) }
    var langMode by remember { mutableStateOf(prefs.langMode) }
    var autoUpdate by remember { mutableStateOf(prefs.autoCheckUpdate) }
    var source by remember { mutableStateOf(prefs.updateSource) }
    // 只存「有没有设过」这个事实，文案在显示时按当前语言取，切语言后不会留着旧说法
    var saveDirSet by remember { mutableStateOf(prefs.saveTreeUri != null) }
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.Info?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.saveTreeUri = uri.toString()
            saveDirSet = true
            onToast(tr(context, "报告以后会存到你选的文件夹", "Reports will be saved to the folder you picked"))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.bg,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("设置", "Settings"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Spacer(Modifier.width(10.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    t("完成", "Done"),
                    fontSize = 15.sp,
                    color = p.fair,
                    modifier = Modifier.clickable { onDismiss() }.padding(6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Spacer(Modifier.height(14.dp))

            // 界面语言
            Label(p, t("界面语言", "Language"))
            Segmented(
                p = p,
                options = listOf(t("中文", "中文") to 0, "English" to 1),
                selected = langMode,
                onSelect = {
                    langMode = it
                    prefs.langMode = it
                    onLangChanged(if (it == 1) Lang.EN else Lang.ZH)
                }
            )
            Hint(p, t("切到 English 只换界面骨架的文字，报告正文与点位说明仍是中文。",
                "English switches the interface chrome only; the report body stays Chinese."))

            // 外观
            Label(p, t("外观", "Appearance"))
            Segmented(
                p = p,
                options = listOf(t("跟随系统", "Auto") to 0, t("浅色", "Light") to 1, t("深色", "Dark") to 2),
                selected = themeMode,
                onSelect = { themeMode = it; prefs.themeMode = it; onThemeChanged(it) }
            )
            Hint(p, t("选「跟随系统」时，会随手机的深色模式自动切换。",
                "Auto follows the system dark mode setting."))

            // 报告保存位置
            Label(p, t("报告保存位置", "Where reports go"))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t("已设置", "Set").takeIf { saveDirSet } ?: t("未设置（每次走分享面板）", "Not set (uses the share sheet)"), fontSize = 13.5.sp, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        t("导出报告时不再弹分享面板，直接写进这个文件夹", "Reports are written straight into this folder"),
                        fontSize = 11.sp, color = p.sub
                    )
                }
                Text(
                    t("选择", "Pick"),
                    fontSize = 13.sp, color = p.fair,
                    modifier = Modifier.clickable { picker.launch(null) }.padding(6.dp)
                )
                if (prefs.saveTreeUri != null) {
                    Text(
                        t("清除", "Clear"),
                        fontSize = 13.sp, color = p.sub,
                        modifier = Modifier.clickable {
                            prefs.saveTreeUri = null
                            saveDirSet = false
                            onToast(tr(context, "已改回每次走分享面板", "Back to the share sheet"))
                        }.padding(6.dp)
                    )
                }
            }

            // 更新检查
            Label(p, t("更新检查方式", "Update check"))
            Segmented(
                p = p,
                options = listOf(t("自动检查（启动时）", "Auto at launch") to 0, t("手动检查", "Manual") to 1),
                selected = if (autoUpdate) 0 else 1,
                onSelect = { autoUpdate = it == 0; prefs.autoCheckUpdate = autoUpdate }
            )
            Hint(p, t("自动：每次启动在后台静默查一次，没网就不响。手动：只有点下面的「检查更新」才联网。",
                "Auto: a silent check at launch. Manual: only when you tap Check for updates."))

            Label(p, t("更新源", "Update source"))
            Segmented(
                p = p,
                options = listOf(
                    t("自动（默认 Gitee）", "Auto (Gitee first)") to -1,
                    "Gitee" to 0,
                    "GitHub" to 1
                ),
                selected = source,
                onSelect = { source = it; prefs.updateSource = it }
            )
            Hint(p, t("默认走 Gitee；只有语言不是中文、时区也不是 UTC+8 这种明确迹象才默认 GitHub，判断不出来仍然走 Gitee。当前实际使用：" +
                    if (UpdateChecker.resolveSource(source) == 1) "GitHub" else "Gitee",
                "Gitee by default; GitHub only when the language is non-Chinese AND the time zone is not UTC+8. Now using: " +
                    if (UpdateChecker.resolveSource(source) == 1) "GitHub" else "Gitee"))

            // 版本与更新
            Label(p, t("版本与更新", "Version"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("当前版本 v$versionName", "Current version v$versionName"),
                        fontSize = 14.sp, color = p.ink, fontWeight = FontWeight.Medium)
                    Text(
                        checkResult ?: t("更新信息来自上面的更新源", "Updates come from the source above"),
                        fontSize = 11.5.sp, color = if (checkResult != null) p.ink else p.sub
                    )
                }
                Text(
                    if (checking) t("检查中…", "Checking…") else t("检查更新", "Check"),
                    fontSize = 13.sp, color = p.fair,
                    modifier = Modifier
                        .clickable(enabled = !checking) {
                            checking = true
                            checkResult = null
                            val src = source
                            Thread {
                                val r = UpdateChecker.check(src, versionName)
                                Handler(Looper.getMainLooper()).post {
                                    checking = false
                            checkResult = r.fold(
                                        onSuccess = { info ->
                                            if (UpdateChecker.isNewer(info.tag, versionName)) {
                                                updateInfo = info
                                                tr(context, "发现新版本 ${info.tag} · 可在弹窗内直接下载安装", "New version ${info.tag} — it can be installed straight from the dialog")
                                            } else {
                                                tr(context, "已是最新版本（远端 ${info.tag}）", "Already up to date (remote ${info.tag})")
                                            }
                                        },
                                        onFailure = { e -> tr(context, "检查失败：${e.message}", "Check failed: ${e.message}") }
                                    )
                                }
                            }.start()
                        }
                        .padding(6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .clickable { onOpenAbout() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("关于与开源信息", "About & open source"), fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                Text("›", fontSize = 18.sp, color = p.sub)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("全部检测在本机完成，不上传任何数据；联网只用在更新检查。",
                    "Everything runs on device. The only network use is the update check."),
                fontSize = 11.sp, color = p.sub
            )
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(info = info, onClose = { updateInfo = null }, onToast = onToast)
    }
}

/** 关于页：署名、协议、地址、隐私、二次开发要求。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(versionName: String, onDismiss: () -> Unit, onToast: (String) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = p.bg,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 26.dp)
        ) {
            Text(t("GitHub 网络自检", "GitHub Net Check"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Spacer(Modifier.height(3.dp))
            Text("v$versionName", fontSize = 12.sp, color = p.sub)
            Spacer(Modifier.height(6.dp))
            Text(
                t("本地离线运行的 GitHub 网络自检工具：Windows 脚本 + 安卓 APK，同一套点位与评级。",
                    "An offline network reachability checker: Windows script + Android APK, same targets and grading."),
                fontSize = 12.5.sp, color = p.sub, lineHeight = 20.sp
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Spacer(Modifier.height(12.dp))

            KvRow(p, t("指导作者", "Author"), t("项目创作时为初中生。\n只负责提需求、定判读标准、验收与反馈问题；不写代码。",
                "A middle-school student when the project was made. He only files requirements, sets the grading rules and accepts or rejects the result; he does not write code."))
            KvRow(p, t("开发", "Built by"), "豆包工作任务的 Agent ×\nDeepSeek-V4.1-Flash")
            Spacer(Modifier.height(8.dp))
            Text(
                t("说明：Agent 由豆包工作任务提供，模型是 DeepSeek 的 V4.1-Flash（由指导作者自己在客户端选定）。"
                        + "两个端的全部代码、构建、签名、新建仓库与发布都由 Agent 完成，与腾讯没有任何关系。",
                    "Note: the agent comes from Doubao Work, the model is DeepSeek V4.1-Flash chosen by the author. "
                            + "All code, builds, signing and repository setup were done by the agent. No relation to Tencent."),
                fontSize = 11.5.sp, color = p.sub, lineHeight = 18.sp
            )
            KvRow(p, t("开源协议", "License"), "Apache-2.0")
            KvRow(p, t("原项目地址", "Primary"), "github.com/neurotracee/\ngithub-netcheck", link = PROJECT_URL, onOpen = { openUrl(context, PROJECT_URL, onToast) })
            KvRow(p, t("镜像地址", "Mirror"), "gitee.com/bio234/\ngithub-netcheck", link = MIRROR_URL, onOpen = { openUrl(context, MIRROR_URL, onToast) })

            Spacer(Modifier.height(14.dp))
            Text(t("隐私", "Privacy"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                t("没有存储权限，也没有后台常驻联网。唯一的网络权限只用在三件事：① 启动时自动检查更新（可在上面关掉）；"
                        + "② 手动点「检查更新」；③ 测那 14 个点位的延迟。"
                        + "检查更新只连两个仓库——国内 Gitee 与国外 GitHub，由脚本按可用的那个自动择一，只读一行公开版本号；"
                        + "不上传信息、不传文件、不带账号或设备标识。报告写在应用私有目录，导出时才交给你选定的位置或系统分享面板。",
                    "No storage permission and no background networking. Network access is used only for three things: "
                            + "the automatic update check at startup (can be turned off above), the manual \"check for updates\", "
                            + "and measuring the 14 targets. Update checks hit only two repos — Gitee (China) and GitHub — "
                            + "picking whichever answers, reading one public version string; nothing is uploaded."),
                fontSize = 12.sp, color = p.sub, lineHeight = 19.sp
            )

            Spacer(Modifier.height(14.dp))
            WarnBox(p) {
                Text(t("二次开发 / 商用：三条硬要求", "Forks & commercial use: three hard rules"),
                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.warnText)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("① 保留「指导作者：项目创作时为初中生」的署名，不得删改。\n"
                            + "② 在你的程序里标注原项目地址，让使用者看得见（关于页、页脚、帮助菜单均可）。\n"
                            + "③ 改动过的版本请注明「本程序基于该地址修改，非原作者发布」。",
                        "① Keep the author credit, do not remove or rewrite it.\n"
                                + "② Show the original project URL somewhere users can see it.\n"
                                + "③ Modified builds must say they are modified and not released by the original author."),
                    fontSize = 12.sp, color = p.warnText, lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            WarnBox(p) {
                Text(t("图标与商标（重要）", "Icon & trademark (important)"),
                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.warnText)
                Spacer(Modifier.height(8.dp))
                Text(
                    t("图标里的猫头来自 GitHub 的吉祥物 Octocat；Octocat® 是 GitHub, Inc. 的注册商标"
                            + "（GitHub, Inc. 现为 Microsoft 旗下公司），相关权利不属于本项目作者。\n"
                            + "本项目未获得 GitHub, Inc. 或 Microsoft 的任何授权、认可或背书，"
                            + "与 GitHub 官方没有任何关系（名字里的 GitHub 只说明检查的对象）。\n"
                            + "本项目仅限个人、非商业使用，禁止任何商业用途。\n"
                            + "强烈建议不要使用这个图标：fork、二次开发或二次发布，"
                            + "请换成不含任何第三方商标元素的图标；不要直接沿用，也不要改动后继续使用。\n"
                            + "若 GitHub, Inc. 或 Microsoft 认为使用不妥并提出，本项目会立即更换图标并清理相关说明。\n"
                            + "以上仅说明作者立场与使用边界，不是授权，不能替代权利人的许可，"
                            + "也不能免除使用者自身的法律责任。",
                        "The cat in the icon is GitHub's mascot Octocat; Octocat® is a registered trademark of "
                                + "GitHub, Inc. (a Microsoft subsidiary). Those rights are not owned by this "
                                + "project's author.\n"
                                + "This project has no authorization, approval or endorsement from GitHub, Inc. "
                                + "or Microsoft, and is not affiliated with GitHub in any way.\n"
                                + "This project is for personal, non-commercial use only; commercial use is "
                                + "prohibited.\n"
                                + "Please do not reuse this icon: forks, derivatives and redistributions must use "
                                + "an icon without any third-party trademark.\n"
                                + "The icon will be replaced if the rights holder objects.\n"
                                + "This text states the author's position only; it is not a licence and does not "
                                + "exempt any user from liability."),
                    fontSize = 12.sp, color = p.warnText, lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                t("维护节奏：平时没有充足时间，大概一两个月上来看一两次，回复不会很快。代码全部公开，"
                        + "修不动的地方欢迎自己改、自己接着用；协议允许商用，不用付钱也不用问我，照上面三条做就行。",
                    "Maintenance is occasional. The code is fully open: fork it, fix it, keep using it. "
                            + "Commercial use is allowed under Apache-2.0 as long as the three rules above are kept."),
                fontSize = 12.sp, color = p.sub, lineHeight = 19.sp
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(t("给两个仓库都点个 Star（标星）", "Star both repos"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(t("两个都点一下最实在：GitHub 那颗让国外和搜索引擎找到它，Gitee 那颗让不翻墙的人也打得开。只点一边，另一半人就搜不到——差一颗星，少一半人。不方便的话点一个就够，功能一个不少。",
                        "A star on both repos does the most: GitHub makes it findable outside China and to search engines, "
                            + "Gitee makes it reachable without a proxy. Skipping one leaves half the audience unable to find it. "
                            + "If that's a hassle, one is plenty — the tool works exactly the same."),
                        fontSize = 11.5.sp, color = p.sub, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.fair)
                                .clickable { openUrl(context, PROJECT_URL, onToast) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(t("GitHub 仓库", "GitHub"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.fair)
                                .clickable { openUrl(context, MIRROR_URL, onToast) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(t("Gitee 镜像", "Gitee"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(t("赞赏（完全自愿）", "Tip jar (completely optional)"),
                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    t("要是这个小工具帮你省了点时间，愿意的话可以请我喝杯奶茶——不打赏也一模一样用，功能一个都不少。",
                        "If this little tool saved you some time, you're welcome to buy me a milk tea. Nothing changes if you don't — every feature stays free."),
                    fontSize = 11.5.sp, color = p.sub, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Image(
                    painter = painterResource(com.neurotracee.netcheck.R.drawable.wechat_donate),
                    contentDescription = t("微信赞赏码", "WeChat tip QR code"),
                    modifier = Modifier.size(176.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text(t("微信赞赏码 · 截图后在微信「扫一扫」里选相册即可", "WeChat tip QR — screenshot it, then pick it from your album in WeChat Scan."),
                    fontSize = 11.sp, color = p.sub)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.fair)
                        .clickable { onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(t("知道了", "Got it"), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String, onToast: (String) -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { onToast(tr(context, "没有能打开浏览器的应用", "No app can open a browser")) }
}

@Composable
private fun Label(p: Palette, text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, fontSize = 12.5.sp, color = p.sub, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Hint(p: Palette, text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, fontSize = 11.sp, color = p.sub, lineHeight = 17.sp)
}

/** 与桌面报告同一套观感的左右分段选择器。 */
@Composable
private fun Segmented(p: Palette, options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, value) ->
            val on = selected == value
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) p.bg else Color.Transparent)
                    .border(
                        width = if (on) 1.dp else 0.dp,
                        color = if (on) p.line else Color.Transparent,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    color = if (on) p.ink else p.sub,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun KvRow(p: Palette, key: String, value: String, link: String? = null, onOpen: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(key, fontSize = 12.5.sp, color = p.sub, modifier = Modifier.width(86.dp))
        Text(
            value,
            fontSize = 12.5.sp,
            color = if (link != null) p.fair else p.ink,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f).let { m -> if (link != null) m.clickable { onOpen() } else m }
        )
    }
}

@Composable
private fun WarnBox(p: Palette, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.warnBg)
            .border(1.dp, p.warnBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) { Column { content() } }
}

@Suppress("unused")
@Composable
private fun SwitchRow(p: Palette, title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Suppress("unused")
@Composable
private fun Dot(p: Palette) {
    Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(p.sub))
}

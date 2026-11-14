# 参与改进

这是一个业余时间维护的小工具，代码量不大，改起来门槛低。下面写清楚「东西在哪、怎么跑、提交时要带什么」，
按这个来能省掉来回问的几轮。

先读一遍 [README](README.md) 里的「关于署名」——二次开发或商用要保留署名与项目地址，那是协议要求。

---

## 一、目录结构

```
网络连通性自检-开源包/
├─ README.md                  项目主说明（两端共用，不另写安卓 README）
├─ LICENSE                    Apache License 2.0 全文
├─ NOTICE.md                  第三方组件与署名声明
├─ ADDITIONAL-TERMS.md        署名与出处要求（对应协议第 4 条、第 6 条）
├─ CHANGELOG.md               更新日志（四段版本号，两端同号）
├─ DOCS.md                    文档地图
├─ CONTRIBUTING.md            本文件
│
├─ windows/                   Windows 端（不打包，纯脚本）
│  ├─ netcheck.ps1            探测内核 + 报告生成，全部逻辑都在这一份里
│  ├─ 一键网络自检.bat         启动器（带 -ExecutionPolicy Bypass）
│  └─ 使用说明.md              面向使用者的简版说明
│
├─ android/                   安卓端（Gradle 工程，可直接用 Android Studio 打开）
│  ├─ settings.gradle.kts / build.gradle.kts / gradle.properties
│  ├─ app/build.gradle.kts    版本号、签名、依赖都在这里
│  └─ app/src/main/
│     ├─ java/com/neurotracee/netcheck/
│     │  ├─ MainActivity.kt          入口
│     │  ├─ MainViewModel.kt         状态与任务编排
│     │  ├─ model/Model.kt           数据模型 + 评级阈值（Verdict）
│     │  ├─ net/Targets.kt           14 个测试点位
│     │  ├─ net/Probe.kt             DNS / TCP / TLS / HTTP / DoH 探测内核
│     │  ├─ net/Checker.kt           串行编排 + 配额解析
│     │  ├─ platform/NetEnvReader.kt 读当前网络环境（代理/VPN/私有DNS/DNS/hosts）
│     │  ├─ data/ReportBuilder.kt    HTML + CSV 生成（模板与桌面端一致）
│     │  ├─ data/ReportStore.kt      落盘与分享
│     │  └─ ui/                      主题与界面（NetCheckScreen / Theme）
│     └─ res/                        图标、主题、FileProvider 路径
│
├─ assets/icon/               图标源文件（README 与安卓图标共用）
├─ docs/                      详细文档（01–06）+ 图片
└─ .github/                   Issue 模板、PR 模板、CI
```

**关键约束：两端必须对齐。** 点位清单、评级阈值、错误措辞、报告区块，任何一项改动都要在
`windows/netcheck.ps1` 与 `android/.../net/*.kt` + `data/ReportBuilder.kt` 里同步改。
只改一端会在对比两份报告时立刻露馅。

---

## 二、怎么跑

### Windows 端

```powershell
# 直接跑，最接近用户的实际用法
powershell -ExecutionPolicy Bypass -File .\windows\netcheck.ps1

# 只测 GitHub 系
powershell -ExecutionPolicy Bypass -File .\windows\netcheck.ps1 -Quick

# 不自动打开浏览器（调试时更顺手）
powershell -ExecutionPolicy Bypass -File .\windows\netcheck.ps1 -NoBrowser
```

报告写到 `windows/reports/`。`reports/` 与 `*.html`、`*.csv` 都在 `.gitignore` 里，不会被误提交。

### 安卓端

```bash
cd android
gradle assembleRelease        # 产物：app/build/outputs/apk/release/app-release.apk
gradle test                   # JVM 单元测试（评级、报告、点位清单）
```

装到手机上：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

没有签名配置（`android/keystore.properties` 不存在）时会打出**未签名**的 release 包，
`adb install` 会拒绝，这时用 `gradle assembleDebug` 或按 [docs/05](docs/05-构建与发布.md)
生成一个自己的 keystore。

### 构建环境

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.7 |
| Android Gradle Plugin | 8.4.2 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0） |

工程目录含中文时 AGP 会拒绝构建，`android/gradle.properties` 里已加 `android.overridePathCheck=true`
（与 PDF 隐私打码工具同一处理）。纯英文路径下可以删掉那行。

---

## 三、改动点位（最常见的一种贡献）

Windows 端改 `netcheck.ps1` 开头的 `$Targets`：

```powershell
@{ Name = '某个站点'; Host = 'example.com'; Path = '/'; Desc = '备注' }
```

安卓端改 `net/Targets.kt` 的 `all` 列表，字段一一对应：

```kotlin
Target("某个站点", "example.com", "/", "备注", Target.Group.GITHUB)
```

`Group` 只影响图标分组与「快捷模式」筛选，取值有 `BASE / GITHUB / AI / CONTAINER`。

**提交点位时请一并说明**：这个点位为什么值得测（对应哪个真实场景）、
不加代理时你在本地测到的大致数值、以及它属于哪个分组。

---

## 四、提交前请自查

1. **两端都改了吗**：点位 / 阈值 / 措辞 / 报告区块，是否有哪一端落下了。
2. **Windows 端能跑通吗**：`-NoBrowser` 跑一次，确认报告能正常生成、没有 PowerShell 报错。
3. **安卓端能编出来吗**：`gradle assembleRelease` 通过；`gradle test` 通过。
4. **报告显示正常吗**：HTML 用浏览器打开看一遍，手机窄屏（375–430 逻辑宽）也看一眼。
5. **没有引入新的联网行为**：不许加统计、崩溃上报、更新检查、远程配置。这是本项目的底线。
6. **没有引入第三方网络库**：探测必须继续走原生 socket，否则四段计时就分不开了。
7. **版本号按规则加**：见下一节。

---

## 五、版本号怎么加

四段：**主.次.修订.补丁**。两端永远同号。

| 改了什么 | 怎么加 | 例子 |
| --- | --- | --- |
| 只改文档、注释、README | 补丁 +1 | 1.0.0.0 → 1.0.0.1 |
| 修 bug、调措辞、微调界面 | 修订 +1 | 1.0.0.0 → 1.0.1.0 |
| 加点位、加功能（用户能感知） | 次 +1 | 1.0.1.0 → 1.1.0.0 |
| 破坏性变更（报告结构、点位全部重排） | 主 +1 | 1.1.0.0 → 2.0.0.0 |

安卓端只改 `android/app/build.gradle.kts` 里的 `appVersionName` 一个字符串，
`versionCode` 会自动算出来（主*1000000 + 次*10000 + 修订*100 + 补丁），不需要手动同步。
Windows 端版本号写在 `netcheck.ps1` 头部的注释与报告页脚里，同步改。

改完在 [CHANGELOG.md](CHANGELOG.md) 顶部加一节，写清「修了什么、为什么」，
不要只写「优化体验」这种等于没写的话。

---

## 六、提 Issue / PR

- **缺陷**：用 [bug_report.yml](.github/ISSUE_TEMPLATE/bug_report.yml)，附报告截图或 `reports/*.csv` 里那几行。
  同一个域名在不同网络下结论完全不同，所以请注明：运营商 / 是否开加速器 / 是否在 VPN 下。
- **新点位**：用 [feature_request.yml](.github/ISSUE_TEMPLATE/feature_request.yml)，写清场景与预期。
- **PR**：说明改了哪一端、是否两端都改了、跑过哪些自检。CI 会跑一遍安卓构建与单元测试。

作者回复可能不快（一两个月看一次），没回不代表被否掉，也不代表已经合并。

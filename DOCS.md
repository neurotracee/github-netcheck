# 文档地图

先看哪份、什么情况看哪份，一张表说完。所有路径都相对于仓库根目录。

## 第一次来

| 想看什么 | 去哪 |
| --- | --- |
| 这东西是干嘛的、能测出什么 | [README.md](README.md) |
| 为什么值得开源、谁做的、署名怎么算 | [README.md](README.md) 的「这个项目是谁做的」「关于署名」两节 |
| 协议全文 | [LICENSE](LICENSE)（Apache-2.0） |
| 二次开发必须做到的三件事 | [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md) |
| 用了哪些第三方组件 | [NOTICE.md](NOTICE.md) |

## 要用起来

| 场景 | 去哪 |
| --- | --- |
| Windows 上怎么跑、参数怎么加、报告在哪 | [docs/01-使用说明-Windows.md](docs/01-使用说明-Windows.md) |
| 安卓怎么装、界面怎么操作、报告怎么导出 | [docs/02-使用说明-安卓.md](docs/02-使用说明-安卓.md) |
| 四段计时是什么意思、标签怎么判读 | [docs/03-检测方法与判读标准.md](docs/03-检测方法与判读标准.md) |
| 报告看不懂 / 某项一直红 / 和加速器一起用 | [docs/04-常见问题.md](docs/04-常见问题.md) |

## 要改代码

| 场景 | 去哪 |
| --- | --- |
| 目录结构、怎么跑、点位怎么加、版本号规则 | [CONTRIBUTING.md](CONTRIBUTING.md) |
| 环境要求、签名、产物核对、两平台发布 | [docs/05-构建与发布.md](docs/05-构建与发布.md) |
| 在网页上一步步发 Release | [docs/06-发布Release操作指南.md](docs/06-发布Release操作指南.md) |
| 这一版改了什么 | [CHANGELOG.md](CHANGELOG.md) |

## 代码入口（想直接看实现）

| 想知道 | 看哪个文件 |
| --- | --- |
| 测哪些域名 | `windows/netcheck.ps1` 的 `$Targets` / `android/.../net/Targets.kt` |
| 四段计时怎么算的 | `windows/netcheck.ps1` 的 `Invoke-Https` / `android/.../net/Probe.kt` |
| 评级阈值 | `windows/netcheck.ps1` 的 `Get-Verdict` / `android/.../model/Model.kt` 的 `Verdict` |
| 报告长什么样 | `windows/netcheck.ps1` 里的 HTML 模板 / `android/.../data/ReportBuilder.kt` |
| 安卓界面 | `android/.../ui/NetCheckScreen.kt`（配色在 `ui/Theme.kt`） |
| 当前网络环境怎么读 | `android/.../platform/NetEnvReader.kt` |

## 文档写作约定

- 面向使用者的文档用中文写，代码注释也用中文——这个项目的读者主要是中文用户。
- 涉及判断的地方写清「依据」，不要只给结论（比如为什么某个阈值定在 400ms）。
- 两端有差异的地方必须写明差异与原因，不要为了好看把差异抹平。
- 不写「优化体验」「提升性能」这类空话，写具体改了什么。

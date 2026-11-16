# 第三方组件声明 / Third-Party Notices

本项目（GitHub 网络自检，Windows 与安卓双端）整体以 **Apache License 2.0** 授权发布，
署名与出处要求见 **[ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md)**。

简而言之：二次开发、商用、闭源分发都可以，但**必须保留作者署名，并在程序里标注原项目地址**
`https://github.com/neurotracee/github-netcheck`（国内镜像：`https://gitee.com/bio234/github-netcheck`）。

下面按平台列出用到的第三方组件，各自的授权与版权归其作者所有。

---

## 一、Windows 端（不打包，随系统运行）

| 组件 | 版本 | 授权 | 版权 |
| --- | --- | --- | --- |
| Windows PowerShell（.NET Framework 4.x） | 系统自带 5.1+ | 随 Windows 分发（商业操作系统组件） | Copyright (c) Microsoft Corporation |
| .NET `System.Net.Sockets` / `System.Net.Security` | 随 .NET Framework | 随 Windows 分发 | Copyright (c) Microsoft Corporation |

Windows 端**没有打包任何第三方程序集**：脚本只用 PowerShell 自带能力，
TLS 证书校验回调是一段 `Add-Type` 内联的 C#（`NetCheckCertHelper`），编译在运行时完成，不落盘、不分发二进制。

## 二、安卓端（打包进 APK）

| 组件 | 版本 | 用途 | 授权 |
| --- | --- | --- | --- |
| AndroidX core-ktx | 1.13.1 | 基础框架、FileProvider | Apache License 2.0 |
| AndroidX lifecycle-runtime-ktx / lifecycle-viewmodel-compose | 2.8.4 | 生命周期与 ViewModel | Apache License 2.0 |
| AndroidX activity-compose | 1.9.1 | Compose 宿主 Activity | Apache License 2.0 |
| Jetpack Compose（ui / ui-graphics / foundation / material3 / material-icons-core） | BOM 2024.06.00 | 界面 | Apache License 2.0 |
| Kotlin 标准库 | 1.9.24 | 语言运行时 | Apache License 2.0 |
| Kotlin Coroutines（经 lifecycle 传递依赖） | 1.7.x / 1.8.x | 异步执行 | Apache License 2.0 |

仅用于测试、不进入 APK：

| 组件 | 用途 | 授权 |
| --- | --- | --- |
| JUnit 4.13.2 | 单元测试框架 | Eclipse Public License 1.0 |
| kotlin-test 1.9.24 | 断言与测试辅助 | Apache License 2.0 |

**明确没有使用的东西**（写在这里是为了让人一眼能确认）：

- 没有 OkHttp / Retrofit / Volley 等 HTTP 客户端——HTTP 请求是手写的，目的就是能把 DNS/TCP/TLS/TTFB 分开计时；
- 没有任何统计、崩溃上报、广告 SDK；
- 没有需要另行声明的 native 库，APK 里不含 `.so`，所以不挑 CPU 架构；
- 没有读取设备标识、位置、通讯录、短信、相机的权限。

## 三、测试目标域名

测试点位指向的都是第三方公共服务（GitHub、Hugging Face、Docker、百度、阿里公共 DNS）。
本项目只向它们发起普通的 HTTPS 请求，**不下载内容、不登录、不提交任何数据**；
这些服务与本项目没有隶属关系，名称与商标归各自所有者。

其中 DoH 对照查询使用阿里公共 DNS 的公开 JSON 接口：

```
https://dns.alidns.com/resolve?name=<域名>&type=A
```

安卓端为了在 DNS 被污染时仍能取到对照值，会**直连 223.5.5.5 这个 IP**，TLS 的 SNI 仍写 `dns.alidns.com`。

## 四、必须保留的署名（Apache-2.0 第 4 条 (c) 项，请勿删除）

```
指导作者：项目创作时为初中生 —— 只负责提需求、定判读标准、验收与反馈问题
开发    ：豆包工作任务的 Agent × DeepSeek-V4.1-Flash（Agent 由豆包工作任务提供，模型为 DeepSeek V4.1-Flash）
原项目  ：https://github.com/neurotracee/github-netcheck
镜像    ：https://gitee.com/bio234/github-netcheck
```

> 名词说明：**Agent 来自豆包工作任务，模型来自 DeepSeek（V4.1-Flash）**。两者都不是腾讯的产品，
> 本项目与腾讯没有任何关系。全部代码、构建、建仓与发布由 Agent 完成，指导作者只负责提需求与验收。

二次开发或商用必须保留以上署名，并在程序中标注原项目地址；改动过的版本须注明
「本程序基于该地址修改，非原作者发布」。详见 [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md)。

## 五、关于「分发时必须附带什么」

Apache-2.0 第 4 条 (a)(d) 项要求：分发本项目的产物（脚本、APK）时，必须一并提供

1. 本协议全文（[LICENSE](LICENSE)）；
2. 本 NOTICE 文件（或其中与本产物相关的署名段落）的可读副本。

本仓库即为完整的对应源码，任何人都可以重新构建出功能等价的产物：

```bash
# Windows：无需构建，直接用 windows/netcheck.ps1
# 安卓：
cd android
gradle assembleRelease
```

构建细节见 [docs/05-构建与发布.md](docs/05-构建与发布.md)。

## 六、名称与图标（重要）

程序名称「GitHub 网络自检 / NetCheck」与图标不在授权范围内（Apache-2.0 第 6 条商标条款）。二次发布请改名、换图标。

### 6.1 图标里包含 GitHub 的 Octocat

本项目的图标（`assets/icon/` 下的 png 与 ico，以及 Windows 端、安卓端的应用图标）中，猫头剪影来自 GitHub 的吉祥物
**Octocat**。**Octocat® 是 GitHub, Inc. 的注册商标**（GitHub, Inc. 现为 Microsoft 旗下公司），该形象另在美国版权局
做过作品登记。这些权利**不归本项目作者所有**，本项目**未获得 GitHub, Inc. 或 Microsoft 的任何授权、认可或背书**。

项目名中的「GitHub」仅用于说明本工具**检查的对象**（与 GitHub 各服务的连通性），属于指示性使用，不代表与 GitHub 官方有任何关联。

### 6.2 使用边界

1. **本项目自身仅限个人、非商业使用。** 作者不售卖、不上架应用商店、不接受商业授权，也不从本项目取得收入；
   赞赏仅为读者自愿小额支持，与授权无关。**禁止将本项目用于任何商业用途。**
2. **强烈建议不要使用本图标。** fork、二次开发、改名发布，或用于任何其他项目时，请自行替换为不含任何第三方商标元素的图标；
   不要直接沿用，也不要改动后继续使用。
3. **权利人可要求移除。** 若 GitHub, Inc. 或 Microsoft 认为使用不妥，通过仓库 Issue 提出后，本项目即更换图标并清理相关说明。
4. **本声明不构成授权。** 它只说明作者立场与使用边界，不能替代权利人的许可，也不能免除使用者自身的法律责任。

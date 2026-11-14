# 发布 Release 操作指南（网页上一步步点）

这份是给「不想敲命令行、想在浏览器里发版」的时候看的。命令行版见 [05-构建与发布.md](05-构建与发布.md) 第六节。

---

## 零、发版前先确认

- [ ] `CHANGELOG.md` 顶部已加本次一节，写清改了什么
- [ ] `android/app/build.gradle.kts` 里 `appVersionName` 已改（Windows 端注释同步改）
- [ ] 两端都跑过一次，结果正常
- [ ] APK 已构建、已核对（包名、版本号、权限、签名）
- [ ] Windows 的两个文件是最新的（`netcheck.ps1` + `一键网络自检.bat`）

产物清单：

```
NetCheck-<版本>.apk          安卓
NetCheck-Windows.zip         Windows（ps1 + bat + 使用说明.md）
```

---

## 一、打 Windows 包

1. 打开 `windows/` 目录，选中 `netcheck.ps1`、`一键网络自检.bat`、`使用说明.md` 三个文件。
2. 右键 → 压缩为 zip → 重命名成 `NetCheck-Windows.zip`。
3. **不要把 `reports/` 打进去**（那是你自己的测试结果，含出口 IP 与解析结果）。

## 二、打安卓包

1. 打开 `android/app/build/outputs/apk/release/`，取 `app-release.apk`。
2. 重命名成 `NetCheck-<版本>.apk`（例：`NetCheck-1.0.0.0.apk`）。
3. 记下 SHA-256（发版正文里要写）：

```powershell
Get-FileHash -Algorithm SHA256 .\NetCheck-1.0.0.0.apk
```

## 三、算校验值

Windows 端打包后的 zip 也算一份，一起写进 Release 正文：

```powershell
Get-FileHash -Algorithm SHA256 .\NetCheck-Windows.zip
```

---

## 四、在 GitHub 上发

1. 打开仓库页面 → 右侧 `Releases` → `Create a new release`（或 `Draft a new release`）。
2. **Choose a tag**：填 `v1.0.0.0`，点 `Create new tag on publish`（分支选 `main`）。
3. **Release title**：`v1.0.0.0`
4. **描述**按这个结构写：

```markdown
首个公开版本。Windows 脚本 + 安卓 APK，两端同一套点位、同一套评级、同一套报告样式。

### Windows
- 14 个点位，DNS / TCP / TLS / HTTP 四段分别计时
- DoH 对照，识别 hosts 改写与 DNS 污染
- 报告自动打开，同时存 HTML + CSV

### 安卓
- 通用 APK（Android 8.0+，不挑 CPU 架构）
- 逐条可展开细节；HTML 报告与 CSV 经系统分享面板导出
- 只有两个权限：INTERNET / ACCESS_NETWORK_STATE

### 校验
SHA-256（NetCheck-1.0.0.0.apk）：<填>
SHA-256（NetCheck-Windows.zip）：<填>

### 已知限制
- 只测 IPv4、固定 TLS 1.2；不测带宽
- 证书校验被忽略（只测连通性）
- 安卓端无法枚举本机端口，该项换为 VPN / 私有 DNS 显示
```

5. 把两个文件拖进 `Attach binaries` 区域。
6. 点 `Publish release`。

## 五、在 Gitee 上发（镜像）

Gitee 的入口叫「发行版」：

1. 仓库页 → 顶部 `发行版`（或右侧 `+` → `新建发行版`）。
2. **版本号**：`v1.0.0.0`（与 GitHub 的 tag 一致）。
3. **发行标题**：同 GitHub。
4. **发行描述**：把 GitHub 那份正文整段粘过来（内容完全一致）。
5. **附件**：上传同样两个文件。
6. 点 `发布`。

> Gitee 的附件 CDN 有时会把 `.apk` 的 Content-Type 标成 `application/zip`，
> 用浏览器下载可能存成 `xxx.apk.zip`。README 里已提示「末尾多了 .zip 就去掉」。
> 本项目的应用内**没有**自动更新，所以不会遇到「应用内下载装不上」的问题。

---

## 六、发完之后要做的三件事

1. **核对两个平台的附件**：各下载一次，比对 SHA-256 与本地一致。
2. **更新 README 的徽章**：`latest release` 徽章会自动跟着最新 Release 变，不用改。
3. **GitHub 与 Gitee 的 tag 对齐**：两边都应有 `v1.0.0.0` 这个 tag，且指向同一次提交。

---

## 七、如果发错了

- **附件错了**：编辑 Release → 删除旧附件 → 上传新的（Gitee 叫「编辑发行版」）。
- **描述错了**：直接编辑，不需要重新发。
- **版本号错了**：不要覆盖同一个 tag（会让人以为内容没变）。改版本号，重发一个新 tag，
  旧 Release 标成 `Pre-release` 或直接删除并说明原因。
- **已经有人下载了**：在 Release 正文顶部加一行醒目说明，写清「哪个文件有问题、请下载哪个」。
  不要指望用户会看 commit 记录。

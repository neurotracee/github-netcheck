## 这个 PR 做了什么

<!-- 一句话说清。修 bug 请附 Issue 编号 -->

## 改的是哪一端

- [ ] Windows（`windows/netcheck.ps1`）
- [ ] 安卓（`android/`）
- [ ] 两端都改（点位 / 阈值 / 措辞 / 报告结构这类改动必须两端一起改）
- [ ] 只改文档

## 自检清单

- [ ] Windows 端跑过 `powershell -ExecutionPolicy Bypass -File .\windows\netcheck.ps1 -NoBrowser`，报告正常生成
- [ ] 安卓端 `gradle assembleRelease` 通过
- [ ] 安卓端 `gradle test` 通过（中文路径下会误报 ClassNotFoundException，请复制到英文路径再跑，见 docs/05）
- [ ] 报告在窄屏（375–430 逻辑宽）下没有错位、溢出
- [ ] 没有引入第三方网络库（探测必须继续走原生 socket）
- [ ] 没有引入统计 / 崩溃上报 / 更新检查
- [ ] 没有新增权限
- [ ] 版本号按 CONTRIBUTING 第五节加了，`CHANGELOG.md` 顶部已补一节
- [ ] 涉及使用者能看到的行为变化，文档（README / docs/01–04）已同步

## 为什么这么改

<!-- 如果有「看起来可以更简单但没这么做」的地方，说明原因，避免以后被改回去 -->

## 截图 / 前后对比（可选）

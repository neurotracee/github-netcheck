# Windows 端使用说明

Windows 端是一段 PowerShell 脚本，不安装、不打包、不留服务，用完关掉窗口就结束。

## 文件

| 文件 | 作用 |
| --- | --- |
| `一键网络自检.bat` | 双击这个就行。它只是带 `-ExecutionPolicy Bypass` 去调下面的 ps1 |
| `netcheck.ps1` | 全部逻辑：点位清单、四段计时、DoH 对照、报告生成 |

两个文件放在同一个文件夹里，不要分开。`reports\` 会在第一次运行时自动创建。

## 怎么跑

1. 双击 `一键网络自检.bat`。
2. 命令行窗口会先打印环境信息（系统代理是否开启、hosts 有几条本地映射、本机 443 端口有没有程序在监听）。
3. 然后逐个测试 14 个目标，每行显示「[3/14] 名称 (域名)」，后面接结果。
4. 测完打印汇总（可连通几个、平均建连、最快、最慢、不通的有哪些）。
5. 自动用默认浏览器打开报告，报告同时存一份到 `reports\netcheck-<时间戳>.html`，并更新 `reports\latest.html`。

耗时：正常网络 15～25 秒，网络很差时接近 40 秒（14 个点位 × 每个最多 3 秒）。

## 参数

```powershell
# 只测 GitHub 系（跳过 Hugging Face 与 Docker 两项）
powershell -ExecutionPolicy Bypass -File .\netcheck.ps1 -Quick

# 测完不自动打开浏览器
powershell -ExecutionPolicy Bypass -File .\netcheck.ps1 -NoBrowser

# 两个一起用
powershell -ExecutionPolicy Bypass -File .\netcheck.ps1 -Quick -NoBrowser
```

`-Quick` 保留的是「域名里带 github 的」加上百度对照站，共 11 个点位——这是脚本里写死的筛选条件，
和安卓端「只测 GitHub 系」用的是同一条规则。

## 报告在哪

```
netcheck.ps1 所在目录\
└─ reports\
   ├─ latest.html                          永远是最新一次
   ├─ netcheck-20261114-181527.html        每次一份，按时间戳命名
   └─ netcheck-20261114-181527.csv         同名 CSV，方便用 Excel 对比多次结果
```

CSV 的列序是 `Name,Host,Ip,DohIp,DnsMs,TcpMs,TlsMs,TtfbMs,Status,Ok,Label`，
安卓端导出的 CSV 与它完全一致，两边可以直接拼在一张表里做对比。

## 报告怎么看

- 顶部四个卡片：可连通数、平均建连延迟、最快、异常目标数。要看趋势先看这四个。
- 环境小圆标签：当前是否有系统代理、hosts 有没有本地映射、本机 443 有没有被监听、DNS 服务器、API 配额。
- 逐条结果：左侧竖条颜色=评级；中间四列是 DNS / TCP / TLS / HTTP 四段耗时；右侧标签+细进度条+用途说明。
- 最后是「诊断与建议」：只列不通的点位，并给一句针对该域名的解释。

评级与阈值见 [03-检测方法与判读标准.md](03-检测方法与判读标准.md)。

## 想换测试目标

用记事本打开 `netcheck.ps1`，找到开头这段：

```powershell
$Targets = @(
    @{ Name = '百度（对照基准）';    Host = 'www.baidu.com';  Path = '/'; Desc = '先确认本地宽带本身正常' },
    ...
)
```

照格式加一行即可：

```powershell
@{ Name = '某个站点'; Host = 'example.com'; Path = '/'; Desc = '备注' }
```

`Path` 必须是对方真实存在的路径（很多站点根路径返回 403，不代表不通）。
加完记得在安卓端的 `net/Targets.kt` 里同步加一行，否则两端报告的点位数会不一致。

## 出事怎么办

| 现象 | 处理 |
| --- | --- |
| 提示「无法加载文件……禁止运行脚本」 | 用 bat 启动；或临时放开：`Set-ExecutionPolicy -Scope Process Bypass` |
| 窗口一闪而过 | 在 PowerShell 里手动跑 `powershell -ExecutionPolicy Bypass -File .\netcheck.ps1 -NoBrowser`，看完整报错 |
| 报告没自动打开 | 手动双击 `reports\latest.html` |
| 所有点位都不通 | 先看第一行百度是否也不通——百度也不通说明是本地网络/DNS 问题，与墙无关 |
| PowerShell 7 上跑 | 可以跑；`Get-NetTCPConnection`、`Get-DnsClientServerAddress` 在 7 上同样存在，只是输出格式略有差异 |

# ============================================================
#  netcheck.ps1 —— GitHub 网络自检（Windows 端）
#  纯 PowerShell 实现，不安装任何东西，不联网上传任何数据
#  测四项：DNS 解析 / TCP 建连 / TLS 握手 / HTTPS 首字节
#  输出：控制台结果 + reports 目录下的 HTML 可视化报告
#
#  版本：1.1.3.0
#  开源协议：Apache License 2.0
#  原项目地址：https://github.com/neurotracee/github-netcheck
#  镜像（Gitee）：https://gitee.com/bio234/github-netcheck
#  指导作者：项目创作时为初中生；需求、判读规则与验收测试由本人完成
#  开发：豆包工作任务的 Agent × DeepSeek-V4.1-Flash
#
#  二次开发 / 商用三条硬要求：
#   ① 保留上面「指导作者：项目创作时为初中生」的署名，不得删改
#   ② 在你的程序里标注原项目地址，让使用者看得见（关于页 / 页脚 / 帮助菜单均可）
#   ③ 改动过的版本请注明「本程序基于该地址修改，非原作者发布」
# ============================================================

param(
    [switch]$NoBrowser,   # 加这个参数则不自动打开报告
    [switch]$Quick,       # 加这个参数只测 GitHub 系（少测几个，快一点）
    [switch]$About,       # 只弹「关于与开源信息」窗口，不测
    [switch]$Settings,    # 只弹设置窗口（报告目录 / 自动打开 / 超时），不测
    [switch]$CheckUpdate  # 只查最新版本号，不测
)

$script:AppVersion = '1.1.3.0'
$script:ProjectUrl = 'https://github.com/neurotracee/github-netcheck'
$script:MirrorUrl  = 'https://gitee.com/bio234/github-netcheck'

$ErrorActionPreference = 'Continue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:TimeoutMs = 3000
$script:StartAt   = Get-Date

# TLS 证书校验回调：必须是真实 .NET 委托，用 PowerShell 脚本块会在异步线程上崩溃
if (-not ('NetCheckCertHelper' -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;
public static class NetCheckCertHelper {
    public static bool Trust(object sender, X509Certificate certificate, X509Chain chain, SslPolicyErrors errors) { return true; }
}
"@
}
$script:CertCallback = [System.Net.Security.RemoteCertificateValidationCallback]([NetCheckCertHelper].GetMethod('Trust').CreateDelegate([System.Net.Security.RemoteCertificateValidationCallback]))

try { $ScriptDir = $PSScriptRoot } catch { $ScriptDir = $null }
if ([string]::IsNullOrEmpty($ScriptDir)) {
    if ($MyInvocation.MyCommand.Path) { $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
    else { $ScriptDir = (Get-Location).Path }
}
$ReportDir = Join-Path $ScriptDir 'reports'
if (-not (Test-Path -LiteralPath $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir | Out-Null }

# ---------- 本地设置：settings.json（不联网、不写注册表、跟着脚本走） ----------
$script:SettingsFile = Join-Path $ScriptDir 'settings.json'
$script:AutoOpen = $false
$script:AutoCheckUpdate = $true
$script:AskBeforeReport = $true
$script:SaveReport = $true
$script:UpdateSource = 'auto'   # auto（默认 Gitee，只有明确迹象在境外才 GitHub）/ gitee / github

$cfg = $null
if (Test-Path -LiteralPath $script:SettingsFile) {
    try { $cfg = (Get-Content -LiteralPath $script:SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { $cfg = $null }
}
if ($cfg) {
    if ($cfg.PSObject.Properties['ReportDir'] -and $cfg.ReportDir) {
        if ([IO.Path]::IsPathRooted([string]$cfg.ReportDir)) { $ReportDir = [string]$cfg.ReportDir }
        else { $ReportDir = Join-Path $ScriptDir ([string]$cfg.ReportDir) }
    }
    if ($cfg.PSObject.Properties['AutoOpen']) { $script:AutoOpen = [bool]$cfg.AutoOpen }
    if ($cfg.PSObject.Properties['AutoCheckUpdate']) { $script:AutoCheckUpdate = [bool]$cfg.AutoCheckUpdate }
    if ($cfg.PSObject.Properties['AskBeforeReport']) { $script:AskBeforeReport = [bool]$cfg.AskBeforeReport }
    if ($cfg.PSObject.Properties['SaveReport']) { $script:SaveReport = [bool]$cfg.SaveReport }
    if ($cfg.PSObject.Properties['UpdateSource'] -and $cfg.UpdateSource) { $script:UpdateSource = [string]$cfg.UpdateSource }
    if ($cfg.PSObject.Properties['TimeoutMs'] -and [int]$cfg.TimeoutMs -gt 0) { $script:TimeoutMs = [int]$cfg.TimeoutMs }
}
if (-not (Test-Path -LiteralPath $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null }

function Save-LocalSettings {
    param([string]$Dir, [bool]$AutoOpen, [int]$TimeoutMs, [bool]$AutoCheckUpdate = $true, [bool]$AskBeforeReport = $true, [bool]$SaveReport = $true, [string]$UpdateSource = 'auto')
    $obj = [ordered]@{
        ReportDir       = $Dir
        AutoOpen        = $AutoOpen
        TimeoutMs       = $TimeoutMs
        AutoCheckUpdate = $AutoCheckUpdate
        AskBeforeReport = $AskBeforeReport
        SaveReport      = $SaveReport
        UpdateSource    = $UpdateSource
    }
    ($obj | ConvertTo-Json) | Set-Content -LiteralPath $script:SettingsFile -Encoding UTF8
}

# ---------- 「关于与开源信息」窗口：和安卓端的关于页同一套内容 ----------
function Show-AboutWindow {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '关于与开源信息 · GitHub 网络自检'
    $form.Size = New-Object System.Drawing.Size(620, 820)
    $form.StartPosition = 'CenterScreen'
    $form.BackColor = [System.Drawing.Color]::FromArgb(244, 246, 248)
    $form.Font = $font

    $title = New-Object System.Windows.Forms.Label
    $title.Text = 'GitHub 网络自检　v' + $script:AppVersion
    $title.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 15, [System.Drawing.FontStyle]::Bold)
    $title.AutoSize = $true
    $title.Location = New-Object System.Drawing.Point(24, 20)
    $form.Controls.Add($title)

    $sub = New-Object System.Windows.Forms.Label
    $sub.Text = '纯 PowerShell 实现的 GitHub 网络自检：DNS / TCP / TLS / HTTP 四段分别计时'
    $sub.AutoSize = $true
    $sub.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $sub.Location = New-Object System.Drawing.Point(26, 58)
    $form.Controls.Add($sub)

    $text = New-Object System.Windows.Forms.TextBox
    $text.Multiline = $true
    $text.ReadOnly = $true
    $text.ScrollBars = 'Vertical'
    $text.BorderStyle = 'FixedSingle'
    $text.Location = New-Object System.Drawing.Point(24, 92)
    $text.Size = New-Object System.Drawing.Size(556, 360)
    $text.BackColor = [System.Drawing.Color]::White
    $lines = @(
        '指导作者　　　项目创作时为初中生。',
        '　　　　　　　只负责提需求、定判读标准、验收与反馈问题；不写代码。',
        '',
        '开发　　　　　豆包工作任务的 Agent × DeepSeek-V4.1-Flash',
        '　　　　　　　（Agent 由豆包工作任务提供，模型是 DeepSeek 的 V4.1-Flash）',
        '　　　　　　　全部代码、构建、建仓与发布都由 Agent 完成，与腾讯无关。',
        '',
        '开源协议　　　Apache-2.0',
        '',
        '原项目地址　　' + $script:ProjectUrl,
        '镜像（Gitee）　' + $script:MirrorUrl,
        '',
        '隐私　　　　　不安装后台服务，不写注册表，不上传任何数据。',
        '　　　　　　　联网只用在本机自检本身；报告写在本机 reports 目录。',
        '　　　　　　　下面「检查更新」只读一行公开的版本号，不发送本机信息。',
        '',
        '二次开发 / 商用：三条硬要求',
        '　① 保留「指导作者：项目创作时为初中生」的署名，不得删改。',
        '　② 在你的程序里标注原项目地址，让使用者看得见。',
        '　③ 改动过的版本请注明「本程序基于该地址修改，非原作者发布」。',
        '',
        '维护节奏：平时没有充足时间，一两个月上来看一两次，回复不会很快。',
        '　　　　　代码全部公开，修不动的地方欢迎自己改、自己接着用。',
        '　　　　　协议允许商用，不用付钱也不用问我，照上面三条做就行。',
        '',
        '如果这个工具帮到了你：给仓库点个 Star 就是最好的支持。'
    )
    $text.Text = ($lines -join "`r`n")
    $text.SelectionStart = 0
    $text.SelectionLength = 0
    $form.Controls.Add($text)

    # 赞赏：完全自愿，不打赏也一模一样用；有赞赏码图片就直接显示，没有就只留一句引导
    $tipTitle = New-Object System.Windows.Forms.Label
    $tipTitle.Text = '赞赏（完全自愿）'
    $tipTitle.AutoSize = $true
    $tipTitle.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 10, [System.Drawing.FontStyle]::Bold)
    $tipTitle.Location = New-Object System.Drawing.Point(24, 466)
    $form.Controls.Add($tipTitle)

    $tipText = New-Object System.Windows.Forms.Label
    $tipText.Text = '要是这个小工具帮你省了点时间，愿意的话可以请我喝杯奶茶——不打赏也一模一样用，功能一个都不少。'
    $tipText.AutoSize = $true
    $tipText.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $tipText.Location = New-Object System.Drawing.Point(26, 492)
    $form.Controls.Add($tipText)

    $tipPic = Join-Path $ScriptDir '赞赏码.png'
    if (Test-Path -LiteralPath $tipPic) {
        $img = New-Object System.Windows.Forms.PictureBox
        $img.Size = New-Object System.Drawing.Size(180, 180)
        $img.Location = New-Object System.Drawing.Point(26, 518)
        $img.SizeMode = 'Zoom'
        $img.BorderStyle = 'FixedSingle'
        try { $img.Image = [System.Drawing.Image]::FromFile($tipPic) } catch { }
        $form.Controls.Add($img)

        $tipCap = New-Object System.Windows.Forms.Label
        $tipCap.Text = '微信赞赏码 · 长按/截图后扫码即可'
        $tipCap.AutoSize = $true
        $tipCap.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
        $tipCap.Location = New-Object System.Drawing.Point(216, 524)
        $form.Controls.Add($tipCap)
    } else {
        $tipCap = New-Object System.Windows.Forms.Label
        $tipCap.Text = '赞赏码在仓库 README 的「赞赏」一节里。'
        $tipCap.AutoSize = $true
        $tipCap.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
        $tipCap.Location = New-Object System.Drawing.Point(26, 520)
        $form.Controls.Add($tipCap)
    }

    $btn = New-Object System.Windows.Forms.Button
    $btn.Text = '知道了'
    $btn.Size = New-Object System.Drawing.Size(96, 32)
    $btn.Location = New-Object System.Drawing.Point(484, 528)
    $btn.BackColor = [System.Drawing.Color]::FromArgb(59, 130, 196)
    $btn.ForeColor = [System.Drawing.Color]::White
    $btn.FlatStyle = 'Flat'
    $btn.Add_Click({ $form.Close() })
    $form.Controls.Add($btn)

    $open = New-Object System.Windows.Forms.Button
    $open.Text = '打开项目页'
    $open.Size = New-Object System.Drawing.Size(104, 32)
    $open.Location = New-Object System.Drawing.Point(370, 528)
    $open.FlatStyle = 'Flat'
    $open.Add_Click({ Start-Process $script:ProjectUrl })
    $form.Controls.Add($open)

    $chkUpd = New-Object System.Windows.Forms.Button
    $chkUpd.Text = '检查更新'
    $chkUpd.Location = New-Object System.Drawing.Point(254, 528)
    $chkUpd.Size = New-Object System.Drawing.Size(104, 32)
    $chkUpd.FlatStyle = 'Flat'
    $chkUpd.Add_Click({
        $chkUpd.Enabled = $false
        $chkUpd.Text = '检查中…'
        $u = Invoke-UpdateCheck
        $text.AppendText("`r`n" + '—— 检查更新：' + $u.Text)
        $chkUpd.Text = '检查更新'
        $chkUpd.Enabled = $true
    })
    $form.Controls.Add($chkUpd)

    $form.ShowDialog() | Out-Null
}

# ---------- 设置窗口：报告存哪、自检完要不要自动打开、超时 ----------
function Show-SettingsWindow {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '设置 · GitHub 网络自检'
    $form.Size = New-Object System.Drawing.Size(600, 470)
    $form.StartPosition = 'CenterScreen'
    $form.BackColor = [System.Drawing.Color]::FromArgb(244, 246, 248)
    $form.Font = $font

    $t = New-Object System.Windows.Forms.Label
    $t.Text = '设置'
    $t.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 15, [System.Drawing.FontStyle]::Bold)
    $t.AutoSize = $true
    $t.Location = New-Object System.Drawing.Point(24, 18)
    $form.Controls.Add($t)

    $l1 = New-Object System.Windows.Forms.Label
    $l1.Text = '报告保存位置'
    $l1.AutoSize = $true
    $l1.Location = New-Object System.Drawing.Point(26, 68)
    $form.Controls.Add($l1)

    $box = New-Object System.Windows.Forms.TextBox
    $box.Location = New-Object System.Drawing.Point(26, 90)
    $box.Size = New-Object System.Drawing.Size(440, 26)
    $box.Text = $ReportDir
    $form.Controls.Add($box)

    $browse = New-Object System.Windows.Forms.Button
    $browse.Text = '浏览…'
    $browse.Size = New-Object System.Drawing.Size(84, 27)
    $browse.Location = New-Object System.Drawing.Point(474, 89)
    $browse.FlatStyle = 'Flat'
    $browse.Add_Click({
        $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
        $dlg.SelectedPath = $box.Text
        if ($dlg.ShowDialog() -eq 'OK') { $box.Text = $dlg.SelectedPath }
    })
    $form.Controls.Add($browse)

    $chkAsk = New-Object System.Windows.Forms.CheckBox
    $chkAsk.Text = '每次自检结束后先问我：要不要出报告、要不要打开'
    $chkAsk.AutoSize = $true
    $chkAsk.Checked = $script:AskBeforeReport
    $chkAsk.Location = New-Object System.Drawing.Point(26, 132)
    $form.Controls.Add($chkAsk)

    $chk = New-Object System.Windows.Forms.CheckBox
    $chk.Text = '（不询问时）生成报告后自动用浏览器打开'
    $chk.AutoSize = $true
    $chk.Checked = $script:AutoOpen
    $chk.Location = New-Object System.Drawing.Point(44, 158)
    $form.Controls.Add($chk)

    $l2 = New-Object System.Windows.Forms.Label
    $l2.Text = '单段超时（毫秒，默认 3000）'
    $l2.AutoSize = $true
    $l2.Location = New-Object System.Drawing.Point(26, 194)
    $form.Controls.Add($l2)

    $num = New-Object System.Windows.Forms.NumericUpDown
    $num.Minimum = 500
    $num.Maximum = 20000
    $num.Increment = 500
    $num.Value = $script:TimeoutMs
    $num.Location = New-Object System.Drawing.Point(240, 192)
    $num.Size = New-Object System.Drawing.Size(90, 26)
    $form.Controls.Add($num)

    $hint = New-Object System.Windows.Forms.Label
    $hint.Text = '设置在 settings.json 里，跟着脚本走，不写注册表也不联网。'
    $hint.AutoSize = $true
    $hint.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $hint.Location = New-Object System.Drawing.Point(26, 236)
    $form.Controls.Add($hint)

    # 自动检查更新：和安卓端一样，启动时静默查一次版本号，只在这里提示一行，不弹窗、不下载
    $chkUpd = New-Object System.Windows.Forms.CheckBox
    $chkUpd.Text = '启动时自动检查更新（只读一行版本号，不下载、不上传）'
    $chkUpd.AutoSize = $true
    $chkUpd.Checked = $script:AutoCheckUpdate
    $chkUpd.Location = New-Object System.Drawing.Point(26, 266)
    $form.Controls.Add($chkUpd)

    $updHint = New-Object System.Windows.Forms.Label
    $updHint.Text = '检查更新只连 Gitee 与 GitHub 两个仓库；连不上就跳过，不影响自检。'
    $updHint.AutoSize = $true
    $updHint.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $updHint.Location = New-Object System.Drawing.Point(44, 290)
    $form.Controls.Add($updHint)

    # 更新源：默认 Gitee，只有明确迹象说明在境外才走 GitHub
    $l3 = New-Object System.Windows.Forms.Label
    $l3.Text = '更新源'
    $l3.AutoSize = $true
    $l3.Location = New-Object System.Drawing.Point(26, 322)
    $form.Controls.Add($l3)

    $cboSrc = New-Object System.Windows.Forms.ComboBox
    $cboSrc.DropDownStyle = 'DropDownList'
    $cboSrc.Size = New-Object System.Drawing.Size(240, 26)
    $cboSrc.Location = New-Object System.Drawing.Point(86, 318)
    [void]$cboSrc.Items.Add('自动（默认 Gitee）')
    [void]$cboSrc.Items.Add('只用 Gitee')
    [void]$cboSrc.Items.Add('只用 GitHub')
    switch ($script:UpdateSource) {
        'gitee'  { $cboSrc.SelectedIndex = 1 }
        'github' { $cboSrc.SelectedIndex = 2 }
        default  { $cboSrc.SelectedIndex = 0 }
    }
    $form.Controls.Add($cboSrc)

    $srcHint = New-Object System.Windows.Forms.Label
    $srcHint.Text = ('默认 Gitee；语言非中文且时区不是 UTC+8 才自动改走 GitHub。当前实际使用：' +
        $(if (Resolve-UpdateSource -eq 'gitee') { 'Gitee' } else { 'GitHub' }))
    $srcHint.AutoSize = $true
    $srcHint.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $srcHint.Location = New-Object System.Drawing.Point(86, 346)
    $form.Controls.Add($srcHint)

    $save = New-Object System.Windows.Forms.Button
    $save.Text = '保存'
    $save.Size = New-Object System.Drawing.Size(96, 32)
    $save.Location = New-Object System.Drawing.Point(382, 380)
    $save.BackColor = [System.Drawing.Color]::FromArgb(18, 161, 80)
    $save.ForeColor = [System.Drawing.Color]::White
    $save.FlatStyle = 'Flat'
    $save.Add_Click({
        $srcVal = 'auto'
        if ($cboSrc.SelectedIndex -eq 1) { $srcVal = 'gitee' }
        elseif ($cboSrc.SelectedIndex -eq 2) { $srcVal = 'github' }
        Save-LocalSettings -Dir $box.Text -AutoOpen $chk.Checked -TimeoutMs ([int]$num.Value) -AutoCheckUpdate $chkUpd.Checked -AskBeforeReport $chkAsk.Checked -SaveReport $script:SaveReport -UpdateSource $srcVal
        [System.Windows.Forms.MessageBox]::Show('已保存。下次运行按新设置来。', '设置')
        $form.Close()
    })
    $form.Controls.Add($save)

    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = '取消'
    $cancel.Size = New-Object System.Drawing.Size(96, 32)
    $cancel.Location = New-Object System.Drawing.Point(486, 380)
    $cancel.FlatStyle = 'Flat'
    $cancel.Add_Click({ $form.Close() })
    $form.Controls.Add($cancel)

    $form.ShowDialog() | Out-Null
}



# ---------- 测试目标清单（对齐瓦特工具箱的加速项） ----------
$Targets = @(
    @{ Name = '百度（对照基准）';    Host = 'www.baidu.com';                 Path = '/'; Desc = '先确认本地宽带本身正常' },
    @{ Name = 'GitHub API 接口';     Host = 'api.github.com';                Path = '/rate_limit'; Desc = 'Agent 查仓库 / 拉信息' },
    @{ Name = 'GitHub Raw 原始文件'; Host = 'raw.githubusercontent.com';     Path = '/github/gitignore/main/README.md'; Desc = 'Agent 直读文件内容' },
    @{ Name = 'GitHub Git 传输';     Host = 'github.com';                    Path = '/git/git.git/info/refs?service=git-upload-pack'; Desc = 'git clone / pull 通道' },
    @{ Name = 'GitHub 仓库打包';     Host = 'codeload.github.com';           Path = '/'; Desc = '仓库 zip / tar 下载' },
    @{ Name = 'GitHub 主站';         Host = 'github.com';                    Path = '/'; Desc = '网页 / 仓库首页' },
    @{ Name = 'GitHub 静态资源';     Host = 'github.githubassets.com';       Path = '/'; Desc = '图标 / 页面资源' },
    @{ Name = 'GitHub 头像 CDN';     Host = 'avatars.githubusercontent.com'; Path = '/'; Desc = '头像 / 图片' },
    @{ Name = 'GitHub Gist';         Host = 'gist.githubusercontent.com';    Path = '/'; Desc = '代码片段 / 配置' },
    @{ Name = 'GitHub Releases';     Host = 'objects.githubusercontent.com'; Path = '/'; Desc = '发行包下载' },
    @{ Name = 'GitHub Pages';        Host = 'github.io';                     Path = '/'; Desc = 'Pages 站点' },
    @{ Name = 'Hugging Face';        Host = 'huggingface.co';                Path = '/'; Desc = '模型 / 数据集' },
    @{ Name = 'Docker 镜像仓库';     Host = 'registry-1.docker.io';          Path = '/v2/'; Desc = 'docker pull' },
    @{ Name = 'Docker Hub 官网';     Host = 'hub.docker.com';                Path = '/'; Desc = '镜像页 / 登录' }
)
if ($Quick) {
    $Targets = $Targets | Where-Object { $_.Host -match 'github' -or $_.Host -eq 'www.baidu.com' }
}

# ---------- 工具函数 ----------

function Test-Dns {
    param([string]$HostName, [int]$TimeoutMs = 2500)
    $r = [pscustomobject]@{ Ok = $false; Ms = 0; Ip = ''; Error = '' }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $task = [System.Net.Dns]::GetHostAddressesAsync($HostName)
        if (-not $task.Wait($TimeoutMs)) { throw 'DNS 解析超时' }
        $ips = @($task.Result | Where-Object { $_.AddressFamily -eq 'InterNetwork' })
        if ($ips.Count -eq 0) { throw 'DNS 未返回 IPv4 地址' }
        $r.Ip = $ips[0].IPAddressToString
        $r.Ok = $true
    } catch {
        $r.Error = $_.Exception.Message
    }
    $sw.Stop()
    $r.Ms = [int]$sw.ElapsedMilliseconds
    return $r
}

# 一次连接同时测出 TCP / TLS / HTTPS 三段耗时
function Invoke-Https {
    param([string]$HostName, [string]$Path = '/', [int]$Port = 443, [int]$TimeoutMs = 2500, [string]$Accept = '*/*')
    $r = [pscustomobject]@{
        Ok = $false; TcpOk = $false; TcpMs = 0; TlsMs = 0; Ttfb = 0; TotalMs = 0
        Status = 0; Error = ''; Body = ''
    }
    $tcp = New-Object System.Net.Sockets.TcpClient
    $swAll = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $swTcp = [System.Diagnostics.Stopwatch]::StartNew()
        $ar = $tcp.BeginConnect($HostName, $Port, $null, $null)
        if (-not $ar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { throw 'TCP 连接超时' }
        $tcp.EndConnect($ar)
        $swTcp.Stop()
        $r.TcpMs = [int]$swTcp.ElapsedMilliseconds
        $r.TcpOk = $true

        $stream = $tcp.GetStream()
        $ssl = New-Object System.Net.Security.SslStream($stream, $false, $script:CertCallback)
        $certs = New-Object System.Security.Cryptography.X509Certificates.X509CertificateCollection

        $swTls = [System.Diagnostics.Stopwatch]::StartNew()
        $arTls = $ssl.BeginAuthenticateAsClient($HostName, $certs, [System.Security.Authentication.SslProtocols]::Tls12, $false, $null, $null)
        if (-not $arTls.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { throw 'TLS 握手超时' }
        $ssl.EndAuthenticateAsClient($arTls)
        $swTls.Stop()
        $r.TlsMs = [int]$swTls.ElapsedMilliseconds

        $ssl.WriteTimeout = $TimeoutMs
        $ssl.ReadTimeout  = $TimeoutMs
        $req = "GET $Path HTTP/1.1`r`nHost: $HostName`r`nUser-Agent: NetCheck/1.0`r`nAccept: $Accept`r`nConnection: close`r`n`r`n"
        $reqBytes = [System.Text.Encoding]::ASCII.GetBytes($req)

        $swTtfb = [System.Diagnostics.Stopwatch]::StartNew()
        $ssl.Write($reqBytes, 0, $reqBytes.Length)
        $buf = New-Object byte[] 4096
        $n = $ssl.Read($buf, 0, $buf.Length)
        $swTtfb.Stop()
        $r.Ttfb = [int]$swTtfb.ElapsedMilliseconds

        $sb = New-Object System.Text.StringBuilder
        if ($n -gt 0) { [void]$sb.Append([System.Text.Encoding]::UTF8.GetString($buf, 0, $n)) }
        $total = $n
        while ($total -lt 8192 -and $n -gt 0) {
            try { $n = $ssl.Read($buf, 0, $buf.Length) } catch { break }
            if ($n -le 0) { break }
            [void]$sb.Append([System.Text.Encoding]::UTF8.GetString($buf, 0, $n))
            $total += $n
        }

        $text = $sb.ToString()
        $firstLine = ($text -split "`r`n")[0]
        if ($firstLine -match 'HTTP/\d(?:\.\d)?\s+(\d{3})') { $r.Status = [int]$Matches[1] }
        $idx = $text.IndexOf("`r`n`r`n")
        if ($idx -ge 0) { $r.Body = $text.Substring($idx + 4) }
        $r.Ok = ($r.Status -gt 0)
        if (-not $r.Ok) { $r.Error = '未收到合法 HTTP 响应' }
    } catch {
        $r.Error = ConvertTo-FriendlyError $_.Exception.Message
    }
    $swAll.Stop()
    $r.TotalMs = [int]$swAll.ElapsedMilliseconds
    try { $tcp.Close() } catch { }
    return $r
}

function ConvertTo-FriendlyError {
    param([string]$Msg)
    if ([string]::IsNullOrWhiteSpace($Msg)) { return '未知错误' }
    if ($Msg -match '超时|timed out|timeout') { return '超时（对方无响应）' }
    if ($Msg -match '强迫关闭|forcibly|reset by peer|actively refused') { return '连接被重置（疑似被阻断）' }
    if ($Msg -match 'did not properly respond|failed to respond|Unable to read data') { return '读取响应超时（链路被掐断）' }
    if ($Msg -match 'name or service not known|No such host|找不到') { return '域名解析失败' }
    if ($Msg -match '证书|certificate') { return '证书校验失败' }
    $Msg = $Msg -replace 'Exception calling[^"]*"[^"]*" with[^:]*:', ''
    $Msg = $Msg -replace '\s+', ' '
    if ($Msg.Length -gt 70) { $Msg = $Msg.Substring(0, 70) }
    return $Msg.Trim()
}

# DoH 对照解析（阿里公共 DNS，走 443 加密查询，可绕过 hosts 改写）
function Get-DohIp {
    param([string]$HostName, [int]$TimeoutMs = 4000)
    try {
        $q = "/resolve?name=$HostName&type=A"
        $h = Invoke-Https -HostName 'dns.alidns.com' -Path $q -TimeoutMs $TimeoutMs -Accept 'application/dns-json'
        if ($h.Ok -and $h.Status -eq 200 -and $h.Body) {
            $obj = $h.Body | ConvertFrom-Json
            if ($obj.Answer) {
                $a = @($obj.Answer | Where-Object { $_.type -eq 1 })
                if ($a.Count -gt 0) { return $a[0].data }
            }
        }
    } catch { }
    return ''
}

function Get-NetEnv {
    $env_info = [pscustomobject]@{
        ProxyEnabled = $false
        ProxyServer  = ''
        HostsHits    = @()
        Local443     = $false
        DnsServers   = @()
    }
    try {
        $ie = Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction Stop
        if ($ie.ProxyEnable -eq 1 -and $ie.ProxyServer) {
            $env_info.ProxyEnabled = $true
            $env_info.ProxyServer  = [string]$ie.ProxyServer
        }
    } catch { }

    $hostsPath = Join-Path $env:WINDIR 'System32\drivers\etc\hosts'
    if (Test-Path -LiteralPath $hostsPath) {
        try {
            $lines = Get-Content -LiteralPath $hostsPath -ErrorAction Stop
            foreach ($line in $lines) {
                $t = $line.Trim()
                if ($t -eq '' -or $t.StartsWith('#')) { continue }
                if ($t -match '^(127\.0\.0\.1|0\.0\.0\.0|::1)\s+(\S*)') {
                    $env_info.HostsHits += $t
                }
            }
        } catch { }
    }

    try {
        $listen = Get-NetTCPConnection -State Listen -LocalPort 443 -ErrorAction Stop
        if (@($listen).Count -gt 0) { $env_info.Local443 = $true }
    } catch { }

    try {
        $dns = Get-DnsClientServerAddress -AddressFamily IPv4 -ErrorAction Stop |
               Where-Object { $_.ServerAddresses.Count -gt 0 } |
               Select-Object -First 1
        if ($dns) { $env_info.DnsServers = @($dns.ServerAddresses) }
    } catch { }

    return $env_info
}

function Get-Verdict {
    param([bool]$Ok, [bool]$TcpOk, [int]$TcpMs)
    if (-not $TcpOk) { return @{ Level = 'r'; Label = '不通'; Text = '连不上' } }
    if (-not $Ok) { return @{ Level = 'o'; Label = '受限'; Text = '能连上但握手失败' } }
    if ($TcpMs -le 120) { return @{ Level = 'g'; Label = '优秀'; Text = '很快' } }
    if ($TcpMs -le 400) { return @{ Level = 'g2'; Label = '良好'; Text = '正常' } }
    if ($TcpMs -le 1000) { return @{ Level = 'y'; Label = '一般'; Text = '偏慢' } }
    return @{ Level = 'o'; Label = '较慢'; Text = '很慢' }
}

# ---------- 检查更新：只读两个平台上一个公开的版本号，不传本机任何信息 ----------
function ConvertTo-VersionParts {
    param([string]$Text)
    $clean = ($Text -replace '^[vV]', '').Trim()
    $nums = @()
    foreach ($p in ($clean -split '[.\-+]')) {
        $n = 0
        if ([int]::TryParse($p, [ref]$n)) { $nums += $n }
    }
    $out = @(0, 0, 0, 0)
    for ($k = 0; $k -lt [Math]::Min(4, $nums.Count); $k++) { $out[$k] = $nums[$k] }
    return $out
}

# 远端比本机新返回 1，相同返回 0，更旧返回 -1
function Compare-VersionText {
    param([string]$Remote, [string]$Local)
    $r = ConvertTo-VersionParts $Remote
    $l = ConvertTo-VersionParts $Local
    for ($k = 0; $k -lt 4; $k++) {
        if ($r[$k] -gt $l[$k]) { return 1 }
        if ($r[$k] -lt $l[$k]) { return -1 }
    }
    return 0
}

function Get-RemoteVersion {
    param([string]$Source = 'gitee', [int]$TimeoutMs = 5000)
    # 先读仓库里的 version.json —— 它按平台分别写版本号，单端更新不会误报到另一端
    if ($Source -eq 'github') {
        $resp = Invoke-Https -HostName 'raw.githubusercontent.com' -Path '/neurotracee/github-netcheck/main/version.json' -TimeoutMs $TimeoutMs -Accept 'application/json'
    } else {
        $resp = Invoke-Https -HostName 'gitee.com' -Path '/bio234/github-netcheck/raw/main/version.json' -TimeoutMs $TimeoutMs -Accept 'application/json'
    }
    if ($resp.Ok -and $resp.Body) {
        $m = [regex]::Match($resp.Body, '"windows"\s*:\s*\{[^}]*"version"\s*:\s*"([^"]+)"')
        if (-not $m.Success) { $m = [regex]::Match($resp.Body, '"windows"\s*:\s*\{[^}]*\}') }
        if ($m.Success -and $m.Groups.Count -ge 2 -and $m.Groups[1].Value) { return $m.Groups[1].Value }
    }
    # 兜底：version.json 拿不到时仍按 release tag 判断（老仓库没有这个文件也能用）
    if ($Source -eq 'github') {
        $resp = Invoke-Https -HostName 'api.github.com' -Path '/repos/neurotracee/github-netcheck/releases/latest' -TimeoutMs $TimeoutMs -Accept 'application/vnd.github+json'
    } else {
        $resp = Invoke-Https -HostName 'gitee.com' -Path '/api/v5/repos/bio234/github-netcheck/releases/latest' -TimeoutMs $TimeoutMs -Accept 'application/json'
    }
    if (-not $resp.Ok) { return $null }
    $m = [regex]::Match($resp.Body, '"tag_name"\s*:\s*"([^"]+)"')
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value
}

# 更新源顺序：默认 Gitee。
# 只有「明确迹象」说明大概率不在中国大陆时才默认 GitHub ——
# 系统语言不是中文，且时区也不是 UTC+8；判断不出来一律走 Gitee。
function Resolve-UpdateSource {
    if ($script:UpdateSource -eq 'gitee') { return 'gitee' }
    if ($script:UpdateSource -eq 'github') { return 'github' }
    $chineseUi = $false
    try { $chineseUi = ([Globalization.CultureInfo]::CurrentUICulture.TwoLetterISOLanguageName -eq 'zh') } catch { }
    $offsetHours = 0
    try { $offsetHours = [TimeZoneInfo]::Local.BaseUtcOffset.TotalHours } catch { }
    if ((-not $chineseUi) -and ($offsetHours -ne 8)) { return 'github' }
    return 'gitee'
}

# 查到新版本时的提示窗：三个出口 —— 用浏览器打开下载 / 复制链接 / 以后再说
function Show-UpdatePrompt {
    param($Update)
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '发现新版本 · GitHub 网络自检'
    $form.Size = New-Object System.Drawing.Size(480, 236)
    $form.StartPosition = 'CenterScreen'
    $form.BackColor = [System.Drawing.Color]::FromArgb(244, 246, 248)
    $form.Font = $font

    $t1 = New-Object System.Windows.Forms.Label
    $t1.Text = ('发现新版本 ' + $Update.Remote + '（当前 ' + $script:AppVersion + '）')
    $t1.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 12, [System.Drawing.FontStyle]::Bold)
    $t1.AutoSize = $true
    $t1.Location = New-Object System.Drawing.Point(22, 22)
    $form.Controls.Add($t1)

    $t2 = New-Object System.Windows.Forms.Label
    $t2.Text = '不更新也一模一样用，只是新版顺手修了几个小毛病，用起来更稳。' + "`r`n" +
               '下次打开还会再提醒一次，到时候想换也来得及。'
    $t2.AutoSize = $true
    $t2.ForeColor = [System.Drawing.Color]::FromArgb(107, 119, 133)
    $t2.Location = New-Object System.Drawing.Point(24, 58)
    $form.Controls.Add($t2)

    $url = $script:ProjectUrl + '/releases/latest'

    $go = New-Object System.Windows.Forms.Button
    $go.Text = '用浏览器打开下载'
    $go.Size = New-Object System.Drawing.Size(140, 32)
    $go.Location = New-Object System.Drawing.Point(24, 168)
    $go.BackColor = [System.Drawing.Color]::FromArgb(18, 161, 80)
    $go.ForeColor = [System.Drawing.Color]::White
    $go.FlatStyle = 'Flat'
    $go.Add_Click({ try { Start-Process $url } catch { }; $form.Close() })
    $form.Controls.Add($go)

    $copy = New-Object System.Windows.Forms.Button
    $copy.Text = '复制链接'
    $copy.Size = New-Object System.Drawing.Size(104, 32)
    $copy.Location = New-Object System.Drawing.Point(176, 168)
    $copy.FlatStyle = 'Flat'
    $copy.Add_Click({
        try { [System.Windows.Forms.Clipboard]::SetText($url); $copy.Text = '已复制' } catch { }
    })
    $form.Controls.Add($copy)

    $later = New-Object System.Windows.Forms.Button
    $later.Text = '以后再说'
    $later.Size = New-Object System.Drawing.Size(104, 32)
    $later.Location = New-Object System.Drawing.Point(292, 168)
    $later.FlatStyle = 'Flat'
    $later.Add_Click({ $form.Close() })
    $form.Controls.Add($later)

    $form.ShowDialog() | Out-Null
}

function Invoke-UpdateCheck {
    param([int]$TimeoutMs = 5000)
    $errs = @()
    $first = Resolve-UpdateSource
    $order = @($first, $(if ($first -eq 'gitee') { 'github' } else { 'gitee' }))
    foreach ($s in $order) {
        $srcName = 'Gitee'
        if ($s -eq 'github') { $srcName = 'GitHub' }
        $tag = Get-RemoteVersion -Source $s -TimeoutMs $TimeoutMs
        if ($tag) {
            $cmp = Compare-VersionText -Remote $tag -Local $script:AppVersion
            if ($cmp -gt 0) {
                return @{ Ok = $true; HasNew = $true; Source = $srcName; Remote = $tag;
                          Text = ('发现新版本 ' + $tag + '（当前 ' + $script:AppVersion + '，来源 ' + $srcName + '）') }
            }
            return @{ Ok = $true; HasNew = $false; Source = $srcName; Remote = $tag;
                      Text = ('已是最新版本（当前 ' + $script:AppVersion + '，远端 ' + $tag + '，来源 ' + $srcName + '）') }
        }
        $errs += ($srcName + ' 连不上')
    }
    return @{ Ok = $false; HasNew = $false; Source = ''; Remote = '';
              Text = ('检查不到更新：' + ($errs -join '，') + '（不影响自检）') }
}

# ---------- 参数分派（放在所有函数定义之后，保证 -About / -Settings / -CheckUpdate 都能用到函数） ----------
if ($About) {
    Show-AboutWindow
    exit 0
}
if ($Settings) {
    Show-SettingsWindow
    exit 0
}
if ($CheckUpdate) {
    Write-Host ''
    Write-Host ('  GitHub 网络自检 v' + $script:AppVersion + ' · 检查更新') -ForegroundColor Cyan
    $u = Invoke-UpdateCheck
    $color = 'Cyan'
    if ($u.HasNew) { $color = 'Yellow' }
    Write-Host ('  ' + $u.Text) -ForegroundColor $color
    if ($u.HasNew) {
        Write-Host ('  下载页：' + $script:ProjectUrl + '/releases') -ForegroundColor DarkGray
        Write-Host ('  镜像下载：' + $script:MirrorUrl + '/releases') -ForegroundColor DarkGray
    }
    Write-Host ''
    exit 0
}

# ---------- 开始测试 ----------

Write-Host ''
Write-Host ('  GitHub 网络自检 v' + $script:AppVersion + '   GitHub / Hugging Face / Docker') -ForegroundColor Cyan
Write-Host '  ---------------------------------------------------------' -ForegroundColor DarkGray
Write-Host ('  作者：项目创作时为初中生 · 开发：豆包工作任务的 Agent × DeepSeek-V4.1-Flash') -ForegroundColor DarkGray
Write-Host ('  开源协议 Apache-2.0 · 原项目 ' + $script:ProjectUrl) -ForegroundColor DarkGray

# 启动时自动检查更新（可在设置里关掉）：只读一行公开版本号，连不上就静默跳过
if ($script:AutoCheckUpdate) {
    try {
        $u = Invoke-UpdateCheck -TimeoutMs 3000
        if ($u.HasNew) {
            Write-Host ('  检查更新：' + $u.Text) -ForegroundColor Yellow
            Write-Host ('  下载页：' + $script:ProjectUrl + '/releases') -ForegroundColor DarkGray
            Show-UpdatePrompt -Update $u
        } else {
            Write-Host ('  检查更新：' + $u.Text) -ForegroundColor DarkGray
        }
    } catch {
        Write-Host '  检查更新：跳过（仓库暂时连不上）' -ForegroundColor DarkGray
    }
}

$script:RateInfo = ''
$NetEnv = Get-NetEnv
if ($NetEnv.ProxyEnabled) { Write-Host ("  系统代理：已开启  " + $NetEnv.ProxyServer) -ForegroundColor Yellow }
else { Write-Host '  系统代理：未开启（直连模式）' -ForegroundColor DarkGray }
if ($NetEnv.HostsHits.Count -gt 0) {
    Write-Host ("  hosts 记录：发现 " + $NetEnv.HostsHits.Count + " 条本地映射（可能来自瓦特工具箱等加速工具）") -ForegroundColor Yellow
} else {
    Write-Host '  hosts 记录：无本地映射' -ForegroundColor DarkGray
}
if ($NetEnv.Local443) { Write-Host '  本机 443 端口：有程序在监听（本地反代很可能在运行）' -ForegroundColor Yellow }
Write-Host ''

$Results = @()
$i = 0
foreach ($t in $Targets) {
    $i++
    Write-Host ("  [" + $i + "/" + $Targets.Count + "] " + $t.Name + "  (" + $t.Host + ")") -ForegroundColor White -NoNewline

    $dns = Test-Dns -HostName $t.Host -TimeoutMs $script:TimeoutMs
    if ($dns.Ok) {
        $https = Invoke-Https -HostName $t.Host -Path $t.Path -TimeoutMs $script:TimeoutMs
        $doh = Get-DohIp -HostName $t.Host
        if ($t.Host -eq 'api.github.com' -and $https.Ok -and $https.Body) {
            try {
                $jo = $https.Body | ConvertFrom-Json
                if ($jo.rate) { $script:RateInfo = 'API 配额 ' + $jo.rate.remaining + ' / ' + $jo.rate.limit }
            } catch { }
        }
    } else {
        $https = [pscustomobject]@{ Ok = $false; TcpOk = $false; TcpMs = 0; TlsMs = 0; Ttfb = 0; TotalMs = 0; Status = 0; Error = 'DNS 解析失败，未继续连接'; Body = '' }
        $doh = ''
    }

    $v = Get-Verdict -Ok $https.Ok -TcpOk $https.TcpOk -TcpMs $https.TcpMs

    $hint = ''
    if ($dns.Ip -eq '127.0.0.1' -or $dns.Ip -eq '::1') { $hint = '本地反代' }
    elseif ($https.Ok -and $https.Status -ge 500) { $hint = '服务端异常' }

    if ($https.Ok) {
        Write-Host ("   " + $v.Label + "   TCP " + $https.TcpMs + "ms / TLS " + $https.TlsMs + "ms / HTTP " + $https.Ttfb + "ms") -ForegroundColor Green
    } elseif ($https.TcpOk) {
        Write-Host ("   " + $v.Label + "   TCP " + $https.TcpMs + "ms，但握手失败：" + $https.Error) -ForegroundColor Yellow
    } else {
        Write-Host ("   " + $v.Label + "   " + $https.Error) -ForegroundColor Red
    }

    $Results += [pscustomobject]@{
        Name    = $t.Name
        Host    = $t.Host
        Desc    = $t.Desc
        Ip      = $dns.Ip
        DohIp   = $doh
        DnsMs   = $dns.Ms
        TcpMs   = $https.TcpMs
        TlsMs   = $https.TlsMs
        TtfbMs  = $https.Ttfb
        Status  = $https.Status
        Ok      = $https.Ok
        TcpOk   = $https.TcpOk
        DnsOk   = $dns.Ok
        Level   = $v.Level
        Label   = $v.Label
        Error   = $https.Error
        Hint    = $hint
    }
}

$Elapsed = [int]((Get-Date) - $script:StartAt).TotalSeconds
$OkCount = @($Results | Where-Object { $_.Ok }).Count
$Total    = $Results.Count
$TcpList  = @($Results | Where-Object { $_.Ok } | ForEach-Object { $_.TcpMs })
$AvgTcp   = 0
if ($TcpList.Count -gt 0) { $AvgTcp = [int](($TcpList | Measure-Object -Average).Average) }
$Fastest  = $Results | Where-Object { $_.Ok } | Sort-Object TcpMs | Select-Object -First 1
$Slowest  = $Results | Where-Object { $_.Ok } | Sort-Object TcpMs -Descending | Select-Object -First 1
$FailList = @($Results | Where-Object { -not $_.Ok })

# ---------- 控制台汇总 ----------
Write-Host ''
Write-Host '  ---------------------------------------------------------' -ForegroundColor DarkGray
$line = "  可连通 " + $OkCount + " / " + $Total + "    平均建连 " + $AvgTcp + " ms    耗时 " + $Elapsed + " 秒"
Write-Host $line -ForegroundColor Cyan
if ($script:RateInfo) { Write-Host ("  " + $script:RateInfo) -ForegroundColor DarkGray }
if ($Fastest) { Write-Host ("  最快：" + $Fastest.Name + "  " + $Fastest.TcpMs + " ms") -ForegroundColor Green }
if ($Slowest) { Write-Host ("  最慢：" + $Slowest.Name + "  " + $Slowest.TcpMs + " ms") -ForegroundColor Yellow }
if ($FailList.Count -gt 0) {
    Write-Host ("  不通：" + (($FailList | ForEach-Object { $_.Name }) -join '、')) -ForegroundColor Red
}
Write-Host ''

# ---------- 生成 HTML 报告 ----------

$sbRows = New-Object System.Text.StringBuilder
foreach ($r in $Results) {
    $barPct = 0
    if ($r.TcpOk) {
        $barPct = [int]([math]::Min(100, ($r.TcpMs / 500.0) * 100))
        if ($barPct -lt 4) { $barPct = 4 }
    } else { $barPct = 100 }

    $dnsTxt = [string]$r.DnsMs
    $tcpTxt = [string]$r.TcpMs
    $tlsTxt = '—'
    $httpTxt = '—'
    if (-not $r.DnsOk) { $dnsTxt = '—' }
    if (-not $r.TcpOk) { $tcpTxt = '—' }
    if ($r.Ok) { $tlsTxt = [string]$r.TlsMs; $httpTxt = [string]$r.TtfbMs }

    $ipText = '—'
    if ($r.Ip) { $ipText = $r.Ip }
    $dohTag = ''
    if ($r.DohIp -and $r.DohIp -ne $r.Ip) {
        $dohTag = '<span class="doh">DoH 真实解析 ' + $r.DohIp + '</span>'
    } elseif ($r.DohIp) {
        $dohTag = '<span class="doh ok">DoH 一致</span>'
    }
    $hintTag = ''
    if ($r.Hint) { $hintTag = '<span class="chip">' + $r.Hint + '</span>' }

    $errLine = ''
    if (-not $r.Ok) { $errLine = '<div class="err">' + [System.Net.WebUtility]::HtmlEncode($r.Error) + '</div>' }

    $num = '{0}'
    [void]$sbRows.AppendLine('<div class="row lv-' + $r.Level + '">')
    [void]$sbRows.AppendLine('  <div class="info">')
    [void]$sbRows.AppendLine('    <div class="nm">' + $r.Name + $hintTag + '</div>')
    [void]$sbRows.AppendLine('    <div class="hst">' + $r.Host + '</div>')
    [void]$sbRows.AppendLine('    <div class="ipline"><span class="ipdot"></span>' + $ipText + $dohTag + '</div>')
    [void]$sbRows.AppendLine('  </div>')
    [void]$sbRows.AppendLine('  <div class="metrics">')
    [void]$sbRows.AppendLine('    <div class="m"><b>' + $dnsTxt + '</b><i>DNS</i></div>')
    [void]$sbRows.AppendLine('    <div class="m"><b>' + $tcpTxt + '</b><i>TCP</i></div>')
    [void]$sbRows.AppendLine('    <div class="m"><b>' + $tlsTxt + '</b><i>TLS</i></div>')
    [void]$sbRows.AppendLine('    <div class="m"><b>' + $httpTxt + '</b><i>HTTP</i></div>')
    [void]$sbRows.AppendLine('  </div>')
    [void]$sbRows.AppendLine('  <div class="verdict">')
    [void]$sbRows.AppendLine('    <span class="tag">' + $r.Label + '</span>')
    [void]$sbRows.AppendLine('    <div class="bar"><i style="width:' + $barPct + '%;"></i></div>')
    [void]$sbRows.AppendLine('    <div class="small">' + $r.Desc + '</div>')
    [void]$sbRows.AppendLine($errLine)
    [void]$sbRows.AppendLine('  </div>')
    [void]$sbRows.AppendLine('</div>')
}
$rowsHtml = $sbRows.ToString()

# 环境信息
$envBits = New-Object System.Text.StringBuilder
if ($NetEnv.ProxyEnabled) { [void]$envBits.Append('<span class="pill warn">系统代理开启 ' + [System.Net.WebUtility]::HtmlEncode($NetEnv.ProxyServer) + '</span>') }
else { [void]$envBits.Append('<span class="pill">系统代理未开启</span>') }
if ($NetEnv.HostsHits.Count -gt 0) { [void]$envBits.Append('<span class="pill warn">hosts 有 ' + $NetEnv.HostsHits.Count + ' 条本地映射</span>') }
else { [void]$envBits.Append('<span class="pill">hosts 无本地映射</span>') }
if ($NetEnv.Local443) { [void]$envBits.Append('<span class="pill warn">本机 443 端口被监听</span>') }
else { [void]$envBits.Append('<span class="pill">本机 443 端口空闲</span>') }
if ($NetEnv.DnsServers.Count -gt 0) { [void]$envBits.Append('<span class="pill">DNS ' + ($NetEnv.DnsServers -join ', ') + '</span>') }
if ($script:RateInfo) { [void]$envBits.Append('<span class="pill">' + $script:RateInfo + '</span>') }
$envHtml = $envBits.ToString()

# 诊断建议
$advice = New-Object System.Text.StringBuilder
if ($FailList.Count -eq 0) {
    [void]$advice.Append('<div class="adv-item good"><b>全部目标均可连通。</b>当前网络对 GitHub 系和 Hugging Face、Docker 的访问正常，不需要额外加速。</div>')
} else {
    foreach ($f in $FailList) {
        $msg = '该站点当前无法建立连接。'
        if ($f.Host -match 'huggingface') { $msg = 'Hugging Face 国内直连经常超时，开启加速或在浏览器里配置镜像站可改善。' }
        elseif ($f.Host -match 'docker') { $msg = 'Docker 官方仓库国内不稳定，可在 Docker 设置里改用国内镜像加速地址。' }
        elseif ($f.Host -match 'githubio|github\.io') { $msg = 'GitHub Pages 的域名在国内经常被解析到不可用地址，可换用自定义域名或加速。' }
        elseif ($f.Host -match 'github') { $msg = 'GitHub 相关域名不稳定是常见现象，开启瓦特工具箱等本地反代后重测对比即可判断是否生效。' }
        if ($f.Ip -eq '127.0.0.1') { $msg = '域名被 hosts 指向本机 127.0.0.1，说明加速工具的本地反代没在正常转发，或反代已退出但 hosts 没清理。' }
        elseif ($f.TcpOk -and -not $f.Ok) { $msg = '端口能连上但 TLS 握手失败，常见原因是 SNI 被干扰或证书被中间设备替换，开启本地反代后通常会恢复。' }
        [void]$advice.Append('<div class="adv-item"><b>' + $f.Name + '</b>：' + $msg + '</div>')
    }
}
$adviceHtml = $advice.ToString()

$dateStr = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')

$html = @'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>GitHub 网络自检报告</title>
<style>
:root{
  --bg:#f4f6f8; --card:#ffffff; --ink:#1a2027; --sub:#6b7785; --line:#e5e9ee;
  --g:#12a150; --g2:#3b82c4; --y:#c8870c; --o:#d9601a; --r:#d0342c; --n:#96a2ae;
}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--ink);font:15px/1.6 system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}
.wrap{max-width:1060px;margin:0 auto;padding:36px 22px 60px}
header{margin-bottom:22px}
.kicker{font-size:12px;letter-spacing:.22em;color:var(--sub);text-transform:uppercase}
h1{font-size:26px;font-weight:650;letter-spacing:-.2px;margin:8px 0 6px}
.meta{font-size:13px;color:var(--sub)}
.pills{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
.pill{font-size:12px;padding:4px 10px;border-radius:999px;background:#fff;border:1px solid var(--line);color:var(--sub)}
.pill.warn{background:#fff8e8;border-color:#f0dfb8;color:#9a6a08}
.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:22px 0 26px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px}
.card .lb{font-size:12px;color:var(--sub)}
.card .v{font-size:26px;font-weight:650;margin-top:2px;letter-spacing:-.5px}
.card .v small{font-size:13px;font-weight:400;color:var(--sub);margin-left:3px}
.list{display:flex;flex-direction:column;gap:10px}
.row{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px 18px;display:grid;grid-template-columns:1.35fr 1.15fr .85fr;gap:10px;align-items:center;border-left:4px solid var(--n)}
.row.lv-g{border-left-color:var(--g)} .row.lv-g2{border-left-color:var(--g2)}
.row.lv-y{border-left-color:var(--y)} .row.lv-o{border-left-color:var(--o)}
.row.lv-r{border-left-color:var(--r)}
.nm{font-weight:600;font-size:14.5px;display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.hst{font-size:12px;color:var(--sub);font-family:ui-monospace,Consolas,monospace}
.ipline{font-size:12px;color:var(--sub);margin-top:3px;display:flex;align-items:center;gap:6px;flex-wrap:wrap}
.ipdot{width:6px;height:6px;border-radius:50%;background:var(--n);display:inline-block}
.row.lv-g .ipdot,.row.lv-g2 .ipdot{background:var(--g)}
.row.lv-r .ipdot{background:var(--r)}
.doh{font-size:11px;color:#a06a05}
.doh.ok{color:var(--g)}
.chip{font-size:11px;font-weight:500;padding:1px 6px;border-radius:5px;background:#fff3e0;color:#a15c0a}
.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;width:100%;max-width:400px;justify-self:center}
.m{text-align:center}
.m b{display:block;font-size:16px;font-weight:600;letter-spacing:-.3px}
.m i{display:block;font-style:normal;font-size:10.5px;color:var(--sub);letter-spacing:.05em}
.row.lv-r .m b{color:var(--n)}
.verdict{text-align:right}
.tag{display:inline-block;font-size:12px;padding:2px 9px;border-radius:6px;font-weight:600}
.row.lv-g .tag{background:#e8f7ee;color:#0c7a3c}
.row.lv-g2 .tag{background:#eaf2fb;color:#2a6a9e}
.row.lv-y .tag{background:#fdf4e3;color:#8a6107}
.row.lv-o .tag{background:#fdeee5;color:#a2480f}
.row.lv-r .tag{background:#fdecea;color:#a92a23}
.bar{height:4px;background:#eef1f4;border-radius:3px;margin-top:8px;overflow:hidden}
.bar i{display:block;height:100%;border-radius:3px;background:var(--g)}
.row.lv-g2 .bar i{background:var(--g2)} .row.lv-y .bar i{background:var(--y)}
.row.lv-o .bar i{background:var(--o)} .row.lv-r .bar i{background:var(--r);opacity:.6}
.small{font-size:11.5px;color:var(--sub);margin-top:6px}
.err{font-size:11.5px;color:#a92a23;margin-top:4px;text-align:right}
.sec{margin-top:28px}
h2{font-size:16px;font-weight:600;margin-bottom:10px}
.adv{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:6px 18px}
.adv-item{padding:10px 0;border-bottom:1px dashed var(--line);font-size:13.5px}
.adv-item:last-child{border-bottom:none}
.adv-item.good{color:#0c7a3c}
.adv-item b{font-weight:600}
.foot{margin-top:26px;font-size:12px;color:var(--sub);line-height:1.9}
.foot code{background:#eceff3;padding:1px 5px;border-radius:4px;font-size:11.5px}
@media (max-width:760px){
  .wrap{padding:24px 14px 44px}
  h1{font-size:20px}
  .cards{grid-template-columns:repeat(2,1fr)}
  .card .v{font-size:22px}
  .row{grid-template-columns:1fr;gap:12px;padding:14px}
  .verdict{text-align:left}
  .bar{margin-top:10px}
  .err{text-align:left}
}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <div class="kicker">GitHub 网络自检 · v__VERSION__</div>
    <h1>GitHub 网络自检报告</h1>
    <div class="meta">__DATE__ 　·　 __COUNT__ 个目标　·　 用时 __ELAPSED__ 秒　·　 全部测试在本机完成，不上传任何数据</div>
    <div class="pills">__ENV__</div>
  </header>

  <div class="cards">
    <div class="card"><div class="lb">可连通</div><div class="v">__OKCOUNT__<small>/ __COUNT__</small></div></div>
    <div class="card"><div class="lb">平均建连延迟</div><div class="v">__AVG__<small>ms</small></div></div>
    <div class="card"><div class="lb">最快</div><div class="v">__FAST__<small>ms</small></div></div>
    <div class="card"><div class="lb">异常目标</div><div class="v">__SLOW__<small>项</small></div></div>
  </div>

  <div class="list">
__ROWS__
  </div>

  <div class="sec">
    <h2>诊断与建议</h2>
    <div class="adv">
__ADVICE__
    </div>
  </div>

  <div class="foot">
    测试方法：DNS 解析 → TCP 443 建连 → TLS 握手 → 发送 HTTP 请求测首字节时间，四段分别计时。<br>
    评级按 TCP 建连延迟：≤120ms 优秀，≤400ms 良好，≤1000ms 一般，&gt;1000ms 较慢，失败为不通。<br>
    <code>DoH 真实解析</code> 用阿里公共 DNS 加密查询对照，用来发现 hosts 改写或 DNS 污染。<br>
    报告中若某域名 IP 显示为 127.0.0.1，说明它被 hosts 指向本机，实际流量走的是本地反代而不是直连。<br>
    <br>
    GitHub 网络自检 <b>v__VERSION__</b>　·　指导作者：项目创作时为初中生　·　开发：豆包工作任务的 Agent × DeepSeek-V4.1-Flash<br>
    原项目 <a href="__PROJECTURL__">github.com/neurotracee/github-netcheck</a>　·　
    镜像 <a href="__MIRRORURL__">gitee.com/bio234/github-netcheck</a>　·　Apache-2.0
  </div>
</div>
</body>
</html>
'@

function Set-Placeholder {
    param([string]$Text, [string]$Key, [string]$Value)
    return $Text.Replace($Key, $Value)
}

$html = Set-Placeholder $html '__DATE__'     $dateStr
$html = Set-Placeholder $html '__VERSION__'  $script:AppVersion
$html = Set-Placeholder $html '__PROJECTURL__' $script:ProjectUrl
$html = Set-Placeholder $html '__MIRRORURL__'  $script:MirrorUrl
$html = Set-Placeholder $html '__COUNT__'    ([string]$Total)
$html = Set-Placeholder $html '__ELAPSED__'  ([string]$Elapsed)
$html = Set-Placeholder $html '__ENV__'      $envHtml
$html = Set-Placeholder $html '__OKCOUNT__'  ([string]$OkCount)
$html = Set-Placeholder $html '__AVG__'      ([string]$AvgTcp)
$fastVal = '-'
if ($Fastest) { $fastVal = [string]$Fastest.TcpMs }
$html = Set-Placeholder $html '__FAST__' $fastVal
$slowVal = '-'
$slowVal = [string]$FailList.Count
$html = Set-Placeholder $html '__SLOW__' $slowVal
$html = Set-Placeholder $html '__ROWS__'     $rowsHtml
$html = Set-Placeholder $html '__ADVICE__'   $adviceHtml

# ---------- 要不要出报告：默认先问一句，而不是闷头生成一堆 HTML ----------
# 规则：AskBeforeReport 开着时每次询问（可在回答后选择「记住这次的选择」，之后不再问）；
#     关掉时按 settings.json 里记住的 SaveReport / AutoOpen 执行。
$wantSave = $false
$wantOpen = $false
if ($NoBrowser) {
    # -NoBrowser 是给自动化/CI 用的：不弹问、不打开浏览器，只按记住的「要不要报告」来
    $wantSave = $script:SaveReport
    $wantOpen = $false
}
elseif ($script:AskBeforeReport) {
    Write-Host ''
    $ansSave = Read-Host '  自检跑完了。这次要生成 HTML 报告吗？(Y/n)'
    if ($ansSave -eq '' -or $ansSave -match '^[Yy]') { $wantSave = $true }
    if ($wantSave) {
        $ansOpen = Read-Host '  要现在用浏览器打开吗？(Y/n)'
        if ($ansOpen -eq '' -or $ansOpen -match '^[Yy]') { $wantOpen = $true }
    }
    $ansRemember = Read-Host '  记住这次的选择吗（下次不再问）？(y/N)'
    if ($ansRemember -match '^[Yy]') {
        Save-LocalSettings -Dir $ReportDir -AutoOpen $wantOpen -TimeoutMs $script:TimeoutMs `
            -AutoCheckUpdate $script:AutoCheckUpdate -AskBeforeReport $false -SaveReport $wantSave
        Write-Host ('  已记住：以后不再询问，' + $(if ($wantSave) { '会生成报告' } else { '不生成报告' }) +
            $(if ($wantOpen) { '并自动打开' } else { '、不自动打开' }) + '。可在设置里改回来。') -ForegroundColor DarkGray
    }
}
else {
    $wantSave = $script:SaveReport
    $wantOpen = $script:AutoOpen
}

if (-not $wantSave) {
    Write-Host ''
    Write-Host '  这次不生成报告（控制台结果已经打完）。想改回来：运行 -Settings 勾上「每次先问我」，或改 settings.json。' -ForegroundColor DarkGray
    Write-Host ''
    exit 0
}

$stamp = (Get-Date).ToString('yyyyMMdd-HHmmss')
$outFile = Join-Path $ReportDir ("netcheck-" + $stamp + ".html")
[System.IO.File]::WriteAllText($outFile, $html, (New-Object System.Text.UTF8Encoding($false)))
$latest = Join-Path $ReportDir 'latest.html'
try { Copy-Item -LiteralPath $outFile -Destination $latest -Force } catch { }

# 同时存一份 CSV，方便以后对比
$csvFile = Join-Path $ReportDir ("netcheck-" + $stamp + ".csv")
try {
    $Results | Select-Object Name, Host, Ip, DohIp, DnsMs, TcpMs, TlsMs, TtfbMs, Status, Ok, Label |
        Export-Csv -LiteralPath $csvFile -NoTypeInformation -Encoding UTF8
} catch { }

Write-Host ("  报告已生成：" + $outFile) -ForegroundColor Cyan
Write-Host ''

if ($wantOpen) {
    try { Start-Process $outFile } catch { Write-Host '  自动打开报告失败，请手动双击 reports 目录下的 html 文件。' -ForegroundColor Yellow }
}

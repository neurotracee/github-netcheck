# ============================================================
#  NetCheckMenu.ps1 —— GitHub 网络自检（Windows 端）可点击终端菜单
#
#  仍然是一个终端窗口，不再额外弹软件界面；以前是「输入数字回车」，
#  现在可以用鼠标点、用触摸屏点，也可以用方向键 + 回车、或直接按数字。
#  语言开关就在菜单里，点一下即切 中文 / English，记在 settings.json。
#
#  所有测量仍由同目录 netcheck.ps1 完成，点菜单等价于以前输入那个数字。
#
#  版本：1.1.7.5
#  协议：Apache License 2.0
#  原项目地址：https://github.com/neurotracee/github-netcheck
#  镜像（Gitee）：https://gitee.com/bio234/github-netcheck
# ============================================================

$ErrorActionPreference = 'Continue'

try { $ScriptDir = $PSScriptRoot } catch { $ScriptDir = $null }
if ([string]::IsNullOrEmpty($ScriptDir)) {
    if ($MyInvocation.MyCommand.Path) { $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
    else { $ScriptDir = (Get-Location).Path }
}

$NetCheck     = Join-Path $ScriptDir 'netcheck.ps1'
$SettingsFile = Join-Path $ScriptDir 'settings.json'

# ---------------- 语言 ----------------
$script:Lang = 'zh'
if (Test-Path -LiteralPath $SettingsFile) {
    try {
        $cfg = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($cfg.PSObject.Properties['Lang'] -and "$($cfg.Lang)" -eq 'en') { $script:Lang = 'en' }
    } catch { }
}

function T([string]$zh, [string]$en) { if ($script:Lang -eq 'en') { $en } else { $zh } }

function Save-Lang([string]$lang) {
    $obj = [ordered]@{}
    if (Test-Path -LiteralPath $SettingsFile) {
        try {
            $cfg = Get-Content -LiteralPath $SettingsFile -Raw -Encoding UTF8 | ConvertFrom-Json
            foreach ($p in $cfg.PSObject.Properties) { $obj[$p.Name] = $p.Value }
        } catch { }
    }
    $obj['Lang'] = $lang
    ($obj | ConvertTo-Json) | Set-Content -LiteralPath $SettingsFile -Encoding UTF8
}

# ---------------- 控制台鼠标输入 ----------------
# 键盘本来就能用；鼠标点击要靠 ReadConsoleInput。这里关掉「快速编辑」
# （否则点击会变成选中文字），并打开鼠标事件。
if (-not ('ConInput' -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class ConInput {
    [DllImport("kernel32.dll")] public static extern IntPtr GetStdHandle(int n);
    [DllImport("kernel32.dll")] public static extern bool GetConsoleMode(IntPtr h, out uint m);
    [DllImport("kernel32.dll")] public static extern bool SetConsoleMode(IntPtr h, uint m);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    public static extern bool ReadConsoleInputW(IntPtr h, IntPtr buf, uint len, out uint read);

    [StructLayout(LayoutKind.Sequential)]
    public struct COORD { public short X; public short Y; }
    [StructLayout(LayoutKind.Sequential)]
    public struct SMALL_RECT { public short Left; public short Top; public short Right; public short Bottom; }
    [StructLayout(LayoutKind.Sequential)]
    public struct CSBI {
        public COORD Size; public COORD CursorPosition; public ushort Attributes;
        public SMALL_RECT Window; public COORD MaxWindowSize;
    }
    [DllImport("kernel32.dll")] public static extern bool GetConsoleScreenBufferInfo(IntPtr h, out CSBI info);

    // 可视窗口顶部在缓冲区里的行号：鼠标事件给的是缓冲区绝对行，
    // 缓冲比窗口高时两者差这么多。
    public static int WindowTop() {
        IntPtr h = GetStdHandle(-11);
        CSBI info;
        if (!GetConsoleScreenBufferInfo(h, out info)) return 0;
        return info.Window.Top;
    }
    public static int CursorY() {
        IntPtr h = GetStdHandle(-11);
        CSBI info;
        if (!GetConsoleScreenBufferInfo(h, out info)) return 0;
        return info.CursorPosition.Y;
    }

    const int STDIN = -10;
    const uint ENABLE_MOUSE = 0x0010;
    const uint ENABLE_QUICK_EDIT = 0x0040;
    const uint ENABLE_EXTENDED = 0x0080;
    const uint ENABLE_WINDOW_INPUT = 0x0008;

    public static uint OldMode;

    public static void Enable() {
        IntPtr h = GetStdHandle(STDIN);
        uint m = 0;
        GetConsoleMode(h, out m);
        OldMode = m;
        uint want = (m | ENABLE_MOUSE | ENABLE_WINDOW_INPUT | ENABLE_EXTENDED) & ~ENABLE_QUICK_EDIT;
        SetConsoleMode(h, want);
    }
    public static void Restore() {
        IntPtr h = GetStdHandle(STDIN);
        if (OldMode != 0) { SetConsoleMode(h, OldMode); }
    }

    public static string Next() {
        IntPtr h = GetStdHandle(STDIN);
        IntPtr buf = Marshal.AllocHGlobal(20);
        try {
            uint read = 0;
            while (true) {
                if (!ReadConsoleInputW(h, buf, 1, out read)) { return "E:"; }
                byte[] b = new byte[20];
                Marshal.Copy(buf, b, 0, 20);
                int type = BitConverter.ToInt16(b, 0);
                if (type == 1) {
                    int down = BitConverter.ToInt32(b, 4);
                    short vk = BitConverter.ToInt16(b, 10);
                    short uch = BitConverter.ToInt16(b, 14);
                    if (down != 0) { return "K:" + vk + "," + ((int)uch); }
                } else if (type == 2) {
                    short mx = BitConverter.ToInt16(b, 4);
                    short my = BitConverter.ToInt16(b, 6);
                    uint btn = BitConverter.ToUInt32(b, 8);
                    return "M:" + mx + "," + my + "," + btn;
                } else if (type == 4) {
                    return "R:";
                }
            }
        } finally { Marshal.FreeHGlobal(buf); }
    }
}
"@
}

# ---------------- 菜单项 ----------------
$items = @(
    @{ key='full';     zh='开始自检（14 个点位，约 30 秒）';        en='Run the full check (14 targets, ~30s)';          args=@();              mode='console' },
    @{ key='quick';    zh='只测 GitHub 系（快一点）';                 en='GitHub-only (faster)';                           args=@('-Quick');       mode='console' },
    @{ key='about';    zh='关于与开源信息';                            en='About & open-source info';                       args=@('-About');       mode='window'  },
    @{ key='settings'; zh='设置（报告位置 / 自动打开 / 超时）';         en='Settings (report folder / auto-open / timeout)'; args=@('-Settings');    mode='window'  },
    @{ key='update';   zh='检查更新（只读远端版本号）';                 en='Check for updates (version only)';               args=@('-CheckUpdate'); mode='console' },
    @{ key='logs';     zh='查看日志（应用内日志 / 清理 TXT）';          en='App logs (built-in log / TXT)';                  args=@('-Logs');        mode='window'  },
    @{ key='clearlogs';zh='清除日志（按日期，问两次）';                 en='Clear logs (by date, asks twice)';               args=@('-ClearLogs');   mode='console' }
)

# ---------------- 绘制 ----------------
function FitBuffer {
    # 以前这里把缓冲区压成一屏高，保证菜单从第一行画起；代价是自检那十几行结果会被顶掉。
    # 现在只做「尽量加高」，压小这种事一律不做：加不上就沿用当前缓冲区（Windows Terminal 自己有回滚历史）。
    try {
        $raw = $Host.UI.RawUI
        $w = $raw.WindowSize.Width
        if ($raw.BufferSize.Height -lt 900) {
            $raw.BufferSize = New-Object System.Management.Automation.Host.Size($w, 900)
        }
    } catch { }
}

$script:Rows = @{}
$script:TopRow = 0
$script:Sel = 0

function Draw {
    $W = 74
    $sep = '  ' + ('-' * ($W - 2))
    # 先把光标挪回缓冲区 (0,0)：Clear-Host 只清可视区，不清光标位置，
    # 不归零的话菜单会画在下面几行，鼠标点击就对不上行号了。
    try { $Host.UI.RawUI.CursorPosition = New-Object System.Management.Automation.Host.Coordinates(0, 0) } catch { }
    Clear-Host
    try { $Host.UI.RawUI.CursorPosition = New-Object System.Management.Automation.Host.Coordinates(0, 0) } catch { }
    # 菜单实际从哪一行开始画：点击坐标是「缓冲区绝对行」，所以要按这个基准换算
    $startRow = 0
    try { $startRow = ([ConInput]::CursorY() - [ConInput]::WindowTop()) } catch { $startRow = 0 }
    $rows = @{}
    $lineNo = 0

    $title = T 'GitHub 网络自检' 'GitHub Network Check'
    Write-Host ("  " + $title + "   v1.1.7.5") -ForegroundColor Cyan
    $lineNo++
    Write-Host ("  作者：项目创作时为初中生；需求、判读规则与验收测试由本人完成") -ForegroundColor DarkGray
    $lineNo++
    Write-Host ("  协议：Apache License 2.0　·　原项目 https://github.com/neurotracee/github-netcheck") -ForegroundColor DarkGray
    $lineNo++
    Write-Host ("  镜像：https://gitee.com/bio234/github-netcheck") -ForegroundColor DarkGray
    $lineNo++
    Write-Host $sep -ForegroundColor DarkGray
    $lineNo++

    $langLabel = T '语言：[中文]　点这一行切换成 English' 'Language: [English]　click this line for 中文'
    Write-Host ("  " + $langLabel) -ForegroundColor Yellow
    $rows[$startRow + $lineNo] = 'lang'
    $lineNo++

    Write-Host $sep -ForegroundColor DarkGray
    $lineNo++

    $i = 0
    foreach ($it in $items) {
        $i++
        $text = T $it.zh $it.en
        if ($script:Sel -eq ($i - 1)) {
            Write-Host ("  [$i]  " + $text) -ForegroundColor Black -BackgroundColor Cyan
        } else {
            Write-Host ("  [$i]  " + $text)
        }
        $rows[$startRow + $lineNo] = $it.key
        $lineNo++
    }

    Write-Host ("  [0]  " + (T '退出' 'Quit')) -ForegroundColor DarkGray
    $rows[$startRow + $lineNo] = 'quit'
    $lineNo++

    Write-Host $sep -ForegroundColor DarkGray
    $lineNo++
    Write-Host ("  " + (T '鼠标点、触摸屏点、方向键 + 回车、或直接按数字都行。' 'Click, tap, arrow keys + Enter, or just press a number.')) -ForegroundColor DarkGray
    Write-Host ("  " + (T '每一项都等价于以前输入对应数字，测量全在本机完成。' 'Each item equals typing its number in the old menu; everything runs locally.')) -ForegroundColor DarkGray

    $script:Rows = $rows
    $script:TopRow = 0   # Draw 从缓冲区第 0 行开始画，行号即菜单行号
}

# ---------------- 跑自检：自己收输出、自己在屏幕上打 ----------------
# 不再把子进程的输出直接甩给控制台：那样在某些终端里会被缓冲区吃掉，
# 只剩最后一句提问。这里由菜单自己读子进程的每一行再打印，同时钉一行进度条。
$script:BarOn  = $false
$script:BarRow = 1
$script:BarFill = [string][char]0x2588
$script:BarRest = [string][char]0x2591

# 一条整体进度条，钉在窗口顶部那一行，原地刷新；结果一行行往下走。
function Update-Bar([int]$done, [int]$total) {
    if (-not $script:BarOn) { return }
    try {
        $w = [Console]::WindowWidth
        $sx = [Console]::CursorLeft
        $sy = [Console]::CursorTop
        $p = 0.0
        if ($total -gt 0) { $p = [double]$done / [double]$total }
        $cells = 26
        $fill = [int][Math]::Round($cells * $p)
        if ($fill -lt 0) { $fill = 0 }
        if ($fill -gt $cells) { $fill = $cells }
        [Console]::SetCursorPosition(0, $script:BarRow)
        [Console]::Write((' ' * ($w - 1)))
        [Console]::SetCursorPosition(0, $script:BarRow)
        $old = [Console]::ForegroundColor
        [Console]::Write('  进度 ')
        [Console]::ForegroundColor = 'Cyan'
        [Console]::Write(($script:BarFill * $fill))
        [Console]::ForegroundColor = 'DarkGray'
        [Console]::Write(($script:BarRest * ($cells - $fill)))
        [Console]::ForegroundColor = $old
        [Console]::Write(('   ' + $done + '/' + $total + '   ' + [int]($p * 100) + '%'))
        [Console]::SetCursorPosition($sx, $sy)
    } catch { $script:BarOn = $false }
}

# 把某一行就地改写成新内容（用来把「正在测 XX」换成「XX 的结果」，一条点位只占一行）
function Write-Line-At([int]$row, [string]$text, $color) {
    try {
        $w = [Console]::WindowWidth - 1
        # 按显示宽度裁（中文算 2 列），保证一条点位永远只占一行、不折行
        $sb = New-Object System.Text.StringBuilder
        $acc = 0
        foreach ($ch in $text.ToCharArray()) {
            $code = [int][char]$ch
            $cw = 1
            if (($code -ge 0x1100 -and $code -le 0x115F) -or ($code -ge 0x2E80 -and $code -le 0xA4CF) -or
                ($code -ge 0xAC00 -and $code -le 0xD7A3) -or ($code -ge 0xF900 -and $code -le 0xFAFF) -or
                ($code -ge 0xFE30 -and $code -le 0xFE4F) -or ($code -ge 0xFF00 -and $code -le 0xFF60) -or
                ($code -ge 0xFFE0 -and $code -le 0xFFE6)) { $cw = 2 }
            if ($acc + $cw -gt $w) { break }
            [void]$sb.Append($ch)
            $acc += $cw
        }
        $text = $sb.ToString()
        $sx = [Console]::CursorLeft
        $sy = [Console]::CursorTop
        [Console]::SetCursorPosition(0, $row)
        [Console]::Write((' ' * $w))
        [Console]::SetCursorPosition(0, $row)
        $old = [Console]::ForegroundColor
        if ($color) { [Console]::ForegroundColor = $color }
        [Console]::Write($text)
        [Console]::ForegroundColor = $old
        [Console]::SetCursorPosition($sx, $sy)
        return $true
    } catch { return $false }
}

function Get-LineColor([string]$text) {
    if ($text -match '优秀') { return 'Green' }
    if ($text -match '良好') { return 'Cyan' }
    if ($text -match '一般') { return 'Yellow' }
    if ($text -match '较慢') { return 'DarkYellow' }
    if ($text -match '受限') { return 'Magenta' }
    if ($text -match '不通')  { return 'Red' }
    return $null
}

function Invoke-Check($item) {
    Clear-Host
    Write-Host ('  ' + (T '菜单 v1.1.7.5 · 完整自检' 'Menu v1.1.7.5 - full check')) -ForegroundColor Cyan
    Write-Host ''
    $script:BarRow = 1
    $script:BarOn = $true
    Update-Bar 0 0
    Write-Host ''
    $total = 0; $done = 0
    $reportPath = ''
    $stderrText = ''
    $rowOfTarget = -1
    $mergedName = ''

    try {
        $p = [System.Diagnostics.Process]::Start($psi)
        while ($true) {
            $line = $p.StandardOutput.ReadLine()
            if ($null -eq $line) { break }

            if ($line -match '报告已生成[:：]\s*(.+)$') { $reportPath = $matches[1].Trim(); continue }

            # 进入第一个点位之前，跳过脚本自己的署名/免责/版本那几行，省出屏幕空间给结果
            if ($total -eq 0 -and ($line.Trim() -eq '' -or $line -match '作者：项目创作时|开源协议 Apache|图标含 GitHub|无背书|以上仅为作者立场|GitHub 网络自检 v|检查更新：|^\s*-{5,}')) { continue }

            # 点位头：先打「正在测哪个」，记住这一行，等结果回来就地补成一行
            if ($line -match '^\s*\[(\d+)/(\d+)\]\s+(.+)$') {
                $done = [int]$matches[1]; $total = [int]$matches[2]
                $mergedName = $matches[3].Trim()
                Write-Host $line -ForegroundColor White
                $rowOfTarget = [Console]::CursorTop - 1
                Update-Bar $done $total
                continue
            }

            # 紧跟着的就是这条点位的结果：把它并到点位那一行，一条点位只占一行
            if ($rowOfTarget -ge 0 -and $line.Trim() -ne '') {
                $verdict = $line.Trim()
                $color = Get-LineColor $verdict
                $txt = '  ' + $mergedName + '   ' + $verdict
                if (-not (Write-Line-At $rowOfTarget $txt $color)) {
                    Write-Host $line -ForegroundColor $color
                }
                $rowOfTarget = -1
                $mergedName = ''
                Update-Bar $done $total
                continue
            }

            $color = Get-LineColor $line
            if ($color) { Write-Host $line -ForegroundColor $color } else { Write-Host $line }
            Update-Bar $done $total
        }
        $stderrText = $p.StandardError.ReadToEnd()
        $p.WaitForExit()
        $exitCode = $p.ExitCode
    } catch {
        $script:BarOn = $false
        $exitCode = -1
        Write-Host ('  运行出错：' + $_.Exception.Message) -ForegroundColor Red
    }
    if ($total -gt 0) { Update-Bar $total $total }
    $script:BarOn = $false

    if ($stderrText -and $stderrText.Trim()) {
        Write-Host ''
        Write-Host ('  ' + (T '脚本报错：' 'Script error:')) -ForegroundColor Red
        foreach ($el in ($stderrText -split "`n")) { if ($el.Trim()) { Write-Host ('  ' + $el.TrimEnd()) -ForegroundColor DarkRed } }
    }
    if ($total -eq 0) {
        Write-Host ''
        $tail = T '。把这段截图发给我。' '. Please send me a screenshot of this.'
        Write-Host ('  ' + (T '没跑出任何点位结果，退出码 ' 'No target results; exit code ') + $exitCode + $tail) -ForegroundColor Red
    }

    Write-Host ''
    if ($reportPath) {
        Write-Host ('  ' + (T '报告：' 'Report: ') + $reportPath) -ForegroundColor DarkGray
        Write-Host ''
    }
    Read-Host (T '  回车回到菜单' '  Enter to return to the menu') | Out-Null
    return 'redraw'
}

# ---------------- 执行 ----------------
function Run-Item([string]$key) {
    if ($key -eq 'quit') { return 'quit' }
    if ($key -eq 'lang') {
        $script:Lang = if ($script:Lang -eq 'en') { 'zh' } else { 'en' }
        Save-Lang $script:Lang
        return 'redraw'
    }
    $item = $items | Where-Object { $_.key -eq $key } | Select-Object -First 1
    if (-not $item) { return 'redraw' }
    if (-not (Test-Path -LiteralPath $NetCheck)) {
        Write-Host ''
        Write-Host (T '  当前文件夹里没有 netcheck.ps1，请把菜单脚本与它放在一起。' '  netcheck.ps1 is missing next to this menu.') -ForegroundColor Red
        Read-Host (T '  回车继续' '  Enter to continue') | Out-Null
        return 'redraw'
    }
    if ($item.key -eq 'full' -or $item.key -eq 'quick') {
        return (Invoke-Check $item)
    }
    Clear-Host
    $argLine = ($item.args -join ' ')
    # 跑之前确保缓冲区够高（能加高就加，加不上就算了）：
    # 缓冲区只有一屏高时，自检的十几行结果会被顶掉，只剩最后那句提问。
    FitBuffer
    Write-Host ("  " + (T '正在执行' 'Running') + ': netcheck.ps1 ' + $argLine) -ForegroundColor Cyan
    Write-Host ''
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $NetCheck) + @($item.args)
    & powershell.exe @argList
    Write-Host ''
    Read-Host (T '  回车回到菜单' '  Enter to return to the menu') | Out-Null
    return 'redraw'
}

function Run-Index([int]$n) {
    if ($n -eq 0) { return 'quit' }
    if ($n -lt 1 -or $n -gt $items.Count) { return 'redraw' }
    return (Run-Item $items[$n - 1].key)
}

# ---------------- 主循环 ----------------
$useMouse = $true
try { [ConInput]::Enable() } catch { $useMouse = $false }

FitBuffer
Draw

while ($true) {
    if ($useMouse) { $ev = [ConInput]::Next() }
    else {
        $k = Read-Host (T '  请输入编号' '  Number')
        if ($k -match '^\d+$') { $ev = 'K:0,' + (48 + [int]$k) } else { $ev = 'K:13,13' }
    }

    if ($ev -eq 'R:') { FitBuffer; Draw; continue }
    if ($ev -eq 'E:') { break }

    if ($ev.StartsWith('K:')) {
        $parts = $ev.Substring(2) -split ','
        $vk = [int]$parts[0]; $ch = [int]$parts[1]
        if ($vk -eq 27) { break }
        if ($vk -eq 38) { $script:Sel = ($script:Sel - 1 + $items.Count) % $items.Count; Draw; continue }
        if ($vk -eq 40) { $script:Sel = ($script:Sel + 1) % $items.Count; Draw; continue }
        if ($vk -eq 13) {
            $r = Run-Item $items[$script:Sel].key
            if ($r -eq 'quit') { break }
            Draw; continue
        }
        if ($ch -ge 48 -and $ch -le 57) {
            $r = Run-Index ($ch - 48)
            if ($r -eq 'quit') { break }
            Draw; continue
        }
        continue
    }

    if ($ev.StartsWith('M:')) {
        $p = $ev.Substring(2) -split ','
        $mx = [int]$p[0]; $my = [int]$p[1]; $btn = [int]$p[2]
        if (($btn -band 0x1) -eq 0) { continue }
        $hit = $null
        foreach ($row in $script:Rows.Keys) {
            $visRow = $my - ([ConInput]::WindowTop())
            if ($visRow -eq $row) { $hit = $script:Rows[$row]; break }
        }
        if ($null -eq $hit) {
            if ($env:NC_DEBUG) { "miss mx=$mx my=$my btn=$btn top=$script:TopRow rows=$($script:Rows.Keys -join '/')" | Add-Content 'C:\Users\YB\AppData\Local\Temp\nc4\menu-click.log' }
            FitBuffer; Draw; continue
        }
        if ($env:NC_DEBUG) { "hit mx=$mx my=$my btn=$btn key=$hit" | Add-Content 'C:\Users\YB\AppData\Local\Temp\nc4\menu-click.log' }
        $r = Run-Item $hit
        if ($r -eq 'quit') { break }
        Draw; continue
    }
}

try { [ConInput]::Restore() } catch { }
Clear-Host

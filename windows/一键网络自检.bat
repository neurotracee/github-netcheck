@echo off
chcp 936 >nul
title GitHub - Network Check v1.1.7.4
setlocal enabledelayedexpansion
if not exist "%~dp0netcheck.ps1" (
  echo [错误] 当前文件夹里没有 netcheck.ps1，请把 bat 与 ps1 放在一起
  pause
  exit /b 1
)

rem ---- default: open the clickable menu window; type --console for the old text menu ----
if /i "%~1"=="--console" goto consolemenu
if exist "%~dp0NetCheckMenu.ps1" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0NetCheckMenu.ps1"
  if not errorlevel 1 goto end
)

:consolemenu
cls
echo.
echo   GitHub 网络自检  v1.1.7.4
echo   -----------------------------------------------------------
echo   作者：项目创作时为初中生（只负责提需求、定判读标准、验收与反馈）
echo   开发：豆包工作任务的 Agent x DeepSeek-V4.1-Flash
echo   协议：Apache License 2.0
echo   原项目：https://github.com/neurotracee/github-netcheck
echo   镜像：  https://gitee.com/bio234/github-netcheck
echo   -----------------------------------------------------------
echo    [1] 开始自检（14 个点位，约 30 秒）
echo    [2] 只测 GitHub 系（更快）
echo    [3] 关于与开源信息
echo    [4] 设置（报告位置 / 先问我 / 自动打开 / 自动查更新 / 超时）
echo    [5] 检查更新（只读远端版本号）
echo    [6] 查看日志（本次启动 / 按日期查 / 导出 TXT）
echo    [7] 清除日志（按日期，会问两次）
echo    [0] 退出
echo.
set "choice="
set /p "choice=  请选择："
if "%choice%"=="1" goto full
if "%choice%"=="2" goto quick
if "%choice%"=="3" goto about
if "%choice%"=="4" goto settings
if "%choice%"=="5" goto update
if "%choice%"=="6" goto logs
if "%choice%"=="7" goto clearlogs
if "%choice%"=="0" goto end
goto consolemenu

:full
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1"
echo.
pause
goto consolemenu

:quick
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -Quick
echo.
pause
goto consolemenu

:about
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -About
goto consolemenu

:settings
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -Settings
goto consolemenu

:update
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -CheckUpdate
echo.
pause
goto consolemenu

:logs
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -Logs
goto consolemenu

:clearlogs
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -ClearLogs
echo.
pause
goto consolemenu

:end
endlocal
exit /b 0

@echo off
chcp 936 >nul
title GitHub - Network Check v1.1.0.3
setlocal enabledelayedexpansion
if not exist "%~dp0netcheck.ps1" (
  echo [错误] 这个文件夹里没有 netcheck.ps1，请把 bat 和 ps1 放在一起。
  pause
  exit /b 1
)
:menu
cls
echo.
echo   GitHub 网络自检  v1.1.0.3
echo   -----------------------------------------------------------
echo   作者：项目创作时为初中生（只负责提需求、定判读标准、验收与反馈）
echo   开发：豆包工作任务的 Agent x DeepSeek-V4.1-Flash（Agent 属豆包，模型属 DeepSeek）
echo   协议：Apache License 2.0
echo   原项目：https://github.com/neurotracee/netcheck
echo   镜像：  https://gitee.com/bio234/netcheck
echo   -----------------------------------------------------------
echo    [1] 开始自检（14 个点位，约 30 秒）
echo    [2] 只测 GitHub 系（更快）
echo    [3] 关于与开源信息
echo    [4] 设置（报告位置 / 自动打开 / 超时）
echo    [5] 检查更新（只读远端版本号）
echo    [0] 退出
echo.
set "choice="
set /p "choice=   请选择："
if "%choice%"=="1" goto full
if "%choice%"=="2" goto quick
if "%choice%"=="3" goto about
if "%choice%"=="4" goto settings
if "%choice%"=="5" goto update
if "%choice%"=="0" goto end
goto menu
:full
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1"
echo.
pause
goto menu
:quick
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -Quick
echo.
pause
goto menu
:about
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -About
goto menu
:settings
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1" -Settings
goto menu
:endendlocal
exit /b 0
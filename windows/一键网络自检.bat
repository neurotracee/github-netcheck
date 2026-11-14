@echo off
chcp 936 >nul
title Net Check - GitHub / HuggingFace / Docker
if not exist "%~dp0netcheck.ps1" (
  echo [ERROR] netcheck.ps1 not found in this folder.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0netcheck.ps1"
echo.
echo Done. Press any key to close.
pause >nul
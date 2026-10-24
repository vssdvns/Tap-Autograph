@echo off
cd /d "%~dp0"
node --version >nul 2>&1
if errorlevel 1 (
  echo Install Node.js 20 or newer, then reopen this file.
  pause
  exit /b 1
)
node server.cjs
pause

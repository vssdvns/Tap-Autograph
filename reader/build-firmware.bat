@echo off
set "PLATFORMIO_SETTING_ENABLE_TELEMETRY=no"
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe goto missing
.venv\Scripts\python.exe -m platformio run -d firmware
pause
exit /b
:missing
echo Run setup-tools.bat first.
pause

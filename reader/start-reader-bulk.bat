@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe goto missing
echo Start the merchant server first and close Arduino Serial Monitor.
.venv\Scripts\python.exe merchant\bridge\reader.py --list
set /p "TAP_PORT=Enter the ESP32 COM port (example COM5): "
.venv\Scripts\python.exe merchant\bridge\reader.py --port "%TAP_PORT%" --poll-ms 1 --fast-usb --spi-bulk
pause
exit /b
:missing
echo Run setup-tools.bat first.
pause

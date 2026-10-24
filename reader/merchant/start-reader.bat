@echo off
cd /d "%~dp0"
echo Start the merchant server first. Close Arduino Serial Monitor before connecting.
echo.
py -3 bridge\reader.py --list
set /p "TAP_PORT=Enter your ESP32 COM port (example COM5): "
py -3 bridge\reader.py --port "%TAP_PORT%"
pause

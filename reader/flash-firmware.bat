@echo off
set "PLATFORMIO_SETTING_ENABLE_TELEMETRY=no"
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe goto missing
echo This profile targets classic ESP32-WROOM-32 dev boards, NOT ESP32-S3/C3.
echo Verify the board model and close the reader bridge/Serial Monitor first.
echo Uploading replaces the firmware on the selected board.
set /p "TAP_FLASH=Type FLASH to continue: "
if /i not "%TAP_FLASH%"=="FLASH" exit /b
set /p "TAP_FLASH_PORT=Enter the ESP32 COM port (example COM5): "
.venv\Scripts\python.exe -m platformio run -d firmware -t upload --upload-port "%TAP_FLASH_PORT%"
pause
exit /b
:missing
echo Run setup-tools.bat first.
pause

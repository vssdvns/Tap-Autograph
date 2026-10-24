@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe goto missing
.venv\Scripts\python.exe merchant\bridge\reader.py --simulate merchant\nfc\fixtures\large.json
pause
exit /b
:missing
echo Run setup-tools.bat first.
pause

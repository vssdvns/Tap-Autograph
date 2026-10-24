@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe goto missing
echo Close the reader bridge and Arduino Serial Monitor first.
echo No merchant server or phone is needed for this check.
.venv\Scripts\python.exe diagnose-reader.py
pause
exit /b
:missing
echo Run setup-tools.bat first.
pause

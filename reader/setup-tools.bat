@echo off
cd /d "%~dp0"
echo Requires Python 3.10+ installed. Python 3.11 or 3.12 recommended.
py -3 -m venv .venv
if errorlevel 1 goto fail
.venv\Scripts\python.exe -m pip install -r requirements-tools.txt
if errorlevel 1 goto fail
echo Ready. Run start-merchant.bat, then simulate-reader.bat to test today.
pause
exit /b 0
:fail
echo Setup failed. Copy the error above for troubleshooting.
pause
exit /b 1

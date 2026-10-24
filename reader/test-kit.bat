@echo off
cd /d "%~dp0"
set "TAP_PYTHON=%~dp0.venv\Scripts\python.exe"
"%TAP_PYTHON%" -m unittest discover -s merchant\bridge -p "test_*.py" -v
if errorlevel 1 goto fail
cd merchant
node --test
if errorlevel 1 goto fail
echo Tests passed.
pause
exit /b 0
:fail
echo Tests failed. See the error above.
pause
exit /b 1

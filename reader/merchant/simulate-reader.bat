@echo off
cd /d "%~dp0"
echo Start the merchant server and open localhost:8787/nfc-lab first.
py -3 bridge\reader.py --simulate nfc\fixtures\large.json
pause

@echo off
chcp 65001 >nul
title Голосовой ассистент — сервер

cd /d "%~dp0backend"

if not exist ".env" (
    echo  Файл .env не найден. Сначала запусти ЗАПУСТИТЬ_НАСТРОЙКУ.bat
    pause
    exit /b
)

echo  Запускаю бэкенд на http://0.0.0.0:8000
echo  Нажми Ctrl+C чтобы остановить.
echo.

python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload

pause

@echo off
chcp 65001 >nul
title Голосовой ассистент — настройка

echo.
echo  Проверяю Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo  Python не найден! Устанавливаю через winget...
    winget install -e --id Python.Python.3.12
    echo  Перезапусти этот файл после установки Python.
    pause
    exit /b
)

echo  Python найден. Запускаю мастер настройки...
echo.

cd /d "%~dp0backend"
python setup_wizard.py

pause

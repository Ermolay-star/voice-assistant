@echo off
chcp 65001 >nul
title Загрузка на GitHub

echo.
echo  ══════════════════════════════════════
echo   Загрузка проекта на GitHub
echo  ══════════════════════════════════════
echo.

:: Проверяем git
git --version >nul 2>&1
if errorlevel 1 (
    echo  Git не установлен. Устанавливаю через winget...
    winget install -e --id Git.Git
    echo  Перезапусти этот файл после установки Git.
    pause
    exit /b
)

cd /d "%~dp0"

:: Инициализация если нужно
if not exist ".git" (
    git init
    git branch -M main
)

:: Спрашиваем ссылку на репозиторий
echo  Шаг 1. Создай ПУСТОЙ репозиторий на https://github.com/new
echo         (без README, без .gitignore)
echo.
set /p REPO_URL= Вставь ссылку на репозиторий (https://github.com/ИМЯ/РЕПО): 

if "%REPO_URL%"=="" (
    echo  Ссылка не введена. Выход.
    pause
    exit /b
)

:: Настраиваем remote
git remote remove origin >nul 2>&1
git remote add origin %REPO_URL%

:: Добавляем файлы и коммитим
git add .
git commit -m "Initial commit: Voice Assistant"

:: Пушим
echo.
echo  Загружаю на GitHub...
git push -u origin main

if errorlevel 1 (
    echo.
    echo  Если спросил логин — введи имя пользователя и Personal Access Token.
    echo  Токен создать: https://github.com/settings/tokens/new
    echo  Нужные права: repo
) else (
    echo.
    echo  ✓ Загружено! GitHub Actions начнёт сборку APK автоматически.
    echo.
    echo  Открываю страницу Actions...
    start %REPO_URL%/actions
)

pause

#!/usr/bin/env python3
"""Мастер настройки. Запусти: python setup_wizard.py"""

import os, sys, subprocess, webbrowser, asyncio
from pathlib import Path

ENV_FILE = Path(__file__).parent / ".env"


def ask(prompt, default=""):
    val = input(f"  → {prompt}{f' [{default}]' if default else ''}: ").strip()
    return val or default


def load_env():
    env = {}
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                env[k.strip()] = v.strip()
    return env


def save_env(env):
    ENV_FILE.write_text("\n".join(f"{k}={v}" for k, v in env.items()) + "\n", encoding="utf-8")
    print(f"  ✓ Сохранено в {ENV_FILE}")


def step_install():
    print("\n── Установка зависимостей ──")
    subprocess.run([sys.executable, "-m", "pip", "install", "-r", "requirements.txt", "-q"])
    print("  ✓ Готово")


def step_gigachat(env):
    print("\n── GigaChat API ──")
    existing = env.get("GIGACHAT_AUTH_KEY", "")
    if existing and len(existing) > 20:
        print(f"  ✓ Ключ уже есть: {existing[:16]}…")
        if ask("Изменить?", "нет").lower() in ("нет", "н", "n", "no"):
            return env

    webbrowser.open("https://developers.sber.ru/portal/products/gigachat-api")
    print("  Открыл сайт Сбера в браузере.")
    print("  Нажми «Получить ключ» → скопируй Authorization key")

    while True:
        key = ask("Вставь ключ")
        if len(key) > 20:
            env["GIGACHAT_AUTH_KEY"] = key
            print("  ✓ Ключ принят")
            break
        print("  ! Слишком короткий, попробуй ещё раз")
    return env


async def step_verify(env):
    print("\n── Проверка ключа ──")
    import httpx, uuid
    key = env.get("GIGACHAT_AUTH_KEY", "")
    try:
        async with httpx.AsyncClient(verify=False) as c:
            r = await c.post(
                "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={"Authorization": f"Basic {key}", "RqUID": str(uuid.uuid4()),
                         "Content-Type": "application/x-www-form-urlencoded"},
                data={"scope": "GIGACHAT_API_PERS"},
            )
            token = r.json()["access_token"]
        async with httpx.AsyncClient(verify=False) as c:
            r = await c.post(
                "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
                headers={"Authorization": f"Bearer {token}"},
                json={"model": "GigaChat", "messages": [{"role": "user", "content": "Скажи: тест пройден"}], "max_tokens": 20},
                timeout=15,
            )
            answer = r.json()["choices"][0]["message"]["content"].strip()
        print(f"  ✓ GigaChat: {answer}")
    except Exception as e:
        print(f"  ✗ Ошибка: {e}")


def step_start():
    print("\n── Запуск сервера ──")
    if ask("Запустить сейчас?", "да").lower() in ("да", "д", "y", "yes"):
        os.chdir(Path(__file__).parent)
        os.execv(sys.executable, [sys.executable, "-m", "uvicorn", "main:app",
                                   "--host", "0.0.0.0", "--port", "8000", "--reload"])
    else:
        print("  Запусти вручную: uvicorn main:app --host 0.0.0.0 --port 8000")


async def main():
    print("╔══════════════════════════════════╗")
    print("║  Голосовой ассистент — Настройка  ║")
    print("╚══════════════════════════════════╝")
    env = load_env()
    step_install()
    env = step_gigachat(env)
    save_env(env)
    await step_verify(env)
    print("\n✓ Настройка завершена!\n")
    step_start()


if __name__ == "__main__":
    asyncio.run(main())

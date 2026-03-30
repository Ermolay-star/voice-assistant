import os
import uuid
import httpx
from dotenv import load_dotenv

load_dotenv()

GIGACHAT_AUTH_KEY = os.getenv("GIGACHAT_AUTH_KEY", "")
TOKEN_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
CHAT_URL  = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"

_SYSTEM = {
    "role": "system",
    "content": (
        "Ты голосовой ассистент. Отвечай на русском языке, кратко и по делу. "
        "Максимум 3 предложения, если пользователь не просит подробностей."
    ),
}


async def _get_token() -> str:
    async with httpx.AsyncClient(verify=False) as c:
        r = await c.post(
            TOKEN_URL,
            headers={
                "Authorization": f"Basic {GIGACHAT_AUTH_KEY}",
                "RqUID": str(uuid.uuid4()),
                "Content-Type": "application/x-www-form-urlencoded",
            },
            data={"scope": "GIGACHAT_API_PERS"},
        )
        r.raise_for_status()
        return r.json()["access_token"]


async def ask_ai(messages: list[dict]) -> str:
    """
    messages — список {"role": "user"/"assistant", "content": "..."}
    Системный промпт добавляется автоматически.
    """
    if not GIGACHAT_AUTH_KEY:
        raise RuntimeError("GIGACHAT_AUTH_KEY не задан в .env")

    token = await _get_token()
    payload = [_SYSTEM] + messages

    async with httpx.AsyncClient(verify=False) as c:
        r = await c.post(
            CHAT_URL,
            headers={"Authorization": f"Bearer {token}"},
            json={"model": "GigaChat", "messages": payload, "temperature": 0.7, "max_tokens": 512},
            timeout=30.0,
        )
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"].strip()

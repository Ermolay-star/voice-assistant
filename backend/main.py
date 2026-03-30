from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from ai_service import ask_ai

app = FastAPI(title="Voice Assistant")


class ChatMessage(BaseModel):
    role: str       # "user" | "assistant"
    content: str


class AskRequest(BaseModel):
    messages: list[ChatMessage]


class AskResponse(BaseModel):
    answer: str


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/ask", response_model=AskResponse)
async def ask_endpoint(req: AskRequest):
    try:
        history = [{"role": m.role, "content": m.content} for m in req.messages]
        answer = await ask_ai(history)
        return AskResponse(answer=answer)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

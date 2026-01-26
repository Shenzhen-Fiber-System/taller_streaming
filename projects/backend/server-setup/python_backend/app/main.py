from fastapi import FastAPI
from contextlib import asynccontextmanager
from app.database import init_db
from app.routers import streams, webrtc
from app.config import get_settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await init_db()
    yield
    # Shutdown logic (close db pools, etc)

app = FastAPI(
    title="Python Streaming Backend",
    version="1.0.0",
    lifespan=lifespan
)

settings = get_settings()

app.include_router(streams.router)
app.include_router(webrtc.router)

@app.get("/actuator/health")
async def health_check():
    return {"status": "UP"}

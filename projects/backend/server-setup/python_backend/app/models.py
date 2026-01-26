from datetime import datetime
from enum import Enum
from typing import Optional
from uuid import UUID, uuid4
from sqlmodel import Field, SQLModel

class StreamStatus(str, Enum):
    CREATED = "CREATED"
    LIVE = "LIVE"
    ENDED = "ENDED"

class StreamMeta(SQLModel, table=True):
    __tablename__ = "stream_meta"

    id: Optional[str] = Field(default_factory=lambda: str(uuid4()), primary_key=True, max_length=36)
    stream_key: str = Field(unique=True, index=True, max_length=64)
    title: str = Field(max_length=200)
    description: Optional[str] = Field(default=None, max_length=2000)
    status: StreamStatus = Field(default=StreamStatus.CREATED, max_length=16)
    
    created_at: datetime = Field(default_factory=datetime.utcnow)
    started_at: Optional[datetime] = None
    ended_at: Optional[datetime] = None

# DTOs para Request/Response simplificados con herencia o modelos separados
class StreamCreate(SQLModel):
    title: str
    description: Optional[str] = None

class StreamUpdateInfo(SQLModel):
    title: Optional[str] = None
    description: Optional[str] = None

class StreamPublicResponse(SQLModel):
    id: str
    stream_key: str
    title: str
    description: Optional[str]
    status: StreamStatus
    created_at: datetime
    started_at: Optional[datetime]
    ended_at: Optional[datetime]

from datetime import datetime
from uuid import uuid4
from fastapi import HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlmodel import select

from app.models import StreamMeta, StreamStatus, StreamCreate

async def create_stream(session: AsyncSession, stream_in: StreamCreate) -> StreamMeta:
    # Generar stream_key aleatorio
    stream_key = str(uuid4()).replace("-", "")[:12]
    
    new_stream = StreamMeta(
        title=stream_in.title,
        description=stream_in.description,
        stream_key=stream_key,
        status=StreamStatus.CREATED
    )
    session.add(new_stream)
    await session.commit()
    await session.refresh(new_stream)
    return new_stream

async def get_stream(session: AsyncSession, stream_id: str) -> StreamMeta:
    statement = select(StreamMeta).where(StreamMeta.id == stream_id)
    result = await session.execute(statement)
    stream = result.scalar_one_or_none()
    if not stream:
        raise HTTPException(status_code=404, detail="Stream no encontrado")
    return stream

async def get_streams(session: AsyncSession, skip: int = 0, limit: int = 20):
    statement = select(StreamMeta).offset(skip).limit(limit).order_by(StreamMeta.created_at.desc())
    result = await session.execute(statement)
    return result.scalars().all()

async def start_stream(session: AsyncSession, stream_id: str) -> StreamMeta:
    stream = await get_stream(session, stream_id)
    
    if stream.status != StreamStatus.CREATED:
        raise HTTPException(
            status_code=409, 
            detail=f"No se puede iniciar stream en estado {stream.status}. Debe estar CREATED."
        )
        
    stream.status = StreamStatus.LIVE
    stream.started_at = datetime.utcnow()
    
    session.add(stream)
    await session.commit()
    await session.refresh(stream)
    return stream

async def end_stream(session: AsyncSession, stream_id: str) -> StreamMeta:
    stream = await get_stream(session, stream_id)
    
    if stream.status != StreamStatus.LIVE:
        raise HTTPException(
            status_code=409, 
            detail=f"No se puede finalizar stream en estado {stream.status}. Debe estar LIVE."
        )
        
    stream.status = StreamStatus.ENDED
    stream.ended_at = datetime.utcnow()
    
    session.add(stream)
    await session.commit()
    await session.refresh(stream)
    return stream

from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.models import StreamMeta, StreamCreate, StreamPublicResponse
from app.services import stream_service

router = APIRouter(prefix="/api/v1/streams", tags=["streams"])

@router.post("/", response_model=StreamPublicResponse)
async def create_stream_endpoint(
    stream_in: StreamCreate, 
    session: AsyncSession = Depends(get_session)
):
    return await stream_service.create_stream(session, stream_in)

@router.get("/", response_model=List[StreamPublicResponse])
async def list_streams(
    skip: int = 0, 
    limit: int = 20, 
    session: AsyncSession = Depends(get_session)
):
    return await stream_service.get_streams(session, skip, limit)

@router.get("/{stream_id}", response_model=StreamPublicResponse)
async def get_stream_detail(
    stream_id: str, 
    session: AsyncSession = Depends(get_session)
):
    return await stream_service.get_stream(session, stream_id)

@router.put("/{stream_id}/start", response_model=StreamPublicResponse)
async def start_stream_endpoint(
    stream_id: str, 
    session: AsyncSession = Depends(get_session)
):
    return await stream_service.start_stream(session, stream_id)

@router.put("/{stream_id}/end", response_model=StreamPublicResponse)
async def end_stream_endpoint(
    stream_id: str, 
    session: AsyncSession = Depends(get_session)
):
    return await stream_service.end_stream(session, stream_id)

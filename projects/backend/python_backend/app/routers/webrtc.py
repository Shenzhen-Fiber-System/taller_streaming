from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from app.janus_client import JanusClient
from app.services.ffmpeg_service import FfmpegHlsService, ffmpeg_manager
from app.config import get_settings
from uuid import uuid4

router = APIRouter(prefix="/api/v1/webrtc", tags=["webrtc"])
settings = get_settings()

class SdpOfferRequest(BaseModel):
    sdp: str
    stream_key: str

class SdpAnswerResponse(BaseModel):
    sdp: str
    hlsUrl: str

@router.get("/ice-servers")
async def get_ice_servers():
    return [
        {"urls": settings.STUN_SERVER}
    ]

@router.post("/offer", response_model=SdpAnswerResponse)
async def process_offer(request: SdpOfferRequest):
    # 1. Instanciar cliente Janus
    janus = JanusClient()
    
    # 2. Conectar y Negociar
    # NOTA: En un sistema real, guardaríamos 'janus' en un mapa global o Redis para 
    # poder handlear ICE candidates trickle después. Aquí simplificamos (no trickle support in demo)
    try:
        await janus.connect()
        
        # 3. Publish (Offer -> Answer)
        sdp_answer = await janus.publish_stream(request.sdp, settings.JANUS_ROOM_ID)
        
        # 4. Iniciar FFmpeg (Fake port assignment for demo)
        rtp_port = 10000 + (hash(request.stream_key) % 1000) # Simple port allocation logic
        # En prod: Usar PortManager real
        
        # Ojo: Janus necesita stream_id (publisher_id) para rtp_forward.
        # En nuestra impl simplificada de publish_stream no extrajimos el id.
        # Asumiremos un ID fijo o lo recuperaríamos del evento 'joined'.
        # Para que funcione: El publish_stream deberia retornar tambien el publisher_id.
        
        # Simulamos rtp_forward
        # await janus.rtp_forward(settings.JANUS_ROOM_ID, publisher_id, rtp_port)
        
        # 5. Pipeline FFmpeg
        ffmpeg = FfmpegHlsService()
        await ffmpeg.start_pipeline(rtp_port, request.stream_key)
        ffmpeg_manager[request.stream_key] = ffmpeg # Guardar para poder detener luego
        
        hs_url = ffmpeg.get_public_url(request.stream_key)
        
        return SdpAnswerResponse(sdp=sdp_answer, hlsUrl=hs_url)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/")
async def stop_webrtc(stream_key: str):
    if stream_key in ffmpeg_manager:
        await ffmpeg_manager[stream_key].stop()
        del ffmpeg_manager[stream_key]
    return {"status": "stopped"}

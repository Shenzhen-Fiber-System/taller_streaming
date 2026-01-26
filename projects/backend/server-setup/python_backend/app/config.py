from pydantic_settings import BaseSettings
from functools import lru_cache

class Settings(BaseSettings):
    # App
    PORT: int = 8087
    
    # Database
    DATABASE_URL: str = "mysql+aiomysql://root:password123@localhost:3306/streamingdb"
    
    # Janus
    JANUS_URL: str = "http://localhost:8088/janus"
    JANUS_ROOM_ID: int = 1234
    JANUS_API_TIMEOUT: int = 30
    
    # WebRTC HLS
    WEBRTC_HLS_OUTPUT_DIR: str = "./data/webrtc-hls"
    WEBRTC_HLS_PUBLIC_BASE_URL: str = ""
    
    # ICE Servers
    STUN_SERVER: str = "stun:stun.l.google.com:19302"
    TURN_SERVER: str = ""
    TURN_USERNAME: str = ""
    TURN_CREDENTIAL: str = ""

    class Config:
        env_file = ".env"
        # Mapeo de variables de entorno de estilo Java a Python si es necesario
        # o simplemente usar las mismas claves en el .env
        extra = "ignore"

@lru_cache
def get_settings():
    return Settings()

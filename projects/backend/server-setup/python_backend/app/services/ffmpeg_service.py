import asyncio
import os
import logging
from app.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

class FfmpegHlsService:
    def __init__(self):
        self.process = None

    async def start_pipeline(self, rtp_port: int, stream_key: str):
        output_dir = f"{settings.WEBRTC_HLS_OUTPUT_DIR}/{stream_key}"
        os.makedirs(output_dir, exist_ok=True)
        hls_playlist = f"{output_dir}/index.m3u8"

        # Comando FFmpeg (simplificado para el ejemplo)
        # En producción: Ajustar params complejos
        cmd = [
            "ffmpeg",
            "-protocol_whitelist", "file,udp,rtp",
            "-i", f"rtp://127.0.0.1:{rtp_port}",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-hls_time", "2",
            "-hls_list_size", "5",
            "-hls_flags", "delete_segments",
            hls_playlist
        ]
        
        logger.info(f"Starting FFmpeg: {' '.join(cmd)}")
        
        self.process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE
        )
        
        # No esperamos (await) a que termine, porque es un proceso largo.
        # Podemos leer stderr en background task si queremos logs.
        asyncio.create_task(self._log_stderr())

    async def stop(self):
        if self.process:
            try:
                self.process.terminate()
                await self.process.wait()
                logger.info("FFmpeg stopped")
            except Exception as e:
                logger.error(f"Error stopping FFmpeg: {e}")

    async def _log_stderr(self):
        if self.process and self.process.stderr:
            while True:
                line = await self.process.stderr.readline()
                if not line:
                    break
                # logger.debug(f"FFmpeg Log: {line.decode().strip()}")

    def get_public_url(self, stream_key: str) -> str:
        # Construye URL basado en settings
        base = settings.WEBRTC_HLS_PUBLIC_BASE_URL
        if not base:
             # Fallback local relative
             return f"/webrtc-hls/{stream_key}/index.m3u8"
        return f"{base}/{stream_key}/index.m3u8"

ffmpeg_manager = {} # Simple in-memory storage of service instances by stream_id for demo

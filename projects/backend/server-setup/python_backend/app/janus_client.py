import httpx
import asyncio
import logging
from app.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

class JanusClient:
    def __init__(self):
        self.base_url = settings.JANUS_URL
        self.session_id = None
        self.handle_id = None
        self.client = httpx.AsyncClient()

    async def connect(self):
        # 1. Create Session
        response = await self.client.post(self.base_url, json={"janus": "create", "transaction": "create_tx"})
        data = response.json()
        if data["janus"] != "success":
            raise Exception(f"Error creating Janus session: {data}")
        self.session_id = data["data"]["id"]
        logger.info(f"Janus Session Created: {self.session_id}")

        # 2. Attach VideoRoom Plugin
        attach_body = {
            "janus": "attach",
            "plugin": "janus.plugin.videoroom",
            "transaction": "attach_tx"
        }
        res_attach = await self.client.post(f"{self.base_url}/{self.session_id}", json=attach_body)
        data_attach = res_attach.json()
        if data_attach["janus"] != "success":
             raise Exception(f"Error attaching plugin: {data_attach}")
        self.handle_id = data_attach["data"]["id"]
        logger.info(f"Janus Handle Attached: {self.handle_id}")

    async def publish_stream(self, sdp_offer: str, room_id: int):
        """
        Envía 'joinandconfigure' con el Offer y espera (polling) por el Answer JSEP
        """
        if not self.session_id or not self.handle_id:
            await self.connect()

        # Enviar Join & Configure
        request = {
            "janus": "message",
            "body": {
                "request": "joinandconfigure",
                "room": room_id,
                "ptype": "publisher",
                "display": "python_publisher",
                "audiocodec": "opus",
                "videocodec": "h264"
            },
            "jsep": {
                "type": "offer",
                "sdp": sdp_offer
            },
            "transaction": "publish_tx"
        }
        
        url = f"{self.base_url}/{self.session_id}/{self.handle_id}"
        resp = await self.client.post(url, json=request)
        ack = resp.json()
        if ack["janus"] != "ack":
            raise Exception(f"Expected ack, got {ack}")

        # Long Polling loop para esperar el evento 'configured' con el JSEP Answer
        # En Python esto es un simple loop async
        max_retries = 30
        for _ in range(max_retries):
            # Poll endpoint
            poll_url = f"{self.base_url}/{self.session_id}?maxev=1&rid={int(asyncio.get_event_loop().time() * 1000)}"
            # Usamos un timeout corto para el poll si queremos ser ágiles, o largo si janus soporta long-polling real
            try:
                poll_resp = await self.client.get(poll_url, timeout=2.0)
                if poll_resp.status_code != 200:
                    continue
                events = poll_resp.json()
                if not isinstance(events, list):
                    events = [events]
                
                for event in events:
                    if event.get("janus") == "event":
                        plugindata = event.get("plugindata", {}).get("data", {})
                        if plugindata.get("videoroom") == "event" and plugindata.get("configured") == "ok":
                            # BINGO! Tenemos respuesta
                            if "jsep" in event:
                                return event["jsep"]["sdp"] # Retornamos el SDP Answer String
            except Exception as e:
                # logger.warning(f"Polling error (retrying): {e}")
                pass
            
            await asyncio.sleep(0.5)

        raise Exception("Timeout esperando Janus Answer")

    async def rtp_forward(self, room_id: int, stream_id_janus: int, port: int):
        """ Configura el reenvío RTP a localhost:<port> """
        body = {
            "request": "rtp_forward",
            "room": room_id,
            "publisher_id": stream_id_janus,
            "host": "127.0.0.1",
            "audio_port": port,
            "video_port": port, # Multiplexado o separado, depende configuración. Simplificado aqui
            "secret": "" 
        }
        # En una impl real revisaríamos documentación exacta (video_port y audio_port suelen ser distintos)
        # Para el taller simplificado asumimos video
        
        # Ojo: Janus rtp_forward suele requerir puertos distintos para audio y video
        # Ajustamos para enviar solo video por ahora o user puertos consecutivos
        # body["audio_port"] = port + 2
        
        request = {
            "janus": "message",
            "body": body,
            "transaction": "rtp_fwd_tx"
        }
        url = f"{self.base_url}/{self.session_id}/{self.handle_id}"
        await self.client.post(url, json=request) 
        logger.info(f"RTP Forward configured to port {port}")

    async def close(self):
        await self.client.aclose()

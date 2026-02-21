# Guía relámpago (2h) — Backend Streaming

Objetivo: explicar **qué se agregó** al backend para WebRTC + Janus + HLS, y dejar el entorno listo para usar el servidor remoto del taller.

---

## 1) Qué cambios se integraron en este repo

Se incorporó el paquete `webrtc` completo en `projects/backend/src/main/java/com/ourshop/streaming/webrtc`:

- **config/**
  - `WebRtcConfig`: lectura de variables WebRTC/ICE/TURN.

- **controller/**
  - `WebRtcSignalingController`: negociación SDP y trickle ICE del publisher.
  - `WebRtcIceServersController`: health WebRTC e ICE servers para cliente.
  - `WebRtcHlsController`: exposición de playlist y segmentos HLS.

- **service/**
  - `WebRtcSignalingService`: orquestación principal del flujo offer/answer, sesión Janus, estado stream, colas ICE y arranque/parada HLS.

- **janus/**
  - `JanusClient`: cliente HTTP para Janus (`create`, `attach`, `message`, `trickle`, `keepalive`, `destroy`).

- **hls/**
  - `FfmpegHlsService`: manejo de proceso FFmpeg, generación de `index.m3u8` + segmentos `.ts`, restart resiliente.
  - `SdpRtpForwardParser`: parseo SDP/puertos para rtp_forward.

- **repo/**
  - `StreamSessionRepository`, `R2dbcStreamSessionRepository`.

- **model/**
  - `StreamSession`, `StreamSessionRole`, `StreamSessionStatus`.

- **dto/**
  - `SdpOfferRequest`, `SdpAnswerResponse`, `IceCandidateRequest`, `IceServersResponse`, `TurnCredentialsResponse`, `CreateStreamSessionRequest`, `StreamSessionResponse`, `WebRtcHealthResponse`.

- **errors/**
  - `StreamSessionNotFoundException`, `UnsupportedWebRtcRoleException`.

Además:

- Se actualizó `projects/backend/src/main/resources/application.yml` con bloques `janus` y `webrtc`.
- Se ajustó `projects/backend/.env.example` para usar endpoints remotos del taller.

---

## 2) Variables clave para el taller (servidor remoto)

Usar estos valores en `.env` del backend:

```dotenv
JANUS_URL=https://taller.ourshop.work/janus
WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls
```

También:

```dotenv
JANUS_ROOM_ID=1234
WEBRTC_ENABLED=true
```

Notas importantes:

- El backend quedó robusto para aceptar `JANUS_URL` con o sin sufijo `/janus`.
- El backend quedó robusto para aceptar `WEBRTC_HLS_PUBLIC_BASE_URL` con o sin sufijo `/webrtc-hls` (evita duplicados en URL final).

---

## 3) Endpoints nuevos a mostrar en vivo

1. `GET /api/v1/webrtc/health`
   - Verifica si WebRTC está habilitado y disponibilidad básica.

2. `GET /api/v1/webrtc/ice-servers`
   - Devuelve arreglo `iceServers` (STUN/TURN) para el cliente publisher/viewer.

3. `POST /api/v1/streams/{streamId}/webrtc/offer`
   - Recibe SDP offer del publisher y devuelve SDP answer + HLS URL.

4. `POST /api/v1/streams/{streamId}/webrtc/ice`
   - Recibe candidatos ICE del cliente (trickle ICE).

5. `DELETE /api/v1/streams/{streamId}/webrtc`
   - Cierra sesión publisher y limpia recursos.

6. `GET /webrtc-hls/{streamKey}/index.m3u8`
   - Playlist HLS.

7. `GET /webrtc-hls/{streamKey}/{segment}.ts`
   - Segmentos HLS.

---

## 4) Guion de exposición (2 horas)

### Bloque A (20 min): Arquitectura

- Explicar que el video **no** pasa por el controlador REST; REST solo hace signaling.
- Flujo: Publisher -> Backend signaling -> Janus -> RTP Forward -> FFmpeg -> HLS -> Viewer.

### Bloque B (25 min): Código nuevo por capas

- `controller` (entrada HTTP), `service` (orquestación), `janus` (API externa), `hls` (proceso local), `repo/model` (estado de sesión).
- Enfatizar separación de responsabilidades y por qué facilita debug.

### Bloque C (25 min): Configuración remota

- Mostrar `.env` con `JANUS_URL` y `WEBRTC_HLS_PUBLIC_BASE_URL` en `taller.ourshop.work`.
- Explicar por qué localhost no sirve para audiencia externa (NAT/firewall/alcance público).

### Bloque D (25 min): Demo técnica

- Crear stream (CRUD existente), levantar publisher, revisar `offer`/`ice`.
- Abrir URL HLS y confirmar reproducción.
- Mostrar logs de backend + FFmpeg para correlacionar eventos.

### Bloque E (25 min): Troubleshooting real

- Caso 1: no llega answer SDP (revisar Janus URL y room).
- Caso 2: llega answer pero no video (revisar rtp_forward host/puertos y FFmpeg).
- Caso 3: URL HLS mal formada (base URL duplicada o incorrecta).

---

## 5) Checklist rápido pre-taller

- [ ] `mvnw.cmd -DskipTests compile` en `projects/backend`.
- [ ] `.env` con URLs remotas correctas.
- [ ] `GET /actuator/health` OK.
- [ ] `GET /api/v1/webrtc/health` OK.
- [ ] Endpoint ICE responde y muestra STUN/TURN esperado.
- [ ] Prueba completa de `offer` + reproducción HLS.

---

## 6) Material fuente que se tomó en cuenta

- Carpeta de referencia externa: `C:/ecommerce/streaming/guion`.
- Se recomienda usar especialmente:
  - `GUION_TALLER.md`
  - `PRERREQUISITOS_TALLER.md`
  - `EXPLICACION_ARQUITECTURA.md`

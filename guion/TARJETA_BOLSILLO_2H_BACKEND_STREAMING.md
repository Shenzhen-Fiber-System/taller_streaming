# Tarjeta de bolsillo (2h) — Backend Streaming

> Uso: mantener abierta al lado del teleprompter.  
> Formato: palabra disparadora -> idea clave -> acción.

---

## 0) Apertura (2 min)

- **Objetivo** -> entender cambios WebRTC/Janus/HLS -> “hoy no construimos todo, hoy operamos con criterio”.
- **Resultado** -> arquitectura clara + demo + troubleshooting.

---

## A) Arquitectura (20 min)

- **Control vs Media** -> REST = signaling, video = WebRTC/RTP/HLS.
- **Flujo 7 pasos** -> Offer -> Janus -> Answer -> ICE -> RTP Forward -> FFmpeg -> HLS.
- **Servidor remoto** -> localhost no sirve para audiencia externa.
- **URLs clave** -> `JANUS_URL=https://taller.ourshop.work/janus` y `WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls`.

**Frase gatillo:** “Dos planos: control estable y media observable.”

---

## B) Código por capas (25 min)

- **Controller** -> entrada HTTP (`offer`, `ice`, `health`, `hls files`).
- **Service** -> `WebRtcSignalingService` orquesta todo.
- **Janus Client** -> sesión, attach, message, trickle, keepalive.
- **HLS Service** -> proceso FFmpeg + playlist + segmentos + restart.
- **Repo/Model** -> estado de sesión para trazabilidad.

**Frase gatillo:** “Cada carpeta tiene una responsabilidad operativa.”

---

## C) Config remota (25 min)

- **Variables mínimas** -> `JANUS_URL`, `WEBRTC_HLS_PUBLIC_BASE_URL`, `JANUS_ROOM_ID`, `WEBRTC_ENABLED`.
- **Regla ambiente** -> mismo binario, cambian variables.
- **Chequeo rápido** -> health backend + health webrtc + ice-servers.

**Frase gatillo:** “Si config falla, no hay demo aunque compile.”

---

## D) Demo técnica (25 min)

- **Contexto stream** -> tener `streamId` listo.
- **Offer/Answer** -> validar negociación.
- **ICE** -> completar conectividad real.
- **HLS** -> abrir `index.m3u8` y confirmar reproducción.
- **Logs** -> correlación end-to-end.

**Frase gatillo:** “Primero señalización, luego media, luego reproducción.”

---

## E) Troubleshooting (25 min)

- **Caso 1**: sin SDP answer -> revisar Janus URL, room, reachability.
- **Caso 2**: hay answer pero no video -> revisar RTP forward, UDP, FFmpeg.
- **Caso 3**: URL HLS rara -> revisar base URL efectiva.

**Frase gatillo:** “No adivinamos: aislamos por capas.”

---

## Endpoints que NO olvidar mostrar

- `GET /api/v1/webrtc/health`
- `GET /api/v1/webrtc/ice-servers`
- `POST /api/v1/streams/{streamId}/webrtc/offer`
- `POST /api/v1/streams/{streamId}/webrtc/ice`
- `DELETE /api/v1/streams/{streamId}/webrtc`
- `GET /webrtc-hls/{streamKey}/index.m3u8`

---

## Checklist 60 segundos antes de iniciar

- [ ] Backend compila.
- [ ] `.env` con `taller.ourshop.work`.
- [ ] Endpoints health/ice OK.
- [ ] `streamId` listo.
- [ ] Terminal de logs visible.

---

## Cierre (2 min)

- **Resumen** -> arquitectura + código + config + demo + fallas reales.
- **Mensaje final** -> base sólida para operar streaming backend mañana.

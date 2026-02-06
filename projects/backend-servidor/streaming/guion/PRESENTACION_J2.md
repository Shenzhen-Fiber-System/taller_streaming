---
marp: true
theme: gaia
class: invert
paginate: true
header: 'Workshop Streaming: Platform Engineering'
footer: '© 2024 OurShop | Streaming Platform Workshop'
style: |
  section { font-family: 'Segoe UI', sans-serif; font-size: 30px; letter-spacing: 0.5px; }
  h1 { color: #82aaff; text-shadow: 2px 2px 4px rgba(0,0,0,0.4); }
  h2 { color: #c3e88d; }
  strong { color: #ff5370; }
  a { color: #89ddff; text-decoration: none; }
  header, footer { font-size: 14px; color: #546e7a; }
  code { background: #292d3e; color: #bfc7d5; border-radius: 4px; padding: 2px 6px; }
---

<!-- _class: lead invert -->
<!-- _header: '' -->
<!-- _footer: '' -->

# 🎥 Construyendo una Plataforma de Streaming
## Jornada 2: WebRTC, Janus y HLS

👨‍🏫 Instructor: **Gerson Castellanos y Joel Acosta**
⏱️ Duración: 4 Horas

---

<!-- _class: invert -->

## 🗺️ Roadmap del Taller

<div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; text-align: center;">

<div style="opacity: 0.5; padding: 20px; border: 1px solid #546e7a; border-radius: 10px;">

### JORNADA 1
Backend Reactivo
✅ (Listo)

</div>

<div style="border: 2px solid #c3e88d; padding: 20px; border-radius: 10px; background: #2a2a2a;">

### 👉 JORNADA 2
**(HOY)**
WebRTC + Janus

</div>

<div style="opacity: 0.5; padding: 20px; border: 1px solid #546e7a; border-radius: 10px;">

### JORNADA 3
Flutter Mobile
🔜

</div>

</div>

---

## 🎯 Objetivos de Hoy

1.  **WebRTC Signaling:** Implementar el "apretón de manos".
2.  **Janus Gateway:** Integrar el cliente HTTP.
3.  **RTP a HLS:** El pipeline mágico de FFmpeg.
4.  **Demo Final:** ¡Video en vivo en el navegador!

---

<!-- _class: invert -->

## 🔄 El Flujo de Datos (J2)

![bg contain right:55%](flujo_streaming_j2_es.png)

1.  **Publicador**
    WebRTC (UDP)
2.  **Janus**
    RTP Forwarding
3.  **FFmpeg**
    Transcodificación
4.  **Nginx**
    Servir archivos HLS
5.  **Espectador**
    Reproducción HTTP

---

## 🤝 Conceptos Clave: SDP y ICE

<table style="border:none; width:100%; table-layout: fixed;">
<tr style="background: none;">
<td style="border:none; width:40%; vertical-align:middle; text-align:center;">

![h:400](sdp_ice_explicacion_es.png)

</td>
<td style="border:none; width:60%; vertical-align:middle; padding-left: 20px;">

### 🗣️ SDP
**(Session Description)**
"Hola, hablo H.264 y Opus"
*Negociación de Capacidades*

<br>

### 🧭 ICE
**(Connectivity)**
"Buscando agujeros en el Firewall"
*Búsqueda de Rutas (STUN/TURN)*

</td>
</tr>
</table>

---

## 🛠️ Configuración Local

Antes de empezar, verifiquen su archivo `.env`:

```properties
JANUS_URL=https://taller.ourshop.work/janus
JANUS_ROOM_ID=1234
WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls
```

⚠️ **Importante:** Deben tener acceso al servidor central.
Prueba: `curl https://taller.ourshop.work/actuator/health`

---

## 💻 Live Coding: WebRTC Controller

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">

<div>

**Endpoints Clave:**

*   `POST /webrtc/offer`
    Recibe el SDP del cliente.
*   `GET /ice-servers`
    Entrega credenciales STUN/TURN.

</div>

<div>

**Reto Técnico:**

Manejar la asincronía del Polling de Janus dentro de un flujo reactivo:

`flatMap` + `retry` + `delayElements`.

</div>

</div>

---

<!-- _class: invert lead -->
<!-- _header: '' -->
<!-- _footer: '' -->

# ☕ Break 1 (10 min)
## ¡A recargar energía! ⚡

---

## 🤖 Janus Gateway Client

<table style="border:none; width:100%; font-size: 0.8em; table-layout: fixed;">
<tr style="background: none;">
<td style="border:none; width:50%; vertical-align:top; padding-right: 15px; border-right: 2px solid #546e7a;">

**Fase 1: Setup**
1.  **`create_session`**
    ➡️ Obtener Session ID.
2.  **`attach_plugin`**
    ➡️ Conectar a `videoroom`.
3.  **`join` (Publisher)**
    ➡️ Enviar SDP Offer.

</td>
<td style="border:none; width:50%; vertical-align:top; padding-left: 15px;">

**Fase 2: Negotiation**
4.  **Polling (Loop)**
    ➡️ Esperar evento `configured` (SDP Answer).
5.  **`rtp_forward`**
    ➡️ Redirigir stream a FFmpeg.

</td>
</tr>
</table>

---

## 🧐 Janus Deep Dive: El "Polling"

> *"Janus no te avisa, tú tienes que preguntar."*

1.  Envías **Offer** (Publisher) ➡️ Janus dice "ACK".
2.  Janus procesa en background...
3.  Tú llamas `GET /janus/{session}?maxev=1` (Long Polling).
4.  ... esperando ...
5.  Janus responde JSON: `{ "janus": "event", "jsep": { "type": "answer" } }`.

⚠️ **Reto:** Esto hay que manejarlo en el `WebClient` de Spring.

---

<!-- _class: invert lead -->
<!-- _header: '' -->
<!-- _footer: '' -->

# ☕ Break 2 (10 min)
## ¡Respiren profundo! 🌬️

---

## ⚙️ El Motor: FFmpeg + HLS

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; align-items: center;">

<div>

```bash
ffmpeg -i rtp://127.0.0.1:10000 \
  -c:v libx264 \
  -preset ultrafast \
  -hls_time 2 \
  -hls_list_size 5 \
  index.m3u8
```

</div>

<div>

*   **`-preset ultrafast`**
    Prioriza latencia sobre compresión.
*   **`-hls_time 2`**
    Segmentos de 2s (Baja latencia).
*   **`-hls_list_size 5`**
    Buffer circular de 10s.

</div>

</div>

---

## ⚙️ Desglosando Flags FFmpeg

| Flag | Significado |
|---|---|
| `-protocol_whitelist` | Seguridad (solo permitir UDP/RTP) |
| `-preset ultrafast` | **Latencia** > Calidad (Encodea muy rápido) |
| `-hls_time 2` | Segmentos de 2s (Latencia teórica ~6-8s) |
| `-hls_list_size 5` | Mantiene solo los últimos 10s en disco |

---

<!-- _class: invert lead -->
<!-- _header: '' -->
<!-- _footer: '' -->

# 🎉 ¡Demo Time!
## Probemos la transmisión en vivo 🔴

---

## 🆘 Troubleshooting Común

| Error | Causa Probable |
|---|---|
| `Connection refused` | URL de Janus incorrecta en `.env` |
| `FFmpeg exit code 1` | Puerto UDP en uso o falta de permisos carpeta |
| `Stream not found` | FFmpeg no recibe datos (Revisar logs Janus) |
| `Blocking call` | Usaste `.block()` en Reactor |
| `ICE failed` | Firewall bloqueando puertos 10000-10200 |

---

## ❓ Preguntas Frecuentes (J2)

### ¿Dudas sobre la Arquitectura?
### ¿WebRTC vs HLS?

> *Hablemos de Latencia, Escalabilidad y Zoom...* 🗣️

---

<!-- _class: lead invert -->

# 🏁 Cierre Jornada 2

---

## ✅ Resumen de Logros

| Concepto | Estado |
|---|---|
| 📡 **WebRTC** | Signaling & Handshake Completo |
| 🔌 **Integración** | Cliente Janus HTTP Asíncrono |
| 🔄 **Pipeline** | RTP -> FFmpeg -> HLS Automatizado |
| 📺 **Playback** | Video reproduciéndose en Chrome/Safari |
| 🧩 **Arquitectura** | Piezas desacopladas y escalables |

---

## 💾 Git Checkpoint

```bash
git add .
git commit -m "Jornada 2 completa: WebRTC + HLS pipeline"
git tag checkpoint/j2-webrtc-hls
git push origin main --tags
```

---

# 📅 Próxima Clase: Jornada 3
## **Flutter & Mobile**

1.  Consumir nuestra API (Signaling).
2.  Manejar Cámara y Micrófono.
3.  Enviar video real desde el celular.

👋 **¡Hasta la próxima!**

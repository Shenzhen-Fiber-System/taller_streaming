# READMEFFMPEG — Configuración de FFmpeg para WebRTC → HLS

Este módulo (`apps/backend/streaming`) genera HLS ejecutando **FFmpeg como proceso**.

Pipeline:
1. El navegador publica WebRTC → backend recibe SDP offer.
2. Backend negocia con Janus (VideoRoom) y pide `rtp_forward`.
3. Janus envía RTP por UDP a puertos locales del backend.
4. Backend ejecuta FFmpeg leyendo un `input.sdp` y escribe `index.m3u8` + segmentos `.ts`.
5. El backend sirve HLS en `/webrtc-hls/{streamKey}/...`.

## 1) Requisitos en el VPS

- **FFmpeg instalado** y disponible en PATH para el usuario que ejecuta el servicio.
- FFmpeg debe soportar estos encoders (por defecto se usa transcode):
  - Video: `libx264`
  - Audio: `aac`

Comandos de verificación:

- `ffmpeg -version`
- `ffmpeg -encoders | findstr /I "libx264"` (Windows)
- `ffmpeg -encoders | grep -i "libx264"` (Linux)

Instalación típica en Ubuntu/Debian:

- `sudo apt-get update`
- `sudo apt-get install -y ffmpeg`

Si `libx264` o `aac` no aparecen, instala una build de FFmpeg que los incluya.

## 2) Variables de configuración (Spring)

En [apps/backend/streaming/src/main/resources/application.yml](apps/backend/streaming/src/main/resources/application.yml) se añadieron defaults:

- `WEBRTC_HLS_OUTPUT_DIR`
  - Directorio donde se escribe HLS.
  - Default: `./data/webrtc-hls`
  - Recomendado en VPS: una ruta absoluta, por ejemplo `/var/lib/ourshop/webrtc-hls`.

Comandos recomendados en Ubuntu para crear la carpeta y permisos (ejemplo con usuario `deploy`):

- `sudo mkdir -p /var/lib/ourshop/webrtc-hls`
- `sudo chown -R deploy:deploy /var/lib/ourshop/webrtc-hls`

Nota: ojo con typos comunes como `/var/lib/ourhsop/...` (debe ser `ourshop`).

- `WEBRTC_HLS_PUBLIC_BASE_URL`
  - Base pública para devolver URLs absolutas en las respuestas.
  - Si se deja vacío, el backend devuelve URL relativa: `/webrtc-hls/{streamKey}/index.m3u8`.
  - Recomendado: `https://taller.ourshop.work`

- `WEBRTC_RTP_FORWARD_HOST`
  - Host/IP hacia el que **Janus** debe enviar el RTP (UDP) cuando se hace `rtp_forward`.
  - Default: `127.0.0.1`
  - Usos típicos:
    - Janus y backend en el mismo host (sin contenedores separados): `127.0.0.1`
    - Janus en contenedor y backend en el host: IP del host accesible desde el contenedor
    - Janus y backend en hosts diferentes: IP del backend y abrir firewall para UDP

Otras variables relevantes:

- `JANUS_URL` (ej. `http://localhost:8088`)
- `JANUS_ROOM_ID`, `JANUS_ROOM_SECRET`

## 3) Puertos y red (lo más importante)

El backend reserva un par de puertos UDP para RTP:
- audio: `basePort`
- video: `basePort + 2`

Escenarios:

### A) Janus y backend en el mismo VPS (recomendado)

- `WEBRTC_RTP_FORWARD_HOST=127.0.0.1`
- No necesitas abrir puertos UDP al exterior si todo queda en loopback.

### B) Janus en Docker, backend en el host

- `WEBRTC_RTP_FORWARD_HOST` debe ser una IP que el contenedor pueda alcanzar.
- Asegúrate de que el contenedor pueda enviar UDP a esos puertos en el host.

### C) Janus y backend en máquinas distintas

- `WEBRTC_RTP_FORWARD_HOST=<IP-del-backend>`
- Abre el firewall para el rango de puertos UDP que uses (recomendación: definir un rango y controlar la asignación).

## 4) Logs y troubleshooting

Por stream se escribe:

- `input.sdp` (SDP generado para que FFmpeg consuma RTP)
- `ffmpeg.log` (salida de FFmpeg)
- `index.m3u8` y segmentos `seg_XXXXX.ts`

Ubicación: `${WEBRTC_HLS_OUTPUT_DIR}/{streamKey}/...`

Checklist cuando “no aparece HLS”:

1. Verifica que exista `ffmpeg.log` y revisa errores.
2. Verifica que `index.m3u8` exista y se actualice.
3. Verifica Nginx/proxy:
   - `GET https://taller.ourshop.work/webrtc-hls/{streamKey}/index.m3u8`
4. Verifica que Janus esté enviando RTP al host correcto (`WEBRTC_RTP_FORWARD_HOST`).

## 5) Notas operativas

- HLS usa `-hls_flags delete_segments+append_list` para evitar crecimiento infinito de segmentos.
- Si un stream termina, el backend intenta detener el proceso FFmpeg asociado.
- Si quieres servir HLS por CDN/Cloudflare, ten en cuenta caching:
  - playlist (`.m3u8`) normalmente **no-cache/no-store**
  - segmentos (`.ts`) caché corta (segundos)

## 6) Nginx (single domain)

La configuración Nginx para el workshop vive dentro de este proyecto.

Archivo recomendado:

- [apps/backend/streaming/janus/nginx-janus-proxy-dev.conf](apps/backend/streaming/janus/nginx-janus-proxy-dev.conf)

Ese vhost expone en el mismo dominio `taller.ourshop.work`:

- `taller.ourshop.work`
  - `/api/` → `http://localhost:8087`
  - `/webrtc-hls/` → `http://localhost:8087`

También incluye proxy para Janus:

- `/janus` → `http://127.0.0.1:7088`

Asegúrate de recargar Nginx después de cambiar configuración.

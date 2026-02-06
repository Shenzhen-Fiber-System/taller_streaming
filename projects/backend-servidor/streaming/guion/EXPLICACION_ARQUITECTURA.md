# 🏛️ Guión de la Arquitectura (Explicación del Gráfico)

Este documento sirve de guía para explicar el diagrama `arquitectura_streaming.png` durante el taller.

## 🔄 Flujo de Datos (Paso a Paso)

Sigue los números del gráfico:

### 1. El Publicador (Ingesta) 🎥
*   **Quién:** El navegador del vendedor (Flutter/React).
*   **Qué hace:** Captura cámara/micrófono.
*   **Protocolo:** **WebRTC** sobre UDP.
*   **Misión:** Enviar el video lo meas rápido posible (Low Latency).

### 2. El Servidor de Señalización (El Cerebro) 🧠
*   **Quién:** Nuestro Backend Spring Boot.
*   **Qué hace:** "Presenta" al Publicador con Janus. Intercambia las ofertas (SDP) para que sepan cómo conectarse.
*   **Importante:** ¡Por aquí NO pasa el video! Solo texto JSON.

### 3. Janus Gateway (El Corazón) ❤️
*   **Quién:** Servidor SFU (Selective Forwarding Unit).
*   **Qué hace:**
    *   Recibe el stream WebRTC encriptado (DTLS/SRTP).
    *   Lo **desencripta** para acceder al contenido crudo.
    *   Reenvía los paquetes RTP hacia FFmpeg.
*   **Analogía:** Es el traductor que convierte el dialecto "Web" en algo que el servidor entiende.

### 4. FFmpeg (El Motor de Transformación) ⚙️
*   **Quién:** Proceso de sistema (Linux Process).
*   **Qué hace:**
    *   Escucha en un puerto UDP local (ej. 5004).
    *   **Transcodifica:** Audio Opus -> AAC.
    *   **Empaqueta:** Convierte el flujo continuo en "rebanadas" (segmentos .ts) de 4 segundos.
    *   Crea el archivo de playlist `index.m3u8`.

### 5. Nginx / CDN (La Distribución) 🚀
*   **Quién:** Servidor Web Estático.
*   **Qué hace:** Sirve los archivos `.m3u8` y `.ts` que genera FFmpeg.
*   **Poder:** Al ser archivos estáticos, son cacheables. Esto permite escalar a millones de usuarios usando CDNs como Cloudfront.

### 6. El Espectador (Consumo) 🍿
*   **Quién:** VLC, Cliente Móvil, Web Player.
*   **Qué hace:**
    1.  Descarga el menú (`.m3u8`).
    2.  Pide el "plato del momento" (segmento `.ts`).
    3.  Lo reproduce.
*   **Latencia:** Vemos el pasado (entre 10 a 20 segundos de retraso) a cambio de estabilidad perfecta.

---

## 🚦 Diferencia Crítica: Protocolos

| Etapa | Protocolo | Transporte | Prioridad |
|:-----|:---------:|:----------:|:---------:|
| **1-3 (Subida)** | WebRTC | UDP | **Velocidad** (Tiempo Real) |
| **5-6 (Bajada)** | HLS | TCP (HTTP) | **Calidad** (Sin cortes) |

> **Pregunta Trampa:** *"¿Por qué no usamos WebRTC para el paso 6?"*
> **Respuesta:** Costo y CPU. Mantener conexiones UDP activas para 10,000 usuarios derrite el servidor. HLS es simplemente "descargar archivos", lo más barato y escalable de internet.


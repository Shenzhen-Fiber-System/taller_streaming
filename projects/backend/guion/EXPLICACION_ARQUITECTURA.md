# 🏛️ Guión de la Arquitectura (Explicación del Gráfico)

Este documento sirve de guía para explicar el diagrama `arquitectura_streaming.png` durante el taller.

---

## 🏗️ La "Big Picture" del Streaming Híbrido

> **Narrativa para el Instructor:**
> "Chicos, no vamos a inventar la rueda, vamos a conectar las mejores ruedas que existen. Nuestro sistema es un híbrido entre **Baja Latencia (WebRTC)** para la entrada y **Alta Escalabilidad (HLS)** para la salida."

---

## 🔄 Flujo de Datos (Paso a Paso)

Sigue las flechas del gráfico en este orden:

### 1. El Ingreso (Publisher) 🎥
*   **Actor:** El navegador del vendedor (Flutter Web o React).
*   **Protocolo:** **WebRTC** sobre UDP (Rápido, tiempo real, inestable).
*   **Acción:** Envía video (H.264) y audio (Opus) directamente al servidor.
*   **Concepto Clave:** "Aquí necesitamos velocidad pura. Si se pierde un paquete, no importa, seguimos."

### 2. El Portero (Janus Gateway) 🛡️
*   **Rol:** Servidor SFU (Selective Forwarding Unit).
*   **Acción:**
    1.  Negocia la conexión (SDP Offer/Answer).
    2.  Recibe el stream WebRTC encriptado (DTLS/SRTP).
    3.  **Desencripta** y extrae el payload RTP crudo.
    4.  **RTP Forwarding:** Reenvía esos paquetes UDP a un puerto local (ej. 5004) donde alguien más los espera.
*   **Analogía:** "Janus es el traductor universal. Recibe el dialecto complejo de la web y lo convierte en algo que el servidor entiende."

### 3. El Transmutador (FFmpeg) ⚗️
*   **Acción:** Escucha en el puerto local (UDP 5004).
*   **Proceso Pesado:**
    *   Toma el stream RTP.
    *   **Transcodifica:** Convierte el audio OPUS (que Web usa) a AAC (que HLS necesita). El video H.264 suele dejarse "copy" para ahorrar CPU, o recodificarse si se cambia la calidad.
    *   **Segmenta:** Corta el video infinito en pedacitos de 2 a 4 segundos (`.ts`).
    *   **Playlist:** Actualiza constantemente un archivo de texto `index.m3u8` que dice: "Los últimos 3 segmentos son estos".

### 4. La Distribución (Nginx / CDN) 🚀
*   **Protocolo:** **HTTP/HTTPS** sobre TCP (Seguro, cacheable, escalable).
*   **Acción:** Sirve archivos estáticos (`.m3u8` y `.ts`) como si fueran imágenes o HTML.
*   **Magia:** Al ser archivos estáticos, se pueden poner detrás de Cloudflare/AWS CloudFront y escalar a millones de usuarios sin tocar nuestro servidor de transcodificación.

### 5. El Consumo (Viewer) 🍿
*   **Actor:** VLC, Navegador móvil, Smart TV.
*   **Tecnología:** HLS.js (Javascript).
*   **Acción:**
    1.  Descarga el `index.m3u8` cada segundo.
    2.  Ve que hay un segmento nuevo.
    3.  Lo descarga y lo reproduce.
*   **Latencia:** La suma de los segmentos en el buffer (aprox. 10-15 segundos de retraso respecto a la realidad).

---

## 🚦 Diferencia Crítica: Protocolos

| Etapa | Protocolo | Transporte | Prioridad |
|:-----|:---------:|:----------:|:---------:|
| **Subida (Ingesta)** | WebRTC | UDP | **Velocidad** (Tiempo Real) |
| **Bajada (Consumo)** | HLS | TCP (HTTP) | **Calidad y Fiabilidad** (Buffering) |

> **Pregunta Trampa para la clase:**
> *"¿Por qué no usamos WebRTC para los espectadores también?"*
> **Respuesta:**
> "Porque WebRTC consume mucha CPU y ancho de banda por usuario en el servidor. HLS escala 'infinitamente' y es barato gracias a las CDNs."

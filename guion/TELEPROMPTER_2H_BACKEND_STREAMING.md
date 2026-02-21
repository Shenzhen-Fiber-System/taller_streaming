# Teleprompter (2h) — Backend Streaming WebRTC + Janus + HLS

> Modo de uso: lee casi literal. Donde veas **[ACCIÓN]**, ejecuta o muestra en pantalla.  
> Ritmo recomendado: voz pausada, frases cortas, 1 idea técnica por minuto.

---

## 0:00 - 0:02 | Apertura

“Equipo, hoy vamos a hacer una explicación relámpago del backend de streaming.  
No vamos a construir todo desde cero, sino entender exactamente qué se agregó y cómo dejarlo funcional con infraestructura real.”

“Objetivo de estas 2 horas:  
uno, entender la arquitectura;  
dos, ubicar las clases nuevas por responsabilidad;  
tres, configurar Janus remoto;  
cuatro, hacer demo;  
y cinco, resolver fallas típicas en vivo.”

---

## Bloque A — Arquitectura (20 min)

### 0:02 - 0:07 | Contexto del flujo

“Primero, idea clave: el video no viaja por nuestros endpoints REST.  
REST solo hace signaling: intercambio de SDP e ICE.  
El media real va por WebRTC hacia Janus, y luego Janus reenvía RTP a FFmpeg.”

“Piensen en dos planos:  
plano de control: HTTP + JSON;  
plano de media: RTP/WebRTC y luego HLS.”

### 0:07 - 0:13 | Flujo paso a paso

“Paso uno: el publisher envía `offer` SDP al backend.  
Paso dos: backend crea sesión en Janus y adjunta plugin VideoRoom.  
Paso tres: Janus responde `answer` SDP.  
Paso cuatro: cliente y Janus completan ICE.  
Paso cinco: Janus hace `rtp_forward` a FFmpeg.  
Paso seis: FFmpeg genera `index.m3u8` y segmentos `.ts`.  
Paso siete: viewers consumen HLS por HTTP.”

### 0:13 - 0:18 | Por qué servidor remoto

“¿Por qué ya no localhost para demo pública?  
Porque localhost no es enrutable para audiencia externa.  
Tenemos NAT, firewall, y además el browser necesita endpoints públicos para consumo real.”

“Por eso usamos infraestructura ya corriendo:  
`JANUS_URL=https://taller.ourshop.work/janus`  
y `WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls`.”

### 0:18 - 0:20 | Cierre bloque A

“Si esta idea queda clara, el resto del código se entiende fácil: cada clase existe para sostener uno de esos pasos.”

---

## Bloque B — Código nuevo por capas (25 min)

### 0:20 - 0:24 | Entrada HTTP

“Entramos por controladores. Aquí tenemos tres piezas:  
`WebRtcSignalingController`, `WebRtcIceServersController` y `WebRtcHlsController`.”

“Uno negocia `offer/ice/close`,  
otro entrega health e ICE servers,  
y otro sirve playlist y segmentos HLS.”

**[ACCIÓN]** Mostrar rápidamente rutas:
- `GET /api/v1/webrtc/health`
- `GET /api/v1/webrtc/ice-servers`
- `POST /api/v1/streams/{streamId}/webrtc/offer`
- `POST /api/v1/streams/{streamId}/webrtc/ice`
- `DELETE /api/v1/streams/{streamId}/webrtc`
- `GET /webrtc-hls/{streamKey}/index.m3u8`

### 0:24 - 0:31 | Orquestación principal

“La pieza central es `WebRtcSignalingService`.  
Aquí pasa la lógica de negocio del ciclo WebRTC.”

“Qué hace este servicio:  
valida SDP del publisher,  
obtiene o crea sesión activa,  
negocia con Janus,  
gestiona cola de ICE,  
marca stream en estado LIVE cuando corresponde,  
y arranca HLS por FFmpeg.”

“Esta separación nos ayuda mucho en debug, porque controladores son delgados y la lógica vive en un solo lugar.”

### 0:31 - 0:36 | Cliente Janus

“`JanusClient` encapsula la API HTTP de Janus: create, attach, message, trickle, keepalive y destroy.”

“Punto importante del ajuste nuevo:  
el backend tolera `JANUS_URL` con o sin sufijo `/janus`.  
Eso evita errores de configuración comunes en taller.”

### 0:36 - 0:41 | FFmpeg/HLS

“`FfmpegHlsService` crea y controla el proceso de FFmpeg.  
Genera playlist y segmentos, reinicia si detecta caída, y calcula URL pública de reproducción.”

“También quedó protegido para no duplicar `/webrtc-hls` en la URL final cuando la base ya lo incluye.”

### 0:41 - 0:45 | Estado persistente y contratos

“`StreamSession*` y repositorio guardan estado de sesión para trazabilidad.  
Los DTOs modelan contratos claros de entrada/salida, y las excepciones webRTC son específicas para fallos previsibles.”

“Cierre del bloque: cada carpeta tiene una responsabilidad concreta. Esto es diseño para operación, no solo para compilar.”

---

## Bloque C — Configuración remota (25 min)

### 0:45 - 0:50 | Variables mínimas

“Para taller, variables mínimas obligatorias:”

```dotenv
JANUS_URL=https://taller.ourshop.work/janus
WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls
JANUS_ROOM_ID=1234
WEBRTC_ENABLED=true
```

“Si esto está mal, no hay demo, aunque el código compile.”

### 0:50 - 0:57 | Qué va en application.yml

“En `application.yml` ya tenemos bloques `janus` y `webrtc`.  
La idea es que `.env` sobreescriba sin tocar código.”

“Mensaje clave para el grupo:  
ambientes distintos, mismo binario.  
Solo cambian variables.”

### 0:57 - 1:04 | Por qué localhost falla para audiencia externa

“Localhost funciona para pruebas aisladas, pero no para exposición pública.  
El viewer externo nunca llega a tus recursos locales.”

“Con `taller.ourshop.work` resolvemos alcance público y consistencia del demo.”

### 1:04 - 1:10 | Mini chequeo en vivo

**[ACCIÓN]** Leer y ejecutar:

“Ahora validamos salud del backend y capa WebRTC.”

- `GET /actuator/health`
- `GET /api/v1/webrtc/health`
- `GET /api/v1/webrtc/ice-servers`

“Si esto responde bien, estamos listos para la demo completa.”

---

## Bloque D — Demo técnica (25 min)

### 1:10 - 1:15 | Preparación del stream

“Arrancamos demo: creamos o usamos un `streamId` existente del CRUD.”

“Recuerden: el stream define contexto de negocio; la sesión WebRTC define contexto de transporte.”

### 1:15 - 1:21 | Offer + Answer

“Ahora el publisher envía `offer` SDP.  
Nuestro backend lo reenvía a Janus y retorna `answer` + URL HLS.”

“Si aquí llega `answer`, ya pasamos la etapa más crítica de signaling.”

### 1:21 - 1:27 | ICE y estabilización

“Con `POST /ice` enviamos candidatos trickle.  
El objetivo es consolidar conectividad de media.”

“Cuando Janus empieza a recibir media, el pipeline RTP -> FFmpeg -> HLS debe activarse.”

### 1:27 - 1:32 | Reproducción HLS

“Tomamos la URL HLS y abrimos `index.m3u8`.  
Si aparece reproducción, completamos la cadena extremo a extremo.”

### 1:32 - 1:35 | Correlación de logs

“Mostramos logs para que vean secuencia real:  
`offer` recibido -> sesión Janus -> publish -> rtp_forward -> FFmpeg -> segmentos HLS.”

“Esto es clave para soporte en producción: leer el sistema por eventos.”

---

## Bloque E — Troubleshooting real (25 min)

### 1:35 - 1:42 | Caso 1: no llega SDP answer

“Si no llega `answer`, revisamos en este orden:  
uno, `JANUS_URL`;  
dos, `JANUS_ROOM_ID` y secretos;  
tres, reachability del servidor Janus.”

“Regla práctica: primero conectividad y config, después código.”

### 1:42 - 1:49 | Caso 2: hay answer pero no hay video

“Si hay `answer` pero no video, el signaling está bien y el problema suele estar en media path.”

“Validar:  
`rtp_forward` host correcto,  
puertos UDP esperados,  
proceso FFmpeg vivo,  
y creación de segmentos en disco.”

### 1:49 - 1:55 | Caso 3: URL HLS mal formada

“Si la URL HLS sale mal, revisar base URL.  
Ya protegimos el backend para no duplicar `/webrtc-hls`, pero igual debemos verificar variable efectiva en runtime.”

### 1:55 - 1:58 | Cierre técnico

“Resumen técnico:  
control plane estable,  
media plane observable,  
configuración externalizada,  
y troubleshooting por hipótesis ordenada.”

### 1:58 - 2:00 | Cierre final

“Con esto ya tienen una base real para operar streaming en backend con Janus y HLS.  
El siguiente paso natural es integrar clientes publisher/viewer de forma guiada y automatizar pruebas de humo de signaling.”

“Gracias equipo. Si quieren, en la siguiente sesión dejamos una checklist de incident response de 15 minutos para soporte en vivo.”

---

## Frases de rescate (por si algo falla en vivo)

- “Perfecto, esta falla es útil porque nos deja ver el proceso de diagnóstico real.”
- “Aquí no adivinamos: validamos capa por capa hasta aislar la causa.”
- “Lo importante no es memorizar endpoints, sino entender el flujo de extremo a extremo.”
- “Si signaling está bien y no hay video, casi siempre el cuello está en RTP/FFmpeg.”
- “La diferencia entre demo y producción es observabilidad; por eso estamos leyendo logs con intención.”

---

## Mini checklist del instructor (1 minuto antes de iniciar)

- [ ] Backend compila (`mvnw.cmd -DskipTests compile`).
- [ ] `.env` con URLs de `taller.ourshop.work`.
- [ ] Endpoints health e ice-servers responden.
- [ ] Tienes a mano un `streamId` para la demo.
- [ ] Terminal de logs visible para el público.

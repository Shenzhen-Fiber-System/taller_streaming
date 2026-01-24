# Taller Completo (3 jornadas × 4 horas)

Curso práctico: **Streaming WebRTC (Publisher) → Janus → rtp_forward → FFmpeg → HLS**

- **Dominio del servidor central (instructor):** `https://taller.ourshop.work`
- **Modalidad:** el instructor tiene el VPS “real” listo (Janus + nginx + FFmpeg + HLS). Los alumnos desarrollan **backend local** y una **app Flutter** en local.
- **Dinámica de repo:** el instructor va implementando por secciones y hace **commits**; los alumnos avanzan haciendo `git pull` (y opcionalmente `git checkout <tag>`).

## Estructura de las Jornadas

| Jornada | Enfoque | Repositorio |
|:-------:|---------|-------------|
| **1** | Backend: Setup + CRUD Reactivo | Este repo (streaming) |
| **2** | Backend: WebRTC + Janus + HLS | Este repo (streaming) |
| **3** | Frontend: App Flutter Publisher | Repo Flutter (separado) |

> ⚠️ **Este documento cubre las Jornadas 1-2 (backend).** La Jornada 3 (Flutter) tiene su propio repositorio.

---

## 0) Antes del curso (obligatorio) — Instalables + verificación

### 0.1 Video previo (10–15 min)
Enviar un video/capturas donde se vea:
- Qué deben tener instalado.
- Cómo validar versiones.
- Cómo clonar el repo.
- Cómo correr **backend** y **Flutter** “hello world”.

### 0.2 Instalables (Windows/macOS/Linux)

**Obligatorios para todos**
- Git
- Java **21** (recomendado). *(Si alguien se queda en 17, que lo sepa antes.)*
- IDE: IntelliJ IDEA / VS Code
- Postman (o alternativa: curl)

**Para la base de datos local (Día 1)**
- MySQL 8.x (local)

**Para Flutter (Día 2)**
- Flutter SDK estable
- Android Studio (para emulador) o dispositivo Android físico
- (Opcional iOS) Xcode + simulador

### 0.3 Verificación rápida (que el alumno haga y envíe screenshot)

**Git**
- `git --version`

**Java**
- `java -version`

**MySQL**
- `mysql --version`

**Flutter**
- `flutter --version`
- `flutter doctor`

### 0.4 Reglas operativas del taller
- Si alguien no pasa el preflight, no se “debuggea” en vivo: se le asigna “pairing” o se le da el código por pull.
- Cada bloque termina en un **checkpoint** verificable.

---

## 1) Flujo de trabajo con Git (instructor commitea, alumnos hacen pull)

### 1.1 Modelo de trabajo recomendado
- Branch principal: `main`
- El instructor hace commits pequeños por sección.
- Al final de cada sección, el instructor anuncia: “**Checkpoint X** listo, hagan pull”.

### 1.2 Convención de checkpoints (sugerida)
Usa tags (opcional, pero hace el taller más controlable):
- `checkpoint/d1-00-setup`
- `checkpoint/d1-01-crud-streammeta`
- `checkpoint/d2-00-webrtc-endpoints`
- `checkpoint/d2-01-janus-client`
- `checkpoint/d2-02-hls-ffmpeg`
- `checkpoint/d2-03-flutter-publisher`

### 1.3 Comandos para alumnos
- Clonar: `git clone <URL_DEL_REPO>`
- Actualizar: `git pull`
- (Opcional) ir a un checkpoint: `git fetch --tags` y `git checkout checkpoint/d1-01-crud-streammeta`

---

## 2) Qué SÍ se desarrolla en el taller (práctico)

### Día 1 (backend base + DB local)
- Backend WebFlux (Spring Boot) corriendo local.
- Persistencia **reactiva** (R2DBC MySQL) con tabla `stream_meta`.
- CRUD + paginación + búsqueda básica.
- State machine `CREATED → LIVE → ENDED`.

### Día 2 (WebRTC + servidor central + Flutter)
- Endpoints de signaling (offer/ice/delete) “1 publisher por streamId”.
- Integración con **Janus** (servidor central) vía HTTP API.
- Arranque de **FFmpeg** para generar HLS (en el servidor central), y retorno de `hlsUrl`.
- App Flutter: publisher WebRTC que envía offer y trickle ICE al backend.
- Demo: abrir `hlsUrl` en navegador y/o player.

---

## 3) Qué NO se hace en clase (se deja como extra)
- Multi-room / múltiples publishers simultáneos con reglas complejas.
- ABR (multi-bitrate) HLS.
- Autenticación/seguridad real de producción.
- Observabilidad avanzada (Prometheus/Grafana) más allá de mostrar logs básicos.

---

## 4) Referencias del proyecto (para copiar por secciones)

> Tú (instructor) vas a ir copiando piezas desde este módulo base.

**Raíz del módulo streaming (fuente):**
- `apps/backend/streaming/pom.xml`
- `apps/backend/streaming/.env.example`
- `apps/backend/streaming/src/main/resources/application.yml`

**Infra utilitaria (casi siempre se copia como “starter kit”):**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/infra/DotenvInitializer.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/infra/CorsConfig.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/infra/HttpRequestLoggingFilter.java` (opcional)

**Streams (Día 1):**
- Paquete base: `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/**`

**WebRTC (Día 2):**
- Paquete base: `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/**`

**Docs (opcional, útil para el taller):**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/docs/DocsController.java`
- `apps/backend/streaming/src/main/resources/docs/webrtc-hls-api.html`

---

# DÍA 1 — 4 horas (240 min)

Objetivo final del día: CRUD funcional de `StreamMeta` con DB local y state machine.

## Bloque D1-0 (00:00–00:20) — Arranque + clonado + “hello run”

**Instructor (demo 3–5 min)**
- Mostrar el objetivo del curso (1 slide/diagrama).
- Mostrar que el VPS central ya existe (sin tocar infra todavía).

**Alumnos (práctica)**
1) `git clone ...`
2) Abrir en IDE
3) Ubicar el módulo: `apps/backend/streaming/`

**Checkpoint**
- Todos listos con el repo abierto.

## Bloque D1-1 (00:20–01:05) — Setup backend local + MySQL local

**Qué implementas / habilitas**
- `.env` local
- correr Spring Boot
- health

**Pasos para alumnos (práctica guiada)**
1) Levantar MySQL local (cada uno en su máquina).
2) Crear DB (ejemplo): `streamingdb`.
3) Crear archivo `.env` en `apps/backend/streaming/.env` copiando `apps/backend/streaming/.env.example`.
4) Ajustar:
   - `SPRING_R2DBC_URL=r2dbc:mysql://localhost:3306/streamingdb`
   - `DB_USERNAME=...`
   - `DB_PASSWORD=...`
5) Correr:
   - Windows: `mvnw.cmd -DskipTests spring-boot:run`
   - macOS/Linux: `./mvnw -DskipTests spring-boot:run`

**Checkpoint**
- `GET http://localhost:8087/actuator/health` devuelve 200.

## Bloque D1-2 (01:05–01:15) — Break (10 min)

## Bloque D1-3 (01:15–02:10) — StreamMeta: modelo + schema + repo

**Objetivo**
- Persistir `stream_meta` sin scripts manuales.

**Implementación (instructor commitea por pasos)**
- Crear/confirmar paquetes:
  - `com.ourshop.streaming.streams.model`
  - `com.ourshop.streaming.streams.repo`
  - `com.ourshop.streaming.streams.infra`

**Archivos referencia a copiar**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/model/StreamStatus.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/model/StreamMeta.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/infra/StreamMetaSchemaInitializer.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/repo/R2dbcStreamMetaRepository.java`

**Checkpoint**
- App arranca y crea tabla `stream_meta` automáticamente.

> Nota didáctica: el initializer usa `.block()`; se explica como “bootstrap/dev tool”, no patrón de negocio.

## Bloque D1-4 (02:10–02:20) — Break (10 min)

## Bloque D1-5 (02:20–03:20) — Service + Controller + DTOs + paginación

**Objetivo**
- CRUD usable con `curl`.

**Archivos referencia a copiar**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/dto/CreateStreamMetaRequest.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/dto/StreamMetaResponse.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/dto/StreamMetaPageResponse.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/service/StreamMetaCrudService.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/controller/StreamMetaController.java`

**Pruebas (curl)**
- Crear:
  - `POST http://localhost:8087/api/v1/streams`
- Listar:
  - `GET http://localhost:8087/api/v1/streams?page=0&size=5`
- Start:
  - `PUT http://localhost:8087/api/v1/streams/{id}/start`
- End:
  - `PUT http://localhost:8087/api/v1/streams/{id}/end`

**Checkpoint**
- El flujo create → list → start → end funciona.

## Bloque D1-6 (03:20–03:50) — Errores + validaciones

**Objetivo**
- Respuestas correctas 400/404/409.

**Archivos referencia**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/streams/errors/**`

**Checkpoint**
- Forzar transición inválida (ej: end sin start) y ver `409`.

## Bloque D1-7 (03:50–04:00) — Cierre día 1

- El instructor hace tag: `checkpoint/d1-01-crud-streammeta`
- Los alumnos hacen `git pull` y quedan todos iguales.

---

# DÍA 2 — 4 horas (240 min)

Objetivo final del día: Offer/Answer + ICE + HLS URL reproducible, usando el VPS central.

## Bloque D2-0 (00:00–00:25) — Demo del servidor real + conceptos (sin tocar código)

**Instructor (demo)**
- Explicar el VPS:
  - nginx: `/janus`, `/api`, `/webrtc-hls/`
  - Janus VideoRoom
  - FFmpeg + directorio HLS
- Mostrar logs (ejemplos):
  - logs de backend
  - logs de Janus
  - `ffmpeg.log`

**Checkpoint mental**
- Todos entienden: WebRTC no sale directo a HLS, se puentea con rtp_forward + ffmpeg.

## Bloque D2-1 (00:25–00:45) — Configuración local para apuntar al VPS central

**Alumnos (práctica)**
- Editar `.env` local para:
  - `JANUS_URL=https://taller.ourshop.work/janus`
  - `JANUS_ROOM_ID=1234`
  - `WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls`

**Referencia**
- `apps/backend/streaming/.env.example`

**Checkpoint**
- Backend local sigue levantando.

## Bloque D2-2 (00:45–01:30) — Endpoints WebRTC (offer/ice/delete + ice-servers)

**Objetivo**
- Tener API lista para que Flutter hable.

**Archivos referencia a copiar**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/controller/WebRtcIceServersController.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/controller/WebRtcSignalingController.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/dto/SdpOfferRequest.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/dto/IceCandidateRequest.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/dto/SdpAnswerResponse.java`

**Checkpoint**
- `GET http://localhost:8087/api/v1/webrtc/ice-servers` responde.
- `POST /webrtc/offer` valida que viene SDP.

## Bloque D2-3 (01:30–01:40) — Break (10 min)

## Bloque D2-4 (01:40–02:30) — Cliente Janus (create/attach/joinandconfigure + poll answer)

**Objetivo**
- Dado un offer real, devolver answer.

**Archivos referencia a copiar**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/janus/JanusClient.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/service/WebRtcSignalingService.java`

**Checkpoint**
- Con un offer válido, el backend retorna un answer.

## Bloque D2-5 (02:30–03:10) — rtp_forward + FFmpeg + HLS URL

**Objetivo**
- Levantar pipeline “Janus → UDP RTP → FFmpeg → HLS” y retornar `hlsUrl`.

**Archivos referencia a copiar**
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/hls/FfmpegHlsService.java`
- `apps/backend/streaming/src/main/java/com/ourshop/streaming/webrtc/controller/WebRtcHlsController.java` (si aplica)

**Checkpoint**
- El response del offer incluye `hlsUrl`.
- Al abrir `hlsUrl` aparece `index.m3u8` (puede demorar algunos segundos en generar segmentos).

## Bloque D2-6 (03:10–03:50) — Flutter Publisher app (práctico)

**Objetivo**
- Flutter publica tracks, manda offer y ICE al backend.

**Estructura (recomendada) de la app Flutter**
- Proyecto: `flutter create ourshop_stream_publisher`
- Paquetes:
  - `flutter_webrtc` (para WebRTC)
  - `http` (para API calls)
  - `permission_handler` (permisos cámara/mic)

**Pantallas mínimas**
1) Pantalla “Config”
   - Backend base URL (ej: `http://10.0.2.2:8087` en emulador o IP LAN)
   - streamId (o botón “crear stream” que llama API)
2) Pantalla “Go Live”
   - pide permisos
   - crea `RTCPeerConnection`
   - agrega tracks locales
   - `createOffer` → POST `/webrtc/offer`
   - setRemoteDescription(answer)
   - onIceCandidate → POST `/webrtc/ice`

**Checkpoint**
- En logs del backend se ve que llegan ICE candidates.
- `hlsUrl` abre (en navegador) y reproduce.

> Nota: para no perder tiempo con playback en Flutter (codecs/players), el taller puede abrir el `hlsUrl` en navegador. Playback en Flutter se deja como extra si el tiempo aprieta.

## Bloque D2-7 (03:50–04:00) — Cierre + debugging checklist

**Checklist final**
- CRUD ok
- offer/answer ok
- ICE llega al backend
- HLS genera playlist/segmentos

**Tag final del instructor**
- `checkpoint/d2-03-flutter-publisher`

---

## Apéndice A — Dependencias Maven (qué copiar del pom)

Copiar secciones desde:
- `apps/backend/streaming/pom.xml`

Mínimo recomendado para el taller:
- `spring-boot-starter-webflux`
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `spring-boot-starter-data-r2dbc`
- `r2dbc-mysql`
- `java-dotenv`

---

## Apéndice B — Configuración (qué copiar)

- `apps/backend/streaming/src/main/resources/application.yml`
- `apps/backend/streaming/.env.example`

Variables clave (día 2, servidor central):
- `JANUS_URL=https://taller.ourshop.work/janus`
- `JANUS_ROOM_ID=1234`
- `WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls`

---

## Apéndice C — Tiempo “real” para explicar servidor/logs

Recomendación: reservar explícitamente estos momentos (ya incluidos arriba):
- Día 2, Bloque D2-0 (25 min) para:
  - enseñar endpoints nginx
  - explicar dónde mirar logs
  - explicar por qué `a=recvonly` en el answer de Janus es correcto para publisher

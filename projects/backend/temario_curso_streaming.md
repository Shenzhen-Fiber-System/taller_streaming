# 📚 Temario Curso: Streaming WebRTC + Spring WebFlux
## Plataforma de Streaming en Vivo con Java 21 y Janus Gateway

> **Duración**: 3 jornadas (4 horas cada una) = 12 horas totales  
> **Estructura**: 2 jornadas Backend (este repo) + 1 jornada Frontend (Flutter)  
> **Nivel**: Intermedio-Avanzado (requiere conocimientos de Spring Boot y Java)

---

## 🎯 Objetivo del Curso

Construir y extender una plataforma de **live streaming con arquitectura reactiva** donde:
- Un **publisher WebRTC** publica hacia **Janus Gateway**
- El sistema genera **HLS** para viewers
- Todo bajo un **solo dominio** (`taller.ourshop.work`)

**Los estudiantes desarrollan el backend en local y se conectan al VPS central del instructor** (ya configurado con Janus, nginx, FFmpeg y directorios HLS).

---

## 🖥️ Servidor Central Pre-configurado (Instructor)

El VPS `taller.ourshop.work` ya tiene listo:

### Infraestructura
- ✅ **Janus Gateway** con VideoRoom configurado
- ✅ **nginx** como reverse proxy exponiendo:
  - `/janus` → Janus HTTP API
  - `/api/` → Backend Spring Boot (estudiantes)
  - `/webrtc-hls/` → Archivos HLS generados
- ✅ **FFmpeg** instalado con `libx264` + `aac`
- ✅ Directorio HLS: `/var/lib/ourshop/webrtc-hls` (permisos configurados)

### Variables de Entorno (.env del servidor)
```bash
WEBRTC_HLS_OUTPUT_DIR=/var/lib/ourshop/webrtc-hls
WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls
WEBRTC_RTP_FORWARD_HOST=127.0.0.1
JANUS_URL=http://127.0.0.1:8088/janus
JANUS_ROOM_ID=1234
```

**Los estudiantes NO despliegan nada**, solo configuran su `.env` local para apuntar al servidor central.

---

## 📦 Stack Tecnológico

```yaml
Backend: Spring Boot 3.2.6 + WebFlux (Reactivo)
Runtime: Java 21 LTS (recomendado)
Build: Maven Wrapper
Database: MySQL con R2DBC (reactivo)
WebRTC: Janus Gateway (servidor central)
Streaming: WebRTC → Janus rtp_forward → FFmpeg → HLS
Servidor: nginx (reverse proxy + HLS delivery)
```

---

## 🗓️ DÍA 1: Arquitectura Reactiva + CRUD de StreamMeta (4 horas)

### **Módulo 1: Introducción y Setup (45 min)**

#### 1.1 Arquitectura del Sistema (20 min)
- Flujo completo: Publisher (WebRTC) → Janus → FFmpeg → HLS → Viewers
- **Diferencias WebFlux vs MVC**:
  - MVC: 1 thread por request (bloqueante)
  - WebFlux: Event loop non-blocking (miles de requests concurrentes)
- Arquitectura reactiva: Mono/Flux, backpressure, schedulers

#### 1.2 Setup Local (25 min)
- Requisitos: **Java 21**, Maven, MySQL local
- Clonar proyecto base del instructor
- Configurar `.env` local:
  ```bash
  # Conexión a servidor central
  JANUS_URL=https://taller.ourshop.work/janus
  JANUS_ROOM_ID=1234
  WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls
  
  # Base de datos local
  SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/streaming
  DB_USERNAME=root
  DB_PASSWORD=tu_password
  ```
- Levantar con Maven Wrapper: `./mvnw spring-boot:run`
- Verificar: `curl http://localhost:8087/actuator/health`

**Entregable**: Backend corriendo en `localhost:8087`

---

### **Módulo 2: CRUD Reactivo de StreamMeta con R2DBC (2h)**

#### 2.1 Fundamentos de Reactor (45 min)
- **Mono vs Flux**: ¿Cuándo usar cada uno?
  - `Mono<T>`: 0 o 1 elemento (ej: buscar por ID)
  - `Flux<T>`: 0 a N elementos (ej: listar todos)
- **Operadores clave**:
  - `map()`: transformar datos
  - `flatMap()`: operaciones asíncronas encadenadas
  - `filter()`: filtrar elementos
  - `switchIfEmpty()`: valor por defecto
  - `onErrorResume()`: manejo de errores
- **⚠️ Regla de oro**: Nunca usar `.block()` en producción

#### 2.2 Modelo StreamMeta + State Machine (30 min)
- Entidad `StreamMeta` (R2DBC):
  ```java
  @Table("stream_meta")
  public class StreamMeta {
      @Id private Long id;
      private String streamKey;
      private String title;
      private StreamStatus status; // CREATED, LIVE, ENDED
      private Instant createdAt;
      private Instant startedAt;
      private Instant endedAt;
  }
  ```
- **State Machine**: `CREATED` → `LIVE` → `ENDED`
- Validaciones de transición de estado

#### 2.3 Repository + Service Reactivo (45 min)
- `StreamMetaRepository extends ReactiveCrudRepository`
- Métodos custom:
  ```java
  Flux<StreamMeta> findByStatus(StreamStatus status);
  Mono<StreamMeta> findByStreamKey(String streamKey);
  Flux<StreamMeta> findAllByOrderByCreatedAtDesc(Pageable pageable);
  ```
- `StreamMetaService`: lógica de negocio reactiva
- Paginación reactiva con `Pageable`

**Práctica**: Implementar búsqueda por título con `LIKE`

---

### **Módulo 3: Endpoints REST + Manejo de Errores (1h 15min)**

#### 3.1 Controller Reactivo (45 min)
- `StreamMetaController` con endpoints:
  ```java
  POST   /api/v1/streams          // Crear stream
  GET    /api/v1/streams          // Listar (paginado)
  GET    /api/v1/streams/{id}     // Obtener por ID
  PUT    /api/v1/streams/{id}     // Actualizar
  DELETE /api/v1/streams/{id}     // Eliminar
  GET    /api/v1/streams/search?q={query}  // Buscar
  ```
- Validaciones con `@Valid` + Bean Validation
- DTOs con MapStruct (opcional)

#### 3.2 Manejo de Errores Reactivo (30 min)
- `@ControllerAdvice` para errores globales
- Códigos HTTP apropiados:
  - `404 NOT_FOUND`: Stream no existe
  - `409 CONFLICT`: Transición de estado inválida
  - `400 BAD_REQUEST`: Validación fallida
- Logging estructurado con `@Slf4j`

**Práctica**: Probar endpoints con Postman/curl

---

## 🗓️ DÍA 2: WebRTC Signaling + Pipeline HLS (4 horas)

### **Módulo 4: Fundamentos WebRTC (1h)**

#### 4.1 Conceptos Esenciales (30 min)
- **SDP (Session Description Protocol)**:
  - Offer: Publisher describe sus capacidades
  - Answer: Janus responde con configuración aceptada
- **ICE Trickle**: Candidates se envían incrementalmente
- **STUN/TURN**: NAT traversal (servidor central ya configurado)

#### 4.2 Arquitectura con Janus (30 min)
- ¿Qué es Janus Gateway?
- Plugin **VideoRoom**: salas de conferencia
- **HTTP API de Janus**: mensajes JSON
- **rtp_forward**: Janus reenvía RTP a FFmpeg

**Demostración**: Inspeccionar SDP Offer/Answer con DevTools

---

### **Módulo 5: Signaling Simplificado (1h 30min)**

#### 5.1 Endpoints WebRTC (45 min)
- **Simplificación**: 1 publisher activo por `streamId` (sin manejo de sesiones en cliente)
- Endpoints:
  ```java
  GET    /api/v1/webrtc/ice-servers
  POST   /api/v1/streams/{streamId}/webrtc/offer
  POST   /api/v1/streams/{streamId}/webrtc/ice
  DELETE /api/v1/streams/{streamId}/webrtc
  ```

#### 5.2 Integración con Janus (45 min)
- `JanusService`: Cliente HTTP reactivo con `WebClient`
- Flujo completo:
  1. Cliente envía SDP Offer
  2. Backend crea sesión en Janus
  3. Backend configura `rtp_forward` a FFmpeg
  4. Backend retorna SDP Answer
  5. Cliente envía ICE Candidates
- Manejo de errores de Janus (timeouts, room full, etc.)

**Práctica**: Simular flujo con Postman (SDP mock)

---

### **Módulo 6: Pipeline WebRTC → HLS (1h 15min)**

#### 6.1 FFmpeg Reactivo (45 min)
- Proceso FFmpeg manejado de forma **no bloqueante**:
  ```java
  Mono.fromCallable(() -> {
      ProcessBuilder pb = new ProcessBuilder(
          "ffmpeg", "-protocol_whitelist", "file,udp,rtp",
          "-i", "rtp://127.0.0.1:10000",
          "-c:v", "libx264", "-preset", "ultrafast",
          "-hls_time", "2", "-hls_list_size", "5",
          "/var/lib/ourshop/webrtc-hls/{streamKey}/index.m3u8"
      );
      return pb.start();
  }).subscribeOn(Schedulers.boundedElastic());
  ```
- Monitoreo de proceso: logs en `ffmpeg.log`
- Cleanup al detener stream

#### 6.2 Servir HLS desde Backend (30 min)
- Endpoint: `GET /webrtc-hls/{streamKey}/index.m3u8`
- nginx del servidor central sirve archivos estáticos
- **Sin CDN**: HLS se sirve directamente desde el servidor
- CORS configurado para reproducción cross-origin

**Demostración**: Reproducir HLS en navegador con `video.js`

---

### **Módulo 7: Testing y Debugging (15 min)**

#### 7.1 Checklist de Verificación
- [ ] CRUD de `StreamMeta` funciona
- [ ] Signaling contra servidor central operativo
- [ ] HLS reproducible en `https://taller.ourshop.work/webrtc-hls/{streamKey}/index.m3u8`
- [ ] Logs de FFmpeg sin errores
- [ ] Actuator health check: `200 OK`

#### 7.2 Debugging Real
- Revisar logs de Janus en servidor central
- Inspeccionar `ffmpeg.log` para errores de codec
- Validar permisos de directorio HLS
- Network tab: verificar requests WebRTC

**Entregable Final**: Sistema completo funcionando end-to-end

---

## 🎓 Resumen de Aprendizajes

### Conceptos Técnicos Dominados
- ✅ Programación reactiva con Project Reactor (Mono/Flux)
- ✅ Spring WebFlux para APIs no bloqueantes
- ✅ R2DBC para persistencia reactiva (MySQL)
- ✅ WebRTC: SDP Offer/Answer, ICE Trickle
- ✅ Integración con Janus Gateway vía HTTP API
- ✅ FFmpeg para conversión RTP → HLS
- ✅ State Machine para gestión de streams

### Habilidades Prácticas
- ✅ CRUD reactivo de `StreamMeta` con paginación
- ✅ Signaling WebRTC simplificado (1 publisher por stream)
- ✅ Integración con servidor central pre-configurado
- ✅ Debugging de flujos reactivos
- ✅ Testing con curl/Postman

---

## 📚 Recursos Adicionales

### Documentación del Proyecto
- `README.md` - Overview general
- `AI-CONTEXT.md` - Contexto técnico completo (2400+ líneas)
- `docs/architecture/REACTIVE_IMPLEMENTATION_COMPLETE.md` - Arquitectura reactiva
- `docs/webrtc/JANUS_INSTALLATION_AND_CONFIG.md` - Instalación Janus

### Referencias Externas
- [Project Reactor Docs](https://projectreactor.io/docs) - Documentación oficial Reactor
- [Spring WebFlux Guide](https://spring.io/guides/gs/reactive-rest-service/) - Guía oficial Spring
- [Janus Gateway Docs](https://janus.conf.meetecho.com/docs/) - API de Janus
- [WebRTC for the Curious](https://webrtcforthecurious.com/) - Libro gratuito WebRTC

---

## 🚀 Extensiones Futuras (Post-Curso)

### Funcionalidades Avanzadas
- **Watchers en tiempo real**: Contador de viewers con WebSocket
- **Estadísticas de stream**: Bitrate, FPS, resolución en vivo
- **Chat integrado**: Mensajería en tiempo real con Reactor
- **Grabación de streams**: Guardar HLS en almacenamiento persistente
- **Multi-calidad (ABR)**: Adaptive Bitrate con múltiples resoluciones

### Observabilidad y Monitoreo
- **Prometheus + Grafana**: Métricas de streams activos, latencia, errores
- **Distributed Tracing**: Spring Cloud Sleuth + Zipkin
- **Alertas**: Notificaciones cuando FFmpeg falla o Janus se cae
- **Logs centralizados**: ELK Stack (Elasticsearch, Logstash, Kibana)

### Escalabilidad
- **Load Balancing**: Múltiples instancias de Janus con nginx upstream
- **Clustering Spring Boot**: Redis para sesiones compartidas
- **CDN**: Cloudflare/AWS CloudFront para distribución global de HLS
- **Auto-scaling**: Kubernetes para escalar según demanda

---

## ✅ Checklist de Preparación para Instructores

### Antes del Curso (1 semana antes)

#### Servidor Central (`taller.ourshop.work`)
- [ ] VPS con Ubuntu 22.04 LTS (mínimo 4GB RAM, 2 vCPUs)
- [ ] Janus Gateway instalado y corriendo en puerto `8088`
- [ ] nginx configurado con reverse proxy:
  - [ ] `/janus` → `http://127.0.0.1:8088/janus`
  - [ ] `/api/` → `http://127.0.0.1:8087/api/`
  - [ ] `/webrtc-hls/` → `/var/lib/ourshop/webrtc-hls/`
- [ ] FFmpeg instalado con `libx264` y `aac`
- [ ] Directorio HLS creado: `/var/lib/ourshop/webrtc-hls` (permisos `755`)
- [ ] Firewall configurado:
  - [ ] Puerto `80/443` (HTTP/HTTPS)
  - [ ] Puerto `8088` (Janus HTTP API - solo localhost)
  - [ ] Puertos `10000-10200/udp` (RTP de Janus)
- [ ] SSL/TLS configurado (Let's Encrypt recomendado)
- [ ] Health check funcionando: `curl https://taller.ourshop.work/health`

#### Materiales del Curso
- [ ] Repositorio Git con proyecto base:
  - [ ] Estructura Maven con `pom.xml` configurado
  - [ ] Entidad `StreamMeta` con anotaciones R2DBC
  - [ ] Repository y Service vacíos (para completar en clase)
  - [ ] Archivo `.env.example` con variables documentadas
- [ ] Base de datos MySQL script:
  - [ ] Schema `streaming` con tabla `stream_meta`
  - [ ] Datos de ejemplo (opcional)
- [ ] Postman Collection con requests de ejemplo:
  - [ ] CRUD de `StreamMeta`
  - [ ] Signaling WebRTC (Offer, ICE, Delete)
- [ ] Slides de presentación (opcional):
  - [ ] Arquitectura del sistema (diagrama)
  - [ ] Comparación WebFlux vs MVC
  - [ ] Flujo WebRTC completo

### Durante el Curso

#### Día 1
- [ ] Compartir enlace del repositorio Git
- [ ] Validar que todos tengan Java 21 instalado
- [ ] Ayudar con configuración de `.env` local
- [ ] Demostrar flujo CRUD completo al menos 2 veces
- [ ] Resolver dudas de Reactor en tiempo real

#### Día 2
- [ ] Demostrar signaling WebRTC con DevTools abierto
- [ ] Mostrar logs de Janus en servidor central
- [ ] Reproducir HLS en navegador para validar pipeline
- [ ] Debugging en vivo de errores comunes (FFmpeg, permisos, etc.)

### Después del Curso
- [ ] Compartir grabación de sesiones (si aplica)
- [ ] Canal de Slack/Discord para soporte post-curso (1 mes)
- [ ] Actualizar documentación con preguntas frecuentes
- [ ] Certificado de finalización (opcional)
- [ ] Encuesta de feedback para mejorar futuras ediciones

---

## 🐛 Errores Comunes y Soluciones

### Error: "Connection refused to Janus"
**Causa**: URL de Janus incorrecta en `.env`  
**Solución**: Verificar `JANUS_URL=https://taller.ourshop.work/janus` (con HTTPS si aplica)

### Error: "FFmpeg process exited with code 1"
**Causa**: Permisos insuficientes en directorio HLS  
**Solución**: Validar permisos de `/var/lib/ourshop/webrtc-hls/` en servidor central

### Error: "Stream not found" al reproducir HLS
**Causa**: Pipeline FFmpeg no generó archivos `.m3u8` / `.ts`  
**Solución**: Revisar logs de FFmpeg en `ffmpeg.log`, validar `rtp_forward` de Janus

### Error: "Blocking call detected" en logs
**Causa**: Uso de `.block()` en código reactivo  
**Solución**: Reemplazar con `subscribeOn(Schedulers.boundedElastic())` o `.subscribe()`

---

**Última actualización**: 2026-01-22  
**Versión del temario**: 3.0 (Servidor Central + Desarrollo Local)


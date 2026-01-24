
# Streaming Server — Visión general del curso por módulos

Este documento es la **visión general** del curso y del proyecto ubicado en `apps/backend/streaming`.

Objetivo del curso:

- Construir (y entender) un backend de streaming con **WebFlux**.
- Integrar **WebRTC** con **Janus** (signaling + sesiones).
- Operar un setup **prod-like** para un curso remoto con cliente Flutter.

Documentos del curso:

- Guía detallada paso a paso (por módulo): `apps/backend/streaming/README.md`
- Setup del instructor (server central, VPS, TLS, Janus, TURN): `apps/backend/streaming/README-CENTRAL.md`
- Paquete de Janus (configs + arranque): `apps/backend/streaming/janus/README.md`

---

## Contexto del curso (remoto + Flutter)

Si el curso es remoto y el cliente principal es una app Flutter, el enfoque más estable es **centralizar infraestructura**.

### Arquitectura del taller

Instructor (un VPS Linux público):

- Backend (HTTPS): API WebFlux (CRUD + signaling)
- Janus: en el mismo servidor (idealmente accesible solo desde `localhost`)
- TURN (coturn): en el mismo servidor (para NATs/redes restrictivas)

Alumnos (Flutter):

- Configuran `BACKEND_BASE_URL` + `iceServers` (STUN/TURN)
- Hacen la práctica sin abrir puertos ni montar Janus/TURN local

### Por qué centralizar

- Señalización: HTTP/JSON (suele funcionar casi siempre)
- Media WebRTC: UDP/DTLS/SRTP (es donde se rompe)

Centralizando, tú controlas el UDP del servidor y TURN cubre redes difíciles.

---

## Módulos del curso

### Módulo 0 — Preparación (recomendado)

Objetivo:

- Validar herramientas y conectividad (Flutter, permisos, reachability, URLs).

Entregables del instructor:

- `BACKEND_BASE_URL` (HTTPS)
- STUN/TURN listos para ICE (incluyendo fallback TCP y opcional `turns:`)

### Módulo 1 — StreamMeta (CRUD + estado)

Objetivo:

- Implementar el ciclo de vida del stream con persistencia.

Resultados:

- CRUD de streams + state machine (`CREATED → LIVE → ENDED`)
- MySQL (R2DBC), búsqueda y paginación

### Módulo 2 — StreamSession (signaling WebRTC con Janus)

Objetivo:

- Señalización publisher: `offer → answer` + ICE trickle.

Resultados:

- Sesiones por stream (`StreamSession`)
- Persistencia de contexto Janus (ids + estado)

### Módulo 3 (siguiente) — Watch/Subscriber

Objetivo:

- Completar el flujo viewer/subscriber (watch), control de concurrencia y UX.

---

## Guion recomendado (curso práctico)

- 0–30 min: práctica que funciona (Flutter publica contra server central)
- 30–60 min: troubleshooting (logs, síntomas típicos: UDP/TURN/HTTPS)
- 60–120 min: “paso a producción” (explicación guiada del servidor central)
- Homework: cada alumno replica en su VPS, con fallback al server central



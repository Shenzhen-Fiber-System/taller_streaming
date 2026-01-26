# 🚀 Streaming Workshop - Spring WebFlux Backend

Backend reactivo para el taller de streaming con Spring WebFlux, R2DBC y WebRTC.

## 📋 Tabla de Contenidos

- [Descripción](#descripción)
- [Arquitectura](#arquitectura)
- [Requisitos Previos](#requisitos-previos)
- [Instalación](#instalación)
- [Configuración](#configuración)
- [Ejecución](#ejecución)
- [Estructura del Proyecto](#estructura-del-proyecto)
- [API Endpoints](#api-endpoints)
- [Desplegar Tu Propio Servidor](#desplegar-tu-propio-servidor)

---

## 🎯 Descripción

Este proyecto es el backend del **Taller de Streaming con Spring WebFlux** (27-29 Enero 2026).

**¿Qué hace este backend?**
- ✅ **Jornada 1**: CRUD reactivo de metadatos de streams con R2DBC y MySQL
- ✅ **Jornada 2**: Proxy/orquestador de WebRTC que se conecta a un servidor central para streaming

**Importante**: Este backend **NO ejecuta** Janus Gateway ni FFmpeg localmente. Actúa como intermediario entre los clientes Flutter y un servidor central que gestiona toda la infraestructura de streaming.

---

## 🏗️ Arquitectura

```
┌─────────────┐      ┌──────────────────┐      ┌──────────────────┐
│   Flutter   │─────▶│  Este Backend    │─────▶│ Servidor Central │
│   Client    │      │  (Proxy/Orq.)    │      │ (Janus+FFmpeg)   │
└─────────────┘      └──────────────────┘      └──────────────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │  MySQL (Local)   │
                     │  Stream Metadata │
                     └──────────────────┘
```

### Componentes

| Componente | Responsabilidad |
|------------|----------------|
| **StreamMeta CRUD** | Gestiona metadatos de streams (título, estado, fechas) |
| **WebRTC Proxy** | Reenvía peticiones WebRTC al servidor central |
| **CentralServerClient** | Cliente HTTP reactivo para comunicarse con el servidor central |
| **State Machine** | Valida transiciones CREATED → LIVE → ENDED |

---

## ⚙️ Requisitos Previos

### Software
- ☕ **Java 21+** (JDK)
- 🗄️ **MySQL 8.0+**
- 🛠️ **Maven** (incluido como `mvnw`)
- 🌐 **Git**

### Conocimientos
- Java básico/intermedio
- HTTP y REST APIs
- Spring Boot fundamentos
- **No necesitas conocer** WebFlux ni WebRTC (se aprende en el taller)

---

## 📦 Instalación

### 1. Clonar el Repositorio

```bash
git clone <URL_DEL_REPO>
cd taller_streaming/projects/backend
```

### 2. Crear Base de Datos

```sql
CREATE DATABASE streamingdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Las tablas se crean automáticamente al iniciar la aplicación.

### 3. Configurar Variables de Entorno

```bash
# Copiar archivo de ejemplo
cp .env.example .env

# Editar con tus datos
nano .env
```

**Configuración mínima (Jornada 1):**
```properties
SPRING_R2DBC_URL=r2dbc:mysql://localhost:3306/streamingdb
DB_USERNAME=root
DB_PASSWORD=tu_password
```

**Configuración completa (Jornada 2):**
```properties
# Base de datos (local)
SPRING_R2DBC_URL=r2dbc:mysql://localhost:3306/streamingdb
DB_USERNAME=root
DB_PASSWORD=tu_password

# Servidor central (remoto)
CENTRAL_SERVER_BASE_URL=https://taller.ourshop.work
CENTRAL_SERVER_TIMEOUT_SECONDS=30
```

---

## 🚀 Ejecución

### Desarrollo Local

```bash
# Compilar y ejecutar
./mvnw spring-boot:run

# O en Windows
mvnw.cmd spring-boot:run
```

### Tests

```bash
./mvnw test
```

### Empaquetar

```bash
./mvnw clean package
java -jar target/streaming-0.0.1-SNAPSHOT.jar
```

---

## 📁 Estructura del Proyecto (Screaming Architecture)

```
src/main/java/com/ourshop/streaming/
├── streams/                        # Feature: Gestión de streams
│   ├── domain/
│   │   ├── StreamMeta.java        # Record inmutable
│   │   ├── StreamStatus.java      # Enum (CREATED, LIVE, ENDED)
│   │   └── exceptions/            # Excepciones de dominio
│   ├── application/
│   │   ├── StreamMetaService.java      # Interface (puerto)
│   │   └── StreamMetaServiceImpl.java  # Implementación
│   ├── infrastructure/
│   │   └── persistence/           # Repos R2DBC
│   └── api/
│       ├── StreamMetaController.java
│       └── dto/                   # DTOs de entrada/salida
│
├── webrtc/                        # Feature: Integración WebRTC
│   ├── domain/
│   │   └── exceptions/
│   ├── application/
│   │   ├── WebRtcService.java         # Interface
│   │   └── WebRtcProxyService.java    # Implementación (orquestador)
│   ├── infrastructure/
│   │   ├── client/
│   │   │   ├── CentralServerClient.java      # Interface
│   │   │   └── WebClientCentralServerClient.java  # HTTP client
│   │   └── config/
│   │       └── CentralServerProperties.java
│   └── api/
│       ├── WebRtcController.java
│       └── dto/
│
├── infra/                         # Infraestructura compartida
│   ├── CorsConfig.java
│   ├── DotenvInitializer.java
│   └── HttpRequestLoggingFilter.java
│
└── StreamingApplication.java
```

**Principios aplicados:**
- ✅ **Screaming Architecture**: Los paquetes gritan el dominio (`streams`, `webrtc`)
- ✅ **Dependency Inversion**: Controllers dependen de interfaces, no de implementaciones
- ✅ **Separation of Concerns**: API, aplicación, dominio e infraestructura separados

---

## 📡 API Endpoints

### Stream Metadata (Jornada 1)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/v1/streams` | Crear nuevo stream |
| `GET` | `/api/v1/streams` | Listar streams (paginado) |
| `GET` | `/api/v1/streams/{id}` | Obtener stream por ID |
| `PUT` | `/api/v1/streams/{id}/start` | Iniciar stream (CREATED → LIVE) |
| `PUT` | `/api/v1/streams/{id}/end` | Finalizar stream (LIVE → ENDED) |

### WebRTC Signaling (Jornada 2)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/v1/webrtc/ice-servers` | Obtener STUN/TURN servers |
| `GET` | `/api/v1/webrtc/health` | Verificar estado del servidor central |
| `POST` | `/api/v1/streams/{id}/webrtc/offer` | Enviar SDP offer, recibir answer |
| `POST` | `/api/v1/streams/{id}/webrtc/ice` | Enviar ICE candidate |
| `DELETE` | `/api/v1/streams/{id}/webrtc` | Cerrar sesión WebRTC |

### Ejemplos con cURL

**Crear stream:**
```bash
curl -X POST http://localhost:8087/api/v1/streams \
  -H "Content-Type: application/json" \
  -d '{"title": "Mi Stream", "description": "Test"}'
```

**Obtener ICE servers:**
```bash
curl http://localhost:8087/api/v1/webrtc/ice-servers
```

---

## 🖥️ Desplegar Tu Propio Servidor

Si quieres montar tu propia infraestructura completa (Janus + FFmpeg + HLS):

👉 **[Ver guía completa en `server-setup/`](server-setup/README.md)**

Incluye:
- Instalación paso a paso de Janus Gateway
- Configuración de FFmpeg para HLS
- Setup de Nginx como reverse proxy
- Integración del código Java completo
- Troubleshooting y optimización

---

## 🎓 Material del Taller

| Documento | Descripción |
|-----------|-------------|
| [PRERREQUISITOS_TALLER.md](guion/PRERREQUISITOS_TALLER.md) | Hardware, software y conocimientos previos |
| [GUION_TALLER.md](guion/GUION_TALLER.md) | Guión completo para instructores (Jornadas 1-2) |
| [FUNDAMENTOS_REACTOR.md](guion/FUNDAMENTOS_REACTOR.md) | Explicación didáctica de WebFlux/Reactor |
| [EXPLICACION_ARQUITECTURA.md](guion/EXPLICACION_ARQUITECTURA.md) | Arquitectura del sistema completo |

---

## 🛠️ Tecnologías Utilizadas

| Tecnología | Propósito |
|-----------|----------|
| **Spring Boot 3.x** | Framework principal |
| **Spring WebFlux** | Programación reactiva no-bloqueante |
| **Project Reactor** | Implementación de Reactive Streams |
| **Spring Data R2DBC** | Acceso reactivo a base de datos |
| **MySQL** | Base de datos relacional |
| **WebClient** | Cliente HTTP reactivo |
| **Lombok** | Reducir boilerplate |
| **Jackson** | Serialización JSON |

---

## 📖 Conceptos Clave del Taller

### State Machine de Streams

```
CREATED ──▶ LIVE ──▶ ENDED
   │                    ▲
   └────────────────────┘
      ❌ Transición inválida
```

- **CREATED**: Stream agendado, aún no transmite
- **LIVE**: Publisher activo, video fluyendo
- **ENDED**: Stream finalizado (histórico)

### Arquitectura Reactiva

- **No bloquea threads**: Un solo thread maneja miles de requests
- **Backpressure**: Control automático de flujo de datos
- **Operadores funcionales**: `map`, `flatMap`, `switchIfEmpty`, etc.

### Cliente al Servidor Central

El backend actúa como **proxy inteligente**:
1. Valida stream localmente (estado, permisos)
2. Reenvía peticiones WebRTC al servidor central
3. Actualiza estado del stream según respuesta
4. Devuelve resultado al cliente Flutter

---

## 🐛 Troubleshooting

### Backend no arranca

**Error**: `Cannot create PoolableConnectionFactory`
**Solución**: Verificar que MySQL esté corriendo y credenciales sean correctas.

### Test de CRUD falla

**Error**: `StreamNotFoundException`
**Solución**: La base de datos debe estar vacía antes de correr tests.

### Servidor central no responde

**Error**: `Connection timed out`
**Solución**: 
1. Verificar `CENTRAL_SERVER_BASE_URL` en `.env`
2. Comprobar que `https://taller.ourshop.work` sea accesible
3. Revisar firewall/proxy

---

## 📄 Licencia

Este proyecto es parte del material educativo del taller de OurSystem.

---

## 👥 Contacto y Soporte

- **Instructor**: [Información del taller]
- **Fechas**: 27-29 Enero 2026
- **Repositorio**: [URL del repo]

---

**¡Disfruta el taller y aprende WebFlux reactivo! 🚀**

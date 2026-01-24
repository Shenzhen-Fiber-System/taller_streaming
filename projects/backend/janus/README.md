# Janus — Runbook del curso (servidor central)

Este directorio contiene un “paquete” de configuración para Janus (VideoRoom + HTTP transport) usado por el proyecto de streaming en `apps/backend/streaming` (Módulo 2).

Objetivo del curso: que **todos los alumnos (Flutter) se conecten a un servidor del instructor**.

- El backend negocia SDP con Janus por HTTP.
- La media WebRTC (DTLS/SRTP) viaja por UDP directo al servidor (puertos RTP).

---

## Arquitectura recomendada para el curso (la que vamos a practicar)

- 1 VPS público del instructor
  - `apps/backend/streaming` (backend) expuesto por HTTPS (ideal) o HTTP temporal.
  - Janus en el mismo VPS.
  - (Opcional pero recomendado) TURN (`coturn`) para redes restrictivas.

Los alumnos NO hablan con Janus directamente: solo consumen el backend.

---

## Paso a paso (Ubuntu en el VPS del instructor)

### Antes de empezar: paths (evita confusiones)

Este README configura **Janus**. El servicio **ourshop-streaming** (Spring Boot) es otro componente y su `systemd unit` puede vivir en un path distinto.

- `REPO_ROOT`: dónde clonaste el repo (ej: `~/streaming` o `/home/deploy/streaming`).
- `JANUS_ROOT`: instalación de Janus (en este curso usamos `/opt/janus`).

Si estás usando documentación legacy del repo, es común encontrar rutas ` /opt/ourshop-streaming/... ` para el runtime del backend. Eso NO lo “pone Janus”: lo define el `ourshop-streaming.service` o scripts de setup del servidor.

### 0) Traer el proyecto al servidor (git clone)

Para el curso es más práctico clonar el repo en el VPS y copiar los configs desde ahí.

Ejemplo (ajusta la ruta si quieres otro folder):

```bash
# Instala git (si no lo tienes)
sudo apt-get update
sudo apt-get install -y git

# Clona tu repo (esto deja una carpeta ./streaming)
cd ~
git clone https://github.com/Shenzhen-Fiber-System/streaming.git

# (Opcional) Entra al repo
cd streaming

# (Opcional) Si necesitas una rama/tag específico:
# git checkout <branch-or-tag>
```

Desde aquí, cuando el README diga `cp apps/backend/streaming/janus/...`, asume que lo haces dentro del repo clonado (ej: `~/streaming`).

Tip: define una sola variable y úsala en todos los comandos para no mezclar rutas:

```bash
export REPO_ROOT="$HOME/streaming"
```

### 1) Instalar Janus (desde source)

Instala en `/opt/janus` (coincide con los paths de este repo):

```bash
# Actualiza el índice de paquetes del sistema
sudo apt-get update

# Instala dependencias necesarias para compilar Janus y sus plugins/transports
sudo apt-get install -y \
	git build-essential pkg-config \
	autoconf automake libtool gengetopt \
	cmake ninja-build \
	libmicrohttpd-dev \
	libjansson-dev \
	libssl-dev \
	libsofia-sip-ua-dev \
	libglib2.0-dev \
	libopus-dev libogg-dev \
	libcurl4-openssl-dev \
	liblua5.3-dev \
	libconfig-dev \
	libnice-dev \
	libsrtp2-dev

# Ve al home del usuario (un lugar “limpio” para clonar)
cd ~

# Clona el repo oficial de Janus Gateway
git clone https://github.com/meetecho/janus-gateway.git

# Entra al repo clonado
cd janus-gateway

# Fija una versión conocida (evita cambios sorpresivos en el taller)
git checkout v1.3.3

# Genera los scripts de build (autotools).
# Piensa en esto como: “preparar el proyecto para poder correr ./configure”.
# Si esto falla, normalmente faltan dependencias tipo autoconf/automake/libtool.
sh autogen.sh

# Configura el build y define dónde se instalará Janus.
# --prefix=/opt/janus significa que al final tendrás:
# - binarios en /opt/janus/bin
# - configs en /opt/janus/etc/janus
# - librerías en /opt/janus/lib
./configure --prefix=/opt/janus

# Compila el código fuente.
# -j"$(nproc)" usa todos los cores para hacerlo más rápido.
make -j"$(nproc)"

# Copia lo compilado al prefix (/opt/janus).
# Ojo: aquí ya “queda instalado” en el sistema (en /opt/janus).
sudo make install

# Instala configs de ejemplo/default del proyecto.
# Luego los reemplazamos por los configs del curso (los .jcfg de este repo).
sudo make configs
```

Validaciones rápidas (para estar seguros que se instaló bien):

```bash
# Debe existir el binario y ser ejecutable
ls -la /opt/janus/bin/janus

# Debe existir la carpeta de configs
ls -la /opt/janus/etc/janus | head

# Un sanity check: Janus debería imprimir help/version sin reventar
/opt/janus/bin/janus --help | head -n 5
```

### 2) Copiar configs del curso

```bash
# Ajusta REPO_ROOT si clonaste en otra ruta
REPO_ROOT=${REPO_ROOT:-"$HOME/streaming"}

# Caso A: repo plano con carpeta /janus
sudo mkdir -p /opt/janus/etc/janus

sudo cp "$REPO_ROOT/janus/janus.jcfg" /opt/janus/etc/janus/janus.jcfg
sudo cp "$REPO_ROOT/janus/janus.transport.http.jcfg" /opt/janus/etc/janus/janus.transport.http.jcfg
sudo cp "$REPO_ROOT/janus/janus.plugin.videoroom.jcfg" /opt/janus/etc/janus/janus.plugin.videoroom.jcfg

# Caso B (si el layout difiere): descubre y copia con find
ls -la "$(dirname "$REPO_ROOT")" | head || true

find "$REPO_ROOT" -type f \( \
	-name 'janus.jcfg' -o \
	-name 'janus.transport.http.jcfg' -o \
	-name 'janus.plugin.videoroom.jcfg' \
\) | sort

SRC_JANUS_JCFG=$(find "$REPO_ROOT" -type f -name 'janus.jcfg' | head -n 1)
SRC_HTTP_JCFG=$(find "$REPO_ROOT" -type f -name 'janus.transport.http.jcfg' | head -n 1)
SRC_VIDEOROOM_JCFG=$(find "$REPO_ROOT" -type f -name 'janus.plugin.videoroom.jcfg' | head -n 1)

if [ -n "$SRC_JANUS_JCFG" ] && [ -n "$SRC_HTTP_JCFG" ] && [ -n "$SRC_VIDEOROOM_JCFG" ]; then
	echo "Usando: $SRC_JANUS_JCFG"
	echo "Usando: $SRC_HTTP_JCFG"
	echo "Usando: $SRC_VIDEOROOM_JCFG"
	sudo cp "$SRC_JANUS_JCFG" /opt/janus/etc/janus/janus.jcfg
	sudo cp "$SRC_HTTP_JCFG" /opt/janus/etc/janus/janus.transport.http.jcfg
	sudo cp "$SRC_VIDEOROOM_JCFG" /opt/janus/etc/janus/janus.plugin.videoroom.jcfg
fi
```

Si clonaste el repo en otra ruta, cambia `/home/deploy/streaming` por la tuya.

Validaciones rápidas (para confirmar que copiaste los configs correctos):

```bash
# Verifica que los 3 archivos del curso existen
ls -la \
	/opt/janus/etc/janus/janus.jcfg \
	/opt/janus/etc/janus/janus.transport.http.jcfg \
	/opt/janus/etc/janus/janus.plugin.videoroom.jcfg

# Opcional: valida que el VideoRoom tenga tu "room" configurado.
# En estos configs del curso, el room NO suele venir como `room_id = 1234`.
# Normalmente se define como un bloque tipo: `room-1234: { ... }`

# Lista los rooms definidos (deberías ver algo como: room-1234:)
grep -nE '^[[:space:]]*room-[0-9]+[[:space:]]*:' /opt/janus/etc/janus/janus.plugin.videoroom.jcfg

# Si quieres inspeccionar el bloque (ajusta 1234 si usas otro room)
sed -n '/^[[:space:]]*room-1234[[:space:]]*:/,/^[[:space:]]*}/p' /opt/janus/etc/janus/janus.plugin.videoroom.jcfg
```

### 3) Ajustes mínimos para VPS (NAT / STUN)

Objetivo: evitar que Janus anuncie *candidates ICE incorrectos*.

Síntoma típico cuando esto está mal: el intercambio SDP (offer/answer) “parece” funcionar, pero la conexión se queda en `checking/connecting` o conecta sin audio/video (porque el cliente intenta llegar a una IP/puerto que no es accesible desde internet).

#### 3.1) Detectar si necesitas `nat_1_1_mapping`

En el VPS compara:

```bash
# IP pública vista desde internet
curl -4 ifconfig.me ; echo

# IP(s) locales del servidor (interfaces)
ip -4 addr show | grep -E "inet "
```

- Si `curl -4 ifconfig.me` **coincide** con una IP `inet` del VPS (misma IP pública), normalmente estás en “IP pública directa” y `nat_1_1_mapping` puede ser opcional.
- Si **NO coincide** (por ejemplo el VPS tiene `10.x/192.168.x` y `curl` devuelve otra IP), estás detrás de NAT/1:1 mapping y **sí** conviene setear `nat_1_1_mapping`.

#### 3.2) Config recomendada (taller)

Edita el archivo:

```bash
sudo nano /opt/janus/etc/janus/janus.jcfg
```

Y agrega/ajusta (ejemplo):

```ini
# Si hay NAT/1:1 mapping, fuerza la IP pública que Janus debe anunciar en los candidates
nat_1_1_mapping = "TU_IP_PUBLICA"

# STUN ayuda a Janus a descubrir su IP/puerto “visto desde fuera”
stun_server = "stun.l.google.com"
stun_port = 19302
```

Notas prácticas:

- `TU_IP_PUBLICA`: usa la que te devolvió `curl -4 ifconfig.me`.
- Aunque tengas IP pública directa, dejar STUN configurado suele ayudar en entornos “raros” (multi-NIC, clouds, etc.).

#### 3.3) Reiniciar y validar

```bash
sudo systemctl restart janus || true

# Si lo estás corriendo en modo debug (sin systemd), reinícialo manualmente.

# Validar que el config quedó como esperas
grep -nE "nat_1_1_mapping|stun_server|stun_port" /opt/janus/etc/janus/janus.jcfg | head -n 20
```

### 4) Abrir puertos (firewall)

Janus usa:

- HTTP API (solo para el backend): `7088/tcp` (base path `/janus`)
- Media WebRTC (obligatorio): `10000-10200/udp` (según `janus.jcfg`)

UFW ejemplo:

```bash
# Nota: si tu firewall está inactivo ("Status: inactive"), estas reglas se guardan pero NO aplican.
# Para aplicar UFW, puedes activarlo al final con `sudo ufw enable` (primero asegúrate de permitir 22/tcp).

sudo ufw allow 22/tcp

# Backend (idealmente expuesto por 80/443 con nginx)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Janus HTTP (no es obligatorio exponerlo público; útil para debug)
sudo ufw allow 7088/tcp

# Media WebRTC (OBLIGATORIO)
sudo ufw allow 10000:10200/udp

sudo ufw status

# Si sale "Status: inactive" y quieres usar UFW:
# sudo ufw enable
# sudo ufw status verbose
```

### 5) Arrancar Janus

Antes de arrancar: certificados DTLS (OBLIGATORIO)

- Janus usa DTLS-SRTP para cifrar el media en WebRTC.
- Si los archivos de certificado/llave NO existen, Janus muere con un error tipo:
  `Error opening certificate file (No such file or directory)`.
- En un taller, un certificado auto-firmado es suficiente (no necesita ser "confiable" como HTTPS).

Tu `janus.jcfg` apunta a:

- `/opt/janus/share/janus/certs/mycert.pem`
- `/opt/janus/share/janus/certs/mycert.key`

Genera ambos así:

```bash
# Crea la carpeta donde Janus espera encontrar el certificado DTLS
sudo mkdir -p /opt/janus/share/janus/certs

# Genera un certificado auto-firmado (X.509) + llave privada (RSA 2048)
# -nodes: sin passphrase (Janus necesita leer la llave sin pedir contraseña)
# -days: validez larga (taller)
# -subj: CN informativo (usa tu IP pública o un dominio)
sudo openssl req -x509 -newkey rsa:2048 -nodes \
	-keyout /opt/janus/share/janus/certs/mycert.key \
	-out /opt/janus/share/janus/certs/mycert.pem \
	-days 3650 \
	-subj "/CN=74.208.249.181"

# Validación: deben existir ambos archivos (mycert.pem y mycert.key)
sudo ls -la /opt/janus/share/janus/certs
```

Debug (rápido para taller):

```bash
/opt/janus/bin/janus -F /opt/janus/etc/janus
```

Tip: en otra terminal puedes confirmar que Janus está escuchando en HTTP (7088):

```bash
ss -lntp | grep 7088 || true
```

O como servicio (systemd):

```bash
# Caso A (tu VPS): repo plano
sudo cp /home/deploy/streaming/janus/janus.service /etc/systemd/system/janus.service

# Caso B (si tu repo está en otra ruta): busca el archivo
# sudo cp "$(find /home/deploy/streaming -type f -name 'janus.service' | head -n 1)" /etc/systemd/system/janus.service

sudo systemctl daemon-reload
sudo systemctl enable --now janus
sudo systemctl status janus
journalctl -u janus -f
```

### 6) Verificar Janus

```bash
curl http://localhost:7088/janus/info
```

Si este `curl` responde JSON, Janus está arriba y el HTTP transport funciona.
Si falla, revisa logs:

- Debug mode: mira el output en la terminal donde corriste Janus.
- systemd: `journalctl -u janus -f`

---

## Conectar el backend (apps/backend/streaming)

---

## Extra (opcional): exponer Janus por DNS (Cloudflare + Nginx + HTTPS)

Esto NO es necesario para el flujo del curso (los alumnos no hablan con Janus). Úsalo solo si quieres:

- debug desde fuera del VPS (`/janus/info`)
- o separar backend y Janus en hosts distintos en el futuro

Objetivo: `https://TU_HOSTNAME/janus/...` -> Nginx -> `http://127.0.0.1:7088/janus/...`

### A) DNS en Cloudflare

1) Hostname final (curso): `taller.ourshop.work`
2) Cloudflare → DNS → **Add record**
	- Type: `A`
	- Name: `taller`
	- IPv4: `TU_IP_PUBLICA`
	- Proxy status: para Janus (HTTP/WebSocket) normalmente **Proxied** funciona; si ves problemas, deja **DNS only**.
3) Valida que resuelva:

```bash
nslookup taller.ourshop.work
```

### B) Instalar Nginx + Certbot en el VPS

```bash
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx
```

Si usas UFW, asegúrate de permitir 80/443:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status
```

### C) Copiar el archivo Nginx del repo y adaptarlo al dominio

Nginx normalmente carga configs desde **/etc/nginx/sites-enabled/** (vía symlink desde **/etc/nginx/sites-available/**) y también desde **/etc/nginx/conf.d/**.

Importante: NO habilites el mismo proxy en ambos lugares a la vez, o vas a ver errores tipo:
`duplicate upstream "janus_backend"`.

1) Copia el archivo (desde el repo clonado en el VPS) a `sites-available`:

```bash
sudo cp /home/deploy/streaming/janus/nginx-janus-proxy-dev.conf /etc/nginx/sites-available/taller.ourshop.work.conf
```

2) Cambia el puerto y el `server_name` (curso: `taller.ourshop.work`):

```bash
sudo sed -i 's/listen 0.0.0.0:8088;/listen 80;/' /etc/nginx/sites-available/taller.ourshop.work.conf
sudo sed -i 's/server_name localhost;/server_name taller.ourshop.work;/' /etc/nginx/sites-available/taller.ourshop.work.conf
```

3) Habilita el sitio (symlink):

```bash
sudo ln -sf /etc/nginx/sites-available/taller.ourshop.work.conf /etc/nginx/sites-enabled/taller.ourshop.work.conf
```

4) Valida y recarga Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

### D) Emitir certificado HTTPS (Let’s Encrypt)

```bash
sudo certbot --nginx -d taller.ourshop.work
```

Cuando pregunte, elige redirección a HTTPS si está disponible.

Nota Cloudflare (muy común): si tu DNS está **Proxied** y en Cloudflare tienes SSL/TLS en modo **Flexible**, puedes quedar en un loop de `301` (Cloudflare habla HTTP con tu origin, Nginx redirige a HTTPS, y se repite). Para evitarlo:

- Cloudflare → SSL/TLS → Overview → cambia a **Full** o **Full (strict)**.
- Alternativa para debug: pon el registro como **DNS only** temporalmente.

### E) Verificar desde fuera

```bash
curl -sS https://taller.ourshop.work/janus/info | head
```

### Recomendación de seguridad (importante)

- Si ya expones Janus por Nginx, idealmente NO expongas `7088/tcp` público (déjalo solo local).
- Para el curso, lo más seguro es mantener Janus “interno” y exponer solo el backend.

En el `.env` del backend en el servidor (mismo VPS):

```dotenv
JANUS_URL=http://127.0.0.1:7088
JANUS_ROOM_ID=1234
JANUS_ROOM_SECRET=adminpwd
```

Verificación desde el backend:

- `GET /api/v1/webrtc/health`
- `GET /api/v1/webrtc/ice-servers`

Si `POST .../offer` no retorna `answer`, casi siempre es porque:

- `JANUS_URL` no apunta bien o Janus no está arriba
- `JANUS_ROOM_ID`/secret no coinciden con `janus.plugin.videoroom.jcfg`

---

## Opción local (solo para pruebas): WSL2

WSL sirve para desarrollar, pero NO es ideal para el curso con alumnos externos porque:

- el forwarding de puertos UDP (necesario para media WebRTC) es complicado desde WSL2

Para local, normalmente basta con:

- levantar Janus dentro de WSL
- pegarle desde Windows a `http://localhost:7088/janus/info`

---

## Nota importante: TURN (plan B del curso)

En redes corporativas/hoteles, STUN puede fallar. TURN da un camino “seguro” (relay) a costa de ancho de banda.

- Si varios alumnos no conectan: levanta `coturn` en el VPS y configura `WEBRTC_TURN_*` en el backend.

El endpoint `GET /api/v1/webrtc/ice-servers` es el que entrega STUN/TURN a Flutter.

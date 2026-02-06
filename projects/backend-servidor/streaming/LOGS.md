# 📊 LOGS - Comandos de Depuración WebRTC/HLS

Guía rápida para monitorear y depurar el sistema de streaming en producción.

---

## � Rendimiento del VPS

### CPU y Memoria
```bash
# Uso general de recursos
htop

# Resumen de uso de CPU/RAM/Disco
top -bn1 | head -20

# Memoria detallada
free -h

# Procesos que más consumen CPU
ps aux --sort=-%cpu | head -10

# Procesos que más consumen RAM
ps aux --sort=-%mem | head -10

# Uso de disco
df -h

# Espacio ocupado por directorio
du -sh /var/lib/ourshop/webrtc-hls/
```

### Red y Ancho de Banda
```bash
# Ver conexiones activas
ss -tun | wc -l

# Tráfico de red en tiempo real (requiere iftop)
sudo iftop -i eth0

# Estadísticas de red
netstat -s

# Monitoreo simple de red
watch -n 2 "ss -tun | wc -l"
```

### Temperatura y Carga del Sistema
```bash
# Load average (1, 5, 15 minutos)
uptime

# Temperatura CPU (si está disponible)
sensors

# Información del sistema
lscpu | grep -E "Model name|CPU\(s\)|Thread"
```

---

## �🔍 Logs de Servicios

### Logs Generales
```bash
# Ver logs de cualquier servicio
sudo journalctl -u [SERVICIO] -f
# Servicios: ourshop-streaming, janus, coturn

# Solo errores
sudo journalctl -u [SERVICIO] -p err -f

# Últimas N líneas
sudo journalctl -u [SERVICIO] -n 100

# Por rango de tiempo
sudo journalctl -u [SERVICIO] --since "10 minutes ago"
```

---

## 🚀 Procesos Activos

### Ver Servicios
```bash
# Estado de servicios
sudo systemctl status [SERVICIO]

# Verificar si están activos
systemctl is-active ourshop-streaming janus coturn

# Ver procesos
ps aux | grep [PROCESO]

# Puertos abiertos
sudo ss -tulnp | grep [PUERTO]
```

---

## 📁 Archivos HLS Generados

### Verificar HLS
```bash
# Listar streams
ls -lht /var/lib/ourshop/webrtc-hls/

# Ver stream específico
ls -lh /var/lib/ourshop/webrtc-hls/{stream_key}/

# Monitorear en tiempo real
watch -n 1 "ls -lht /var/lib/ourshop/webrtc-hls/{stream_key}/ | head -10"

# Ver playlist
cat /var/lib/ourshop/webrtc-hls/{stream_key}/index.m3u8
```

---

## 🌐 Red y Conectividad

### Puertos Abiertos
```bash
# Ver todos los puertos escuchando
sudo ss -tulnp

# Verificar puertos críticos
sudo ss -tulnp | grep -E "8087|7088|3478|10000"

# Verificar rango RTP de Janus (10000-10200)
sudo ss -unp | grep "10[0-2][0-9][0-9]"
```

### Red
```bash
# Puertos abiertos
sudo ss -tulnp | grep -E "8087|7088|3478"

# Capturar tráfico
sudo tcpdump -i any -n port [PUERTO] -c 20

# Firewall
sudo ufw statusSQL:
# Ver streams activos
SELECT id, title, status, created_at FROM streams WHERE status = 'LIVE';

# Ver sesiones WebRTC activas
SELECT * FROM stream_sessions WHERE status = 'CONNECTED';

# Contar sesiones por estado
SELECT status, COUNT(*) FROM stream_sessions GROUP BY status;

# Ver últimos streams creados
SELECT id, title, status, created_at FROM streams ORDER BY created_at DESC LIMIT 10;
```

---

## 🔧 Diagnóstico Rápido (All-in-One)

### Script Completo de Status
```bash
#!/bin/bash
echo "=== SERVICIOS ==="
systemctl is-active ourshop-streaming janus coturn

echo -e "\n=== PROCESOS FFmpeg ==="
ps aux | grep ffmpeg | grep -v grep | wc -l

echo -e "\n=== HLS STREAMS ==="
ls -lht /var/lib/ourshop/webrtc-hls/ | head -5

echo -e "\n=== PUERTOS ==="
sudo ss -tulnp | grep -E "8087|7088|3478"

echo -e "\n=== ÚLTIMOS LOGS BACKEND ==="
sudo journalctl -u ourshop-streaming -n 10 --no-pager

echo -e "\n=== ÚLTIMOS LOGS JANUS ==="
sudo journalctl -u janus -n 10 --no-pager
```

Guarda este script como `check-streaming.sh`, dale permisos `chmod +x check-streaming.sh` y ejecútalo:
```bash
./check-streaming.sh
```


```bash
# Ver todo de una vez
echo "=== SERVICIOS ==="
systemctl is-active ourshop-streaming janus coturn

echo -e "\n=== RECURSOS ==="
free -h
df -h | grep -E "Filesystem|/$"

echo -e "\n=== STREAMS ACTIVOS ==="
ps aux | grep ffmpeg | grep -v grep | wc -l
ls -lht /var/lib/ourshop/webrtc-hls/ | head -3

echo -e "\n=== LOGS RECIENTES ==="
sudo journalctl -u ourshop-streaming -n 5 --no-pager | tail -5

# Ver Solución de Problemas

```bash
# Servicio no arranca
sudo journalctl -u [SERVICIO] -xe
sudo lsof -i :[PUERTO]

# Sin video/audio
sudo journalctl -u janus -f | grep -E "DTLS|media"
sudo journalctl -u ourshop-streaming -f | grep -E "rtp_forward|FFmpeg"

# HLS no se genera
ps aux | grep ffmpeg
ls -lh /var/lib/ourshop/webrtc-hls/{stream_key}/
cat /var/lib/ourshop/webrtc-hls/{stream_key}/ffmpeg.log

# Problemas de red
sudo tcpdump -i any -n port [PUERTO] -c 20
curl http://localhost:8087/api/v1/webrtc/ice-servers

---

**Última actualización**: 2026-01-23

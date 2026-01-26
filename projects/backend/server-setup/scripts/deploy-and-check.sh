#!/bin/bash
# Deploy rápido del JAR al VPS y ver logs en tiempo real

set -e

JAR_LOCAL="target/streaming-0.0.1-SNAPSHOT.jar"
VPS="deploy@74.208.249.181"
REMOTE_DIR="/home/deploy/streaming"
SERVICE="ourshop-streaming"

echo "=== 1. Copiando JAR al VPS ==="
scp "$JAR_LOCAL" "$VPS:$REMOTE_DIR/streaming.jar"

echo -e "\n=== 2. Reiniciando servicio ==="
ssh "$VPS" "sudo systemctl restart $SERVICE"

echo -e "\n=== 3. Esperando 3 segundos ==="
sleep 3

echo -e "\n=== 4. Status del servicio ==="
ssh "$VPS" "sudo systemctl status $SERVICE --no-pager -l"

echo -e "\n=== 5. Logs en tiempo real (CTRL+C para salir) ==="
echo "Busca: 'Starting HLS pipeline', 'rtp_forward', 'FFmpeg'"
ssh "$VPS" "sudo journalctl -u $SERVICE -f"

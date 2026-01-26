#!/bin/bash
# Ver solo los logs relevantes para HLS/FFmpeg

VPS="deploy@74.208.249.181"
SERVICE="ourshop-streaming"

echo "=== Buscando logs HLS/FFmpeg (últimas 200 líneas) ==="
ssh "$VPS" "sudo journalctl -u $SERVICE -n 200 --no-pager" | grep -E "Starting HLS|rtp_forward|FFmpeg|publisherId|CONNECTED|ERROR|FAILED"

echo -e "\n=== Procesos FFmpeg activos ==="
ssh "$VPS" "ps aux | grep ffmpeg | grep -v grep"

echo -e "\n=== Archivos HLS generados ==="
ssh "$VPS" "ls -lht /var/lib/ourshop/webrtc-hls/ 2>/dev/null | head -10"

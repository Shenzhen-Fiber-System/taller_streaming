# 🔧 Troubleshooting Guide

Common issues and solutions when deploying your own streaming server.

## 📋 Table of Contents

1. [Janus Issues](#janus-issues)
2. [FFmpeg Issues](#ffmpeg-issues)
3. [Nginx Issues](#nginx-issues)
4. [WebRTC Connection Issues](#webrtc-connection-issues)
5. [HLS Playback Issues](#hls-playback-issues)
6. [Backend Integration Issues](#backend-integration-issues)

---

## Janus Issues

### Issue: Janus fails to start

**Symptoms:**
```bash
sudo systemctl status janus
● janus.service - Janus WebRTC Server
   Loaded: loaded
   Active: failed
```

**Solutions:**

1. **Check Janus logs:**
```bash
sudo journalctl -u janus -n 50
# Or
sudo tail -f /var/log/janus/janus.log
```

2. **Port already in use:**
```bash
sudo netstat -tulpn | grep 8088
```
If another process is using port 8088, either stop that process or change Janus HTTP transport port in `/etc/janus/janus.transport.http.jcfg`.

3. **Missing dependencies:**
```bash
sudo apt install -y libmicrohttpd-dev libjansson-dev libssl-dev
```

4. **Configuration syntax error:**
```bash
# Validate config files
janus --check-config
```

### Issue: VideoRoom plugin not loading

**Symptoms:**
Backend logs show: `Plugin 'janus.plugin.videoroom' not found`

**Solutions:**

1. **Verify plugin is installed:**
```bash
ls -la /usr/lib/janus/plugins/
# Should see libjanus_videoroom.so
```

2. **Check plugin configuration:**
```bash
sudo nano /etc/janus/janus.plugin.videoroom.jcfg
# Ensure room 1234 is configured
```

3. **Enable plugin in main config:**
```bash
sudo nano /etc/janus/janus.jcfg
# Ensure plugins path is correct
```

---

## FFmpeg Issues

### Issue: FFmpeg not found or missing codecs

**Symptoms:**
```
Cannot run program "ffmpeg": error=2, No such file or directory
```

**Solutions:**

1. **Install FFmpeg:**
```bash
sudo apt install -y ffmpeg libavcodec-extra
```

2. **Verify installation:**
```bash
which ffmpeg
ffmpeg -version
ffmpeg -codecs | grep h264
ffmpeg -codecs | grep aac
```

3. **Add FFmpeg to PATH (if installed in custom location):**
```bash
export PATH=$PATH:/usr/local/bin
```

### Issue: FFmpeg exits immediately (error code 1)

**Symptoms:**
FFmpeg logs show errors about input format or codecs

**Solutions:**

1. **Check FFmpeg logs:**
```bash
cat /var/lib/ourshop/webrtc-hls/{streamKey}/ffmpeg.log
```

2. **Common errors:**
   - **"Protocol not found":** Add `-protocol_whitelist file,udp,rtp` to FFmpeg command
   - **"Codec not supported":** Ensure libx264 and aac are available
   - **"Input/output error":** Check UDP port is not blocked by firewall

3. **Test RTP reception manually:**
```bash
ffplay -protocol_whitelist file,udp,rtp rtp://127.0.0.1:10000
```

### Issue: No HLS files generated

**Symptoms:**
FFmpeg process runs but no `.m3u8` or `.ts` files appear

**Solutions:**

1. **Check output directory permissions:**
```bash
ls -la /var/lib/ourshop/webrtc-hls/
sudo chown -R $USER:$USER /var/lib/ourshop/webrtc-hls/
```

2. **Verify RTP is being forwarded:**
Check Janus logs for rtp_forward confirmation.

3. **Test FFmpeg manually:**
```bash
ffmpeg -protocol_whitelist file,udp,rtp \
  -i rtp://127.0.0.1:10000 \
  -c:v libx264 -c:a aac \
  -f hls -hls_time 2 \
  /tmp/test.m3u8
```

---

## Nginx Issues

### Issue: Nginx fails to start

**Symptoms:**
```bash
sudo systemctl status nginx
● nginx.service - failed
```

**Solutions:**

1. **Test configuration:**
```bash
sudo nginx -t
```

2. **Common errors:**
   - **"bind() to 0.0.0.0:80 failed (98: Address already in use)":**
     ```bash
     sudo netstat -tulpn | grep :80
     sudo systemctl stop apache2  # If Apache is running
     ```
   
   - **"SSL certificate not found":**
     ```bash
     sudo certbot --nginx -d your-domain.com
     ```

3. **Check nginx error log:**
```bash
sudo tail -f /var/log/nginx/error.log
```

### Issue: 502 Bad Gateway when accessing Janus

**Symptoms:**
`curl https://your-domain.com/janus/info` returns 502

**Solutions:**

1. **Verify Janus is running:**
```bash
curl http://localhost:8088/janus/info
```

2. **Check nginx proxy configuration:**
```bash
sudo nano /etc/nginx/sites-available/streaming
# Verify proxy_pass matches Janus URL
```

3. **Check SELinux (if applicable):**
```bash
sudo setsebool -P httpd_can_network_connect 1
```

---

## WebRTC Connection Issues

### Issue: ICE connection fails

**Symptoms:**
Browser console shows: `ICE connection state: failed`

**Solutions:**

1. **Verify STUN servers are reachable:**
```bash
# Test with browser or use stunclient
stunclient stun.l.google.com 19302
```

2. **Check firewall rules:**
```bash
sudo ufw status
# Ensure UDP ports 10000-10200 are open
sudo ufw allow 10000:10200/udp
```

3. **Verify NAT traversal:**
If behind NAT, configure TURN server in backend:
```yaml
webrtc:
  turn:
    server: turn:your-turn-server.com:3478
    username: youruser
    credential: yourpass
```

4. **Check Janus ICE configuration:**
```bash
sudo nano /etc/janus/janus.jcfg
# Ensure nat.1_1_mapping is set if behind NAT
```

### Issue: SDP negotiation fails

**Symptoms:**
Backend logs show: `Janus HTTP error: 456 - No such room`

**Solutions:**

1. **Verify room exists:**
```bash
# Check VideoRoom config
sudo nano /etc/janus/janus.plugin.videoroom.jcfg
```

2. **Check room ID in backend config:**
```yaml
janus:
  videoroom:
    room-id: 1234  # Must match room in config
```

3. **Check admin key:**
```yaml
janus:
  admin-key: supersecret  # Must match Janus config
```

---

## HLS Playback Issues

### Issue: HLS URL returns 404

**Symptoms:**
`curl https://your-domain.com/webrtc-hls/{streamKey}/index.m3u8` returns 404

**Solutions:**

1. **Verify nginx is serving HLS files:**
```bash
sudo nano /etc/nginx/sites-available/streaming
# Check: location /webrtc-hls/ { alias /var/lib/ourshop/webrtc-hls/; }
```

2. **Check files exist:**
```bash
ls -la /var/lib/ourshop/webrtc-hls/{streamKey}/
# Should see index.m3u8 and seg_*.ts
```

3. **Test direct file access:**
```bash
curl http://localhost/webrtc-hls/{streamKey}/index.m3u8
```

### Issue: HLS plays but stutters/buffers

**Symptoms:**
Video playback is choppy or frequently pauses

**Solutions:**

1. **Check FFmpeg encoding parameters:**
Reduce encoding complexity in `FfmpegHlsService.java`:
```java
cmd.add("-preset");
cmd.add("ultrafast");  // Use fastest preset
```

2. **Increase HLS segment size:**
```java
cmd.add("-hls_time");
cmd.add("4");  // Increase from 2 to 4 seconds
```

3. **Monitor CPU usage:**
```bash
top
# If CPU is maxed, consider hardware acceleration:
# -c:v h264_nvenc (for NVIDIA GPUs)
```

4. **Check network bandwidth:**
```bash
# Test download speed from server
curl -o /dev/null https://your-domain.com/webrtc-hls/{streamKey}/seg_00001.ts
```

---

## Backend Integration Issues

### Issue: Backend can't connect to Janus

**Symptoms:**
```
Failed to connect to Janus: Connection refused
```

**Solutions:**

1. **Verify Janus URL in config:**
```yaml
janus:
  url: http://localhost:8088  # Or https://domain/janus
```

2. **Test connectivity:**
```bash
curl http://localhost:8088/janus/info
```

3. **Check firewall between backend and Janus:**
```bash
telnet localhost 8088
```

### Issue: RTP forwarding fails

**Symptoms:**
Backend logs: `rtp_forward returned empty stream_id`

**Solutions:**

1. **Verify admin key:**
```yaml
janus:
  admin-key: supersecret  # Must match Janus config
```

2. **Check Janus VideoRoom plugin logs:**
```bash
sudo journalctl -u janus | grep rtp_forward
```

3. **Verify UDP ports are available:**
```bash
sudo netstat -ulpn | grep 10000
```

### Issue: Session management errors

**Symptoms:**
Multiple sessions created for same stream

**Solutions:**

1. **Implement proper session cleanup:**
Ensure `closePublisher()` is called when stream ends.

2. **Check database for orphaned sessions:**
```sql
SELECT * FROM stream_session WHERE status != 'CLOSED';
```

3. **Add session timeout:**
Automatically close sessions after N minutes of inactivity.

---

## 🔍 Diagnostic Commands

### Health Check Script

```bash
#!/bin/bash
echo "=== System Health Check ==="

echo "1. Janus Status:"
systemctl status janus | grep Active

echo "2. Nginx Status:"
systemctl status nginx | grep Active

echo "3. Janus API:"
curl -s http://localhost:8088/janus/info | jq .janus

echo "4. HLS Files:"
ls /var/lib/ourshop/webrtc-hls/ | head -5

echo "5. Open Ports:"
sudo netstat -tulpn | grep -E '(8088|80|443|10000)'

echo "6. Disk Space:"
df -h /var/lib/ourshop/

echo "7. FFmpeg:"
which ffmpeg && ffmpeg -version | head -1
```

### Log Aggregation

```bash
# View all relevant logs in real-time
sudo tail -f \
  /var/log/janus/janus.log \
  /var/log/nginx/error.log \
  /var/lib/ourshop/webrtc-hls/*/ffmpeg.log
```

---

## 📞 Getting Help

If you're still stuck:

1. **Check full logs** with timestamps
2. **Test each component independently** (Janus → FFmpeg → Nginx)
3. **Review workshop guion** for architecture clarifications
4. **Compare with working reference server** at `https://taller.ourshop.work`

**Common patterns:**
- 🔥 **Connection refused**: Service not running or firewall blocking
- ⚠️ **Permission denied**: File/directory permissions incorrect
- 🚫 **404 Not Found**: Nginx misconfiguration or files missing
- ⏱️ **Timeout**: Network issue or service overloaded

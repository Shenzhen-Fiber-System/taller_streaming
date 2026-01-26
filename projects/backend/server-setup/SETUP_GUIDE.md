# 📘 Complete Server Setup Guide

This guide walks you through deploying your own complete streaming infrastructure from scratch.

## 🎯 What You'll Build

By the end of this guide, you'll have:
- ✅ Janus Gateway running with VideoRoom plugin
- ✅ FFmpeg configured for RTP → HLS transcoding
- ✅ Nginx serving HLS content and reverse proxying Janus
- ✅ Java backend integrated with Janus + FFmpeg
- ✅ Full WebRTC → HLS streaming pipeline

## 📋 Prerequisites

### Hardware Requirements
- **Server**: Ubuntu 22.04 LTS (or similar Debian-based distro)
- **RAM**: 4GB minimum, 8GB recommended
- **CPU**: 2+ cores recommended
- **Disk**: 20GB+ free space
- **Network**: Public IP address with open ports

### Software Requirements
- Root or sudo access
- Basic Linux command line knowledge
- Domain name pointed to your server (optional but recommended)

### Ports to Open
```bash
# HTTP/HTTPS
80/tcp   - HTTP (for Let's Encrypt and HTTP traffic)
443/tcp  - HTTPS

# Janus
8088/tcp - Janus HTTP API (behind nginx proxy)

# WebRTC Media (UDP)
10000-10200/udp - RTP streams (Janus to FFmpeg)
```

---

## 🚀 Installation Steps

### Step 1: Update System

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git build-essential
```

### Step 2: Install Janus Gateway

#### Option A: From Repository (Easier)
```bash
# Add Janus repository
sudo add-apt-repository -y ppa:janus/stable
sudo apt update

# Install Janus
sudo apt install -y janus janus-dev
```

#### Option B: From Source (More Control)
```bash
# Install dependencies
sudo apt install -y \
  libmicrohttpd-dev \
  libjansson-dev \
  libssl-dev \
  libsofia-sip-ua-dev \
  libglib2.0-dev \
  libopus-dev \
  libogg-dev \
  libcurl4-openssl-dev \
  liblua5.3-dev \
  libconfig-dev \
  pkg-config \
  gengetopt \
  libtool \
  automake

# Clone and build Janus
cd /usr/local/src
sudo git clone https://github.com/meetecho/janus-gateway.git
cd janus-gateway

sudo sh autogen.sh
sudo ./configure --prefix=/opt/janus
sudo make
sudo make install
```

**Configuration Files Location:**
- From repo: `/etc/janus/`
- From source: `/opt/janus/etc/janus/`

### Step 3: Configure Janus

Copy the configuration files from `server-setup/janus/` to Janus config directory:

```bash
# Backup original configs
sudo cp /etc/janus/janus.jcfg /etc/janus/janus.jcfg.bak
sudo cp /etc/janus/janus.plugin.videoroom.jcfg /etc/janus/janus.plugin.videoroom.jcfg.bak

# Copy new configs
sudo cp server-setup/janus/janus.jcfg /etc/janus/
sudo cp server-setup/janus/janus.plugin.videoroom.jcfg /etc/janus/
sudo cp server-setup/janus/janus.transport.http.jcfg /etc/janus/
```

**Key Configuration Changes:**
- HTTP transport on port 8088
- VideoRoom plugin with room 1234
- Admin key: `supersecret` (change this!)
- RTP forwarding enabled

### Step 4: Install FFmpeg

```bash
# Install FFmpeg with x264 support
sudo apt install -y ffmpeg libavcodec-extra

# Verify installation
ffmpeg -version
ffmpeg -codecs | grep h264
```

**Important**: Ensure FFmpeg has H.264 and AAC codec support.

### Step 5: Install Nginx

```bash
sudo apt install -y nginx

# Stop default nginx to configure
sudo systemctl stop nginx
```

### Step 6: Configure Nginx

Copy nginx configuration:

```bash
sudo cp server-setup/janus/nginx-janus-proxy-dev.conf /etc/nginx/sites-available/streaming

# Update server_name in the file
sudo nano /etc/nginx/sites-available/streaming
# Change: server_name taller.ourshop.work;
# To:     server_name your-domain.com;

# Enable site
sudo ln -s /etc/nginx/sites-available/streaming /etc/nginx/sites-enabled/

# Test configuration
sudo nginx -t

# Start nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

### Step 7: Configure SSL (Let's Encrypt)

```bash
# Install certbot
sudo apt install -y certbot python3-certbot-nginx

# Get SSL certificate
sudo certbot --nginx -d your-domain.com

# Certbot will automatically configure nginx for HTTPS
```

### Step 8: Setup HLS Output Directory

```bash
# Create directory for HLS segments
sudo mkdir -p /var/lib/ourshop/webrtc-hls
sudo chown -R $USER:$USER /var/lib/ourshop/webrtc-hls
sudo chmod -R 755 /var/lib/ourshop/webrtc-hls

# Configure nginx to serve HLS files
# This is already in the nginx config at:
# location /webrtc-hls/ { alias /var/lib/ourshop/webrtc-hls/; }
```

### Step 9: Create Janus Systemd Service

```bash
sudo cp server-setup/janus/janus.service /etc/systemd/system/

# Reload systemd
sudo systemctl daemon-reload

# Start Janus
sudo systemctl start janus
sudo systemctl enable janus

# Check status
sudo systemctl status janus
```

### Step 10: Configure Firewall

```bash
# Allow HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Allow Janus HTTP API (if not behind nginx)
sudo ufw allow 8088/tcp

# Allow WebRTC media (UDP range for RTP)
sudo ufw allow 10000:10200/udp

# Enable firewall
sudo ufw enable
sudo ufw status
```

---

## 🔧 Java Backend Integration

### Step 1: Copy Java Implementation

The complete Java code is in `server-setup/java-implementation/`:

- `JanusClient.java` - HTTP client for Janus API
- `FfmpegHlsService.java` - FFmpeg process management
- `WebRtcSignalingService.java` - Main orchestrator
- `StreamSession.java` - Session model

**Copy these files to your backend:**

```bash
# Assuming your backend is in ../src/main/java/com/ourshop/streaming/
cp server-setup/java-implementation/*.java \
   ../src/main/java/com/ourshop/streaming/webrtc/
```

### Step 2: Configure Application Properties

Update your `application.yml` or `.env`:

```yaml
janus:
  url: http://localhost:8088  # Or https://your-domain.com/janus if behind nginx
  api:
    timeout-seconds: 30
  admin-key: supersecret
  videoroom:
    room-id: 1234
    secret: adminpwd

webrtc:
  hls:
    output-dir: /var/lib/ourshop/webrtc-hls
    public-base-url: https://your-domain.com/webrtc-hls
```

### Step 3: Add Maven Dependencies

If not already present, add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### Step 4: Restart Backend

```bash
./mvnw clean package
./mvnw spring-boot:run
```

---

## ✅ Verification

### 1. Check Janus

```bash
# Check Janus is running
curl http://localhost:8088/janus/info

# Should return JSON with Janus version
```

### 2. Check Nginx

```bash
# Test Janus through nginx
curl https://your-domain.com/janus/info

# Test health endpoint (if backend is running)
curl https://your-domain.com/actuator/health
```

### 3. Test WebRTC ICE Servers

```bash
curl https://your-domain.com/api/v1/webrtc/ice-servers

# Should return STUN/TURN configuration
```

### 4. Test HLS Generation

After a successful WebRTC session:

```bash
ls -lh /var/lib/ourshop/webrtc-hls/{streamKey}/

# Should see: index.m3u8 and seg_*.ts files
```

### 5. Play HLS Stream

Open in VLC or browser:
```
https://your-domain.com/webrtc-hls/{streamKey}/index.m3u8
```

---

## 📊 Monitoring

### View Janus Logs

```bash
sudo journalctl -u janus -f
```

### View Nginx Logs

```bash
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

### View FFmpeg Logs

```bash
# FFmpeg logs are in the HLS output directory
tail -f /var/lib/ourshop/webrtc-hls/{streamKey}/ffmpeg.log
```

### Backend Logs

```bash
# If running as systemd service
sudo journalctl -u streaming-backend -f

# Or check application logs
tail -f logs/spring.log
```

---

## 🆘 Troubleshooting

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for common issues and solutions.

**Quick Checks:**

1. **Janus not starting:**
   - Check `/var/log/janus/janus.log`
   - Verify port 8088 is not in use: `sudo netstat -tulpn | grep 8088`

2. **FFmpeg not generating HLS:**
   - Check FFmpeg is in PATH: `which ffmpeg`
   - Verify UDP ports are open: `sudo ufw status`
   - Check permissions on output directory

3. **WebRTC connection fails:**
   - Verify STUN/TURN servers are reachable
   - Check browser console for ICE connection errors
   - Ensure UDP ports 10000-10200 are open

4. **HLS playback fails:**
   - Verify nginx is serving files: `curl https://your-domain.com/webrtc-hls/test/`
   - Check file permissions: `ls -la /var/lib/ourshop/webrtc-hls/`
   - Inspect FFmpeg logs for transcoding errors

---

## 🎓 Next Steps

1. **Security Hardening:**
   - Change default passwords (Janus admin key, room secrets)
   - Configure fail2ban for SSH protection
   - Setup regular security updates

2. **Performance Optimization:**
   - Tune FFmpeg encoding parameters
   - Configure nginx caching for HLS segments
   - Monitor CPU/RAM usage and scale as needed

3. **High Availability:**
   - Setup load balancer for multiple Janus instances
   - Use CDN for HLS delivery (CloudFlare, AWS CloudFront)
   - Configure database replication

4. **Monitoring & Alerts:**
   - Setup Prometheus + Grafana for metrics
   - Configure alerting for service failures
   - Monitor HLS segment generation rate

---

## 📚 Additional Resources

- [Janus Documentation](https://janus.conf.meetecho.com/docs/)
- [FFmpeg Documentation](https://ffmpeg.org/documentation.html)
- [WebRTC Basics](https://webrtc.org/getting-started/overview)
- [HLS Specification](https://datatracker.ietf.org/doc/html/rfc8216)

---

**Need Help?** Check [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) or review the workshop guion.

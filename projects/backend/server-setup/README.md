# 🚀 Deploy Your Own Streaming Server

This directory contains everything you need to deploy your own complete streaming infrastructure with Janus Gateway, FFmpeg, and the Java backend.

## 📦 What's Included

- **`janus/`**: Janus Gateway configuration files
- **`ffmpeg/`**: FFmpeg setup documentation and examples
- **`scripts/`**: Deployment and monitoring scripts
- **`java-implementation/`**: Complete Java code for local Janus/FFmpeg integration
- **`docs/`**: Detailed setup guides for each component

## 🎯 Quick Links

| Document | Description |
|----------|-------------|
| [**SETUP_GUIDE.md**](SETUP_GUIDE.md) | 📘 Complete step-by-step setup guide |
| [**ARCHITECTURE.md**](ARCHITECTURE.md) | 🏛️ Architecture explanation |
| [docs/JANUS_INSTALLATION.md](docs/JANUS_INSTALLATION.md) | Janus Gateway installation |
| [docs/FFMPEG_CONFIGURATION.md](docs/FFMPEG_CONFIGURATION.md) | FFmpeg configuration |
| [docs/NGINX_SETUP.md](docs/NGINX_SETUP.md) | Nginx reverse proxy setup |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Common issues and solutions |

## ⚡ Quick Start

### Prerequisites
- Ubuntu 22.04 LTS or similar
- 4GB RAM minimum (8GB recommended)
- Java 21+ installed
- Root or sudo access

### Installation

```bash
# 1. Navigate to this directory
cd server-setup/

# 2. Run the installation script
chmod +x scripts/install-dependencies.sh
sudo ./scripts/install-dependencies.sh

# 3. Follow the SETUP_GUIDE.md for detailed configuration
```

## 🏗️ Architecture Overview

```
Flutter Client → Your Java Backend → Janus Gateway → FFmpeg → HLS → CDN/Nginx
                                         ↓
                                    RTP Forward
                                         ↓
                                      FFmpeg
                                         ↓
                                  HLS Segments (.m3u8/.ts)
```

The backend orchestrates:
1. **WebRTC Signaling**: SDP negotiation with Janus
2. **RTP Forwarding**: Janus forwards RTP to local UDP ports
3. **HLS Transcoding**: FFmpeg converts RTP to HLS
4. **Content Delivery**: Nginx serves HLS to viewers

## 📋 What You Need to Understand

### Java Implementation

The complete Java code in `java-implementation/` includes:

- **JanusClient**: HTTP client for Janus Gateway API
- **FfmpegHlsService**: Manages FFmpeg processes for HLS generation
- **WebRtcSignalingService**: Orchestrates the entire streaming pipeline
- **StreamSession**: Tracks active WebRTC sessions with Janus IDs

### Integration Points

Your backend needs to:

1. Create Janus sessions and attach to VideoRoom plugin
2. Send SDP offers to Janus and receive answers
3. Configure RTP forwarding to local UDP ports
4. Launch FFmpeg processes listening on those ports
5. Manage session lifecycle (keepalive, cleanup)

## 🔥 Key Differences from Workshop Code

The **main workshop repository** uses a simplified architecture:
- Backend acts as a **proxy** to a remote streaming server
- No local Janus/FFmpeg installation required
- Focus on Spring WebFlux and reactive programming

This **server-setup/** directory is for:
- Production deployments
- Self-hosted infrastructure
- Complete control over the streaming stack

## 📚 Next Steps

1. Read [SETUP_GUIDE.md](SETUP_GUIDE.md) for complete installation
2. Review [ARCHITECTURE.md](ARCHITECTURE.md) to understand the flow
3. Check [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for common issues
4. Integrate the Java code from `java-implementation/` into your backend

## 🆘 Support

If you encounter issues:
1. Check [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
2. Review Janus logs: `/var/log/janus/janus.log`
3. Check FFmpeg logs in the HLS output directory
4. Verify firewall rules (UDP ports 10000-10200)

## 📄 License

This code is part of the Spring WebFlux Streaming Workshop.
See the main repository for license details.

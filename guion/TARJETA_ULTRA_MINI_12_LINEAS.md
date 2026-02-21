1) A-B-C-D-E: Arquitectura, Base de código, Config remota, Demo, Errores.
2) Tiempo: 20-25-25-25-25 (no te salgas del reloj).
3) Clave: REST señaliza, WebRTC transporta media, HLS distribuye.
4) Flujo: Offer -> Answer -> ICE -> RTP forward -> HLS.
5) URLs taller: JANUS_URL=https://taller.ourshop.work/janus.
6) HLS público: WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls.
7) Capas: Controller entra, Service orquesta, Janus conecta, FFmpeg genera.
8) Demo: streamId listo -> /offer -> /ice -> index.m3u8.
9) Caso 1: sin answer SDP -> revisar URL Janus + room + reachability.
10) Caso 2: hay answer sin video -> revisar RTP/UDP/FFmpeg.
11) Caso 3: URL rara -> revisar base URL efectiva en runtime.
12) Cierre: no adivinar, diagnosticar por capas (Config -> Conectividad -> Código).
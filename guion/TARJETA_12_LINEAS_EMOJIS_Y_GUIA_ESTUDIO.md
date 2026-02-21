# Tarjeta 12 líneas + emojis visuales

1) 🧭 A-B-C-D-E: Arquitectura, Base de código, Config remota, Demo, Errores.
2) ⏱️ Tiempo: 20-25-25-25-25 (el reloj manda).
3) 🧠 Regla madre: REST señaliza, WebRTC transporta media, HLS distribuye.
4) 🔁 Flujo: Offer -> Answer -> ICE -> RTP forward -> HLS.
5) 🌐 Janus: JANUS_URL=https://taller.ourshop.work/janus.
6) 📺 HLS público: WEBRTC_HLS_PUBLIC_BASE_URL=https://taller.ourshop.work/webrtc-hls.
7) 🧱 Capas: Controller entra, Service orquesta, Janus conecta, FFmpeg genera.
8) 🧪 Demo: streamId listo -> /offer -> /ice -> index.m3u8.
9) 🚨 Caso 1: sin answer SDP -> URL Janus + room + reachability.
10) 🛠️ Caso 2: hay answer sin video -> RTP/UDP/FFmpeg.
11) 🔍 Caso 3: URL rara -> revisar base URL efectiva en runtime.
12) ✅ Cierre: no adivinar, diagnosticar por capas (Config -> Conectividad -> Código).

---

# Guía de estudio (orden estricto para mejores resultados)

## Fase 1 — Memoria base (10 min)
1. Lee en voz alta las 12 líneas completas, 3 veces.
2. Tapa el texto y repite solo los inicios: A-B-C-D-E, Tiempo, Regla madre, Flujo, Janus, HLS, Capas, Demo, Caso 1, Caso 2, Caso 3, Cierre.
3. Si fallas una línea, reinicia desde la línea anterior (no saltes pasos).

## Fase 2 — Orden y tiempos (10 min)
1. Dibuja en una hoja: A(20) - B(25) - C(25) - D(25) - E(25).
2. Relaciona cada bloque con sus líneas:
   - A: líneas 1, 3, 4
   - B: línea 7
   - C: líneas 5, 6
   - D: línea 8
   - E: líneas 9, 10, 11, 12
3. Ensaya una pasada completa de 3 minutos sin mirar notas.

## Fase 3 — Simulación de clase (15 min)
1. Simula apertura (30s) usando líneas 1-4.
2. Simula configuración (60s) usando líneas 5-6.
3. Simula demo (60s) usando líneas 7-8.
4. Simula troubleshooting (90s) usando líneas 9-11.
5. Simula cierre (30s) con línea 12.

## Fase 4 — Blindaje anti-bloqueo (5 min)
1. Si te trabas, repite: “A-B-C-D-E”.
2. Retoma desde la última línea correcta, no improvises fuera de orden.
3. Usa esta frase puente: “Volvamos al flujo: control, media y observabilidad”.

## Checklist previo al taller (2 min)
- [ ] Puedo recitar las 12 líneas sin mirar.
- [ ] Recuerdo el bloque de cada línea.
- [ ] Recuerdo los 3 casos de falla y su diagnóstico.
- [ ] Mantengo el orden A-B-C-D-E sin saltos.
- [ ] Tengo claro el cierre técnico en una frase.

#!/bin/bash
# ============================================
# DEPLOY STREAMING (SIN AMBIENTES)
# ============================================
# Objetivo:
# - Compilar el Spring Boot del proyecto (repo plano)
# - Copiar el JAR a /home/deploy/lib/streaming-api.jar
# - Copiar .env a /home/deploy/config/.env
# - Reiniciar el servicio systemd (por defecto: ourshop-streaming)
#
# Uso:
#   ./deploy-streaming.sh
#
# Personalización (opcional) con variables de entorno:
#   SERVICE_NAME=ourshop-streaming DEPLOY_JAR_NAME=streaming-api.jar ./deploy-streaming.sh
# ============================================

set -euo pipefail

# Defaults alineados con tu layout en VPS (puedes override por env vars)
SERVICE_NAME="${SERVICE_NAME:-ourshop-streaming}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/home/deploy}"
DEPLOY_LIB_DIR="${DEPLOY_LIB_DIR:-$DEPLOY_ROOT/lib}"
DEPLOY_CONFIG_DIR="${DEPLOY_CONFIG_DIR:-$DEPLOY_ROOT/config}"
DEPLOY_JAR_NAME="${DEPLOY_JAR_NAME:-streaming-api.jar}"

# Repo root: en tu VPS el proyecto vive como repo plano en /home/deploy/streaming
# (pom.xml + mvnw en la raíz). Puedes override con REPO_DIR=... si cambia.
DEFAULT_REPO_DIR="/home/deploy/streaming"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="${REPO_DIR:-$DEFAULT_REPO_DIR}"
if [ ! -d "$REPO_DIR" ]; then
  REPO_DIR="$SCRIPT_DIR"
fi

# Layout del repo (standalone):
# - <repo>/pom.xml (y mvnw) en la raíz
PROJECT_DIR_REL="${PROJECT_DIR_REL:-}"

PROJECT_DIR=""

# Si el usuario exportó PROJECT_DIR_REL (o lo pasó inline), lo respetamos SOLO si existe.
if [ -n "$PROJECT_DIR_REL" ]; then
  CANDIDATE_PROJECT_DIR="$REPO_DIR/$PROJECT_DIR_REL"
  if [ -d "$CANDIDATE_PROJECT_DIR" ]; then
    PROJECT_DIR="$CANDIDATE_PROJECT_DIR"
  else
    echo "⚠️  Aviso: PROJECT_DIR_REL='$PROJECT_DIR_REL' apunta a '$CANDIDATE_PROJECT_DIR' pero esa carpeta NO existe." >&2
    echo "    Continuando con autodetección (repo plano con pom.xml en la raíz)..." >&2
    PROJECT_DIR_REL=""
  fi
fi

if [ -z "$PROJECT_DIR" ]; then
  if [ -f "$REPO_DIR/pom.xml" ] && [ -f "$REPO_DIR/mvnw" ]; then
    PROJECT_DIR_REL="."
    PROJECT_DIR="$REPO_DIR"
  elif [ -f "$SCRIPT_DIR/pom.xml" ] && [ -f "$SCRIPT_DIR/mvnw" ]; then
    # Caso: copiaste el script dentro del proyecto (root plano o módulo)
    PROJECT_DIR_REL="."
    PROJECT_DIR="$SCRIPT_DIR"
  else
    echo "❌ Error: no pude detectar el proyecto Maven (standalone)." >&2
    echo "   Probé: $REPO_DIR (pom.xml + mvnw)" >&2
    echo "   Sugerencia: ejecuta con REPO_DIR=/home/deploy/streaming o PROJECT_DIR_REL=." >&2
    echo "   Tip: si antes exportaste PROJECT_DIR_REL, haz: unset PROJECT_DIR_REL" >&2
    exit 1
  fi
fi

ENV_SOURCE_FILE="${ENV_SOURCE_FILE:-$PROJECT_DIR/.env}"

# sudo helper
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
fi

echo "============================================"
echo "Deploying Streaming (no envs)"
echo "- Repo:        $REPO_DIR"
echo "- Project:     $PROJECT_DIR_REL"
echo "- ProjectDir:  $PROJECT_DIR"
echo "- Service:     $SERVICE_NAME"
echo "- Deploy JAR:  $DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME"
echo "- Deploy .env: $DEPLOY_CONFIG_DIR/.env"
echo "============================================"
echo ""

if [ ! -d "$PROJECT_DIR" ]; then
  echo "❌ Error: no existe PROJECT_DIR: $PROJECT_DIR" >&2
  exit 1
fi

cd "$PROJECT_DIR"

# Clean build
echo "Cleaning old build..."
rm -rf target

# Build
echo "Building JAR..."
chmod +x ./mvnw
./mvnw clean package -DskipTests

# Detect jar output
JAR_PATH=""
if compgen -G "target/*.jar" > /dev/null; then
  # Toma el .jar más reciente y evita artefactos no ejecutables
  JAR_PATH=$(ls -1t target/*.jar | grep -vE '(^|/)original-|(-sources|-javadoc)\\.jar$' | head -n 1 || true)
fi

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
  echo "❌ Error: no pude detectar el JAR en target/." >&2
  echo "   Archivos en target/:" >&2
  ls -la target || true
  exit 1
fi

echo "✅ Built JAR: $JAR_PATH"

# Ensure deploy dirs
$SUDO mkdir -p "$DEPLOY_LIB_DIR" "$DEPLOY_CONFIG_DIR"

# Backup current jar (si existe)
if $SUDO test -f "$DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME"; then
  TS=$(date +%Y%m%d_%H%M%S)
  echo "Backing up current JAR -> ${DEPLOY_JAR_NAME}.backup.$TS"
  $SUDO cp "$DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME" "$DEPLOY_LIB_DIR/${DEPLOY_JAR_NAME}.backup.$TS"
fi

# Deploy jar
echo "Deploying JAR..."
$SUDO cp "$JAR_PATH" "$DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME"

# Deploy .env
if [ -f "$ENV_SOURCE_FILE" ]; then
  echo "Deploying .env..."
  $SUDO cp "$ENV_SOURCE_FILE" "$DEPLOY_CONFIG_DIR/.env"
else
  echo "⚠️  Aviso: no encontré .env en $ENV_SOURCE_FILE" >&2
  echo "    Si tu servicio usa EnvironmentFile=$DEPLOY_CONFIG_DIR/.env, créalo antes de reiniciar." >&2
fi

# Restart service
echo "Restarting service: $SERVICE_NAME"
if ! $SUDO systemctl restart "$SERVICE_NAME"; then
  echo "" >&2
  echo "❌ systemd restart falló para: $SERVICE_NAME" >&2
  echo "" >&2
  echo "Unit file (systemctl cat):" >&2
  $SUDO systemctl cat "$SERVICE_NAME" --no-pager || true

  echo "" >&2
  echo "Verificación rápida de rutas esperadas:" >&2
  $SUDO ls -lah "$DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME" "$DEPLOY_CONFIG_DIR/.env" 2>/dev/null || true

  echo "" >&2
  echo "Service status (top):" >&2
  $SUDO systemctl status "$SERVICE_NAME" --no-pager | head -n 80 || true

  echo "" >&2
  echo "Last logs (journalctl):" >&2
  $SUDO journalctl -u "$SERVICE_NAME" -n 120 --no-pager || true

  echo "" >&2
  echo "Tip rápido: verifica que el unit tenga ExecStart apuntando a: $DEPLOY_LIB_DIR/$DEPLOY_JAR_NAME" >&2
  echo "          y (si aplica) EnvironmentFile=$DEPLOY_CONFIG_DIR/.env" >&2
  exit 1
fi

echo ""
echo "Service status (top):"
$SUDO systemctl status "$SERVICE_NAME" --no-pager | head -n 30 || true

echo ""
echo "Last logs:"
$SUDO journalctl -u "$SERVICE_NAME" -n 30 --no-pager || true

echo ""
echo "✅ Deployment complete. Tail logs with:"
echo "   sudo journalctl -u $SERVICE_NAME -f"

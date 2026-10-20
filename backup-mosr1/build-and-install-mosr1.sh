#!/usr/bin/env bash
# Build the launcher and (re-)install it on MiTV-MOSR1 (hermano_eu).
# Run from the repo root:
#   TV_HOST=192.168.1.8 bash backup-mosr1/build-and-install-mosr1.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.1.8 bash $0}"

# Java (prefer openjdk@21 from Homebrew, fall back to whatever is on PATH)
export JAVA_HOME="${JAVA_HOME:-$( \
  [ -d /opt/homebrew/opt/openjdk@21 ] && echo /opt/homebrew/opt/openjdk@21 || \
  /usr/libexec/java_home -v 17 2>/dev/null || \
  java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')}"
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk; do [ -d "$d" ] && echo "$d" && break; done)}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Java: $JAVA_HOME"
echo "SDK:  $ANDROID_HOME"

# Stock launcher for this model
export TV_HOST
STOCK_LAUNCHER=com.google.android.tvlauncher \
  TV_HOST=$TV_HOST \
  bash launcher/build-and-install.sh

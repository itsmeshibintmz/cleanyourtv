#!/usr/bin/env bash
# Restore script for MiTV-MOSR1 (hermano_eu) — re-enables every disabled package
# and brings the stock Android TV launcher back as Home.
#
# Run from the repo root:
#   TV_HOST=192.168.1.8 bash backup-mosr1/restore-mosr1.sh
#
# Or to restore just one package:
#   TV_HOST=192.168.1.8 tools/venv/bin/python -I tools/tv.py 'pm enable com.PACKAGE.NAME'
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.1.8 bash $0}"
export TV_HOST

PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "Run python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt first"; exit 1; }

echo "=== Re-enabling all packages ==="
TV_HOST=$TV_HOST tools/restore.sh \
  examples/xiaomi-mitv-mosr1/debloat.txt \
  com.google.android.tvlauncher

echo ""
echo "=== Restoring AdGuard DNS ==="
echo "To undo AdGuard DNS (revert to standard DNS):"
echo "  TV_HOST=\$TV_HOST tools/venv/bin/python -I tools/tv.py 'settings put global private_dns_mode opportunistic'"
echo "Run that command manually if you want standard DNS back."

echo ""
echo "=== Done ==="
echo "The stock Android TV launcher (com.google.android.tvlauncher) is back as Home."
echo "Press Home on the remote to confirm."
echo ""
echo "If the new launcher (com.gathin.tvhome) is still installed and you want it back:"
echo "  TV_HOST=\$TV_HOST TV_HOST=\$TV_HOST bash launcher/build-and-install.sh"

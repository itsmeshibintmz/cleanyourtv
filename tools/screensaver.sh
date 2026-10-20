#!/usr/bin/env bash
# Push a wallpaper for the ClockDream screensaver (appears behind the drifting clock).
# Any JPEG works. --reset removes it (screensaver returns to black + ambient glow).
#
# Usage:
#   TV_HOST=192.168.1.7 tools/screensaver.sh path/to/image.jpg
#   TV_HOST=192.168.1.7 tools/screensaver.sh --reset
set -euo pipefail
case "${1:-}" in --reset|"") ;; /*) ;; *) ARG="$PWD/${1:-}"; shift; set -- "$ARG" "$@" ;; esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.1.7 $0 image.jpg}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
tv() { "$PY" -I tv.py "$@"; }
if [ "${1:-}" = "--reset" ]; then
  tv "rm -f $DIR/screensaver.jpg; echo removed"
  echo "Screensaver wallpaper removed — back to ambient glow only."
  exit 0
fi
IMG="${1:?usage: screensaver.sh <image.jpg> | --reset}"
[ -f "$IMG" ] || { echo "File not found: $IMG" >&2; exit 1; }
tv "mkdir -p $DIR" >/dev/null
"$PY" -I b64push.py "$IMG" "$DIR/screensaver.jpg"
echo "Screensaver wallpaper set. It takes effect next time the screensaver starts."

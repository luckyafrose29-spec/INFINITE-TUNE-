#!/bin/bash
# =============================================================================
# INFINITE-TUNE Minecraft Server - container entrypoint
#
# Hugging Face Spaces only routes external HTTP traffic to ONE exposed port
# (7860 by default). The Minecraft protocol is not HTTP, so real player
# connections cannot go through that port. Instead:
#
#   1. A tiny status page is served on 7860 so the Space's health check /
#      preview iframe has something to show.
#   2. The playit.gg agent is started in the background. It makes an
#      OUTBOUND connection to playit.gg and opens a public TCP address that
#      tunnels straight back to 127.0.0.1:25565 inside this container - no
#      inbound port needs to be exposed by Hugging Face for this to work.
#   3. The original itzg/minecraft-server entrypoint (/start) takes over the
#      foreground process and boots the actual Purpur server.
# =============================================================================
set -e

STATUS_DIR="/tmp/status"
mkdir -p "${STATUS_DIR}"
cat > "${STATUS_DIR}/index.html" <<'HTML'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>INFINITE-TUNE Minecraft Server</title>
  <style>
    body { font-family: system-ui, sans-serif; background:#111; color:#eee; text-align:center; padding-top:10vh; }
    h1 { color:#6ee36e; }
    code { background:#222; padding:2px 6px; border-radius:4px; }
  </style>
</head>
<body>
  <h1>&#9989; INFINITE-TUNE Minecraft server is running</h1>
  <p>This Space only hosts the server process - it does not speak HTTP for gameplay.</p>
  <p>Connect using the address printed by <code>playit.gg</code> in this Space's logs,
     or from your playit.gg dashuser dashboard.</p>
</body>
</html>
HTML

echo "[status] Starting HTTP status page on :7860 for Hugging Face health checks..."
( cd "${STATUS_DIR}" && python3 -m http.server 7860 --bind 0.0.0.0 ) &

if [ -n "${PLAYIT_SECRET_KEY}" ]; then
  echo "[playit] PLAYIT_SECRET_KEY found - starting playit.gg tunnel agent..."
  ( playit --secret "${PLAYIT_SECRET_KEY}" 2>&1 | sed 's/^/[playit] /' ) &
else
  echo "[playit] WARNING: PLAYIT_SECRET_KEY is not set."
  echo "[playit] Add it as a Secret in this Space's settings to get a public"
  echo "[playit] address for your friends. See README.md for the full guide."
fi

echo "[minecraft] Handing off to the Purpur server entrypoint..."
exec /start

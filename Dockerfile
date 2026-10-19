# =============================================================================
#  INFINITE-TUNE Minecraft Server
#  Purpur (latest, PaperMC/Spigot-compatible) on itzg/minecraft-server
#  Built to run as a Hugging Face Space (Docker SDK) and exposed to the
#  internet through a playit.gg tunnel so 4-5 friends can connect.
# =============================================================================
FROM itzg/minecraft-server:latest

LABEL maintainer="INFINITE-TUNE" \
      description="Purpur Minecraft server optimized for Hugging Face Spaces, tunneled with playit.gg"

# -----------------------------------------------------------------------------
# Core server configuration (handled by the itzg/minecraft-server entrypoint)
# -----------------------------------------------------------------------------
ENV EULA=TRUE \
    TYPE=PURPUR \
    VERSION=LATEST \
    MEMORY=14G \
    MAX_PLAYERS=5 \
    ONLINE_MODE=false \
    VIEW_DISTANCE=8 \
    SIMULATION_DISTANCE=6 \
    ALLOW_FLIGHT=true \
    ENABLE_COMMAND_BLOCK=true \
    USE_AIKAR_FLAGS=false \
    ENABLE_RCON=false \
    ICON=""

# -----------------------------------------------------------------------------
# JVM / G1GC flags tuned for a large (14G) heap on Hugging Face's 16GB hardware.
# Based on Aikar's well known G1GC tuning guide, adjusted for a bigger heap so
# the server stays smooth with 4-5 players and modest view/simulation distance.
# -----------------------------------------------------------------------------
ENV JVM_XX_OPTS="-XX:+UseG1GC \
-XX:+ParallelRefProcEnabled \
-XX:MaxGCPauseMillis=100 \
-XX:+UnlockExperimentalVMOptions \
-XX:+DisableExplicitGC \
-XX:+AlwaysPreTouch \
-XX:G1NewSizePercent=30 \
-XX:G1MaxNewSizePercent=40 \
-XX:G1HeapRegionSize=8M \
-XX:G1ReservePercent=20 \
-XX:G1HeapWastePercent=5 \
-XX:G1MixedGCCountTarget=4 \
-XX:InitiatingHeapOccupancyPercent=15 \
-XX:G1MixedGCLiveThresholdPercent=90 \
-XX:G1RSetUpdatingPauseTimePercent=5 \
-XX:SurvivorRatio=32 \
-XX:+PerfDisableSharedMem \
-XX:MaxTenuringThreshold=1"

# -----------------------------------------------------------------------------
# Install the playit.gg agent so the container can open an outbound tunnel and
# expose Minecraft's TCP port (25565) to the internet without Hugging Face
# needing to publish anything other than the HTTP status port (7860).
# -----------------------------------------------------------------------------
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates python3 \
    && curl -fsSL -o /usr/local/bin/playit \
       "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-amd64" \
    && chmod +x /usr/local/bin/playit \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# Default server.properties shipped with the image (itzg still lets env vars
# above override these values on first boot / when OVERRIDE_SERVER_PROPERTIES
# style vars are set, but this file guarantees sane defaults even without them).
COPY server.properties /server.properties
COPY start.sh /start.sh
RUN chmod +x /start.sh

# Hugging Face Spaces only routes HTTP traffic to a single exposed port.
# We serve a tiny status page there; real game traffic goes out via playit.gg.
EXPOSE 7860

ENTRYPOINT ["/start.sh"]

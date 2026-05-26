#!/usr/bin/env bash
#
# demo-console.sh — launch the MBSB Demo Console (a small local web UI to
# start/stop the services, seed data, and flip the Statement Analysis
# micro-app version v1<->v2 — no terminal needed once it's open).
#
#   ./scripts/demo-console.sh            # serves on http://localhost:9700
#   CONSOLE_PORT=9800 ./scripts/demo-console.sh
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${CONSOLE_PORT:-9700}"

command -v node >/dev/null 2>&1 || { echo "node not found (Node 20+ required)"; exit 1; }

echo "Demo Console -> http://localhost:$PORT  (Ctrl-C to stop the console; services keep running)"
# open the browser shortly after the server starts
( sleep 1; command -v open >/dev/null 2>&1 && open "http://localhost:$PORT" ) >/dev/null 2>&1 &

CONSOLE_PORT="$PORT" exec node "$DIR/demo-console/server.js"

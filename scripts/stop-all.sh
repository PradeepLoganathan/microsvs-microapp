#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$ROOT_DIR/run"

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo -e "${CYAN}Stopping all services...${NC}"
echo ""

if [ ! -d "$RUN_DIR" ]; then
    echo -e "${RED}No run directory found. Nothing to stop.${NC}"
    exit 0
fi

stopped=0
for pid_file in "$RUN_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    svc=$(basename "$pid_file" .pid)
    pid=$(cat "$pid_file")

    echo -ne "  Stopping ${CYAN}$svc${NC} (PID: $pid)... "
    if kill -0 "$pid" 2>/dev/null; then
        # Kill the process tree (Maven + child Java process)
        pkill -P "$pid" 2>/dev/null || true
        kill "$pid" 2>/dev/null || true
        # Wait briefly for graceful shutdown
        sleep 1
        # Force kill if still running
        if kill -0 "$pid" 2>/dev/null; then
            kill -9 "$pid" 2>/dev/null || true
        fi
        echo -e "${GREEN}stopped${NC}"
        stopped=$((stopped + 1))
    else
        echo -e "${RED}already stopped${NC}"
    fi
    rm -f "$pid_file"
done

if [ $stopped -eq 0 ]; then
    echo -e "  ${RED}No running services found.${NC}"
fi

echo ""
echo -e "${GREEN}All services stopped.${NC}"
echo ""

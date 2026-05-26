#!/usr/bin/env bash
#
# stop-local.sh — stop all locally-running Akka services started by run-local.sh
# (or the Warp launch config). Kills tracked PIDs, then frees the ports.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$ROOT_DIR/run"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
PORTS=(8082 8085 8079 8083 8084 8086 8087 8088 4200)  # 8086 advisor, 8087 onboarding, 8088 customer, 4200 UI shell

echo -e "${CYAN}Stopping services...${NC}"

# 1. tracked PIDs from run-local.sh
if [ -d "$RUN_DIR" ]; then
  for pid_file in "$RUN_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    svc="$(basename "$pid_file" .pid)"; pid="$(cat "$pid_file" 2>/dev/null)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null; echo -e "  ${CYAN}$svc${NC} (pid $pid) signalled"
    fi
    rm -f "$pid_file"
  done
fi

sleep 2

# 2. force-free the ports (covers Warp panes / untracked starts); LISTEN only
for p in "${PORTS[@]}"; do
  pids="$(lsof -ti ":$p" -sTCP:LISTEN 2>/dev/null | tr '\n' ' ')"
  [ -n "$pids" ] && { kill -9 $pids 2>/dev/null; echo -e "  freed :$p (killed $pids)"; }
done
# 3. mop up any lingering maven exec wrappers and the UI dev server
pkill -9 -f "akka.javasdk" 2>/dev/null || true
pkill -9 -f "ng serve" 2>/dev/null || true

sleep 1
echo -e "${CYAN}Verify:${NC}"
allfree=1
for p in "${PORTS[@]}"; do
  if lsof -ti ":$p" -sTCP:LISTEN >/dev/null 2>&1; then echo -e "  ${YELLOW}:$p STILL UP${NC}"; allfree=0
  else echo -e "  ${GREEN}:$p free${NC}"; fi
done
[ "$allfree" -eq 1 ] && echo -e "${GREEN}All stopped.${NC}"

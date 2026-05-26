#!/usr/bin/env bash
#
# run-local.sh — start all 5 Akka services locally in the background, then
# seed + verify (via smoke-local.sh). One command, one terminal tab.
#
#   ./scripts/run-local.sh            # compile + run (fast; reuses prior build)
#   ./scripts/run-local.sh --clean    # clean compile + run (fresh build)
#
# Stop everything with: ./scripts/stop-local.sh
# Logs per service:     logs/<service>.log
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"; RUN_DIR="$ROOT_DIR/run"
mkdir -p "$LOGS_DIR" "$RUN_DIR"

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
die() { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }

MVN_GOALS="compile exec:java"
[ "${1:-}" = "--clean" ] && MVN_GOALS="clean compile exec:java"

# name:port:module   (started in this order; analysis/recommendation depend on the earlier ones)
SERVICES=(
  "statement-service:8082:backend/statement-service"
  "product-service:8085:backend/product-service"
  "platform-service:8079:platform/platform-service"
  "analysis-service:8083:backend/analysis-service"
  "recommendation-service:8084:backend/recommendation-service"
  "advisor-service:8086:backend/advisor-service"
  "onboarding-service:8087:backend/onboarding-service"
)

command -v java >/dev/null 2>&1 || die "java not found (need Java 21)"
command -v mvn  >/dev/null 2>&1 || die "maven not found"

echo -e "${CYAN}Starting services ($MVN_GOALS)...${NC}"
for entry in "${SERVICES[@]}"; do
  IFS=: read -r name port module <<< "$entry"
  if lsof -ti ":$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo -e "  ${YELLOW}$name: port $port already in use — skipping${NC}"
    continue
  fi
  mvn -f "$ROOT_DIR/$module/pom.xml" $MVN_GOALS > "$LOGS_DIR/$name.log" 2>&1 &
  echo $! > "$RUN_DIR/$name.pid"
  echo -e "  ${CYAN}$name${NC} -> :$port  (pid $(cat "$RUN_DIR/$name.pid"), log: logs/$name.log)"
done

echo -e "${CYAN}Waiting for ports to come up (first run compiles — can take a minute)...${NC}"
for entry in "${SERVICES[@]}"; do
  IFS=: read -r name port module <<< "$entry"
  ok=0
  for i in $(seq 1 120); do
    if curl -s -o /dev/null --max-time 2 "http://localhost:$port"; then ok=1; break; fi
    # surface a dead build early
    if ! kill -0 "$(cat "$RUN_DIR/$name.pid" 2>/dev/null)" 2>/dev/null; then
      echo -e "  ${RED}$name died — see logs/$name.log${NC}"; break
    fi
    sleep 1
  done
  [ "$ok" -eq 1 ] && echo -e "  ${GREEN}$name ready${NC} (:$port)" || echo -e "  ${YELLOW}$name not responding yet (:$port)${NC}"
done

echo ""
"$SCRIPT_DIR/smoke-local.sh"

echo ""
echo -e "${CYAN}Tail a log:${NC}  tail -f logs/<service>.log"
echo -e "${CYAN}Stop all:${NC}   ./scripts/stop-local.sh"

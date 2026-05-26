#!/usr/bin/env bash
#
# smoke-local.sh — wait for the 5 services, seed data, and verify every endpoint.
# Safe to run repeatedly. Used standalone or as the Warp "seed + smoke" pane.
set -uo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ACCT="acc-1001"; STMT="stmt-2025-12"
PORTS=(8082 8085 8079 8083 8084)

echo -e "${CYAN}Waiting for services (ports: ${PORTS[*]})...${NC}"
ready=0
for i in $(seq 1 180); do
  allup=1
  for p in "${PORTS[@]}"; do curl -s -o /dev/null --max-time 2 "http://localhost:$p" || allup=0; done
  if [ "$allup" -eq 1 ]; then ready=1; break; fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo -e "${YELLOW}Not all ports responded — checking what's up:${NC}"
  for p in "${PORTS[@]}"; do
    printf "  :%s " "$p"; curl -s -o /dev/null --max-time 2 "http://localhost:$p" && echo up || echo DOWN
  done
fi

echo -e "${CYAN}Seeding (idempotent)...${NC}"
for s in "8082:/accounts/seed" "8085:/products/seed" "8079:/microfrontends/seed"; do
  port="${s%%:*}"; path="${s#*:}"
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST "http://localhost:$port$path" || echo 000)
  printf "  POST :%s%-22s -> %s\n" "$port" "$path" "$code"
done
sleep 1  # let views catch up

check() {  # name  url  expected-substring
  local name="$1" url="$2" expect="$3" body code
  body=$(curl -s -w $'\n%{http_code}' --max-time 10 "$url" 2>/dev/null)
  code=$(printf '%s' "$body" | tail -1); body=$(printf '%s' "$body" | sed '$d')
  if [ "$code" = "200" ] && printf '%s' "$body" | grep -q "$expect"; then
    printf "  ${GREEN}PASS${NC} %-15s %s\n" "$name" "$(printf '%s' "$body" | head -c 70)"
  else
    printf "  ${RED}FAIL${NC} %-15s HTTP %s  %s\n" "$name" "$code" "$(printf '%s' "$body" | head -c 70)"
  fi
}

echo -e "${CYAN}Verifying endpoints (account=$ACCT, statement=$STMT)...${NC}"
check "statement"      "http://localhost:8082/accounts/$ACCT/statements"                              "statementId"
check "product"        "http://localhost:8085/products"                                               "productId"
check "platform"       "http://localhost:8079/microfrontends/manifest/demo"                           "microfrontends"
check "analysis"       "http://localhost:8083/accounts/$ACCT/analysis/summary?statementId=$STMT"      "totalSpend"
check "recommendation" "http://localhost:8084/accounts/$ACCT/recommendations?statementId=$STMT"       "productId"
echo -e "${CYAN}Done.${NC}"

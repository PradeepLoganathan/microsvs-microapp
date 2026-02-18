#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Handle --clean flag
if [[ "${1:-}" == "--clean" ]]; then
    echo -e "${YELLOW}Cleaning logs and run directories...${NC}"
    rm -rf "$LOGS_DIR" "$RUN_DIR"
    echo -e "${GREEN}Clean complete.${NC}"
    exit 0
fi

# Create directories
mkdir -p "$LOGS_DIR" "$RUN_DIR"

# Check prerequisites
check_prereqs() {
    echo -e "${CYAN}Checking prerequisites...${NC}"
    if ! command -v java &>/dev/null; then
        echo -e "${RED}ERROR: Java not found. Please install Java 21.${NC}"
        exit 1
    fi
    JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    echo "  Java version: $JAVA_VER"

    if ! command -v mvn &>/dev/null; then
        echo -e "${RED}ERROR: Maven not found. Please install Maven 3.9+.${NC}"
        exit 1
    fi
    echo "  Maven: $(mvn --version 2>&1 | head -1)"
    echo ""
}

# Check if port is available
check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t &>/dev/null 2>&1; then
        return 1
    fi
    return 0
}

# Start an Akka SDK service
start_akka_service() {
    local name=$1
    local port=$2
    local dir=$3

    echo -ne "  Starting ${CYAN}$name${NC} on port $port... "

    if ! check_port $port; then
        echo -e "${RED}FAILED (port $port already in use)${NC}"
        return 1
    fi

    cd "$ROOT_DIR/$dir"
    mvn -q compile exec:java > "$LOGS_DIR/$name.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$RUN_DIR/$name.pid"

    # Wait for service to start (up to 60 seconds)
    local retries=60
    while [ $retries -gt 0 ]; do
        if ! kill -0 $pid 2>/dev/null; then
            echo -e "${RED}FAILED (process died — check $LOGS_DIR/$name.log)${NC}"
            return 1
        fi
        if curl -s "http://localhost:$port" &>/dev/null 2>&1; then
            echo -e "${GREEN}OK${NC} (PID: $pid)"
            return 0
        fi
        sleep 1
        retries=$((retries - 1))
    done

    echo -e "${YELLOW}TIMEOUT${NC} (PID: $pid — may still be starting, check $LOGS_DIR/$name.log)"
    return 0
}

# Start a platform service (simple Java)
start_platform_service() {
    local name=$1
    local port=$2
    local dir=$3

    echo -ne "  Starting ${CYAN}$name${NC} on port $port... "

    if ! check_port $port; then
        echo -e "${RED}FAILED (port $port already in use)${NC}"
        return 1
    fi

    cd "$ROOT_DIR/$dir"
    mvn -q compile exec:java > "$LOGS_DIR/$name.log" 2>&1 &
    local pid=$!
    echo "$pid" > "$RUN_DIR/$name.pid"

    # Wait for service to start (up to 15 seconds)
    local retries=15
    while [ $retries -gt 0 ]; do
        if ! kill -0 $pid 2>/dev/null; then
            echo -e "${RED}FAILED (process died — check $LOGS_DIR/$name.log)${NC}"
            return 1
        fi
        if curl -s "http://localhost:$port/health" &>/dev/null 2>&1; then
            echo -e "${GREEN}OK${NC} (PID: $pid)"
            return 0
        fi
        sleep 1
        retries=$((retries - 1))
    done

    echo -e "${YELLOW}TIMEOUT${NC} (PID: $pid — may still be starting)"
    return 0
}

# Main
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   Microservices + Micro-App Demo — Starting All     ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

check_prereqs

echo -e "${CYAN}Starting backend services (Akka SDK)...${NC}"
start_akka_service "statement-service" 8082 "backend/statement-service"
start_akka_service "product-service" 8085 "backend/product-service"
start_akka_service "analysis-service" 8083 "backend/analysis-service"
start_akka_service "recommendation-service" 8084 "backend/recommendation-service"

echo ""
echo -e "${CYAN}Seeding data into EventSourced entities...${NC}"
sleep 2  # Allow views to initialize

echo -ne "  Seeding ${CYAN}statement-service${NC}... "
if curl -s -X POST http://localhost:8082/accounts/seed &>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}SKIPPED${NC} (service may not be ready)"
fi

echo -ne "  Seeding ${CYAN}product-service${NC}... "
if curl -s -X POST http://localhost:8085/products/seed &>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}SKIPPED${NC} (service may not be ready)"
fi

sleep 1  # Allow views to catch up

echo ""
echo -e "${CYAN}Starting platform services...${NC}"
start_platform_service "microfrontend-registry" 8079 "platform/microfrontend-registry"
start_platform_service "microfrontend-cdn" 8090 "platform/microfrontend-cdn"

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                  Service Summary                     ║${NC}"
echo -e "${CYAN}╠══════════════════════════════════════════════════════╣${NC}"
printf "${CYAN}║${NC} %-25s %-8s %-18s ${CYAN}║${NC}\n" "SERVICE" "PORT" "STATUS"
echo -e "${CYAN}╠══════════════════════════════════════════════════════╣${NC}"

for svc in statement-service product-service analysis-service recommendation-service microfrontend-registry microfrontend-cdn; do
    pid_file="$RUN_DIR/$svc.pid"
    case $svc in
        statement-service) port=8082 ;;
        product-service) port=8085 ;;
        analysis-service) port=8083 ;;
        recommendation-service) port=8084 ;;
        microfrontend-registry) port=8079 ;;
        microfrontend-cdn) port=8090 ;;
    esac

    if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
        printf "${CYAN}║${NC} %-25s %-8s ${GREEN}%-18s${NC} ${CYAN}║${NC}\n" "$svc" "$port" "RUNNING"
    else
        printf "${CYAN}║${NC} %-25s %-8s ${RED}%-18s${NC} ${CYAN}║${NC}\n" "$svc" "$port" "NOT RUNNING"
    fi
done

echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Logs:${NC}     $LOGS_DIR/"
echo -e "${CYAN}PIDs:${NC}     $RUN_DIR/"
echo ""
echo -e "${CYAN}Test endpoints:${NC}"
echo "  curl http://localhost:8082/accounts/acc-1001/statements"
echo "  curl http://localhost:8085/products"
echo "  curl http://localhost:8083/accounts/acc-1001/analysis/summary?statementId=stmt-2025-12"
echo "  curl http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12"
echo "  curl http://localhost:8079/microfrontends/manifest?channel=demo"
echo ""

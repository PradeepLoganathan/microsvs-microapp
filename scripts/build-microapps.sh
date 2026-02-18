#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MICROAPPS_DIR="$ROOT_DIR/mobile/microapps"

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo ""
echo -e "${CYAN}Building Micro-Apps...${NC}"
echo ""

# Check Node.js
if ! command -v node &>/dev/null; then
    echo -e "${RED}ERROR: Node.js not found. Please install Node.js 20+.${NC}"
    exit 1
fi
echo "  Node: $(node --version)"
echo "  npm:  $(npm --version)"
echo ""

build_microapp() {
    local name=$1
    local config=${2:-production}

    echo -ne "  Building ${CYAN}$name${NC} (config: $config)... "
    cd "$MICROAPPS_DIR/$name"

    if [ ! -d "node_modules" ]; then
        npm install --silent 2>/dev/null
    fi

    npx ng build --configuration "$config" 2>/dev/null

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAILED${NC}"
        return 1
    fi
}

# Build each micro-app
build_microapp "statement-details"
build_microapp "statement-analysis" "production"
build_microapp "statement-analysis" "v2"
build_microapp "recommendations"

echo ""
echo -e "${GREEN}All micro-apps built successfully.${NC}"
echo ""
echo -e "${CYAN}Next step:${NC} Run ./scripts/publish-microapps.sh to publish to CDN"
echo ""

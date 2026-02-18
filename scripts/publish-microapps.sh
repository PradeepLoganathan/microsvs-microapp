#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MICROAPPS_DIR="$ROOT_DIR/mobile/microapps"
CDN_DIR="$ROOT_DIR/platform/microfrontend-cdn/www"

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo -e "${CYAN}Publishing Micro-Apps to CDN...${NC}"
echo ""

publish_microapp() {
    local name=$1
    local version=$2
    local source_dir=$3

    local target_dir="$CDN_DIR/$name/$version"
    mkdir -p "$target_dir"

    echo -ne "  Publishing ${CYAN}$name${NC} v$version... "

    if [ ! -d "$source_dir" ]; then
        echo -e "${RED}FAILED (build output not found: $source_dir)${NC}"
        return 1
    fi

    # Copy all JS, CSS, and HTML files from the build output
    cp "$source_dir"/*.js "$target_dir/" 2>/dev/null || true
    cp "$source_dir"/*.css "$target_dir/" 2>/dev/null || true

    # Rename the main bundle to main.js if it has a different name
    if [ ! -f "$target_dir/main.js" ]; then
        # Find the largest JS file and rename it
        local largest=$(ls -S "$target_dir"/*.js 2>/dev/null | head -1)
        if [ -n "$largest" ]; then
            cp "$largest" "$target_dir/main.js"
        fi
    fi

    echo -e "${GREEN}OK${NC} → $target_dir/"
}

# Publish statement-details v1
publish_microapp "statement-details" "1.0.0" \
    "$MICROAPPS_DIR/statement-details/dist/statement-details/browser"

# Publish statement-analysis v1
publish_microapp "statement-analysis" "1.0.0" \
    "$MICROAPPS_DIR/statement-analysis/dist/statement-analysis/browser"

# Publish statement-analysis v2
publish_microapp "statement-analysis" "2.0.0" \
    "$MICROAPPS_DIR/statement-analysis/dist/statement-analysis-v2/browser"

# Publish recommendations v1
publish_microapp "recommendations" "1.0.0" \
    "$MICROAPPS_DIR/recommendations/dist/recommendations/browser"

echo ""
echo -e "${GREEN}All micro-apps published.${NC}"
echo ""
echo -e "${CYAN}CDN contents:${NC}"
find "$CDN_DIR" -type f -name "*.js" | sort | while read f; do
    echo "  $(echo "$f" | sed "s|$CDN_DIR/||")"
done

echo ""
echo -e "${YELLOW}To demo the v1→v2 update:${NC}"
echo "  1. Manifest currently points to StatementAnalysis v1.0.0"
echo "  2. Edit: platform/microfrontend-registry/manifest.json"
echo "  3. Change StatementAnalysis version to 2.0.0 and remoteEntry URL"
echo "  4. Refresh the app in browser"
echo ""

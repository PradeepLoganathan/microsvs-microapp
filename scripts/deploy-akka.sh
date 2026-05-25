#!/usr/bin/env bash
#
# deploy-akka.sh — build & deploy all backend services to the Akka platform.
#
# Repeatable end-to-end deploy:
#   1. preflight  (akka CLI, docker daemon, authentication)
#   2. project    (ensure the target project exists and is current)
#   3. build+push (per service: mvn image build -> akka service deploy --push)
#   4. wait       (until every service reports Ready)
#   5. expose     (public route per service, with CORS)
#   6. seed       (POST the seed endpoints against the exposed hosts)
#
# Override defaults via env, e.g.:
#   AKKA_PROJECT=my-proj AKKA_ORG=my-org AKKA_REGION=gcp-us-east1 ./scripts/deploy-akka.sh
#
# Flags:
#   --skip-build    deploy the most recently built local images (no mvn build)
#   --skip-seed     don't POST the seed endpoints
#   --no-expose     don't create public routes (and implies --skip-seed)
#
# NOTE: service names below MUST stay as-is — cross-service calls use
# httpClientFor("analysis-service") etc., which resolve by deployed service name.
# The Ionic banking-shell is a static frontend and is NOT deployed here.

set -euo pipefail

PROJECT="${AKKA_PROJECT:-mbsb-nextgen-demo}"
ORG="${AKKA_ORG:-lightbend-eng}"
REGION="${AKKA_REGION:-europe-west1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOGS_DIR="$ROOT_DIR/logs"
mkdir -p "$LOGS_DIR"

# akka-service-name : maven module dir (artifactId == service name in this repo)
SERVICES=(
  "statement-service:backend/statement-service"
  "product-service:backend/product-service"
  "analysis-service:backend/analysis-service"
  "recommendation-service:backend/recommendation-service"
  "platform-service:platform/platform-service"
)

# service : seed-path  (services with a seed endpoint)
SEEDS=(
  "statement-service:/accounts/seed"
  "product-service:/products/seed"
  "platform-service:/microfrontends/seed"
)

SKIP_BUILD=0; SKIP_SEED=0; NO_EXPOSE=0
for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=1 ;;
    --skip-seed)  SKIP_SEED=1 ;;
    --no-expose)  NO_EXPOSE=1; SKIP_SEED=1 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
say()  { echo -e "${CYAN}$*${NC}"; }
ok()   { echo -e "  ${GREEN}$*${NC}"; }
warn() { echo -e "  ${YELLOW}$*${NC}"; }
die()  { echo -e "${RED}ERROR: $*${NC}" >&2; exit 1; }

svc_name() { echo "${1%%:*}"; }
svc_path() { echo "${1##*:}"; }

# ---------------------------------------------------------------- 1. preflight
say "1/6  Preflight"
command -v akka  >/dev/null 2>&1 || die "akka CLI not found — https://doc.akka.io/reference/cli/installation.html"
command -v mvn   >/dev/null 2>&1 || die "maven not found"
command -v docker >/dev/null 2>&1 || die "docker not found"
docker info >/dev/null 2>&1 || die "docker daemon not running — start Docker Desktop (open -a Docker)"
akka projects list >/dev/null 2>&1 || die "not authenticated — run: akka auth login"
# One-time interactive setup: the ACR docker credential helper must be installed
# so 'akka service deploy --push' can authenticate to the Akka Container Registry.
command -v docker-credential-akka >/dev/null 2>&1 || die "Akka Container Registry credential helper not installed.
  Run this ONCE in an interactive terminal (accept the default install path, e.g. /opt/homebrew/bin):
      akka auth container-registry configure
  then re-run this script."
ok "akka CLI, maven, docker daemon, auth, ACR credential helper OK"

# ------------------------------------------------------------------ 2. project
say "2/6  Project: $PROJECT (org=$ORG, region=$REGION)"
if akka projects list 2>/dev/null | awk '{print $1}' | grep -qx "$PROJECT" \
   || akka projects list 2>/dev/null | grep -q "\b$PROJECT\b"; then
  ok "project '$PROJECT' exists"
else
  warn "project '$PROJECT' not found — creating it in '$ORG' (may incur cost if the org bills)"
  akka projects new "$PROJECT" "MBSB Project NextGen demo — banking micro-app services" \
    --region="$REGION" --organization="$ORG"
fi
akka config set project "$PROJECT" >/dev/null
ok "current project set to '$PROJECT'"

# ------------------------------------------------------------ 3. build + deploy
say "3/6  Build images and deploy (--push)"
for entry in "${SERVICES[@]}"; do
  name="$(svc_name "$entry")"; dir="$(svc_path "$entry")"
  if [ "$SKIP_BUILD" -eq 0 ]; then
    echo -ne "  building ${CYAN}$name${NC}... "
    if mvn -q -f "$ROOT_DIR/$dir/pom.xml" clean install -DskipTests \
         > "$LOGS_DIR/build-$name.log" 2>&1; then
      echo -e "${GREEN}done${NC}"
    else
      die "build failed for $name — see $LOGS_DIR/build-$name.log"
    fi
  fi
  # newest locally-built image for this artifact (repo name == service name);
  # exclude the mutable ':latest' tag and deploy the immutable timestamped one
  image="$(docker images "$name" --format '{{.CreatedAt}}\t{{.Repository}}:{{.Tag}}' \
            | grep -v ':latest' | sort -r | head -1 | cut -f2)"
  [ -n "$image" ] || die "no local docker image found for '$name' (did the build run?)"
  echo -ne "  deploying ${CYAN}$name${NC} <- $image ... "
  akka service deploy "$name" "$image" --push >/dev/null
  echo -e "${GREEN}submitted${NC}"
done

# ------------------------------------------------------------------- 4. wait
say "4/6  Waiting for services to become Ready"
deadline=$(( $(date +%s) + 300 ))
while :; do
  listing="$(akka services list 2>/dev/null || true)"
  pending=0
  for entry in "${SERVICES[@]}"; do
    name="$(svc_name "$entry")"
    echo "$listing" | grep -E "(^|[[:space:]])$name([[:space:]]|$)" | grep -qw "Ready" || pending=$((pending+1))
  done
  [ "$pending" -eq 0 ] && { ok "all ${#SERVICES[@]} services Ready"; break; }
  [ "$(date +%s)" -ge "$deadline" ] && { warn "timeout — current status:"; echo "$listing"; break; }
  sleep 5
done

[ "$NO_EXPOSE" -eq 1 ] && { say "Done (no expose)."; akka services list; exit 0; }

# ------------------------------------------------------------------ 5. expose
say "5/6  Exposing services (with CORS)"
declare -A HOSTS
for entry in "${SERVICES[@]}"; do
  name="$(svc_name "$entry")"
  out="$(akka service expose "$name" --enable-cors 2>&1 || true)"
  host="$(echo "$out" | grep -oE '[a-z0-9-]+\.[a-z0-9.-]+\.(akka|kalix)\.(services|io)' | head -1)"
  if [ -z "$host" ]; then
    # already exposed — fetch from service details
    host="$(akka service get "$name" 2>/dev/null | grep -oE '[a-z0-9-]+\.[a-z0-9.-]+\.(akka|kalix)\.(services|io)' | head -1)"
  fi
  HOSTS["$name"]="$host"
  [ -n "$host" ] && ok "$name -> https://$host" || warn "$name -> (no public route found)"
done

# -------------------------------------------------------------------- 6. seed
if [ "$SKIP_SEED" -eq 0 ]; then
  say "6/6  Seeding data"
  for entry in "${SEEDS[@]}"; do
    name="$(svc_name "$entry")"; path="$(svc_path "$entry")"
    host="${HOSTS[$name]:-}"
    if [ -z "$host" ]; then warn "skip $name seed (no host)"; continue; fi
    code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "https://$host$path" --max-time 30 || echo 000)"
    [ "$code" = "200" ] && ok "seeded $name ($path) -> $code" || warn "seed $name ($path) -> $code"
  done
fi

# ---------------------------------------------------------------------- summary
say "Deploy complete — $PROJECT"
akka services list
echo ""
say "Frontend: point mobile/banking-shell/src/environments/environment*.ts"
echo "  platformBaseUrl -> https://${HOSTS[platform-service]:-<platform-service-host>}"
echo "  then rebuild/host the shell separately (it is not an Akka service)."

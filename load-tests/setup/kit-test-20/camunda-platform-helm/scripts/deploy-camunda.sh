#!/usr/bin/env bash
set -euo pipefail

# --- Logging helpers ---
timestamp() { date +"%Y-%m-%dT%H:%M:%S%z"; }

is_tty=0
if [[ -t 1 ]]; then
  is_tty=1
fi

if [[ "$is_tty" -eq 1 ]] && command -v tput > /dev/null 2>&1; then
  COLOR_RED="$(tput setaf 1)"
  COLOR_YELLOW="$(tput setaf 3)"
  COLOR_GREEN="$(tput setaf 2)"
  COLOR_BLUE="$(tput setaf 4)"
  COLOR_RESET="$(tput sgr0)"
else
  COLOR_RED=""
  COLOR_YELLOW=""
  COLOR_GREEN=""
  COLOR_BLUE=""
  COLOR_RESET=""
fi

log() {
  local level="$1"
  shift
  local color=""
  case "$level" in
    INFO) color="$COLOR_BLUE" ;;
    WARN) color="$COLOR_YELLOW" ;;
    ERROR) color="$COLOR_RED" ;;
    OK) color="$COLOR_GREEN" ;;
  esac
  printf "%s %s[%s]%s %s\n" "$(timestamp)" "$color" "$level" "$COLOR_RESET" "$*" >&2
}

info() { log INFO "$@"; }
warn() { log WARN "$@"; }
error() { log ERROR "$@"; }
success() { log OK "$@"; }

DEBUG="${DEBUG:-0}"
debug() { [[ "$DEBUG" == "1" ]] && log INFO "[debug] $*" || true; }

on_error() {
  local exit_code=$?
  local line_no=${1:-unknown}
  error "Failed at line ${line_no} with exit code ${exit_code}."
}
trap 'on_error ${LINENO}' ERR

cleanup() {
  if [[ -d "${TEMP_VALUES_DIR:-}" ]]; then
    debug "Cleaning up temporary values directory: $TEMP_VALUES_DIR"
    rm -rf "$TEMP_VALUES_DIR"
  fi
}
trap cleanup EXIT

# --- Configuration ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# Required parameters
CHART_PATH="${CHART_PATH:-}"
NAMESPACE="${NAMESPACE:-}"
RELEASE="${RELEASE:-}"
SCENARIO="${SCENARIO:-}"

# Optional parameters with defaults
AUTH="${AUTH:-keycloak}"
PLATFORM="${PLATFORM:-gke}"
LOG_LEVEL="${LOG_LEVEL:-info}"
SKIP_DEPENDENCY_UPDATE="${SKIP_DEPENDENCY_UPDATE:-true}"
EXTERNAL_SECRETS_ENABLED="${EXTERNAL_SECRETS_ENABLED:-true}"

# Keycloak external configuration (required for GKE/ROSA with Keycloak)
KEYCLOAK_EXT_HOST="${KEYCLOAK_EXT_HOST:-keycloak-24-9-0.ci.distro.ultrawombat.com}"
KEYCLOAK_EXT_PROTOCOL="${KEYCLOAK_EXT_PROTOCOL:-https}"

# Binary paths
PREPARE_HELM_VALUES="${PREPARE_HELM_VALUES:-$SCRIPT_DIR/prepare-helm-values/prepare-helm-values}"
CAMUNDA_DEPLOYER="${CAMUNDA_DEPLOYER:-$SCRIPT_DIR/camunda-deployer/camunda-deployer}"

# --- Helper functions ---
print_usage() {
  cat >&2 << EOF
Usage: $0 [OPTIONS]

Deploy Camunda Platform with prepared Helm values and Keycloak realm.

Required:
  --chart-path PATH          Path to the Camunda chart directory
  --namespace NS             Kubernetes namespace
  --release NAME             Helm release name
  --scenario NAME            Scenario name (e.g., keycloak-original)

Optional:
  --auth NAME                Auth scenario (default: keycloak)
  --platform PLATFORM        Target platform: gke, rosa, eks (default: gke)
  --log-level LEVEL          Log level: trace, debug, info, warn, error (default: info)
  --skip-dependency-update   Skip Helm dependency update (default: true)
  --external-secrets BOOL    Enable external secrets (default: true)
  --keycloak-host HOST       Keycloak external host (default: keycloak-24-9-0.ci.distro.ultrawombat.com)
  --keycloak-protocol PROTO  Keycloak protocol (default: https)
  --repo-root PATH           Repository root path (default: auto-detected)
  --help                     Show this help message

Environment Variables:
  KEYCLOAK_EXT_HOST          Alternative to --keycloak-host
  DEBUG                      Enable debug output (0 or 1)

Examples:
  # Basic deployment
  $0 --chart-path ./charts/camunda-platform-8.8 \\
     --namespace my-test-ns \\
     --release my-release \\
     --scenario keycloak-original

  # With custom Keycloak host
  $0 --chart-path ./charts/camunda-platform-8.8 \\
     --namespace test-ns \\
     --release test \\
     --scenario keycloak-original \\
     --keycloak-host keycloak.example.com \\
     --platform gke

EOF
}

# Generate random suffix for uniqueness
generate_random_suffix() {
  # Generate 8-character alphanumeric suffix
  tr -dc 'a-z0-9' < /dev/urandom | head -c 8 || echo "$(date +%s | sha256sum | head -c 8)"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --chart-path)
      CHART_PATH="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --release)
      RELEASE="$2"
      shift 2
      ;;
    --scenario)
      SCENARIO="$2"
      shift 2
      ;;
    --auth)
      AUTH="$2"
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    --log-level)
      LOG_LEVEL="$2"
      shift 2
      ;;
    --skip-dependency-update)
      SKIP_DEPENDENCY_UPDATE="$2"
      shift 2
      ;;
    --external-secrets)
      EXTERNAL_SECRETS_ENABLED="$2"
      shift 2
      ;;
    --keycloak-host)
      KEYCLOAK_EXT_HOST="$2"
      shift 2
      ;;
    --keycloak-protocol)
      KEYCLOAK_EXT_PROTOCOL="$2"
      shift 2
      ;;
    --repo-root)
      REPO_ROOT="$2"
      shift 2
      ;;
    --flow)
      FLOW="$2"
      shift 2
      ;;
    --help)
      print_usage
      exit 0
      ;;
    *)
      error "Unknown option: $1"
      print_usage
      exit 1
      ;;
  esac
done

# Default flow to install if not provided
FLOW="${FLOW:-install}"

# --- Validate inputs ---
if [[ -z "$CHART_PATH" ]]; then
  error "Missing required parameter: --chart-path"
  print_usage
  exit 1
fi

if [[ -z "$NAMESPACE" ]]; then
  error "Missing required parameter: --namespace"
  print_usage
  exit 1
fi

if [[ -z "$RELEASE" ]]; then
  error "Missing required parameter: --release"
  print_usage
  exit 1
fi

if [[ -z "$SCENARIO" ]]; then
  error "Missing required parameter: --scenario"
  print_usage
  exit 1
fi

# Resolve chart path to absolute
CHART_PATH="$(cd "$CHART_PATH" && pwd)"

# Check binaries exist
for binary in "$PREPARE_HELM_VALUES" "$CAMUNDA_DEPLOYER"; do
  if [[ ! -x "$binary" ]]; then
    error "Binary not found or not executable: $binary"
    exit 1
  fi
done

# --- Generate random identifiers ---
RANDOM_SUFFIX="$(generate_random_suffix)"
REALM_NAME="${NAMESPACE}-${RANDOM_SUFFIX}"
OPTIMIZE_PREFIX="opt-${RANDOM_SUFFIX}"
ORCHESTRATION_PREFIX="orch-${RANDOM_SUFFIX}"

info "Generated identifiers:"
info "  Realm name: $REALM_NAME"
info "  Optimize index prefix: $OPTIMIZE_PREFIX"
info "  Orchestration index prefix: $ORCHESTRATION_PREFIX"

# --- Create temporary directory for prepared values ---
TEMP_VALUES_DIR="$(mktemp -d)"
info "Created temporary values directory: $TEMP_VALUES_DIR"

# --- Export environment variables for prepare-helm-values ---
export KEYCLOAK_REALM="$REALM_NAME"
export OPTIMIZE_INDEX_PREFIX="$OPTIMIZE_PREFIX"
export ORCHESTRATION_INDEX_PREFIX="$ORCHESTRATION_PREFIX"
export FLOW="$FLOW"

# Only export Keycloak host if provided (required for some platforms)
if [[ -n "$KEYCLOAK_EXT_HOST" ]]; then
  # Default Keycloak version
  KEYCLOAK_VERSION="24.9.0"
  
  # Sanitize version for env var (e.g. 24.9.0 -> 24_9_0)
  KEYCLOAK_VERSION_SAFE=$(echo "$KEYCLOAK_VERSION" | tr '.' '_')
  KEYCLOAK_HOST_VAR="KEYCLOAK_EXT_HOST_${KEYCLOAK_VERSION_SAFE}"
  KEYCLOAK_PROTOCOL_VAR="KEYCLOAK_EXT_PROTOCOL_${KEYCLOAK_VERSION_SAFE}"

  export "${KEYCLOAK_HOST_VAR}=${KEYCLOAK_EXT_HOST}"
  export "${KEYCLOAK_PROTOCOL_VAR}=${KEYCLOAK_EXT_PROTOCOL}"

  info "Exported Keycloak configuration:"
  info "  ${KEYCLOAK_HOST_VAR}=${KEYCLOAK_EXT_HOST}"
  info "  ${KEYCLOAK_PROTOCOL_VAR}=${KEYCLOAK_EXT_PROTOCOL}"
fi
# --- Step 1: Prepare Helm values ---
info "Step 1/2: Preparing Helm values with prepare-helm-values..."

# Build prepare-helm-values arguments
PREPARE_ARGS=(
  --chart-path "$CHART_PATH"
  --scenario "$SCENARIO"
  --output-dir "$TEMP_VALUES_DIR"
  --log-level "$LOG_LEVEL"
)

# Add auth scenario if it's different from the main scenario
if [[ -n "$AUTH" && "$AUTH" != "$SCENARIO" ]]; then
  info "  Preparing auth scenario: $AUTH"

  # Prepare auth values first
  "$PREPARE_HELM_VALUES" \
    --chart-path "$CHART_PATH" \
    --scenario "$AUTH" \
    --output-dir "$TEMP_VALUES_DIR"
fi

# Prepare main scenario values
info "  Preparing main scenario: $SCENARIO"
debug "Command: $PREPARE_HELM_VALUES ${PREPARE_ARGS[*]}"

"$PREPARE_HELM_VALUES" "${PREPARE_ARGS[@]}"

success "Helm values prepared successfully"

# --- Step 2: Deploy with camunda-deployer ---
info "Step 2/2: Deploying with camunda-deployer..."

DEPLOYER_ARGS=(
  --chart "$CHART_PATH"
  --namespace "$NAMESPACE"
  --release "$RELEASE"
  --scenario "$SCENARIO"
  --auth "$AUTH"
  --scenario-dir "$TEMP_VALUES_DIR"
  --platform "$PLATFORM"
  --repo-root "$REPO_ROOT"
  --log-level "$LOG_LEVEL"
  --load-keycloak-realm
  --keycloak-realm-name "$REALM_NAME"
  --flow "$FLOW"
)

# Add boolean flags
if [[ "$SKIP_DEPENDENCY_UPDATE" == "true" ]]; then
  DEPLOYER_ARGS+=(--skip-dependency-update)
fi

if [[ "$EXTERNAL_SECRETS_ENABLED" == "true" ]]; then
  DEPLOYER_ARGS+=(--external-secrets-enabled)
fi

debug "Command: $CAMUNDA_DEPLOYER ${DEPLOYER_ARGS[*]}"

"$CAMUNDA_DEPLOYER" "${DEPLOYER_ARGS[@]}"

success "Deployment completed successfully!"
info "Deployment details:"
info "  Namespace: $NAMESPACE"
info "  Release: $RELEASE"
info "  Realm: $REALM_NAME"
info "  Optimize prefix: $OPTIMIZE_PREFIX"
info "  Orchestration prefix: $ORCHESTRATION_PREFIX"

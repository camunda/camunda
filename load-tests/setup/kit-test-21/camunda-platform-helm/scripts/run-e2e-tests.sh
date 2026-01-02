#!/usr/bin/env bash

source "$(dirname "$0")/base_playwright_script.sh"
source "$(dirname "$0")/render-e2e-env.sh"

# ------------------------------------------------------------------------------
# Helper Functions
# ------------------------------------------------------------------------------

validate_args() {
  local chart_path="$1"
  local namespace="$2"
  
  log "DEBUG: Validating arguments"

  if [[ -z "$chart_path" ]]; then
    echo "Error: --absolute-chart-path is required" >&2
    exit 1
  fi

  if [[ ! -f "$chart_path/Chart.yaml" ]]; then
    echo "Error: chart path '$chart_path' does not contain a Chart.yaml file" >&2
    exit 1
  fi

  if [[ -z "$namespace" ]]; then
    echo "Error: --namespace is required" >&2
    exit 1
  fi

  if ! kubectl get namespace "$namespace" > /dev/null 2>&1; then
    echo "Error: namespace '$namespace' not found in the current Kubernetes context" >&2
    exit 1
  fi
  
  log "DEBUG: Arguments validated successfully"
}

usage() {
  cat << EOF
This script runs the integration tests for the Camunda Platform Helm chart.

Usage:
  $0 [options]

Options:
  --absolute-chart-path ABSOLUTE_CHART_PATH   The absolute path to the chart directory.
  --namespace NAMESPACE                       The namespace c8 is deployed into
  --show-html-report                          Show the HTML report after the tests have run.
  --shard-index SHARD_INDEX                   The shard index to run.
  --shard-total SHARD_TOTAL                   The total number of shards.
  --test-exclude TEST_EXCLUDE                 The tests to exclude
  --not-ci                                    Don't set the CI env var to true
  --run-smoke-tests                           Run the smoke tests
  --opensearch                                Run the opensearch tests
  --rba                                       Run the rba tests
  --mt                                        Run the mt tests
  --playwright-debug                          Enable Playwright API debug logs and traces
  -v | --verbose                              Show verbose output.
  -h | --help                                 Show this help message and exit.
EOF
}

# ------------------------------------------------------------------------------
# Main
# ------------------------------------------------------------------------------

# Default values
ABSOLUTE_CHART_PATH=""
NAMESPACE=""
SHOW_HTML_REPORT=false
VERBOSE=false
SHARD_INDEX=1
SHARD_TOTAL=1
TEST_EXCLUDE=""
IS_CI=true
RUN_SMOKE_TESTS=false
IS_OPENSEARCH=false
IS_RBA=false
IS_MT=false
PLAYWRIGHT_DEBUG=false

check_required_cmds

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --absolute-chart-path)
      ABSOLUTE_CHART_PATH="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --show-html-report)
      SHOW_HTML_REPORT=true
      shift
      ;;
    --shard-index)
      SHARD_INDEX="$2"
      shift 2
      ;;
    --shard-total)
      SHARD_TOTAL="$2"
      shift 2
      ;;
    --test-exclude)
      TEST_EXCLUDE="$2"
      shift 2
      ;;
    --not-ci)
      IS_CI=false
      shift
      ;;
    --run-smoke-tests)
      RUN_SMOKE_TESTS=true
      shift
      ;;
    --opensearch)
      IS_OPENSEARCH=true
      shift
      ;;
    --rba)
      IS_RBA=true
      shift
      ;;
    --mt)
      IS_MT=true
      shift
      ;;
    --playwright-debug)
      PLAYWRIGHT_DEBUG=true
      shift
      ;;
    -v | --verbose)
      VERBOSE=true
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

log "DEBUG: Starting run-e2e-tests.sh"
log "DEBUG: Chart: $ABSOLUTE_CHART_PATH, Namespace: $NAMESPACE"

validate_args "$ABSOLUTE_CHART_PATH" "$NAMESPACE"

TEST_SUITE_PATH="${ABSOLUTE_CHART_PATH%/}/test/e2e"
hostname=$(get_ingress_hostname "$NAMESPACE")

log "DEBUG: Hostname: $hostname"
log "DEBUG: Test suite path: $TEST_SUITE_PATH"
[[ "$IS_OPENSEARCH" == "true" ]] && log "IS_OPENSEARCH is set to true"
[[ "$IS_RBA" == "true" ]] && log "IS_RBA is set to true"
[[ "$IS_MT" == "true" ]] && log "IS_MT is set to true"

render_env_file "$TEST_SUITE_PATH/.env" "$TEST_SUITE_PATH" "$hostname" "$NAMESPACE" "$IS_CI" "$IS_OPENSEARCH" "$IS_RBA" "$IS_MT" "$RUN_SMOKE_TESTS"

log "$TEST_SUITE_PATH"
log "Running smoke tests: $RUN_SMOKE_TESTS"
log "DEBUG: Shard: $SHARD_INDEX/$SHARD_TOTAL, Exclude: $TEST_EXCLUDE, Debug: $PLAYWRIGHT_DEBUG"

run_playwright_tests "$TEST_SUITE_PATH" "$SHOW_HTML_REPORT" "$SHARD_INDEX" "$SHARD_TOTAL" "blob" "$TEST_EXCLUDE" "$RUN_SMOKE_TESTS" "$PLAYWRIGHT_DEBUG" "$NAMESPACE"

log "DEBUG: E2E tests completed"

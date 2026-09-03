#!/bin/bash

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"

. "${SCRIPT_DIR}/utils.sh"

AVAILABLE_VERSIONS=(main stable-87 stable-88 stable-89 stable-810)

# Internal plumbing hook for the golden-file test harness: lets callers enumerate versions without
# duplicating AVAILABLE_VERSIONS.
if [[ " $* " == *" --list-versions "* ]]; then
  echo "${AVAILABLE_VERSIONS[*]}"
  exit 0
fi

# Pre-scan argv for --target-version/-t so the version config (and its
# allowed storage list) is known before any usage/help text needs printing.
target_version="main"
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  if [[ "${args[i]}" == "--target-version" || "${args[i]}" == "-t" ]]; then
    target_version="${args[i+1]:-}"
    break
  fi
done

if ! contains "$target_version" "${AVAILABLE_VERSIONS[@]}"; then
  echo "Error: No setup found for version '$target_version'." >&2
  echo "Available versions: ${AVAILABLE_VERSIONS[*]}" >&2
  exit 1
fi

VERSION_DIR="$SCRIPT_DIR/$target_version"

### Version-specific configuration
# Defaults match the main version; stable versions override only what differs.
elasticsearch_version=""

case "$target_version" in
  main)
    # renovate: version=camunda-platform-8.10
    camunda_platform_helm_chart_version="15.0.0-alpha4"
    allowed_storage=(elasticsearch opensearch postgresql mysql mariadb mssql oracle none)
    ;;
  stable-87)
    # renovate: version=camunda-platform-8.7
    camunda_platform_helm_chart_version="12.13.3"
    allowed_storage=(elasticsearch)
    elasticsearch_version="8.17.4"
    ;;
  stable-88)
    # renovate: version=camunda-platform-8.8
    camunda_platform_helm_chart_version="13.12.8"
    allowed_storage=(elasticsearch opensearch none)
    elasticsearch_version="8.18.0"
    ;;
  stable-89)
    # renovate: version=camunda-platform-8.9
    camunda_platform_helm_chart_version="14.8.5"
    allowed_storage=(elasticsearch opensearch postgresql mysql mariadb mssql oracle none)
    elasticsearch_version="8.18.0"
    ;;
  stable-810)
    # renovate: version=camunda-platform-8.10
    camunda_platform_helm_chart_version="15.0.0-alpha4"
    allowed_storage=(elasticsearch opensearch postgresql mysql mariadb mssql oracle none)
    elasticsearch_version="8.18.0"
    ;;
esac

# Build the usage help based of the allowed storage options for the selected version.
usage() {
  local prefix=""
  [[ "$target_version" != "main" ]] && prefix="--target-version $target_version "
  cat <<EOF
Usage: newLoadTest.sh ${prefix}<namespace> [secondary_storage] [ttl_days] [enable_optimize]

Arguments:
  namespace           Base namespace name. Will be prefixed with "c8-" if missing.
  secondary_storage   Optional. One of: ${allowed_storage[*]}. Default: elasticsearch.
  ttl_days            Optional. Positive integer for namespace TTL in days. Default: 1.
  enable_optimize     Optional. true|false to enable Optimize. Default: true.

Options:
  --target-version|-t <version>    Version-specific setup to use. Default: main.
                                    Available versions: ${AVAILABLE_VERSIONS[*]}
  -h, --help                       Show this help message.

Examples:
  ./newLoadTest.sh ${prefix}demo
  ./newLoadTest.sh ${prefix}perf elasticsearch 3 true

This script scaffolds the per-namespace folder (Makefile, values files). The
cluster itself is unchanged by this script — the namespace and the
\`camunda-credentials\` secret are created on first \`make install\`, via the
\`load-test-setup\` Helm chart. Reruns of \`make install\` after a TTL deletion
reinstall the same chart, so credentials are regenerated deterministically
(same namespace name in, same values out) and the orchestration OIDC secret
stays in sync with \`load-tester-values-defaults.yaml\`.
EOF
}

remaining_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-version|-t)
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --warm-platform-chart-cache)
      # Internal plumbing hook for the golden-file test harness's chart
      # pre-warm step: pulls the
      # camunda-platform version resolved above for -t/--target-version into
      # LOAD_TEST_CHART_CACHE_DIR (if not already there) and exits before the
      # namespace argument is required. Reuses the same version resolution
      # and cache path convention as the per-scenario pull below, so the two
      # can never drift apart.
      if [[ -z "${LOAD_TEST_CHART_CACHE_DIR:-}" ]]; then
        echo "Error: --warm-platform-chart-cache requires LOAD_TEST_CHART_CACHE_DIR to be set." >&2
        exit 1
      fi
      dest="$LOAD_TEST_CHART_CACHE_DIR/$camunda_platform_helm_chart_version/camunda-platform"
      if [[ ! -d "$dest" ]]; then
        echo "Pre-pulling camunda-platform $camunda_platform_helm_chart_version for $target_version..."
        RETRY_CLEANUP_CMD="rm -rf \"$dest\"" retry_with_backoff helm pull camunda/camunda-platform \
            --untar --untardir "$LOAD_TEST_CHART_CACHE_DIR/$camunda_platform_helm_chart_version" \
            --version "$camunda_platform_helm_chart_version"
      fi
      exit 0
      ;;
    *)
      remaining_args+=("$1")
      shift
      ;;
  esac
done

if [ -z "${remaining_args[0]:-}" ]; then
  echo "Error: Missing namespace name." >&2
  usage
  exit 1
fi

### Argument parsing and validation

namespace="$(parse_namespace "${remaining_args[0]}")"
secondary_storage="$(parse_secondary_storage "${remaining_args[1]:-elasticsearch}" "${allowed_storage[@]}")"
ttl_days="$(parse_ttl "${remaining_args[2]:-1}")"
enable_optimize="$(parse_bool "${remaining_args[3]:-true}")"

# `hashmod_zone` is deterministic, so the zone baked into namespace.yaml and
# the values files matches any re-applied manifest after TTL deletion. Every
# load test pins its workloads to one zone; there is no unpinned mode.
availability_zone="$(hashmod_zone "$namespace")"

git_author=${GIT_AUTHOR:-$(compute_git_author)}
deadline_date="$(compute_deadline_date "$ttl_days")"

TARGET_DIRECTORY="${ROOT_DIR}/${namespace}"

# Scaffold the namespace folder with only the files this $secondary_storage uses.
# A namespace is bound to its storage at create time; to switch storage, create
# a new namespace via ./newLoadTest.sh <new-name> <newStorage>.
echo "Creating new load test in: $TARGET_DIRECTORY"
mkdir -p "$TARGET_DIRECTORY"

# Scaffold the always-copied files into the namespace folder root: the
# Makefile, the resources/ manifests, four storage-agnostic values files
# (defaults + override + load-test + stable), and the matching
# camunda-platform-values-${secondary_storage}.yaml. Flat layout so the
# per-namespace Makefile's -f <file>.yaml references resolve unchanged.
cp -v  "$VERSION_DIR/Makefile"                                                  "$TARGET_DIRECTORY/"
cp -rv "$SCRIPT_DIR/charts"                                                     "$TARGET_DIRECTORY/"
cp -v  "$VERSION_DIR/values/camunda-platform-override-values.yaml"              "$TARGET_DIRECTORY/"
cp -v  "$SCRIPT_DIR/scenarios/load-tester-values-defaults.yaml"                 "$TARGET_DIRECTORY/"
cp -v  "$VERSION_DIR/values/values-stable.yaml"                                 "$TARGET_DIRECTORY/"
cp -v  "$SCRIPT_DIR/scenarios/load-tester-values-realistic-benchmark.yaml"      "$TARGET_DIRECTORY/"
cp -v  "$VERSION_DIR/values/camunda-platform-values-defaults.yaml"              "$TARGET_DIRECTORY/"
cp -v  "$VERSION_DIR/values/camunda-platform-values-${secondary_storage}.yaml"   "$TARGET_DIRECTORY/"

# Don't configure Elasticsearch unless specifically enabled (secondary storage,
# or via Optimize)
elasticsearchEnabled=false
opensearchEnabled=false
postgresqlEnabled=false

# The Elasticsearch/OpenSearch URI for the Prometheus exporter for Elasticsearch.
es_uri=""

# Storage-specific copies.
case "$secondary_storage" in
  elasticsearch|opensearch)
    if [ "$secondary_storage" = elasticsearch ]; then
      elasticsearchEnabled=true
      es_uri="http://elasticsearch-es-http:9200"
    else
      opensearchEnabled=true
      es_uri="http://opensearch:9200"
    fi
    ;;
  postgresql|mysql|mariadb|mssql|oracle)
    cp -v "$VERSION_DIR/values/camunda-platform-values-rdbms.yaml" "$TARGET_DIRECTORY/"

    if [ "$secondary_storage" = "postgresql" ]; then
      postgresqlEnabled=true
    fi

    secondary_storage_config_file="$VERSION_DIR/databases/${secondary_storage}.yaml"
    if [[ -f "$secondary_storage_config_file" ]]; then
      mkdir -p "$TARGET_DIRECTORY/databases"
      cp -v "$secondary_storage_config_file" "$TARGET_DIRECTORY/databases/"
    fi
    ;;
esac


if [[ "$enable_optimize" == "true" && "$secondary_storage" == "none" ]]; then
  echo "Optimize requires a secondary storage backend; forcefully disabling Optimize."
  enable_optimize=false
elif [[ "$enable_optimize" == "true" ]]; then
  # Optimize-specific configuration
  cp -v "$VERSION_DIR/values/camunda-platform-values-optimize.yaml" "$TARGET_DIRECTORY/"

  if [[ "$secondary_storage" == "elasticsearch" ]]; then
    # stable-87's Makefile uses this file when secondary_storage=elasticsearch;
    # for other versions it's harmlessly present (their Makefiles skip it for ES).
    cp -v "$VERSION_DIR/values/camunda-platform-values-optimize-elasticsearch.yaml" "$TARGET_DIRECTORY/"
  elif [[ "$secondary_storage" == "opensearch" ]]; then
    optimize_opensearch_config_file="$VERSION_DIR/values/camunda-platform-values-optimize-opensearch.yaml"
    if [[ -f "$optimize_opensearch_config_file" ]]; then
      # We don't ship the OpenSearch configuration for Optimize on 8.8
      cp -v "$optimize_opensearch_config_file" "$TARGET_DIRECTORY/"
    fi
  else
    # Non-ES/non-OS storage: forcefully configure Optimize to use Elasticsearch.
    elasticsearchEnabled=true
    es_uri="http://elasticsearch-es-http:9200"
    echo "Forcefully enabling Elasticsearch for Optimize"
    cp -v "$VERSION_DIR/values/camunda-platform-values-optimize-elasticsearch.yaml" "$TARGET_DIRECTORY/"
  fi
fi

# The exporter is only useful when there's an Elasticsearch/OpenSearch cluster to scrape.
prometheusExporterEnabled=false
if [[ "$elasticsearchEnabled" == "true" || "$opensearchEnabled" == "true" ]]; then
  prometheusExporterEnabled=true
fi

cd "$TARGET_DIRECTORY"

# Bake values into the rendered Makefile.
sed_inplace "s/__NAMESPACE__/$namespace/"             Makefile
sed_inplace "s/__STORAGE_TYPE__/$secondary_storage/"   Makefile
sed_inplace "s/__ENABLE_OPTIMIZE__/$enable_optimize/" Makefile

# Bake values into the resource manifests and the platform/load-test values.
sed_targets=(*.yaml)
[[ -d databases ]] && sed_targets+=(databases/*.yaml)
sed_inplace "s/__NAMESPACE__/$namespace/" "${sed_targets[@]}"
sed_inplace "s/__AVAILABILITY_ZONE__/$availability_zone/" "${sed_targets[@]}"
sed_inplace "s/__AUTHOR__/$git_author/"                   "${sed_targets[@]}"

{
  cat <<EOF
# Propagated to the camunda-load-tests (load-tester) subchart via Helm global
# coalescing.
global:
  commonLabels:
    camunda.io/created-by: "$git_author"
  nodeSelector:
    topology.kubernetes.io/zone: $availability_zone

name: "$namespace"
author: "$git_author"
deadlineDate: "$deadline_date"
topologyZone: $availability_zone

EOF

  if [[ "$target_version" == "stable-87" ]]; then
    cat <<'EOF'
camundaManagementUrl: "http://zeebe-gateway:9600"

# 8.7 doesn't have a REST API
global:
    performReadBenchmarks: false

loadTest:
  client:
    oidc:
      # The OAuth parameters between 8.7 and > 8.7 are quite different.
      # The "load test setup" Helm Chart defaults to the > 8.7 settings, which
      # are overridden here.
      clientId: zeebe
      zeebeRestAddress: http://zeebe-gateway:8080
      zeebeGrpcAddress: http://zeebe-gateway:26500
      authorizationAudience: zeebe-api
      secret:
        # We need to provision a specific secret through Identity/Keycloak
        key: identity-zeebe-client-token

EOF
  fi

  cat <<EOF
elasticsearch:
  # Elasticsearch settings are configured through the options from
  # charts/load-test-setup/values.yaml
  enabled: $elasticsearchEnabled

EOF
  if [[ "$elasticsearchEnabled" == "true" && -n "$elasticsearch_version" ]]; then
    echo "  version: \"$elasticsearch_version\""
    echo
  fi

  cat <<EOF
opensearch:
  enabled: $opensearchEnabled

  nodeSelector:
    topology.kubernetes.io/zone: "$availability_zone"
EOF

  if [[ -n "$es_uri" ]]; then
    cat <<EOF
metricsExporter:
  database:
    url: "$es_uri"

EOF
  fi

  cat <<EOF
prometheus-elasticsearch-exporter:
  enabled: $prometheusExporterEnabled
EOF
  if [[ "$prometheusExporterEnabled" == "true" ]]; then
    cat <<EOF
  es:
    uri: "$es_uri"

  commonLabels:
    camunda.io/created-by: "$git_author"

  nodeSelector:
    topology.kubernetes.io/zone: "$availability_zone"
EOF
  fi

  cat <<EOF
postgresql:
  enabled: $postgresqlEnabled
EOF
} > load-test-setup-values.yaml

# Add/update helm repositories. Skippable via LOAD_TEST_SKIP_REPO_UPDATE for
# callers (the golden-file test harness) that already primed the repo index
# once before spawning many scenarios; unset by default, so a manual
# invocation behaves exactly as before, just retried on transient failures.
if [[ "${LOAD_TEST_SKIP_REPO_UPDATE:-false}" != "true" ]]; then
  retry_with_backoff helm repo add camunda https://helm.camunda.io/ --force-update
  if [[ "$secondary_storage" == "opensearch" ]]; then
    retry_with_backoff helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
  fi
  retry_with_backoff helm repo update
fi

CHARTS_DIR="charts"

# If LOAD_TEST_CHART_CACHE_DIR points at a pre-warmed cache (populated once,
# per version, before this script runs), reuse it instead of pulling over the
# network again. Unset by default, so a manual invocation always pulls fresh.
platform_cache_src="${LOAD_TEST_CHART_CACHE_DIR:-}/${camunda_platform_helm_chart_version}/camunda-platform"
if [[ -n "${LOAD_TEST_CHART_CACHE_DIR:-}" && -d "$platform_cache_src" ]]; then
  echo "Using pre-warmed camunda-platform $camunda_platform_helm_chart_version chart from cache..."
  rm -rf "$CHARTS_DIR/camunda-platform"
  cp -r "$platform_cache_src" "$CHARTS_DIR/camunda-platform"
else
  echo "Pulling Camunda Platform Helm Chart: $camunda_platform_helm_chart_version"
  RETRY_CLEANUP_CMD="rm -rf \"$CHARTS_DIR/camunda-platform\"" retry_with_backoff helm pull camunda/camunda-platform \
      --untar --untardir "$CHARTS_DIR" \
      --version "$camunda_platform_helm_chart_version"
fi

# If LOAD_TEST_CHART_CACHE_DIR points at pre-resolved load-test-setup
# dependencies (warmed once against a scratch copy, before this script runs
# many times in parallel), reuse them instead of resolving over the network
# again. Unset by default, so a manual invocation always resolves fresh.
deps_cache_src="${LOAD_TEST_CHART_CACHE_DIR:-}/load-test-setup-deps"
if [[ -n "${LOAD_TEST_CHART_CACHE_DIR:-}" && -d "$deps_cache_src" ]]; then
  echo "Using pre-warmed load-test-setup subchart dependencies from cache..."
  rm -rf "$CHARTS_DIR/load-test-setup/charts"
  cp -r "$deps_cache_src" "$CHARTS_DIR/load-test-setup/charts"
else
  echo "Resolving load-test-setup subchart dependencies..."
  retry_with_backoff helm dependency update "$CHARTS_DIR/load-test-setup"
fi

echo
echo "Scaffolding complete. Next steps:"
echo "  cd $namespace"
echo "  make install"
echo
echo "Deadline: $deadline_date (TTL = $ttl_days day(s)). To extend, edit deadlineDate in load-test-setup-values.yaml and run \`make install-load-test-setup\`."

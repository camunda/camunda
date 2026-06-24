#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The directory where the load tests and shared utilities are located.
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Contains OS specific sed function
. "${ROOT_DIR}/utils.sh"

set -eo pipefail

usage() {
  cat <<'EOF'
Usage: newLoadTest.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize] [enable_single_zone]

Arguments:
  namespace          Base namespace name. Will be prefixed with "c8-" if missing.
  secondaryStorage   Optional. One of: elasticsearch, opensearch, none. Default: elasticsearch.
  ttl_days           Optional. Positive integer for namespace TTL in days. Default: 1.
  enable_optimize    Optional. true|false to enable Optimize. Default: true.
  enable_single_zone Optional. true|false to deploy the cluster on a single zone. Default: true

Options:
  -h, --help         Show this help message.

Examples:
  ./newLoadTest.sh demo
  ./newLoadTest.sh perf opensearch 3 false

This script scaffolds the per-namespace folder including rendered Kubernetes
manifests under resources/ (namespace + credentials). The cluster itself is
unchanged by this script — namespace and secret are created on first
`make install` via `kubectl apply -f resources/...`. Reruns of `make install`
after a TTL deletion recreate both from the same baked manifests, so
credentials stay in sync with `load-test-values.yaml`.
EOF
}

if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
  usage
  exit 0
fi

if [ -z "$1" ]; then
  echo "Error: Missing namespace name."
  usage
  exit 1
fi

### Load test helper script
### First parameter is used as namespace name
### For a new namespace a new folder will be created

helm_chart="camunda-platform-8.8"
namespace="$1"

# Add c8- prefix if not present
if [[ ! "$namespace" =~ ^c8- ]]; then
  namespace="c8-$namespace"
  echo "Namespace prefix added: $namespace"
fi

# Validate secondaryStorage value
secondaryStorage="${2:-elasticsearch}"
if [[ "$secondaryStorage" != "elasticsearch" && "$secondaryStorage" != "opensearch" && "$secondaryStorage" != "none" ]]; then
  echo "Error: Invalid secondary storage type '$secondaryStorage'"
  echo "Allowed values are: elasticsearch, opensearch, postgresql, none"
  exit 1
fi

# Validate TTL value
ttl_days="${3:-1}"
numberRegex='^[0-9]+$'
if ! [[ $ttl_days =~ $numberRegex ]] ; then
   echo "Error: TTL '$ttl_days' is not a number"
   exit 1
fi

# Validate enable_optimize value
enable_optimize="${4:-true}"
enable_optimize=$(echo "$enable_optimize" | tr '[:upper:]' '[:lower:]')
if [[ "$enable_optimize" != "true" && "$enable_optimize" != "false" ]]; then
  echo "Error: Invalid enable_optimize value '$enable_optimize'"
  echo "Allowed values are: true or false"
  exit 1
fi

enable_single_zone="${5:-true}"
enable_single_zone=$(echo "$enable_single_zone" | tr '[:upper:]' '[:lower:]')

# `hashmod_zone` is deterministic, so the zone baked into namespace.yaml and
# the values files matches any re-applied manifest after TTL deletion.
if [[ "$enable_single_zone" == "true" ]]; then
  availability_zone="$(hashmod_zone "$namespace")"
else
  availability_zone="~"
fi

git_author=${GIT_AUTHOR:-$(compute_git_author)}

# Compute deadline-date once at scaffold time. The Makefile's check-deadline
# target compares this against today; if it has passed, install fails fast.
if deadline_date=$(date -d "+${ttl_days} days" +%Y-%m-%d 2>/dev/null); then
  : # GNU date succeeded
elif deadline_date=$(date -v +${ttl_days}d +%Y-%m-%d 2>/dev/null); then
  : # BSD/macOS date succeeded
else
  echo "Error: Could not calculate deadline date. Supported on Linux and macOS only."
  exit 1
fi

TARGET_DIRECTORY="${ROOT_DIR}/${namespace}"

# Scaffold the namespace folder with only the files this $secondaryStorage uses.
# A namespace is bound to its storage at create time; to switch storage, create
# a new namespace via ./newLoadTest.sh <new-name> <newStorage>.
mkdir -p "$TARGET_DIRECTORY"

# Scaffold the always-copied files into the namespace folder root: the
# Makefile, the resources/ manifests, four storage-agnostic values files
# (defaults + override + load-test + stable), and the matching
# camunda-platform-values-${secondaryStorage}.yaml. Flat layout so the
# per-namespace Makefile's -f <file>.yaml references resolve unchanged.
cp -v  "Makefile"                                                "$TARGET_DIRECTORY/"
cp -rv "resources/"                                              "$TARGET_DIRECTORY/"
cp -v  "values/camunda-platform-override-values.yaml"            "$TARGET_DIRECTORY/"
cp -v  "values/load-test-values.yaml"                            "$TARGET_DIRECTORY/"
cp -v  "values/values-stable.yaml"                               "$TARGET_DIRECTORY/"
cp -v  "values/camunda-platform-values-defaults.yaml"            "$TARGET_DIRECTORY/"
cp -v  "values/camunda-platform-values-${secondaryStorage}.yaml" "$TARGET_DIRECTORY/"

# Storage-specific copies.
case "$secondaryStorage" in
  elasticsearch|opensearch)
    cp -v "values/prometheus-elasticsearch-exporter-values.yaml" "$TARGET_DIRECTORY/"
    ;;
esac

if [[ "$enable_optimize" == "true" ]]; then
  # Optimize needs specifically Elasticsearch (independently from the secondary
  # storage configuration).
  cp -v "values/camunda-platform-values-optimize-elasticsearch.yaml" "$TARGET_DIRECTORY/"
  cp -v "values/prometheus-elasticsearch-exporter-values.yaml"       "$TARGET_DIRECTORY/"
fi

cd "$TARGET_DIRECTORY"

# Bake values into the rendered Makefile.
sed_inplace "s/__NAMESPACE__/$namespace/"           Makefile
sed_inplace "s/__STORAGE_TYPE__/$secondaryStorage/" Makefile
sed_inplace "s/__ENABLE_OPTIMIZE__/$enable_optimize/" Makefile
sed_inplace "s/__DEADLINE_DATE__/$deadline_date/"    Makefile

# Bake values into the resource manifests and the platform/load-test values.
# Values shared with the chart (NAMESPACE, AVAILABILITY_ZONE, AUTHOR) flow into
# the upstream yaml files via the same sed pass.
sed_inplace "s/__NAMESPACE__/$namespace/"                       load-test-values.yaml resources/*.yaml
sed_targets=(*.yaml resources/namespace.yaml)
sed_inplace "s/__AVAILABILITY_ZONE__/$availability_zone/" "${sed_targets[@]}"
sed_inplace "s/__AUTHOR__/$git_author/"                   "${sed_targets[@]}"
sed_inplace "s/__DEADLINE_DATE__/$deadline_date/"                resources/namespace.yaml

# When single-zone is disabled the topology annotation has no useful value;
# strip the annotation line and the now-empty `annotations:` key so the manifest
# stays tidy.
if [[ "$enable_single_zone" != "true" ]]; then
  sed_inplace "/topology.kubernetes.io\\/zone:/d" resources/namespace.yaml
  # `sed_inplace` splits args on whitespace and the `annotations:` line pattern
  # contains literal spaces, so call sed directly with OS-aware -i flag.
  detect_os
  if [ "${GO_OS}" == "darwin" ]; then
    sed -i '' -e '/^  annotations:$/d' resources/namespace.yaml
  else
    sed -i    -e '/^  annotations:$/d' resources/namespace.yaml
  fi
fi

#############################################################################################
################################ CREDENTIALS ################################################
#############################################################################################

# `get_existing_secret`, `gen_password`, and `gen_token` are sourced from utils.sh.

# If the secret already exists in the cluster, preserve all existing values to avoid breaking any live load test.
# If the secret doesn't exist, generate new random values for all keys.
# This might happend on CI/GitHub Workflows that update existing load tests but do not persist the credentials across runs.
if kubectl -n "$namespace" get secret camunda-credentials >/dev/null 2>&1; then
  echo "Secret 'camunda-credentials' already exists in namespace '$namespace'; preserving existing credentials."
  jsonObject=$(kubectl -n "$namespace" get secret camunda-credentials -o jsonpath='{.data}')

  IDENTITY_FIRSTUSER_PASSWORD=$(get_existing_secret "$jsonObject" "identity-firstuser-password")
  IDENTITY_KEYCLOAK_ADMIN_PASSWORD=$(get_existing_secret "$jsonObject" "identity-keycloak-admin-password")
  IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD=$(get_existing_secret "$jsonObject" "identity-keycloak-postgresql-admin-password")
  IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD=$(get_existing_secret "$jsonObject" "identity-keycloak-postgresql-user-password")
  IDENTITY_POSTGRESQL_ADMIN_PASSWORD=$(get_existing_secret "$jsonObject" "identity-postgresql-admin-password")
  IDENTITY_POSTGRESQL_USER_PASSWORD=$(get_existing_secret "$jsonObject" "identity-postgresql-user-password")
  CONNECTORS_SECRET=$(get_existing_secret "$jsonObject" "connectors-security-authentication-oidc-secret")
  ORCHESTRATION_SECRET=$(get_existing_secret "$jsonObject" "orchestration-security-authentication-oidc-secret")
  IDENTITY_ADMIN_CLIENT_TOKEN=$(get_existing_secret "$jsonObject" "identity-admin-client-token")
  IDENTITY_OPTIMIZE_CLIENT_TOKEN=$(get_existing_secret "$jsonObject" "identity-optimize-client-token")

else
  echo "Generating new credentials for secret 'camunda-credentials'."
  IDENTITY_FIRSTUSER_PASSWORD=$(gen_password)
  IDENTITY_KEYCLOAK_ADMIN_PASSWORD=$(gen_password)
  IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD=$(gen_password)
  IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD=$(gen_password)
  IDENTITY_POSTGRESQL_ADMIN_PASSWORD=$(gen_password)
  IDENTITY_POSTGRESQL_USER_PASSWORD=$(gen_password)
  CONNECTORS_SECRET=$(gen_password)
  ORCHESTRATION_SECRET=$(gen_password)
  IDENTITY_ADMIN_CLIENT_TOKEN=$(gen_token)
  IDENTITY_OPTIMIZE_CLIENT_TOKEN=$(gen_token)
fi

# Bake the orchestration OIDC secret into the load-test starter values.
sed_inplace "s|__SECRET__|$ORCHESTRATION_SECRET|" load-test-values.yaml

# Bake the credential values into the secret manifest.
sed_inplace "s|__IDENTITY_FIRSTUSER_PASSWORD__|$IDENTITY_FIRSTUSER_PASSWORD|"                                 resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_KEYCLOAK_ADMIN_PASSWORD__|$IDENTITY_KEYCLOAK_ADMIN_PASSWORD|"                       resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD__|$IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD|" resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD__|$IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD|"   resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_POSTGRESQL_ADMIN_PASSWORD__|$IDENTITY_POSTGRESQL_ADMIN_PASSWORD|"                   resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_POSTGRESQL_USER_PASSWORD__|$IDENTITY_POSTGRESQL_USER_PASSWORD|"                     resources/camunda-credentials.yaml
sed_inplace "s|__ORCHESTRATION_SECRET__|$ORCHESTRATION_SECRET|"                                               resources/camunda-credentials.yaml
sed_inplace "s|__CONNECTORS_SECRET__|$CONNECTORS_SECRET|"                                                     resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_ADMIN_CLIENT_TOKEN__|$IDENTITY_ADMIN_CLIENT_TOKEN|"                                 resources/camunda-credentials.yaml
sed_inplace "s|__IDENTITY_OPTIMIZE_CLIENT_TOKEN__|$IDENTITY_OPTIMIZE_CLIENT_TOKEN|"                           resources/camunda-credentials.yaml

# Add/update helm repositories
helm repo add camunda https://helm.camunda.io/ --force-update
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update
helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
helm repo update

# Clone Camunda Platform Helm so we can run the latest chart
# TODO: 347642d30179479f8ab8a2f00b2d979be05f5a8c is the latest commit before the removal of the
# embedded Bitnami Helm Chart.
# We should remove the checkout of this specific revision once we have a solution to replace these
# removed dependencies.
git clone --depth 1 --revision 347642d30179479f8ab8a2f00b2d979be05f5a8c --single-branch https://github.com/camunda/camunda-platform-helm.git

# Make deps
helm dependency build "camunda-platform-helm/charts/$helm_chart"

set +x
echo
echo "Scaffolding complete. Next steps:"
echo "  cd $namespace"
echo "  make install   # applies resources/namespace.yaml + resources/camunda-credentials.yaml and deploys"
echo
echo "Deadline: $deadline_date (TTL = $ttl_days day(s)). Bump it via resources/namespace.yaml + kubectl label, or rerun this script."

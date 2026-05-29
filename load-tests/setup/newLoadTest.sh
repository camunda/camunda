#!/bin/bash

# Contains OS specific sed function
. utils.sh

set -eo pipefail

usage() {
  cat <<'EOF'
Usage: newLoadTest.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize] [enable_single_zone]

Arguments:
  namespace          Base namespace name. Will be prefixed with "c8-" if missing.
  secondaryStorage   Optional. One of: elasticsearch, opensearch, postgresql, mysql, mariadb, mssql, oracle, none. Default: elasticsearch.
  ttl_days           Optional. Positive integer for namespace TTL in days. Default: 1.
  enable_optimize    Optional. true|false to enable Optimize. Default: true.
  enable_single_zone Optional. true|false to deploy the cluster on a single zone. Default: true

Options:
  -h, --help         Show this help message.

Examples:
  ./newLoadTest.sh demo
  ./newLoadTest.sh perf opensearch 3 false

This script scaffolds the per-namespace folder including a local "umbrella"
Helm chart parameterized by umbrella-values.yaml (namespace, deadline, AZ)
and umbrella-values.secrets.yaml (generated passwords/tokens). The cluster
itself is unchanged by this script — the namespace, credentials secret,
leader-balancer cronjob, and Optimize cleanup job are created on first
`make install` via the umbrella Helm release. Reruns of `make install`
after a TTL deletion reinstall the same chart, so credentials stay in sync
with `load-test-values.yaml`.
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

helm_chart="camunda-platform-8.10"
namespace="$1"

# Add c8- prefix if not present
if [[ ! "$namespace" =~ ^c8- ]]; then
  namespace="c8-$namespace"
  echo "Namespace prefix added: $namespace"
fi

# Validate against Kubernetes DNS-1123 label rules. Previously this was
# implicit (`kubectl create namespace` rejected bad names at cluster time);
# now that namespace creation is deferred to `make install`, validate here so
# we don't render a folder with random secrets just to discover the name is
# invalid.
if [[ ! "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
  echo "Error: namespace '$namespace' is not a valid Kubernetes DNS-1123 label."
  echo "       Allowed: lowercase letters, digits, '-'. Must start and end with an alphanumeric."
  exit 1
fi
if [ ${#namespace} -gt 63 ]; then
  echo "Error: namespace '$namespace' is ${#namespace} characters; Kubernetes labels are capped at 63."
  exit 1
fi

# Validate secondaryStorage value
secondaryStorage="${2:-elasticsearch}"
if [[ "$secondaryStorage" != "elasticsearch" && "$secondaryStorage" != "opensearch" && "$secondaryStorage" != "postgresql" && "$secondaryStorage" != "mysql" && "$secondaryStorage" != "mariadb" && "$secondaryStorage" != "mssql" && "$secondaryStorage" != "oracle" && "$secondaryStorage" != "none" ]]; then
  echo "Error: Invalid secondary storage type '$secondaryStorage'"
  echo "Allowed values are: elasticsearch, opensearch, postgresql, mysql, mariadb, mssql, oracle, none"
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

# Pick a "random" zone, selected from the input value.
function hashmod_zone() {
    local input="${1?"Specify an initial value to compute the zone from"}"

    # We can get the list of zones with already created nodes with:
    # kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.labels.topology\.kubernetes\.io\/zone}{"\n"}{end}' | sort | uniq -c
    zones=(
        europe-west1-b
        europe-west1-c
        europe-west1-d
    )
    nb_zones=${#zones[@]}

    # bc only accept hexadecimal with capitalized letters
    checksum="$(echo "$input" | md5sum | cut -c 1-32 | tr "a-z" "A-Z")"
    hashmod="$(echo "ibase=16; $checksum % $nb_zones" | bc)"

    zone="${zones[$hashmod]}"
    echo "$zone"
}

# `hashmod_zone` is deterministic, so the zone baked into namespace.yaml and
# the values files matches any re-applied manifest after TTL deletion.
if [[ "$enable_single_zone" == "true" ]]; then
  availability_zone="$(hashmod_zone "$namespace")"
else
  availability_zone="~"
fi

git_author=$(compute_git_author)

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

# Generate credentials. These are baked into umbrella-values.secrets.yaml
# and (for the orchestration OIDC secret) into load-test-values.yaml. Any
# subsequent `make install` reinstalls the umbrella chart, so the secret in
# the cluster always matches the value the load test starter authenticates
# with.
# `head -c 20` closes the pipe early; upstream `tr` then takes SIGPIPE and
# returns 141 under `set -o pipefail`. Wrap in a subshell so the harmless
# SIGPIPE doesn't trip `set -e` in the caller.
gen_password() { ( set +o pipefail; LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20 ); }
gen_token()    { openssl rand -hex 16; }

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

# Scaffold the namespace folder with only the files this $secondaryStorage uses.
# A namespace is bound to its storage at create time; to switch storage, create
# a new namespace via ./newLoadTest.sh <new-name> <newStorage>.
mkdir -p "$namespace"

# Scaffold the always-copied files into the namespace folder root: the
# Makefile, the charts dependencies, four storage-agnostic values files
# (defaults + override + load-test + stable), and the matching
# camunda-platform-values-${secondaryStorage}.yaml. Flat layout so the
# per-namespace Makefile's -f <file>.yaml references resolve unchanged.
cp -v  default/Makefile                              "$namespace/"
cp -rv default/charts/                               "$namespace/"
cp -v  default/values/camunda-platform-override-values.yaml "$namespace/"
cp -v  default/values/load-test-values.yaml                 "$namespace/"
cp -v  default/values/values-stable.yaml                    "$namespace/"
cp -v "default/values/camunda-platform-values-defaults.yaml" "$namespace/"
cp -v "default/values/camunda-platform-values-${secondaryStorage}.yaml" "$namespace/"

# Storage-specific copies. databases/ is created only for mssql/oracle.
case "$secondaryStorage" in
  elasticsearch)
    cp -v default/values/prometheus-elasticsearch-exporter-values.yaml "$namespace/"
    ;;
  postgresql|mysql|mariadb)
    cp -v default/values/camunda-platform-values-rdbms.yaml                 "$namespace/"
    ;;
  mssql|oracle)
    cp -v default/values/camunda-platform-values-rdbms.yaml                 "$namespace/"
    mkdir -p "$namespace/databases"
    cp -v "default/databases/${secondaryStorage}.yaml"                       "$namespace/databases/"
    ;;
esac

if [[ "$enable_optimize" == "true" ]]; then
  # Optimize needs specifically Elasticsearch (independently from the secondary
  # storage configuration).
  cp -v default/values/camunda-platform-values-optimize-elasticsearch.yaml "$namespace/"
fi

cd "$namespace"

cat <<EOF > umbrella-values.yaml
name: "$namespace"
author: "$git_author"
deadlineDate: "$deadline_date"
# Can be unset using "topologyZone: ~"
topologyZone: $availability_zone
EOF

# Configure credentials in a separated files to make sure this one is clearly
# identified as "secret" and is not accidentally committed with git.
cat <<EOF > umbrella-values.secrets.yaml
credentials:
  identity:
    firstuser:
      password: "$IDENTITY_FIRSTUSER_PASSWORD"
    keycloak:
      admin:
        password: "$IDENTITY_KEYCLOAK_ADMIN_PASSWORD"
      postgresql:
        admin:
          password: "$IDENTITY_KEYCLOAK_POSTGRESQL_ADMIN_PASSWORD"
        user:
          password: "$IDENTITY_KEYCLOAK_POSTGRESQL_USER_PASSWORD"
    postgresql:
      admin:
        password: "$IDENTITY_POSTGRESQL_ADMIN_PASSWORD"
      user:
        password: "$IDENTITY_POSTGRESQL_USER_PASSWORD"
    admin:
      client:
        token: "$IDENTITY_ADMIN_CLIENT_TOKEN"
    optimize:
      client:
        token: "$IDENTITY_OPTIMIZE_CLIENT_TOKEN"
  orchestration:
    security:
      authentication:
        oidc:
          secret: "$ORCHESTRATION_SECRET"
  connectors:
    security:
      authentication:
        oidc:
          secret: "$CONNECTORS_SECRET"
EOF

# Bake values into the rendered Makefile. The deadline lives only in
# umbrella-values.yaml (single source of truth) — check-deadline parses
# it out of there so the user only edits one place to extend the TTL.
sed_inplace "s/__NAMESPACE__/$namespace/"           Makefile
sed_inplace "s/__STORAGE_TYPE__/$secondaryStorage/" Makefile
sed_inplace "s/__ENABLE_OPTIMIZE__/$enable_optimize/" Makefile

# Bake values into the resource manifests and the platform/load-test values.
# Values shared with the chart (NAMESPACE, AVAILABILITY_ZONE) flow into
# the upstream yaml files via the same sed pass.
sed_inplace "s/__NAMESPACE__/$namespace/"                       load-test-values.yaml
sed_targets=(*.yaml)
[[ -d databases ]] && sed_targets+=(databases/*.yaml)
sed_inplace "s/__AVAILABILITY_ZONE__/$availability_zone/" "${sed_targets[@]}"
sed_inplace "s/__AUTHOR__/$git_author/"                   "${sed_targets[@]}"

# Bake the orchestration OIDC secret into the load-test starter values.
sed_inplace "s|__SECRET__|$ORCHESTRATION_SECRET|" load-test-values.yaml

# Add/update helm repositories
helm repo add camunda https://helm.camunda.io/ --force-update
helm repo add camunda-load-tests https://camunda.github.io/camunda-load-tests-helm/ --force-update
helm repo add opensearch https://opensearch-project.github.io/helm-charts/ --force-update
helm repo update

# The directory where local Helm Charts will be stored in.
CHARTS_DIR="charts"

# Clone Platform Helm so we can run the latest chart
git clone --depth 1 --branch main --single-branch https://github.com/camunda/camunda-platform-helm.git "$CHARTS_DIR/camunda-platform-helm"

# Make deps
helm dependency build "$CHARTS_DIR/camunda-platform-helm/charts/$helm_chart"

echo "Update Umbrella chart dependencies with latest values from the Camunda Platform chart..."
helm dependency update "$CHARTS_DIR/umbrella"
helm dependency build "$CHARTS_DIR/umbrella"

echo
echo "Scaffolding complete. Next steps:"
echo "  cd $namespace"
echo "  make install"
echo
echo "Deadline: $deadline_date (TTL = $ttl_days day(s)). To extend, edit deadlineDate in umbrella-values.yaml and run \`make install-umbrella\`."

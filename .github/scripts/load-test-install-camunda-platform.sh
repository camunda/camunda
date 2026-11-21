#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Builds the Helm value overrides for both the Camunda platform and the load-tester
# deployments from the workflow's inputs, then runs `make install`/`make install-stable`
# against the rendered load-test folder. Centralized here because the override logic
# branches on several independent, optional inputs (custom vs. Docker Hub images per
# component, read benchmarks, Optimize metrics, user-supplied Helm values, whether the
# metrics-exporter image was built this run).
#
# Usage: load-test-install-camunda-platform.sh <namespace> <install-target> <image-tag> \
#          <image-repository> <perform-read-benchmarks> <enable-optimize> \
#          <enable-optimize-metrics> <metrics-exporter-built> <enable-chaos> \
#          <physical-tenant-count> [OPTIONS]
#
#   namespace                  Load-test namespace (also the setup folder name)
#   install-target             Make target to run (install or install-stable)
#   image-tag                  Image tag of the load-tester (starter/worker) images, also used for
#                              the metrics-exporter image when it was built
#   image-repository           Repository for the load-tester (starter/worker) images
#   perform-read-benchmarks    Run continuous read benchmarks (true/false)
#   enable-optimize            Whether Optimize is part of this deployment (true/false)
#   enable-optimize-metrics    Enable the load-tester Optimize report-evaluation meter (true/false)
#   metrics-exporter-built     Whether build-metrics-exporter-image ran this run (true/false)
#   enable-chaos               Enable chaos-killer (true/false)
#   physical-tenant-count      Number of extra physical tenants (pt1..ptN) to deploy (0 = disabled)
#
# The previous format with image-registry, camunda-repo and optimize-repo as positional arguments
# after image-repository is still accepted, only for the stable/8.10 copy of camunda-load-test.yml.
#
# Options (all optional, empty by default). A platform image option that is empty or missing sets
# no Helm value, so the value of the setup folder applies:
#   --orchestration-registry REGISTRY      Orchestration image registry
#   --orchestration-repository REPOSITORY  Orchestration image repository
#   --orchestration-tag TAG                Orchestration image tag
#   --optimize-registry REGISTRY           Optimize image registry (ignored when enable-optimize is false)
#   --optimize-repository REPOSITORY       Optimize image repository (ignored when enable-optimize is false)
#   --optimize-tag TAG                     Optimize image tag (ignored when enable-optimize is false)
#   --identity-tag TAG            Explicit Identity image tag
#   --connectors-tag TAG          Explicit Connectors image tag
#   --load-test-load VALUE        Extra --set args for the load-tester Helm chart
#   --platform-helm-values VALUE  Extra --set args for the platform Helm chart
#   --load-test-setup-helm-values VALUE  Extra --set args for the load-test-setup Helm chart
#   --scenario SCENARIO           Workload scenario to run

set -euo pipefail

namespace="$1"
install_target="$2"
image_tag="$3"
image_repository="$4"

# The stable/8.10 copy of camunda-load-test.yml runs this script from main with the previous
# argument format: image-registry, camunda-repo and optimize-repo as positional arguments after
# image-repository (13 positional arguments). The 11th argument is true or false in that format,
# and an option or missing in the current one. Remove this block when that copy uses the options.
legacy_arguments=false
if [[ "${11:-}" == "true" || "${11:-}" == "false" ]]; then
  legacy_arguments=true
  legacy_image_registry="$5"
  legacy_camunda_repo="$6"
  legacy_optimize_repo="$7"
  set -- "$1" "$2" "$3" "$4" "${@:8}"
fi

perform_read_benchmarks="$5"
enable_optimize="$6"
enable_optimize_metrics="$7"
metrics_exporter_built="$8"
enable_chaos="$9"
physical_tenant_count="${10}"
shift 10

orchestration_registry=""
orchestration_repository=""
orchestration_tag=""
optimize_registry=""
optimize_repository=""
optimize_tag=""
identity_tag=""
connectors_tag=""
load_test_load=""
platform_helm_values=""
load_test_setup_helm_values=""
scenario=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --orchestration-registry) orchestration_registry="$2"; shift 2 ;;
    --orchestration-repository) orchestration_repository="$2"; shift 2 ;;
    --orchestration-tag) orchestration_tag="$2"; shift 2 ;;
    --optimize-registry) optimize_registry="$2"; shift 2 ;;
    --optimize-repository) optimize_repository="$2"; shift 2 ;;
    --optimize-tag) optimize_tag="$2"; shift 2 ;;
    --identity-tag) identity_tag="$2"; shift 2 ;;
    --connectors-tag) connectors_tag="$2"; shift 2 ;;
    --load-test-load) load_test_load="$2"; shift 2 ;;
    --platform-helm-values) platform_helm_values="$2"; shift 2 ;;
    --load-test-setup-helm-values) load_test_setup_helm_values="$2"; shift 2 ;;
    --scenario) scenario="$2"; shift 2 ;;
    *) echo "::error::Unknown argument: $1" >&2; exit 1 ;;
  esac
done

# Previous format: --orchestration-tag only tells whether a Docker Hub image is used, the image
# tag of the orchestration image is image-tag, and Optimize is built from the same registry unless
# an Optimize tag is given.
if [[ "${legacy_arguments}" == "true" ]]; then
  legacy_orchestration_tag="${orchestration_tag}"
  orchestration_registry="${legacy_image_registry}"
  orchestration_repository="${legacy_camunda_repo}"
  orchestration_tag="${image_tag}"
  if [[ -z "${optimize_tag}" && -z "${legacy_orchestration_tag}" ]]; then
    optimize_registry="${legacy_image_registry}"
    optimize_repository="${legacy_optimize_repo}"
    optimize_tag="${image_tag}"
  fi
fi

cd "load-tests/setup/${namespace}"

# Build load test configuration
load_test_config="--set global.image.tag=${image_tag} \
                  --set global.image.repository=${image_repository} \
                  --set global.image.pullSecrets[0].name=harbor-registry"

# Only pass the value to turn read benchmarks off. This flag is rendered after the Makefile flags,
# so always passing it (including "true") would override the Makefile, which turns read benchmarks
# off for scenarios without secondary storage. When on, the chart default and the Makefile decide.
# The k6 data-read test reads from secondary storage, like the read benchmarks.
if [[ "${perform_read_benchmarks}" != "true" ]]; then
  load_test_config="$load_test_config --set global.performReadBenchmarks=false"
  load_test_config="$load_test_config --set k6.tests.data-read.enabled=false"
fi

# Enable the load-tester Optimize report-evaluation meter via chart extraConfig
# (binds to the Spring property load-tester.optimize.report-evaluation-enabled; off by default).
if [[ "${enable_optimize_metrics}" == "true" ]]; then
  load_test_config="$load_test_config --set global.extraConfig.load-tester.optimize.report-evaluation-enabled=true"
fi

# Append custom load test inputs
load_test_config="$load_test_config ${load_test_load}"

# Build platform configuration. Each image value is set only when provided, otherwise the value
# of the setup folder applies.
additional_platform_config=""
additional_load_test_setup_configuration="${load_test_setup_helm_values}"

add_platform_set() {
  if [[ -n "$2" ]]; then
    additional_platform_config+=" --set $1=$2"
  fi
}

add_platform_set orchestration.image.registry "${orchestration_registry}"
add_platform_set orchestration.image.repository "${orchestration_repository}"
add_platform_set orchestration.image.tag "${orchestration_tag}"

if [[ "${enable_optimize}" == "true" ]]; then
  add_platform_set optimize.image.registry "${optimize_registry}"
  add_platform_set optimize.image.repository "${optimize_repository}"
  add_platform_set optimize.image.tag "${optimize_tag}"
fi

# Append identity image config if a dedicated tag is provided
if [[ -n "${identity_tag}" ]]; then
  additional_platform_config+=" --set identity.image.tag=${identity_tag}"
fi

# Append connectors image config if a dedicated tag is provided
if [[ -n "${connectors_tag}" ]]; then
  additional_platform_config+=" --set connectors.image.tag=${connectors_tag}"
fi

# Append user-provided helm values if present
if [[ -n "${platform_helm_values}" ]]; then
  additional_platform_config+=" ${platform_helm_values}"
fi
additional_platform_config="${additional_platform_config# }"

# Pin the metrics-exporter image to the tag built by build-metrics-exporter-image.
# If the image has been skipped, keep using the "latest" tag, which
# is assuming to be always published by the dedicated CI running on
# the main branch.
if [[ "${metrics_exporter_built}" == "true" ]]; then
  additional_load_test_setup_configuration+=" --set metricsExporter.image.tag=${image_tag}"
fi

make "${install_target}" \
  scenario="${scenario}" \
  chaos="${enable_chaos}" \
  physical_tenant_count="${physical_tenant_count}" \
  additional_platform_configuration="$additional_platform_config" \
  additional_load_test_configuration="$load_test_config" \
  additional_load_test_setup_configuration="$additional_load_test_setup_configuration"

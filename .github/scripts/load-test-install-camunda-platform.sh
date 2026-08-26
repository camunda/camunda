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
#          <image-repository> <image-registry> <camunda-repo> <optimize-repo> \
#          <perform-read-benchmarks> <enable-optimize> <enable-optimize-metrics> \
#          <metrics-exporter-built> <enable-chaos> <physical-tenant-count> [OPTIONS]
#
#   namespace                  Load-test namespace (also the setup folder name)
#   install-target             Make target to run (install or install-stable)
#   image-tag                  Calculated image tag for platform/starter/worker images
#   image-repository           Repository for the load-tester (starter/worker) images
#   image-registry             Registry for the orchestration/optimize images
#   camunda-repo               Repository for the orchestration image
#   optimize-repo              Repository for the optimize image (custom build path)
#   perform-read-benchmarks    Run continuous read benchmarks (true/false)
#   enable-optimize            Whether Optimize is part of this deployment (true/false)
#   enable-optimize-metrics    Enable the load-tester Optimize report-evaluation meter (true/false)
#   metrics-exporter-built     Whether build-metrics-exporter-image ran this run (true/false)
#   enable-chaos               Enable chaos-killer (true/false)
#   physical-tenant-count      Number of extra physical tenants (pt1..ptN) to deploy (0 = disabled)
#
# Options (all optional, empty by default):
#   --optimize-tag TAG            Explicit Optimize image tag (empty to use the built image)
#   --orchestration-tag TAG       Docker Hub orchestration tag (empty when using a custom build)
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
image_registry="$5"
camunda_repo="$6"
optimize_repo="$7"
perform_read_benchmarks="$8"
enable_optimize="$9"
enable_optimize_metrics="${10}"
metrics_exporter_built="${11}"
enable_chaos="${12}"
physical_tenant_count="${13}"
shift 13

optimize_tag=""
orchestration_tag=""
identity_tag=""
connectors_tag=""
load_test_load=""
platform_helm_values=""
load_test_setup_helm_values=""
scenario=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --optimize-tag) optimize_tag="$2"; shift 2 ;;
    --orchestration-tag) orchestration_tag="$2"; shift 2 ;;
    --identity-tag) identity_tag="$2"; shift 2 ;;
    --connectors-tag) connectors_tag="$2"; shift 2 ;;
    --load-test-load) load_test_load="$2"; shift 2 ;;
    --platform-helm-values) platform_helm_values="$2"; shift 2 ;;
    --load-test-setup-helm-values) load_test_setup_helm_values="$2"; shift 2 ;;
    --scenario) scenario="$2"; shift 2 ;;
    *) echo "::error::Unknown argument: $1" >&2; exit 1 ;;
  esac
done

cd "load-tests/setup/${namespace}"

# Build load test configuration
load_test_config="--set global.image.tag=${image_tag} \
                  --set global.image.repository=${image_repository} \
                  --set global.image.pullSecrets[0].name=harbor-registry \
                  --set global.performReadBenchmarks=${perform_read_benchmarks}"

# Enable the load-tester Optimize report-evaluation meter via chart extraConfig
# (binds to the Spring property load-tester.optimize.report-evaluation-enabled; off by default).
if [[ "${enable_optimize_metrics}" == "true" ]]; then
  load_test_config="$load_test_config --set global.extraConfig.load-tester.optimize.report-evaluation-enabled=true"
fi

# Append custom load test inputs
load_test_config="$load_test_config ${load_test_load}"

# Resolve image registry/repository: Docker Hub when orchestration-tag is set, internal registry otherwise
# optimize_repo is only consumed in the custom build path where orchestration-tag is empty

# Build platform configuration
additional_platform_config="--set orchestration.image.registry=${image_registry} \
  --set orchestration.image.repository=${camunda_repo} \
  --set orchestration.image.tag=${image_tag}"

additional_load_test_setup_configuration="${load_test_setup_helm_values}"

# Append optimize image config: optimize-tag wins if set, otherwise custom builds use internal registry
if [[ "${enable_optimize}" == "true" ]]; then
  if [[ -n "${optimize_tag}" ]]; then
    # Explicit optimize tag provided: use it directly (Docker Hub image)
    additional_platform_config+=" --set optimize.image.tag=${optimize_tag}"
  elif [[ -z "${orchestration_tag}" ]]; then
    # Custom build without explicit optimize tag: use the built image from internal registry
    additional_platform_config+=" \
      --set optimize.image.registry=${image_registry} \
      --set optimize.image.repository=${optimize_repo} \
      --set optimize.image.tag=${image_tag}"
  fi
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

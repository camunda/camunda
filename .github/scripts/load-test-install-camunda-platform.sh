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
# Inputs are read from environment variables, so that a variable can be added to the workflow
# without breaking a script that does not know it yet. Required variables must be set, possibly
# to an empty string; optional variables default to empty.
#
# Required:
#   NAMESPACE                  Load-test namespace (also the setup folder name)
#   INSTALL_TARGET             Make target to run (install or install-stable)
#   CALCULATED_IMAGE_TAG       Calculated image tag for platform/starter/worker images
#   IMAGE_REPOSITORY           Repository for the load-tester (starter/worker) images
#   RESOLVED_IMAGE_REGISTRY    Registry for the orchestration/optimize images
#   CAMUNDA_REPO               Repository for the orchestration image
#   OPTIMIZE_REPO              Repository for the optimize image (custom build path)
#   PERFORM_READ_BENCHMARKS    Run continuous read benchmarks (true/false)
#   ENABLE_OPTIMIZE            Whether Optimize is part of this deployment (true/false)
#   ENABLE_OPTIMIZE_METRICS    Enable the load-tester Optimize report-evaluation meter (true/false)
#   METRICS_EXPORTER_BUILT     Whether build-metrics-exporter-image ran this run (true/false)
#   ENABLE_CHAOS               Enable chaos-killer (true/false)
#   PHYSICAL_TENANT_COUNT      Number of extra physical tenants (pt1..ptN) to deploy (0 = disabled)
#
# Optional:
#   OPTIMIZE_TAG                  Explicit Optimize image tag (empty to use the built image)
#   ORCHESTRATION_TAG             Docker Hub orchestration tag (empty when using a custom build)
#   IDENTITY_TAG                  Explicit Identity image tag
#   CONNECTORS_TAG                Explicit Connectors image tag
#   LOAD_TEST_LOAD                Extra --set args for the load-tester Helm chart
#   PLATFORM_HELM_VALUES          Extra --set args for the platform Helm chart
#   LOAD_TEST_SETUP_HELM_VALUES   Extra --set args for the load-test-setup Helm chart
#   SCENARIO                      Workload scenario to run

set -euo pipefail

namespace="${NAMESPACE?}"
install_target="${INSTALL_TARGET?}"
image_tag="${CALCULATED_IMAGE_TAG?}"
image_repository="${IMAGE_REPOSITORY?}"
image_registry="${RESOLVED_IMAGE_REGISTRY?}"
camunda_repo="${CAMUNDA_REPO?}"
optimize_repo="${OPTIMIZE_REPO?}"
perform_read_benchmarks="${PERFORM_READ_BENCHMARKS?}"
enable_optimize="${ENABLE_OPTIMIZE?}"
enable_optimize_metrics="${ENABLE_OPTIMIZE_METRICS?}"
metrics_exporter_built="${METRICS_EXPORTER_BUILT?}"
enable_chaos="${ENABLE_CHAOS?}"
physical_tenant_count="${PHYSICAL_TENANT_COUNT?}"

optimize_tag="${OPTIMIZE_TAG:-}"
orchestration_tag="${ORCHESTRATION_TAG:-}"
identity_tag="${IDENTITY_TAG:-}"
connectors_tag="${CONNECTORS_TAG:-}"
load_test_load="${LOAD_TEST_LOAD:-}"
platform_helm_values="${PLATFORM_HELM_VALUES:-}"
load_test_setup_helm_values="${LOAD_TEST_SETUP_HELM_VALUES:-}"
scenario="${SCENARIO:-}"

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

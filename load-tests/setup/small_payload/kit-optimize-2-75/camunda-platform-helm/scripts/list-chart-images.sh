#!/bin/bash
set -euo pipefail

#
# List chart images.
#

helm template --skip-tests camunda "${CHART_SOURCE}" --version "${CHART_VERSION}" \
    --values "${CHART_VALUES_DIR}test/integration/scenarios/chart-full-setup/values-integration-test-ingress-keycloak.yaml" 2> /dev/null |
        tr -d "\"'" | awk '/image:/{gsub(/^(camunda|bitnami)/, "docker.io/&", $2); printf "- %s\n", $2}'

#!/bin/bash

set -o errexit -o nounset -o pipefail
IFS=$'\n\t'

NAMESPACE="${1:?"Namespace is required as the first argument"}"

echo "# Load test"
echo
echo "The test has been set up successfully. You can observe it [here](https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace=${NAMESPACE})"
echo

echo "## Installed Helm Releases (namespace: \`${NAMESPACE}\`)"
echo
echo '```'
helm list -n "${NAMESPACE}"
echo '```'
echo

echo "### Helm Release Values"
echo
for release in $(helm list -n "${NAMESPACE}" -q); do
    echo "### Helm Release \`${release}\` values"
    echo '```yaml'
    helm get values "${release}" -n "${NAMESPACE}"
    echo '```'
    echo
done

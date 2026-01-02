#!/bin/bash
set -euo pipefail

#
# Check if latest chart version matches the latest release.
#

helm repo add camunda https://helm.camunda.io
helm repo update

chart_main_dir=$(ls -d1 charts/camunda-platform-8* | tail -n1)
chart_main_version="$(yq '.version' ${chart_main_dir}/Chart.yaml)"
components_versions=$(helm template camunda/camunda-platform | awk -F'helm.sh/chart: ' '/helm.sh\/chart:/ {print $2}' | sort | uniq)

components_count=2

print_components_versions() {
    echo "Current versions from Camunda Helm repo:"
    printf -- "- %s\n" ${components_versions}
}

if [[ $(echo "${components_versions}" | grep -c "${chart_main_version}") -lt "${components_count}" ]]; then
    echo '[ERROR] Not all Camunda components are updated!'
    print_components_versions
    exit 1
fi

echo '[INFO] All Camunda components are updated.'
print_components_versions
exit 0

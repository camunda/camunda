#!/bin/bash

# This snippet helps to download all Docker images used in the latest Camunda 8 Helm chart
# to push them to a private Docker registry. The registry name could be easily customized in the values file
# under "global.image.registry".

set -euo pipefail

helm repo add camunda https://helm.camunda.io
helm repo update

camunda_platform_images () {
    helm template camunda/camunda-platform --skip-tests | \
    awk '/image:/ {gsub(/"/, "", $2); print $2}' | sort | uniq
}

camunda_platform_images | while read image_name; do
    # Replace forward slash to easy manage image files on the different operating systems.
    image_file="$(echo ${image_name} | tr '/' '_').tar"

    echo -e "\n\n## Image: ${image_name}"
    echo "File: ${image_file}"

    docker pull "${image_name}"
    docker save "${image_name}" -o "${image_file}"
done
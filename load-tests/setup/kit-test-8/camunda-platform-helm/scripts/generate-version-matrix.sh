#!/bin/bash

set -euox pipefail

# Check dependencies.
dep_names="awk git gomplate helm jq tr yq"
for dep_name in ${dep_names}; do
    test -n "$(which ${dep_name})" || (
      echo "Missing dependency: ${dep_name}";
      echo "Dependencies list: ${dep_names}";
      exit 1
    )
done

CHART_NAME="${CHART_NAME:-camunda-platform}"
CHART_DIR="${CHART_DIR:-$(ls -d1 charts/camunda-platform-8.* | sort -V | tail -n1)}"
CHART_SOURCE="${CHART_SOURCE:-camunda/$CHART_NAME}"
# Add unsupported Camunda version to reduce generation time.
CAMUNDA_APPS_UNSUPPORTED_VERSIONS_REGEX='(1.*|8.[0123])'

# Update Helm and Git repos to get the latest versions.
init_updates () {
    helm repo update > /dev/null
    git fetch origin tag "${CHART_NAME}-*"
}

# Get all Helm chart released versions grouped by chart 'appVersion' (Camunda release like 8.5).
get_versions_formatted () {
    helm search repo "${CHART_SOURCE}" --devel --versions --output json |
      jq 'group_by(.app_version) | map({
        "app": .[0].app_version | .[:-2], "charts": map(.version)
        }) | reverse'
}

# Get only supported Camunda version to reduce generation time.
get_versions_filtered () {
    get_versions_formatted |
      jq --arg VAR "${CAMUNDA_APPS_UNSUPPORTED_VERSIONS_REGEX}" \
        'map(select(.app | test($VAR) | not ))'
}

# Get all images used in a certain Helm chart.
get_chart_images () {
    chart_version="${1}"
    major_minor="$(echo "$CHART_DIR" | sed 's/charts\/camunda-platform-//g')"
    version_matrix_file="version-matrix/camunda-$major_minor/version-matrix.json"
    test -d "${CHART_DIR}" || CHART_DIR="$(ls -d1 charts/camunda-platform-8* | tail -n1)"
    test -f "${version_matrix_file}" || echo '[]' > "${version_matrix_file}"

    if [ ! -f "../released-charts.json" ]; then
      export RELEASED_CHARTS="$(helm search repo "${CHART_SOURCE}" --versions --output json)"
      echo $RELEASED_CHARTS > ../released-charts.json
    fi
    if ! $(cat ../released-charts.json | jq "any(.version == \"${chart_version}\")"); then
      export CHART_SOURCE="charts/camunda-platform-${major_minor}"
    fi

    # Check if the chart data already in version-matrix.json and add it if needed.
    if ! $(jq "any(.chart_version == \"${chart_version}\")" ${version_matrix_file}); then
      # Generateing the chart version data.
      helm repo update > /dev/null
      chart_images="$(
        helm template --skip-tests camunda "${CHART_SOURCE}" --version "${chart_version}" \
          --values "${CHART_DIR}/test/integration/scenarios/chart-full-setup/values-integration-test-ingress-keycloak.yaml" 2> /dev/null |
        tr -d "\"'" | awk '/image:/{gsub(/^(camunda|bitnami)/, "docker.io/&", $2); printf "%s\n", $2}' |
        sort | uniq;
      )"
      chart_images_json="$(echo -e "$chart_images" | jq -R | jq -sc)"
      output_json="$(cat ${version_matrix_file} | jq -r ". + [{ \"chart_version\": \"${chart_version}\", \"chart_images\": ${chart_images_json}}]")"
      echo "$output_json" > "${version_matrix_file}"
    fi

    # Print chart images from version-matrix.json file.
    version_matrix_images="$(cat ${version_matrix_file} | jq -r ".[] | select(.chart_version==\"$chart_version\").chart_images[]" | awk '{gsub(/\x1e/, ""); print}')"
    printf -- "- %s\n" $(echo -e "$version_matrix_images")
}

# Get Helm CLI version based on the asdf .tool-versions file.
get_helm_cli_version () {
    chart_ref_name="${CHART_REF_NAME:-$1}"
    (git show ${chart_ref_name}:.tool-versions 2> /dev/null | awk '/helm /{printf $2}') ||
      echo -n ''
}

# Generate version matrix index for all Camunda versions with corresponding charts.
generate_version_matrix_index () {
    export ALL_CAMUNDA_VERSIONS="$(get_versions_formatted)" \

    export CHART_SOURCE="${CHART_DIR}"
    export CHART_REF_NAME="$(git branch --show-current)"
    export UNRELEASED_VERSION="{
      \"app\": \"$(yq '.appVersion | sub("\..$", "")' "${CHART_SOURCE}/Chart.yaml")\",
      \"charts\": [
        \"$(yq '.version' "${CHART_SOURCE}/Chart.yaml")\"
      ]
    }"

    # merge unreleased version into ALL_CAMUNDA_VERSIONS
    merged_versions="$(jq -r -c --slurp 'flatten | group_by(.app) | map({ app: .[0].app, charts: (map(.charts[]) | unique) })' <(echo "$ALL_CAMUNDA_VERSIONS") <(echo "$UNRELEASED_VERSION"))"

    # Sort versions using the Go script to handle semantic versioning properly
    # Try system go first, then asdf go
    if [ -x "/usr/bin/go" ]; then
        GO_BIN="/usr/bin/go"
    else
        GO_BIN="$(which go 2>/dev/null)"
    fi
    export ALL_CAMUNDA_VERSIONS="$(echo "$merged_versions" | "$GO_BIN" run scripts/sort-version-matrix.go | jq -c '.')"

    echo "$ALL_CAMUNDA_VERSIONS"

    gomplate \
      --config scripts/templates/version-matrix/.gomplate.yaml \
      --datasource versions=env:///ALL_CAMUNDA_VERSIONS?type=application/array+json \
      --file scripts/templates/version-matrix/VERSION-MATRIX-INDEX.md.tpl |
        tee "version-matrix/README.md"
}

# Generate a version matrix for a certain Camunda version.
generate_version_matrix_single () {
    SUPPORTED_CAMUNDA_VERSION_DATA="${1}" \
    gomplate \
      --config scripts/templates/version-matrix/.gomplate.yaml \
      --datasource release=env:///SUPPORTED_CAMUNDA_VERSION_DATA?type=application/json \
      --file scripts/templates/version-matrix/VERSION-MATRIX-RELEASE.md.tpl
}

# Generate a version matrix for each released and supported Camunda version.
# It's still possible to generate the version matrix for all released Camunda versions by setting 
# CAMUNDA_APPS_UNSUPPORTED_VERSIONS_REGEX to "any" so it will match all versions even unsupported ones.
generate_version_matrix_released () {
    get_versions_filtered | jq -c '.[]' | while read SUPPORTED_CAMUNDA_VERSION_DATA; do
        SUPPORTED_CAMUNDA_VERSION="$(echo ${SUPPORTED_CAMUNDA_VERSION_DATA} | jq -r '.app')"
        mkdir -p "version-matrix/camunda-${SUPPORTED_CAMUNDA_VERSION}"
        echo -e "#\n# Generating version matrix for Camunda ${SUPPORTED_CAMUNDA_VERSION}\n#"
        generate_version_matrix_single "${SUPPORTED_CAMUNDA_VERSION_DATA}" | tee \
          "version-matrix/camunda-${SUPPORTED_CAMUNDA_VERSION}/README.md"
    done
}

# Generate a version matrix from the unreleased chart using the local git repo.
generate_version_matrix_unreleased () {
    export CHART_SOURCE="${CHART_DIR}"
    export CHART_REF_NAME="$(git branch --show-current)"
    CHART_VERSION_LOCAL="{
      \"app\": \"$(echo $(yq '.appVersion | sub("\..$", "")' "${CHART_SOURCE}/Chart.yaml"))\",
      \"charts\": [
        \"$(yq '.version' "${CHART_SOURCE}/Chart.yaml")\"
      ]
    }"

    export CHART_SOURCE="camunda/camunda-platform"
    SUPPORTED_CAMUNDA_VERSION="$(
    get_versions_filtered | jq -c '.[]' | while read SUPPORTED_CAMUNDA_VERSION_DATA; do
      if [[ "$(jq -r -c --slurp '.[0].app == .[1].app' <(echo "$SUPPORTED_CAMUNDA_VERSION_DATA") <(echo "$CHART_VERSION_LOCAL") )" == "true" ]]; then
        echo "$SUPPORTED_CAMUNDA_VERSION_DATA"
      fi
    done | tail -n1)"

    # merge unreleased version into SUPPORTED_CAMUNDA_VERSION
    merged_versions="$(jq -r -c --slurp 'flatten | group_by(.app) | map({ app: .[0].app, charts: (map(.charts[]) | unique) })' <(echo "$SUPPORTED_CAMUNDA_VERSION") <(echo "$CHART_VERSION_LOCAL"))"

    # Sort versions using the Go script to handle semantic versioning properly
    # Try system go first, then asdf go
    if [ -x "/usr/bin/go" ]; then
        GO_BIN="/usr/bin/go"
    else
        GO_BIN="$(which go 2>/dev/null)"
    fi
    export ALL_CAMUNDA_VERSIONS="$(echo "$merged_versions" | "$GO_BIN" run scripts/sort-version-matrix.go | jq -c '.')"
    SINGLE_VERSION="$(echo ${ALL_CAMUNDA_VERSIONS} | jq -c '.[0]')"

    generate_version_matrix_single "${SINGLE_VERSION}" | tee \
      "version-matrix/camunda-$(echo ${SINGLE_VERSION} | jq -r '.app')/README.md"
}

# Print help message.
print_help () {
    cat <<- EOF
Usage: $0 [OPTION]
$(grep -Eo -- "  \-\-.*\)" $0 | tr -d ')')
EOF
    exit 1
}

# Handling if no script args are provided.
test -z "${1:-}" && print_help

# Parse script input args.
while test -n "${1:-}"; do
    case "${1:-}" in
        --init)
          init_updates
          ;;
        --index)
          generate_version_matrix_index
          ;;
        --released)
          generate_version_matrix_released
          ;;
        --unreleased)
          generate_version_matrix_unreleased
          ;;
        --helm-cli-version)
          test -n "${2:-}" || (
            echo "[ERROR] Git ref name is needed as an arg for this option";
            exit 1
          )
          get_helm_cli_version "${2}"
          shift
          ;;
        --chart-images-camunda)
          test -n "${3:-}" || (
            echo "[ERROR] Chart dir and Helm chart version are needed as an arg for this option";
            exit 1
          )
          CHART_DIR="${2}" get_chart_images "${3}" | grep "camunda"
          shift 2
          ;;
        --chart-images-non-camunda)
          test -n "${3:-}" || (
            echo "[ERROR] Chart dir and Helm chart version are needed as an arg for this option";
            exit 1
          )
          CHART_DIR="${2}" get_chart_images "${3}" | grep -v "camunda"
          shift 2
          ;;
        *)
          print_help
          exit 1
          ;;
    esac
    shift
done

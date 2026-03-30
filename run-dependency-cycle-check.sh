#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${ROOT_DIR}"

if ! command -v yq >/dev/null 2>&1; then
        echo "yq is required (mikefarah yq v4 with XML support)" >&2
        exit 1
fi

maven_config_backup="${ROOT_DIR}/.mvn/maven.config.fossa.bak"
root_pom_backup="${ROOT_DIR}/pom.xml.fossa.bak"
parent_pom_backup="${ROOT_DIR}/parent/pom.xml.fossa.bak"
bom_pom_backup="${ROOT_DIR}/bom/pom.xml.fossa.bak"
optimize_pom_backup="${ROOT_DIR}/optimize/pom.xml.fossa.bak"

cleanup() {
        if [[ -f "${maven_config_backup}" ]]; then
                mv "${maven_config_backup}" "${ROOT_DIR}/.mvn/maven.config"
        fi
        if [[ -f "${root_pom_backup}" ]]; then
                mv "${root_pom_backup}" "${ROOT_DIR}/pom.xml"
        fi
        if [[ -f "${parent_pom_backup}" ]]; then
                mv "${parent_pom_backup}" "${ROOT_DIR}/parent/pom.xml"
        fi
        if [[ -f "${bom_pom_backup}" ]]; then
                mv "${bom_pom_backup}" "${ROOT_DIR}/bom/pom.xml"
        fi
        if [[ -f "${optimize_pom_backup}" ]]; then
                mv "${optimize_pom_backup}" "${ROOT_DIR}/optimize/pom.xml"
        fi
}
trap cleanup EXIT

if [[ -f "${ROOT_DIR}/.mvn/maven.config" ]]; then
        mv "${ROOT_DIR}/.mvn/maven.config" "${maven_config_backup}"
fi
printf '%s\n' "-Dquickly=true" "-DskipQaBuild=true" | tee "${ROOT_DIR}/.mvn/maven.config" >/dev/null

cp "${ROOT_DIR}/pom.xml" "${root_pom_backup}"
cp "${ROOT_DIR}/parent/pom.xml" "${parent_pom_backup}"
cp "${ROOT_DIR}/bom/pom.xml" "${bom_pom_backup}"
cp "${ROOT_DIR}/optimize/pom.xml" "${optimize_pom_backup}"

# The bom/pom.xml must be the actual root, otherwise, FOSSA won't detect the hierarchy correctly
yq -i '.project.modules.module += "./.."' "${ROOT_DIR}/parent/pom.xml"
yq -i '.project.modules.module += "./../parent"' "${ROOT_DIR}/bom/pom.xml"
# Remove bom and parent from the list of modules of ./pom.xml
yq -i 'del(.project.modules.module[] | select(. == "bom" or . == "parent"))' "${ROOT_DIR}/pom.xml"
# Remove optimize/qa module as a bug in FOSSA prevents scope filtering
yq -i 'del(.project.modules.module[] | select(. == "qa"))' "${ROOT_DIR}/optimize/pom.xml"

./mvnw com.github.ferstl:depgraph-maven-plugin:4.0.1:aggregate \
        -f "./pom.xml" \
        -DgraphFormat=text \
        -DmergeScopes \
        -DreduceEdges=false \
        -DshowVersions=true \
        -DshowGroupIds=true \
        -DshowOptional=true >depgraph.txt

./scripts/generate-fossa-deps.sh --depgraph-file depgraph.txt

fossa analyze --exclude-manifest-strategies --fossa-deps-file ./fossa-deps.yml
fossa test

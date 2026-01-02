#!/bin/bash
set -euo pipefail

#
# Check if values-latest.yaml files contain the actual latest image versions for all components.
#
# This script validates that renovatebot successfully updated the values-latest.yaml files
# by comparing the tags in those files with the latest available tags in Docker registries.
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track overall status
OVERALL_STATUS=0

# Function to get the latest Docker image tag from Docker Hub
# Args: $1 = repository (e.g., camunda/console), $2 = version pattern (e.g., 8.7)
get_latest_docker_tag() {
    local repo="$1"
    local version_pattern="$2"
    local registry_url="https://hub.docker.com"
    
    # Extract namespace and image name
    local namespace
    local image
    namespace=$(echo "$repo" | cut -d'/' -f1)
    image=$(echo "$repo" | cut -d'/' -f2)
    
    # Query Docker Hub API for tags
    local api_url="${registry_url}/v2/repositories/${namespace}/${image}/tags?page_size=100"
    
    # Get tags and filter for the version pattern
    local latest_tag
    local response
    
    # Fetch from Docker Hub API
    response=$(curl -s -f "$api_url" 2>&1)
    local curl_status=$?
    
    if [[ $curl_status -ne 0 ]]; then
        echo "ERROR: Failed to fetch tags from Docker Hub for $repo" >&2
        return 1
    fi
    
    # Parse JSON and filter tags
    latest_tag=$(echo "$response" | \
        jq -r '.results[].name' 2>/dev/null | \
        grep -E "^${version_pattern}\.[0-9]+$" | \
        sort -V | \
        tail -n1)
    
    if [[ -z "$latest_tag" ]]; then
        echo "ERROR: No matching tags found for pattern ${version_pattern}.x" >&2
        return 1
    fi
    
    echo "$latest_tag"
}

# Function to extract image tags from values-latest.yaml
# Args: $1 = chart path
extract_image_tags() {
    local chart_path="$1"
    local values_file="${chart_path}/values-latest.yaml"
    
    if [[ ! -f "$values_file" ]]; then
        echo "ERROR: $values_file not found" >&2
        return 1
    fi
    
    # Extract all image repositories and tags with repository field
    yq eval -o=json '.. | select(has("image")) | .image | select(has("repository") and has("tag")) | {"repo": .repository, "tag": .tag}' "$values_file" | \
        jq -r 'select(.tag != null and .tag != "" and .tag != "SNAPSHOT" and (.tag | test("^[0-9]+\\.[0-9]+")) ) | "\(.repo)|\(.tag)"'
    
    # Also extract webModeler image which has repository in renovate comment
    # Extract repository from renovate comment and tag from the image section
    local webmodeler_tag
    webmodeler_tag=$(yq eval '.webModeler.image.tag' "$values_file" 2>/dev/null)
    if [[ -n "$webmodeler_tag" ]] && [[ "$webmodeler_tag" != "null" ]] && [[ "$webmodeler_tag" =~ ^[0-9]+\.[0-9]+ ]]; then
        # Extract repository from renovate comment
        local webmodeler_repo
        webmodeler_repo=$(grep -A5 "webModeler:" "$values_file" | grep "depName=" | sed 's/.*depName=\([^[:space:]]*\).*/\1/')
        if [[ -n "$webmodeler_repo" ]]; then
            # Note: For older versions (8.2-8.5), webModeler uses a private registry
            # (registry.camunda.cloud) which we cannot query. Skip these.
            if [[ "$webmodeler_repo" != *"registry.camunda.cloud"* ]]; then
                echo "${webmodeler_repo}|${webmodeler_tag}"
            fi
        fi
    fi
}

# Function to validate a single chart version
# Args: $1 = chart directory name (e.g., camunda-platform-8.7)
validate_chart() {
    local chart_dir="$1"
    local chart_path="${REPO_ROOT}/charts/${chart_dir}"
    local chart_version
    chart_version=$(echo "$chart_dir" | sed 's/camunda-platform-\([0-9]\+\.[0-9]\+\)/\1/')
    
    echo ""
    echo -e "${YELLOW}Validating ${chart_dir}...${NC}"
    
    if [[ ! -d "$chart_path" ]]; then
        echo -e "${RED}ERROR: Chart directory not found: $chart_path${NC}"
        return 1
    fi
    
    local validation_failed=0
    
    # Extract image tags from values-latest.yaml
    while IFS='|' read -r repo tag; do
        # Skip non-Camunda images for now (they have different versioning schemes)
        if [[ "$repo" != camunda/* ]]; then
            echo "  Skipping non-Camunda image: $repo:$tag"
            continue
        fi
        
        # Skip SNAPSHOT versions as they are always the latest
        if [[ "$tag" == *"SNAPSHOT"* ]] || [[ "$tag" == *"-alpha"* ]] || [[ "$tag" == *"-latest"* ]]; then
            echo "  Skipping snapshot/alpha/latest tag: $repo:$tag"
            continue
        fi
        
        # Extract the minor version from the tag (e.g., 8.7 from 8.7.16)
        local tag_minor_version
        tag_minor_version=$(echo "$tag" | sed 's/^\([0-9]\+\.[0-9]\+\).*/\1/')
        
        # Only check if the tag matches the chart version
        if [[ "$tag_minor_version" == "$chart_version" ]]; then
            echo "  Checking $repo:$tag (chart version: $chart_version)"
            
            # Get the latest tag from Docker Hub
            local latest_tag
            latest_tag=$(get_latest_docker_tag "$repo" "$chart_version" 2>&1)
            
            # Check if latest_tag contains an error message
            if [[ "$latest_tag" == ERROR:* ]] || [[ -z "$latest_tag" ]]; then
                echo -e "    ${YELLOW}WARNING: Could not verify latest tag for $repo (${latest_tag:-no data})${NC}"
                # Don't fail validation for API errors - these could be temporary
                continue
            fi
            
            # Compare versions
            if [[ "$tag" == "$latest_tag" ]]; then
                echo -e "    ${GREEN}✓ Up-to-date: $tag${NC}"
            else
                echo -e "    ${RED}✗ Outdated: $tag (latest: $latest_tag)${NC}"
                validation_failed=1
            fi
        fi
    done < <(extract_image_tags "$chart_path")
    
    if [[ $validation_failed -eq 0 ]]; then
        echo -e "${GREEN}✓ ${chart_dir} validation passed${NC}"
        return 0
    else
        echo -e "${RED}✗ ${chart_dir} validation failed${NC}"
        return 1
    fi
}

# Main function
main() {
    echo "=========================================="
    echo "Validating values-latest.yaml files"
    echo "=========================================="
    
    # Check required tools
    for tool in yq jq curl; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            echo -e "${RED}ERROR: Required tool '$tool' is not installed${NC}"
            exit 1
        fi
    done
    
    # Get all chart directories
    local charts=()
    if [[ $# -gt 0 ]]; then
        # Use provided chart versions
        for version in "$@"; do
            charts+=("camunda-platform-${version}")
        done
    else
        # Find all chart directories
        while IFS= read -r chart_dir; do
            charts+=("$(basename "$chart_dir")")
        done < <(find "${REPO_ROOT}/charts" -maxdepth 1 -type d -name "camunda-platform-8.*" | sort)
    fi
    
    # Validate each chart
    for chart in "${charts[@]}"; do
        if ! validate_chart "$chart"; then
            OVERALL_STATUS=1
        fi
    done
    
    echo ""
    echo "=========================================="
    if [[ $OVERALL_STATUS -eq 0 ]]; then
        echo -e "${GREEN}✓ All validations passed${NC}"
    else
        echo -e "${RED}✗ Some validations failed${NC}"
    fi
    echo "=========================================="
    
    exit $OVERALL_STATUS
}

# Run main function with all arguments
main "$@"

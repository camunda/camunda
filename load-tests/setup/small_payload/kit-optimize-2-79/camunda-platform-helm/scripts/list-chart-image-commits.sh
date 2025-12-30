#!/bin/bash

#
# List git commits for each Docker image used in the chart.
# Extracts revision information from Docker image labels using skopeo (no pull required).
#

set -euo pipefail

# Function to extract git commit from Docker image labels
get_image_commit() {
    local image=$1
    local image_name
    local commit
    
    # Extract image name, removing @sha256 digest if present
    image_name=$(echo "$image" | sed 's/@sha256:.*//' | cut -d':' -f1 | awk -F'/' '{print $NF}')
    
    # Rename camunda/camunda to orchestration for internal naming
    if [[ "$image_name" == "camunda" ]]; then
        image_name="orchestration"
    fi
    
    # Use skopeo to inspect image without pulling it
    # Try to get the org.opencontainers.image.revision label
    commit=$(skopeo inspect "docker://${image}" 2>/dev/null | jq -r '.Labels["org.opencontainers.image.revision"] // ""' || echo "")
    
    # If commit is empty or null, mark as N/A
    if [[ -z "$commit" || "$commit" == "null" ]]; then
        commit="N/A"
    fi
    
    echo "$image_name|$commit"
}

# Function to format output as markdown table
format_as_table() {
    echo ""
    echo "## Docker Image Git Commits"
    echo ""
    echo "| Component | Git Commit |"
    echo "|-----------|------------|"
    
    # Read from stdin and format as table
    while IFS='|' read -r component commit; do
        # Truncate commit to 12 characters if it's a full SHA
        if [[ ${#commit} -gt 12 && "$commit" != "N/A" ]]; then
            commit="${commit:0:12}"
        fi
        echo "| $component | \`$commit\` |"
    done | sort -u
    
    echo ""
}

# Main execution
main() {
    # Check if required tools are available
    for tool in skopeo jq kubectl; do
        if ! command -v "$tool" &> /dev/null; then
            echo "Warning: $tool is not installed. Skipping image commit extraction." >&2
            exit 0
        fi
    done
    
    # Get list of images from the deployed resources in the namespace
    echo "Extracting images from namespace: ${TEST_NAMESPACE:-default}" >&2
    
    images=$(kubectl get pods -n "${TEST_NAMESPACE:-default}" -o jsonpath='{.items[*].spec.containers[*].image}' 2>/dev/null | tr ' ' '\n' | sort -u)
    
    if [[ -z "$images" ]]; then
        echo "Warning: No images found in namespace ${TEST_NAMESPACE:-default}" >&2
        exit 0
    fi
    
    echo "Found $(echo "$images" | wc -l) unique images" >&2
    echo "" >&2
    
    # Process each image
    declare -A processed_images
    for image in $images; do
        # Skip if already processed (same image with different tags)
        # Remove @sha256 digest before extracting base name
        image_base=$(echo "$image" | sed 's/@sha256:.*//' | cut -d':' -f1 | awk -F'/' '{print $NF}')
        
        # Apply orchestration naming for deduplication too
        if [[ "$image_base" == "camunda" ]]; then
            image_base="orchestration"
        fi
        
        if [[ -n "${processed_images[$image_base]:-}" ]]; then
            continue
        fi
        processed_images[$image_base]=1
        
        echo "Processing: $image" >&2
        
        # Extract commit information using skopeo (no pull needed)
        get_image_commit "$image"
    done | format_as_table
}

# Run main function
main

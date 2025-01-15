#!/bin/bash

# Default values
operate_prefix=""
tasklist_prefix=""
new_prefix=""

# Help function
display_help() {
    echo "Usage: $0 [options]"
    echo
    echo "Options:"
    echo "  --operate <value>     Prefix defined for operate"
    echo "  --tasklist <value>    Prefix defined for tasklist"
    echo "  --prefix <value>      New prefix for harmonised indices"
    echo "  -h, --help            Display this help message."
    exit 0
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --operate)
            operate_prefix="$2"
            shift 2
            ;;
        --tasklist)
            tasklist_prefix="$2"
            shift 2
            ;;
        --prefix)
            new_prefix="$2"
            shift 2
            ;;
        -h|--help)
            display_help
            ;;
        *)
            echo "Unknown option: $1"
            display_help
            ;;
    esac
done

# Validate required arguments
if [[ -z $operate_prefix ]]; then
    echo "Error: --operate_prefix is required."
    display_help
fi

if [[ -z $tasklist_prefix ]]; then
    echo "Error: --tasklist_prefix is required."
    display_help
fi

if [[ -z $new_prefix ]]; then
    echo "Error: --prefix is required."
    display_help
fi

handle_response() {
    local response="$1"
    local status_code="${response: -3}"
    local body="${response%${status_code}}"

    if [ "$status_code" -eq 200 ]; then
        echo "Re-indexed $index"
    elif [ "$status_code" -eq 404 ]; then
        echo "Failed to reindex $index: ${body}"
    else
        echo "Unexpected status code: $status_code, ${body}"
    fi
}

reindex_indices() {
    local indices=("${!1}")
    local componentName="$2"
    local oldIndexPrefix="$3"

    for index in "${indices[@]}"; do
        response=$(curl -s -w "%{http_code}" -X POST "http://localhost:9200/_reindex" -H 'Content-Type: application/json' -d '{
          "source": {
            "index": "'$oldIndexPrefix'-'$index'_"
          },
          "dest": {
            "index": "'$new_prefix'-'$componentName'-'$index'_"
          }
        }')

        handle_response "$response"
    done
}

tasklist_indices=(
"form-8.4.0"
"task-8.5.0" 
"metric-8.3.0"
"import-position-8.2.0"
"draft-task-variable-8.3.0"
"task-variable-8.3.0"
"web-session-1.1.0"
)

reindex_indices tasklist_indices[@] "tasklist" "$tasklist_prefix"

operate_indices=(
"list-view-8.3.0"
"job-8.6.0"
"metric-8.3.0"
"import-position-8.3.0"
"batch-operation-1.0.0"
"process-8.3.0"
"decision-requirements-8.3.0"
"decision-8.3.0"
"event-8.3.0"
"variable-8.3.0"
"post-importer-queue-8.3.0"
"sequence-flow-8.3.0"
"message-8.5.0"
"decision-instance-8.3.0"
"incident-8.3.1"
"flownode-instance-8.3.1"
"operation-8.4.1"
"web-session-1.1.0"
)

reindex_indices operate_indices[@] "operate" "$operate_prefix"

all_indices=($(curl -s -X GET "http://localhost:9200/_cat/indices?h=index&format=json" | jq -r '.[].index'))

archived_indices=()

# get all archived indices for operate and tasklist
for index in "${all_indices[@]}"; do
    if [[ "$index" =~ ($operate_prefix|$tasklist_prefix).*_[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
        archived_indices+=("$index")
    fi
done

echo "found ${#archived_indices[@]} historic indices"
echo "Cloning indices to new prefix"

replace_prefix() {
  local index="$1"

  if [[ $index == "$tasklist_prefix"* ]]; then
    echo "${index/$tasklist_prefix/$new_prefix-tasklist}"
  elif [[ $index == "$operate_prefix"* ]]; then
    echo "${index/$operate_prefix/$new_prefix-operate}"
  else
    echo "$index"  # No replacement if no matching prefix
  fi
}

for index in "${archived_indices[@]}"; do
    echo ""
    new_index_name=$(replace_prefix $index)

    echo "Set $index to read-only"
    curl -s -o /dev/null -X PUT "http://localhost:9200/$index/_settings" -H "Content-Type: application/json" -d '{
      "settings": {
        "index.blocks.write": "true"
      }
    }'

    echo "Cloning $index into $new_index_name"

    new_alias_name=$(echo "${new_index_name%_*}_alias")

  curl -s -o /dev/null -X POST "http://localhost:9200/$index/_clone/$new_index_name" -H "Content-Type: application/json" \
-d @- <<-EOF
    {
      "settings": {
        "index.blocks.write": false,
        "number_of_replicas": 0
      },
      "aliases": {
        "$new_alias_name": {}
      }
    }
EOF
done

echo ""
echo "Migration to new custom prefix $new_prefix done"

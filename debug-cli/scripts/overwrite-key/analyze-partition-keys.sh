#!/usr/bin/env bash
# analyze-partition-keys.sh
# 
# Queries Elasticsearch to extract all flow node instance keys for a specific partition
# and saves them to a file for jump analysis.
#
# Required environment variables:
#   PARTITION_ID  - Partition ID to analyze (e.g., 1)
#
# Required if SKIP_DOWNLOAD is not set:
#   ES_URL        - Elasticsearch URL (e.g., http://localhost:9200)
#
# Optional environment variables:
#   BATCH_SIZE     - Keys per batch (default: 1000, max: 10000)
#   AFTER_KEY      - Resume from this key (for manual continuation)
#   SKIP_DOWNLOAD  - Set to "true" to skip download and only analyze existing file
#   JUMP_THRESHOLD - Minimum difference to consider a jump (default: 1000000)
#
# Output:
#   partition_${PARTITION_ID}_keys.txt - Tab-separated file with keys and timestamps
#
# Usage:
#   # Download and analyze:
#   ES_URL="http://localhost:9200" PARTITION_ID=1 ./analyze-partition-keys.sh
#   
#   # With custom batch size:
#   ES_URL="http://localhost:9200" PARTITION_ID=1 BATCH_SIZE=5000 ./analyze-partition-keys.sh
#   
#   # Resume from a specific key:
#   ES_URL="http://localhost:9200" PARTITION_ID=1 AFTER_KEY=2251799813999999 ./analyze-partition-keys.sh
#
#   # Skip download and only analyze existing file:
#   PARTITION_ID=1 SKIP_DOWNLOAD=true ./analyze-partition-keys.sh
#
#   # Custom jump threshold:
#   ES_URL="http://localhost:9200" PARTITION_ID=1 JUMP_THRESHOLD=5000000 ./analyze-partition-keys.sh
#
# Make executable with: chmod +x analyze-partition-keys.sh

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Set defaults for optional parameters
SKIP_DOWNLOAD="${SKIP_DOWNLOAD:-false}"

# Validate required parameters
if [ -z "${PARTITION_ID:-}" ]; then
  echo -e "${RED}Error: PARTITION_ID is required${NC}"
  echo "Usage: ES_URL=\"http://localhost:9200\" PARTITION_ID=1 $0"
  echo "   or: PARTITION_ID=1 SKIP_DOWNLOAD=true $0"
  exit 1
fi

if [ "$SKIP_DOWNLOAD" != "true" ] && [ -z "${ES_URL:-}" ]; then
  echo -e "${RED}Error: ES_URL is required (unless SKIP_DOWNLOAD=true)${NC}"
  echo "Usage: ES_URL=\"http://localhost:9200\" PARTITION_ID=1 $0"
  echo "   or: PARTITION_ID=1 SKIP_DOWNLOAD=true $0"
  exit 1
fi

# Set defaults for optional parameters
BATCH_SIZE="${BATCH_SIZE:-1000}"
AFTER_KEY="${AFTER_KEY:-}"
JUMP_THRESHOLD="${JUMP_THRESHOLD:-1000000}"

# Validate batch size
if [ "$BATCH_SIZE" -gt 10000 ]; then
  echo -e "${YELLOW}Warning: BATCH_SIZE cannot exceed 10000. Setting to 10000.${NC}"
  BATCH_SIZE=10000
fi

OUTPUT_FILE="partition_${PARTITION_ID}_keys.txt"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Partition Key Analysis${NC}"
echo -e "${BLUE}======================================${NC}"
if [ "$SKIP_DOWNLOAD" = "true" ]; then
  echo "Mode:              Analysis only (SKIP_DOWNLOAD)"
else
  echo "Elasticsearch URL: $ES_URL"
  echo "Batch Size:        $BATCH_SIZE"
fi
echo "Partition ID:      $PARTITION_ID"
echo "Jump Threshold:    $JUMP_THRESHOLD"
echo "Output File:       $OUTPUT_FILE"

if [ "$SKIP_DOWNLOAD" = "true" ]; then
  # Skip download mode - just check if file exists
  if [ ! -f "$OUTPUT_FILE" ]; then
    echo -e "${RED}======================================${NC}"
    echo -e "${RED}ERROR: File does not exist!${NC}"
    echo -e "${RED}======================================${NC}"
    echo "File: $OUTPUT_FILE"
    echo ""
    echo "The file must exist to use SKIP_DOWNLOAD=true."
    echo "Either download the data first, or remove SKIP_DOWNLOAD flag."
    exit 1
  fi
  echo -e "${GREEN}✓ Found existing file with $(wc -l < "$OUTPUT_FILE") keys${NC}"
else
  # Download mode - check if output file exists
  if [ -f "$OUTPUT_FILE" ]; then
    if [ -z "$AFTER_KEY" ]; then
      echo -e "${YELLOW}======================================${NC}"
      echo -e "${YELLOW}WARNING: Output file already exists!${NC}"
      echo -e "${YELLOW}======================================${NC}"
      echo "File: $OUTPUT_FILE"
      echo ""
      echo "To start fresh, delete the file first:"
      echo "  rm $OUTPUT_FILE"
      echo ""
      echo "To resume from where you left off, set AFTER_KEY to the last key in the file."
      echo ""
      echo "To skip download and only analyze, set SKIP_DOWNLOAD=true."
      echo ""
      echo -e "${RED}Aborting to prevent data loss.${NC}"
      exit 1
    else
      echo -e "${YELLOW}Resuming: Will append to existing file${NC}"
    fi
  else
    # Create empty file
    > "$OUTPUT_FILE"
  fi

  # Test Elasticsearch connectivity
  echo ""
  echo "Testing Elasticsearch connectivity..."
  if ! curl -sf "${ES_URL}/_cluster/health" > /dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to Elasticsearch at $ES_URL${NC}"
    echo "Please verify the URL and ensure Elasticsearch is running."
    exit 1
  fi
  echo -e "${GREEN}✓ Connected to Elasticsearch${NC}"

  # Main pagination loop
  echo ""
  echo -e "${BLUE}Fetching flow node instance keys...${NC}"
  BATCH_NUM=1
  TOTAL_KEYS=0
  CURRENT_AFTER_KEY="$AFTER_KEY"

  while true; do
    # Build after parameter
    AFTER_PARAM=""
    if [ -n "$CURRENT_AFTER_KEY" ]; then
      AFTER_PARAM=", \"after\": {\"key\": $CURRENT_AFTER_KEY}"
    fi
    
    # Query Elasticsearch
    echo -n "Batch $BATCH_NUM: Fetching up to $BATCH_SIZE keys..."
    
    RESPONSE=$(curl -sf "${ES_URL}/operate-flownode-instance-*/_search" \
      -H 'Content-Type: application/json' \
      -d "$(cat <<EOF
{
  "size": 0,
  "query": {
    "term": {
      "partitionId": ${PARTITION_ID}
    }
  },
  "aggs": {
    "flownode_instances": {
      "composite": {
        "size": ${BATCH_SIZE},
        "sources": [
          {"key": {"terms": {"field": "key"}}}
        ]
        ${AFTER_PARAM}
      },
      "aggs": {
        "min_timestamp": {"min": {"field": "startDate"}}
      }
    }
  }
}
EOF
)") || {
      echo -e "${RED}Failed!${NC}"
      echo -e "${RED}Error: Failed to query Elasticsearch${NC}"
      exit 1
    }
  
  # Extract data and append to file
  BATCH_KEYS=$(echo "$RESPONSE" | jq -r '.aggregations.flownode_instances.buckets[] | [.key.key, .min_timestamp.value_as_string // "N/A"] | @tsv' | tee -a "$OUTPUT_FILE" | wc -l)
  
  TOTAL_KEYS=$((TOTAL_KEYS + BATCH_KEYS))
  echo -e " ${GREEN}✓ $BATCH_KEYS keys (Total: $TOTAL_KEYS)${NC}"
  
  # Check for after_key
  CURRENT_AFTER_KEY=$(echo "$RESPONSE" | jq -r '.aggregations.flownode_instances.after_key.key // empty')
  
  if [ -z "$CURRENT_AFTER_KEY" ]; then
    echo -e "${GREEN}✓ All keys fetched${NC}"
    break
  fi
  
  BATCH_NUM=$((BATCH_NUM + 1))
done
fi

# Count total keys in file
TOTAL_KEYS=$(wc -l < "$OUTPUT_FILE")

echo ""
echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Analyzing for Key Jumps...${NC}"
echo -e "${BLUE}======================================${NC}"

# Step 2: Analyze the keys to find jumps
JUMP_FOUND=false

prev_key=""
prev_ts=""
while IFS=$'\t' read -r key timestamp; do
  if [ -n "$prev_key" ]; then
    diff=$((key - prev_key))
    if [ $diff -gt $JUMP_THRESHOLD ]; then
      JUMP_FOUND=true
      echo -e "${RED}=== JUMP DETECTED ===${NC}"
      echo "Previous key: $prev_key (timestamp: $prev_ts)"
      echo "Current key:  $key (timestamp: $timestamp)"
      echo "Difference:   $diff"
      echo ""
    fi
  fi
  prev_key="$key"
  prev_ts="$timestamp"
done < "$OUTPUT_FILE"

if [ "$JUMP_FOUND" = false ]; then
  echo -e "${GREEN}✓ No jumps detected (all differences <= $JUMP_THRESHOLD)${NC}"
fi

echo ""
echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}Analysis Complete${NC}"
echo -e "${BLUE}======================================${NC}"
echo "Total keys analyzed: $TOTAL_KEYS"
echo "Results saved to:    $OUTPUT_FILE"
echo ""
echo "You can also analyze the file manually using:"
echo "  - Python (see README for example)"
echo "  - Excel/Spreadsheet (import as tab-delimited)"
echo ""
echo "To clean up, run:"
echo "  rm $OUTPUT_FILE"

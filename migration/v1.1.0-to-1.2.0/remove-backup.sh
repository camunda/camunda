#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

echo "Delete all indices that match operate-*-1.1.0_"
echo "-------------------------------"
$RESTCLIENT --request DELETE --url $ES/operate-*-1.1.0_
echo
echo "-------------------------------"

echo "Delete all pipelines that match operate-*-1.1.0-to-1.2.0"
echo "-------------------------------"
$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/operate-*-1.1.0-to-1.2.0
echo
echo "-------------------------------"
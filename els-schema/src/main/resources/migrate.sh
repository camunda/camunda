#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for index in migrate/*.json; do
	echo "Migrate $index"
	echo "-------------------------------"
    $RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index
    echo
    echo "-------------------------------"
done

echo "Delete all pipelines that match operate-*"
echo "-------------------------------"
$RESTCLIENT --request DELETE --url $ES/_ingest/pipeline/operate-*
echo
echo "-------------------------------"

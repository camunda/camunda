#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for index in reindex/*.json; do
	echo "Reindex $index"
	echo "-------------------------------"
    $RESTCLIENT --request POST --url $ES/_reindex?wait_for_completion=true --data @$index
    echo
    echo "-------------------------------"
done

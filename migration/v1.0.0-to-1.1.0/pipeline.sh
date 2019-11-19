#!/bin/sh
# Define migration functions
ES=${1:-http://localhost:9200}
# For testing prefix 'echo ' 
RESTCLIENT="curl -K curl.config"

for pipeline in pipelines/*.json; do
    pipelinename=`basename $pipeline .json`
    echo "Create pipeline $pipelinename"
    echo "-------------------------------"
    $RESTCLIENT --request PUT --url $ES/_ingest/pipeline/$pipelinename --data @$pipeline
    echo
    echo "-------------------------------"
done

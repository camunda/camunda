#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 1 ]] || die "1 argument required [NAMESPACE], $# provided"

NAMESPACE=$1

#Monitoring Import of optimize-import (Should be true till data got imported)
IMPORTING="true"
until [[ ("$IMPORTING" == "false") ]]; do
    curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true
    IMPORTING=$(curl "http://optimize.${NAMESPACE}:8090/api/status" | jq '.engineStatus."camunda-bpm".isImporting') || true

    sleep 60
done
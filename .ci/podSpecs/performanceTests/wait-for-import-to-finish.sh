#!/bin/bash
die () {
    echo >&2 "$@"
    exit 1
}

[[ "$#" -eq 1 ]] || die "1 argument required [NAMESPACE], $# provided"

NAMESPACE=$1

#Monitoring Import of optimize-import (Should be true till data got imported)
IMPORTING="true"
until [[ ${IMPORTING} = "false" ]]; do
    # note: each call here is followed by `|| true` to not let the whole script fail if the curl call fails due to potential downtimes of pods
    curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search?size=0' | jq '.hits.total' || true
    curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"events": {"nested": {"path": "events"},"aggs": {"event_count": {"value_count": {"field": "events.id"}}}}}}' | jq '.aggregations.events.doc_count' || true
    curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0,"aggs": {"userTasks": {"nested": {"path": "userTasks"},"aggs": {"user_task_Count": {"value_count": {"field": "userTasks.id"}}}}}}' | jq '.aggregations.userTasks.doc_count' || true
    curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search' -H 'Content-Type: application/json' -d '{"size": 0, "aggs": {"variables": {"nested": { "path": "variables" },  "aggs": { "variable_count": { "value_count": { "field": "variables.id" } } } } } }' | jq '.aggregations.variables.doc_count' || true
    curl -s -X GET 'http://elasticsearch.${NAMESPACE}:9200/optimize-decision-instance/_search?size=0' | jq '.hits.total' || true

    curl -s -X GET "http://elasticsearch.${NAMESPACE}:9200/optimize-timestamp-based-import-index/_search?size=20" || true
    curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true
    IMPORTING=$(curl "http://optimize.${NAMESPACE}:8090/api/status" | jq '.isImporting."camunda-bpm"') || true
    sleep 60
done
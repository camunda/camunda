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
    curl -s -X POST "http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search" \
        -H 'Content-Type: application/json' \
        -d '{"size": 0,"aggs": {"events": {"nested": {"path": "events"},"aggs": {"event_count": {"value_count": {"field": "events.id"}}}}}}' \
        || true
    curl -s -X POST "http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search" \
        -H 'Content-Type: application/json' \
        -d '{"size": 0,"aggs": {"userTasks": {"nested": {"path": "userTasks"},"aggs": {"user_task_count": {"value_count": {"field": "userTasks.id"}}}}}}' \
        || true
    curl -s -X POST "http://elasticsearch.${NAMESPACE}:9200/optimize-process-instance/_search" \
        -H 'Content-Type: application/json' \
        -d '{"size": 0, "aggs": {"stringVariables": {"nested": {"path": "stringVariables" }, "aggs": { "variable_count": { "value_count": { "field": "stringVariables.id" } } } }, "integerVariables": { "nested": { "path": "integerVariables" }, "aggs": { "variable_count": { "value_count": { "field": "integerVariables.id" } } } }, "longVariables": { "nested": { "path": "longVariables" }, "aggs": { "variable_count": { "value_count": { "field": "longVariables.id" } } } }, "shortVariables": { "nested": { "path": "shortVariables" }, "aggs": { "variable_count": { "value_count": { "field": "shortVariables.id" } } } }, "doubleVariables": { "nested": { "path": "doubleVariables" }, "aggs": { "variable_count": { "value_count": { "field": "doubleVariables.id" } } } }, "dateVariables": { "nested": { "path": "dateVariables" }, "aggs": { "variable_count": { "value_count": { "field": "dateVariables.id" } } } }, "booleanVariables": { "nested": { "path": "booleanVariables" }, "aggs": { "variable_count": { "value_count": { "field": "booleanVariables.id" } } } } }}' \
        || true
    curl -s -X GET "http://elasticsearch.${NAMESPACE}:9200/optimize-decision-instance/_search?size=0" || true
    curl -s -X GET "http://elasticsearch.${NAMESPACE}:9200/optimize-timestamp-based-import-index/_search?size=20" || true
    curl -s "http://elasticsearch.${NAMESPACE}:9200/_cat/indices?v" || true
    IMPORTING=$(curl "http://optimize.${NAMESPACE}:8090/api/status" | jq '.isImporting."camunda-bpm"') || true
    sleep 60
done
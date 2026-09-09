#!/bin/bash

printf "\nTest: Operate process instance api\n"

curl --fail-with-body -L -X POST 'http://localhost:8080/v2/process-instances/search' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{
  "filter": {
    "state": "ACTIVE"
  }
}'

returnCode=$?

if [[ "$returnCode" != 0 ]]; then
        echo "test failed"
        exit 1
fi

printf "\nTest: Tasklist user task\n"
curl --fail-with-body -L -X POST 'http://localhost:8080/v2/user-tasks/search' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{}'

returnCode=$?

if [[ "$returnCode" != 0 ]]; then
        echo "test failed"
        exit 1
fi

printf "\nTest: Zeebe topology endpoint\n"
curl localhost:8080/v2/topology

returnCode=$?
if [[ "$returnCode" != 0 ]]; then
        echo "test failed"
        exit 1
fi
printf "\nTest: test --config flag\n"

#PREFIX="$(curl localhost:9600/actuator/configprops | jq '.contexts.camunda.beans.["camunda-io.camunda.configuration.Camunda"].properties.data.secondaryStorage.elasticsearch.indexPrefix')"
#echo $PREFIX
#if [[ "$PREFIX" != "\"extra-prefix-zeebe-record\"" ]]; then
#        echo "test failed"
#        exit 1
#fi

printf "\nTest: centralized secret resolution\n"

secret_value="c8run-centralized-secret-test-value"
deployment_response="$(mktemp)"
present_instance_response="$(mktemp)"
activation_response="$(mktemp)"
missing_instance_response="$(mktemp)"
missing_activation_response="$(mktemp)"
missing_incident_response="$(mktemp)"
trap 'rm -f "$deployment_response" "$present_instance_response" "$activation_response" "$missing_instance_response" "$missing_activation_response" "$missing_incident_response"' EXIT

curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/deployments' \
        -H 'Accept: application/json' \
        -F 'resources=@centralized_secrets.bpmn' \
        -o "$deployment_response"

curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/process-instances' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{"processDefinitionId":"c8runSecretPresent"}' \
        -o "$present_instance_response"

curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/jobs/activation' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{"type":"c8run-secret-present","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":30000}' \
        -o "$activation_response"

if ! jq -e --arg secret "$secret_value" '.jobs | length == 1 and .[0].variables.resolvedSecret == $secret' "$activation_response" >/dev/null; then
        printf "centralized secret was not injected into the activated job\n"
        exit 1
fi

curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/process-instances' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{"processDefinitionId":"c8runSecretMissing"}' \
        -o "$missing_instance_response"

curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/jobs/activation' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{"type":"c8run-secret-missing","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":5000}' \
        -o "$missing_activation_response"

if ! jq -e '.jobs | length == 0' "$missing_activation_response" >/dev/null; then
        printf "a job with a missing centralized secret was activated\n"
        exit 1
fi

missing_instance_key="$(jq -r '.processInstanceKey' "$missing_instance_response")"
for _ in {1..30}; do
        curl --silent --show-error --fail -X POST "http://localhost:8080/v2/process-instances/${missing_instance_key}/incidents/search" \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                --data-raw '{}' \
                -o "$missing_incident_response"
        if jq -e '.items | any(.errorType == "SECRET_RESOLUTION_ERROR" and .state == "ACTIVE")' "$missing_incident_response" >/dev/null; then
                break
        fi
        sleep 1
done

if ! jq -e '.items | any(.errorType == "SECRET_RESOLUTION_ERROR" and .state == "ACTIVE")' "$missing_incident_response" >/dev/null; then
        printf "missing centralized secret did not create a secret resolution incident\n"
        exit 1
fi

printf "\nTest: connectors api \n"

STATUS="$(curl localhost:8086/actuator/health | jq '.status')"
echo "$STATUS"
if [[ "$STATUS" != "\"UP\"" ]]; then
        echo "test failed"
        exit 1
fi

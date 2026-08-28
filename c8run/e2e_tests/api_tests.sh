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
incident_response="$(mktemp)"
trap 'rm -f "$deployment_response" "$present_instance_response" "$activation_response" "$missing_instance_response" "$incident_response"' EXIT

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

missing_instance_key="$(jq -r '.processInstanceKey' "$missing_instance_response")"
curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/jobs/activation' \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        --data-raw '{"type":"c8run-secret-missing","worker":"c8run-e2e","timeout":30000,"maxJobsToActivate":1,"requestTimeout":1000}' \
        -o /dev/null

incident_found=false
for _ in {1..90}; do
        curl --silent --show-error --fail -X POST 'http://localhost:8080/v2/incidents/search' \
                -H 'Content-Type: application/json' \
                -H 'Accept: application/json' \
                --data-raw "{\"filter\":{\"processInstanceKey\":\"$missing_instance_key\"}}" \
                -o "$incident_response"
        if jq -e '.items | any(.errorType == "SECRET_RESOLUTION_ERROR" and (.errorMessage | contains("camunda.secrets.C8RUN_E2E_MISSING")))' "$incident_response" >/dev/null; then
                incident_found=true
                break
        fi
        sleep 1
done

if [[ "$incident_found" != true ]]; then
        printf "missing centralized secret did not create the expected incident\n"
        exit 1
fi

printf "\nTest: connectors api \n"

STATUS="$(curl localhost:8086/actuator/health | jq '.status')"
echo "$STATUS"
if [[ "$STATUS" != "\"UP\"" ]]; then
        echo "test failed"
        exit 1
fi

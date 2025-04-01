#!/bin/bash

printf "\nTest: Authenticate\n"
curl -f --request POST 'http://localhost:8080/api/login?username=demo&password=demo' \
   --cookie-jar cookie.txt
returnCode=$?
if [[ "$returnCode" != 0 ]]; then
   echo "test failed"
   exit 1
fi


printf "\nTest: Operate process instance api\n"
curl -f -L -X POST 'http://localhost:8080/v2/process-instances/search' --cookie cookie.txt \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
--data-raw '{
  "filter": {
    "running": true,
    "active": true
  }
}'
returnCode=$?
if [[ "$returnCode" != 0 ]]; then
   echo "test failed"
   exit 1
fi


printf "\nTest: Tasklist user task\n"
curl -f -L -X POST 'http://localhost:8080/v2/user-tasks/search' --cookie cookie.txt \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
--data-raw '{}'

returnCode=$?

if [[ "$returnCode" != 0 ]]; then
   echo "test failed"
   exit 1
fi


printf "\nTest: Zeebe topology endpoint\n"
curl -f --cookie  cookie.txt  localhost:8080/v2/topology

returnCode=$?
if [[ "$returnCode" != 0 ]]; then
   echo "test failed"
   exit 1
fi
printf "\nTest: test --config flag\n"

PREFIX="$( curl localhost:9600/actuator/configprops | jq '.contexts.Camunda.beans.["io.camunda.tasklist.property.TasklistProperties"].properties.zeebeElasticsearch.prefix' )"
echo $PREFIX
if [[ "$PREFIX" != "\"extra-prefix-zeebe-record\"" ]]; then
   echo "test failed"
   exit 1
fi


printf "\nTest: connectors api \n"

STATUS="$( curl localhost:8085/actuator/health | jq '.status' )"
echo $STATUS
if [[ "$STATUS" != "\"UP\"" ]]; then
   echo "test failed"
   exit 1
fi


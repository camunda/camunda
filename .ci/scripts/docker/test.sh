#!/bin/bash

set -Eeuox pipefail

TMP_FILE=$(mktemp)
cat << EOF > ${TMP_FILE}
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_0b9p413" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.9.0">
  <bpmn:process id="process" name="Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start" />
    <bpmn:sequenceFlow id="Flow_0r0a195" sourceRef="StartEvent_1" targetRef="Activity_1akmbiv" />
    <bpmn:serviceTask id="Activity_1akmbiv" name="Task">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="task" />
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:endEvent id="Event_1uhj218" name="End" />
    <bpmn:sequenceFlow id="Flow_1g6hr1d" sourceRef="Activity_1akmbiv" targetRef="Event_1uhj218" />
  </bpmn:process>
</bpmn:definitions>
EOF
chmod a+r ${TMP_FILE}

CONTAINER_ID=$(docker run --rm -d -e ZEEBE_INSECURE_CONNECTION=true ${IMAGE}:${TAG})

function cleanup {
  if [ $? -ne 0 ]; then
    docker logs ${CONTAINER_ID}
  fi
  rm -f ${TMP_FILE}
  docker rm -fv ${CONTAINER_ID}
}
trap cleanup EXIT

function zbctl {
  docker exec ${CONTAINER_ID} zbctl $@
}

docker cp ${TMP_FILE} ${CONTAINER_ID}:/tmp/process.bpmn

until docker exec -it ${CONTAINER_ID} bash -c 'wget -q $(hostname -i):9600/ready'
do
  sleep 1
done

zbctl deploy /tmp/process.bpmn

zbctl create worker task --handler=cat &
WORKER_PID=$!

zbctl create instance --withResult process

kill ${WORKER_PID}

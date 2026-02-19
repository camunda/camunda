/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const runningInstance: InstanceMock = {
  detail: {
    id: '2251799813687144',
    processId: '2251799813686165',
    processName: 'Signal event',
    processVersion: 1,
    startDate: '2023-08-14T05:45:17.331+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'signalEventProcess',
    hasActiveOperation: false,
    operations: [],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
    sortValues: ['', ''],
    tenantId: '',
  },
  detailV2: {
    processInstanceKey: '2251799813687144',
    processDefinitionKey: '2251799813686165',
    processDefinitionName: 'Signal event',
    processDefinitionVersion: 1,
    startDate: '2023-08-14T05:45:17.331+0000',
    state: 'ACTIVE',
    processDefinitionId: 'signalEventProcess',
    tenantId: '',
    hasIncident: false,
  },
  callHierarchy: [],
  xml: `<?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1w68912" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.6.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.1.0">
      <bpmn:process id="signalEventProcess" name="Signal event" isExecutable="true">
        <bpmn:sequenceFlow id="Flow_0s222mp" sourceRef="StartEvent_1" targetRef="Activity_0dex012" />
        <bpmn:endEvent id="Event_0uqq9ok">
          <bpmn:incoming>Flow_00wmwdw</bpmn:incoming>
        </bpmn:endEvent>
        <bpmn:sequenceFlow id="Flow_00wmwdw" sourceRef="Activity_0dex012" targetRef="Event_0uqq9ok" />
        <bpmn:startEvent id="StartEvent_1">
          <bpmn:outgoing>Flow_0s222mp</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_0fuv1s9" signalRef="Signal_1jnfrnu" />
        </bpmn:startEvent>
        <bpmn:userTask id="Activity_0dex012" name="Signal user task">
          <bpmn:incoming>Flow_0s222mp</bpmn:incoming>
          <bpmn:outgoing>Flow_00wmwdw</bpmn:outgoing>
        </bpmn:userTask>
      </bpmn:process>
      <bpmn:signal id="Signal_1jnfrnu" name="startSignal1" />
      <bpmndi:BPMNDiagram id="BPMNDiagram_1">
        <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="signalEventProcess">
          <bpmndi:BPMNShape id="Event_0uqq9ok_di" bpmnElement="Event_0uqq9ok">
            <dc:Bounds x="442" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_14abjwl_di" bpmnElement="StartEvent_1">
            <dc:Bounds x="179" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Activity_1427447_di" bpmnElement="Activity_0dex012">
            <dc:Bounds x="280" y="77" width="100" height="80" />
            <bpmndi:BPMNLabel />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNEdge id="Flow_0s222mp_di" bpmnElement="Flow_0s222mp">
            <di:waypoint x="215" y="117" />
            <di:waypoint x="280" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_00wmwdw_di" bpmnElement="Flow_00wmwdw">
            <di:waypoint x="380" y="117" />
            <di:waypoint x="442" y="117" />
          </bpmndi:BPMNEdge>
        </bpmndi:BPMNPlane>
      </bpmndi:BPMNDiagram>
    </bpmn:definitions>
    `,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813687146',
        processInstanceKey: '2251799813687144',
        processDefinitionKey: '2251799813686165',
        processDefinitionId: 'signalEventProcess',
        elementId: 'StartEvent_1',
        elementName: 'StartEvent_1',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '',
      },
      {
        elementInstanceKey: '2251799813687150',
        processInstanceKey: '2251799813687144',
        processDefinitionKey: '2251799813686165',
        processDefinitionId: 'signalEventProcess',
        elementId: 'Activity_0dex012',
        elementName: 'Signal user task',
        type: 'USER_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        tenantId: '',
      },
    ],
    page: {totalItems: 2},
  },
  variables: [
    {
      variableKey: '2251799813687144-signalNumber',
      name: 'signalNumber',
      value: '47',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '2251799813687144',
      scopeKey: '2251799813687144',
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '2251799813687144',
      activityId: 'Flow_0s222mp',
    },
  ],
  sequenceFlowsV2: {
    items: [
      {
        processInstanceKey: '2251799813687144',
        elementId: 'Flow_0s222mp',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
    ],
  },
  statistics: {
    items: [
      {
        elementId: 'Activity_0dex012',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
  },
  metaData: {
    flowNodeInstanceId: '2251799813687150',
    flowNodeId: null,
    flowNodeType: null,
    instanceCount: null,
    instanceMetadata: {
      flowNodeId: 'Activity_0dex012',
      flowNodeInstanceId: '2251799813687150',
      flowNodeType: 'USER_TASK',
      startDate: '2023-10-24T08:41:45.911+0000',
      endDate: null,
      calledProcessInstanceId: null,
      calledProcessDefinitionName: null,
      calledDecisionInstanceId: null,
      calledDecisionDefinitionName: null,
      eventId: '2251799813687144_2251799813687150',
      jobType: 'io.camunda.zeebe:userTask',
      jobRetries: 1,
      jobWorker: '',
      jobDeadline: null,
      jobCustomHeaders: {},
      jobId: '',
    },
    incidentCount: 0,
    incident: null,
  },
};

export {runningInstance};

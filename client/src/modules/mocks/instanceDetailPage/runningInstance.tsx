/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const flowNodeInstances = {
  '2251799813685591': {
    children: [
      {
        id: '2251799813685594',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'start',
        startDate: '2021-08-20T12:04:28.277+0000',
        endDate: '2021-08-20T12:04:28.281+0000',
        treePath: '2251799813685591/2251799813685594',
        sortValues: [1629461068277, '2251799813685594'],
      },
      {
        id: '2251799813685596',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'neverFails',
        startDate: '2021-08-20T12:04:28.281+0000',
        endDate: '2021-08-20T12:04:28.508+0000',
        treePath: '2251799813685591/2251799813685596',
        sortValues: [1629461068281, '2251799813685596'],
      },
      {
        id: '2251799813685601',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        flowNodeId: 'neverFails2',
        startDate: '2021-08-20T12:04:28.513+0000',
        endDate: null,
        treePath: '2251799813685591/2251799813685601',
        sortValues: [1629461068513, '2251799813685601'],
      },
    ],
    running: null,
  },
};

const flowNodeStates = {
  neverFails: 'COMPLETED',
  start: 'COMPLETED',
  neverFails2: 'ACTIVE',
};

const instance = {
  id: '2251799813685591',
  processId: '2251799813685582',
  processName: 'Without Incidents Process',
  processVersion: 2,
  startDate: '2021-08-20T12:04:28.270+0000',
  endDate: null,
  state: 'ACTIVE',
  bpmnProcessId: 'withoutIncidentsProcess',
  hasActiveOperation: false,
  operations: [],
  parentInstanceId: null,
  sortValues: null,
  callHierarchy: [],
};

const callHierarchy = [
  {
    instanceId: '546546543276',
    processDefinitionName: 'Parent Process Name',
  },
  {
    instanceId: '968765314354',
    processDefinitionName: '1st level Child Process Name',
  },
  {
    instanceId: '2251799813685447',
    processDefinitionName: '2nd level Child Process Name',
  },
];

const longCallHierarchy = [
  {
    instanceId: '546546543276',
    processDefinitionName: 'Parent Process Name',
  },
  {
    instanceId: '968765314354',
    processDefinitionName: '1st level Child Process Name',
  },
  {
    instanceId: '2251799813685447',
    processDefinitionName: '2nd level Child Process Name',
  },
  {
    instanceId: '3',
    processDefinitionName: '3rd level Child Process Name',
  },
  {
    instanceId: '4',
    processDefinitionName: '4th level Child Process Name',
  },
  {
    instanceId: '5',
    processDefinitionName: '5th level Child Process Name',
  },
  {
    instanceId: '6',
    processDefinitionName: '6tg level Child Process Name',
  },
  {
    instanceId: '7',
    processDefinitionName: '7th level Child Process Name',
  },
  {
    instanceId: '8',
    processDefinitionName: '8th level Child Process Name',
  },
  {
    instanceId: '9',
    processDefinitionName: '9th level Child Process Name',
  },
  {
    instanceId: '10',
    processDefinitionName: '10th level Child Process Name',
  },
];

const sequenceFlows = [
  {
    processInstanceId: '2251799813685591',
    activityId: 'SequenceFlow_0v10lat',
  },
  {
    processInstanceId: '2251799813685591',
    activityId: 'SequenceFlow_1sz6737',
  },
];

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.6.2">
  <bpmn:process id="withoutIncidentsProcess" name="Without Incidents Process" isExecutable="true">
    <bpmn:startEvent id="start" name="start">
      <bpmn:outgoing>SequenceFlow_1sz6737</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="SequenceFlow_1sz6737" sourceRef="start" targetRef="neverFails" />
    <bpmn:endEvent id="end" name="end">
      <bpmn:incoming>SequenceFlow_0g2buaw</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:serviceTask id="neverFails" name="Never fails">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="neverFails" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_1sz6737</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0v10lat</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="neverFails2" name="Never fails again">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="neverFails2" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0v10lat</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0g2buaw</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_0v10lat" sourceRef="neverFails" targetRef="neverFails2" />
    <bpmn:sequenceFlow id="SequenceFlow_0g2buaw" sourceRef="neverFails2" targetRef="end" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="withoutIncidentsProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="start">
        <dc:Bounds x="173" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="180" y="138" width="22" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1sz6737_di" bpmnElement="SequenceFlow_1sz6737">
        <di:waypoint x="209" y="120" />
        <di:waypoint x="374" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="260" y="105" width="0" height="0" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_0gbv3sc_di" bpmnElement="end">
        <dc:Bounds x="867" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="876" y="138" width="18" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0sryj72_di" bpmnElement="neverFails">
        <dc:Bounds x="374" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_1j8pmi5_di" bpmnElement="neverFails2">
        <dc:Bounds x="602" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0v10lat_di" bpmnElement="SequenceFlow_0v10lat">
        <di:waypoint x="474" y="120" />
        <di:waypoint x="602" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0g2buaw_di" bpmnElement="SequenceFlow_0g2buaw">
        <di:waypoint x="702" y="120" />
        <di:waypoint x="867" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {
  flowNodeInstances,
  flowNodeStates,
  instance,
  sequenceFlows,
  xml,
  callHierarchy,
  longCallHierarchy,
};

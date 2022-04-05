/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const flowNodeInstances = {
  '4503599627371108': {
    children: [
      {
        id: '4503599627371111',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'StartEvent_1',
        startDate: '2021-08-23T08:37:23.529+0000',
        endDate: '2021-08-23T08:37:23.535+0000',
        treePath: '4503599627371108/4503599627371111',
        sortValues: [1629707843529, '4503599627371111'],
      },
      {
        id: '4503599627371113',
        type: 'PARALLEL_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'Gateway_0xvsgeh',
        startDate: '2021-08-23T08:37:23.542+0000',
        endDate: '2021-08-23T08:37:23.542+0000',
        treePath: '4503599627371108/4503599627371113',
        sortValues: [1629707843542, '4503599627371113'],
      },
      {
        id: '4503599627371115',
        type: 'MULTI_INSTANCE_BODY',
        state: 'TERMINATED',
        flowNodeId: 'multiInstanceTask',
        startDate: '2021-08-23T08:37:23.546+0000',
        endDate: '2021-08-23T08:46:56.483+0000',
        treePath: '4503599627371108/4503599627371115',
        sortValues: [1629707843546, '4503599627371115'],
      },
      {
        id: '4503599627371117',
        type: 'MULTI_INSTANCE_BODY',
        state: 'TERMINATED',
        flowNodeId: 'Activity_18im4wt',
        startDate: '2021-08-23T08:37:23.548+0000',
        endDate: '2021-08-23T08:46:56.493+0000',
        treePath: '4503599627371108/4503599627371117',
        sortValues: [1629707843548, '4503599627371117'],
      },
    ],
    running: null,
  },
};

const flowNodeStates = {
  taskInsideSubprocess: 'TERMINATED',
  Gateway_0xvsgeh: 'COMPLETED',
  StartEvent_1: 'COMPLETED',
  Event_000kz8o: 'COMPLETED',
  Activity_18im4wt: 'TERMINATED',
  multiInstanceTask: 'TERMINATED',
};

const instance = {
  id: '4503599627371108',
  processId: '2251799813685466',
  processName: 'Sequential Multi-Instance Process',
  processVersion: 1,
  startDate: '2021-08-23T08:37:23.527+0000',
  endDate: '2021-08-23T08:46:56.493+0000',
  state: 'CANCELED',
  bpmnProcessId: 'multiInstanceProcess',
  hasActiveOperation: false,
  operations: [],
  parentInstanceId: null,
  sortValues: null,
  callHierarchy: [],
};

const sequenceFlows = [
  {
    processInstanceId: '4503599627371108',
    activityId: 'Flow_0k1yypy',
  },
  {
    processInstanceId: '4503599627371108',
    activityId: 'Flow_1du92w6',
  },
  {
    processInstanceId: '4503599627371108',
    activityId: 'Flow_1m9it17',
  },
  {
    processInstanceId: '4503599627371108',
    activityId: 'Flow_1xq2lys',
  },
];

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_0xml0fa" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.9.1">
  <bpmn:process id="multiInstanceProcess" name="Sequential Multi-Instance Process"  isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_1xq2lys</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1xq2lys" sourceRef="StartEvent_1" targetRef="Gateway_0xvsgeh" />
    <bpmn:subProcess id="Activity_18im4wt">
      <bpmn:incoming>Flow_1m9it17</bpmn:incoming>
      <bpmn:outgoing>Flow_046x3y1</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="true">
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=items" inputElement="item" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
      <bpmn:startEvent id="Event_000kz8o">
        <bpmn:outgoing>Flow_0k1yypy</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:sequenceFlow id="Flow_0k1yypy" sourceRef="Event_000kz8o" targetRef="taskInsideSubprocess" />
      <bpmn:endEvent id="Event_0fvrpgs">
        <bpmn:incoming>Flow_0azpvjc</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="Flow_0azpvjc" sourceRef="taskInsideSubprocess" targetRef="Event_0fvrpgs" />
      <bpmn:serviceTask id="taskInsideSubprocess" name="Task inside subprocess">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="taskInsideSubprocess" />
        </bpmn:extensionElements>
        <bpmn:incoming>Flow_0k1yypy</bpmn:incoming>
        <bpmn:outgoing>Flow_0azpvjc</bpmn:outgoing>
      </bpmn:serviceTask>
    </bpmn:subProcess>
    <bpmn:endEvent id="Event_1pcipnr">
      <bpmn:incoming>Flow_046x3y1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_046x3y1" sourceRef="Activity_18im4wt" targetRef="Event_1pcipnr" />
    <bpmn:sequenceFlow id="Flow_1m9it17" sourceRef="Gateway_0xvsgeh" targetRef="Activity_18im4wt" />
    <bpmn:sequenceFlow id="Flow_1du92w6" sourceRef="Gateway_0xvsgeh" targetRef="multiInstanceTask" />
    <bpmn:endEvent id="Event_1dmf1fe">
      <bpmn:incoming>Flow_0xnqix9</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0xnqix9" sourceRef="multiInstanceTask" targetRef="Event_1dmf1fe" />
    <bpmn:serviceTask id="multiInstanceTask" name="Mutli-instance sequential task">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="multiInstanceTask" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1du92w6</bpmn:incoming>
      <bpmn:outgoing>Flow_0xnqix9</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="true">
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=items" inputElement="item" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
    </bpmn:serviceTask>
    <bpmn:parallelGateway id="Gateway_0xvsgeh">
      <bpmn:incoming>Flow_1xq2lys</bpmn:incoming>
      <bpmn:outgoing>Flow_1m9it17</bpmn:outgoing>
      <bpmn:outgoing>Flow_1du92w6</bpmn:outgoing>
    </bpmn:parallelGateway>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="sequentialMultiInstance">
      <bpmndi:BPMNEdge id="Flow_1xq2lys_di" bpmnElement="Flow_1xq2lys">
        <di:waypoint x="188" y="320" />
        <di:waypoint x="289" y="320" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_046x3y1_di" bpmnElement="Flow_046x3y1">
        <di:waypoint x="800" y="180" />
        <di:waypoint x="882" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1m9it17_di" bpmnElement="Flow_1m9it17">
        <di:waypoint x="314" y="295" />
        <di:waypoint x="314" y="180" />
        <di:waypoint x="440" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1du92w6_di" bpmnElement="Flow_1du92w6">
        <di:waypoint x="314" y="345" />
        <di:waypoint x="314" y="370" />
        <di:waypoint x="430" y="370" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0xnqix9_di" bpmnElement="Flow_0xnqix9">
        <di:waypoint x="530" y="370" />
        <di:waypoint x="592" y="370" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="Event_1pcipnr_di" bpmnElement="Event_1pcipnr">
        <dc:Bounds x="882" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="302" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1eyhw08_di" bpmnElement="multiInstanceTask">
        <dc:Bounds x="430" y="330" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1dmf1fe_di" bpmnElement="Event_1dmf1fe">
        <dc:Bounds x="592" y="352" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1tedc2q_di" bpmnElement="Gateway_0xvsgeh">
        <dc:Bounds x="289" y="295" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0ty4ji6_di" bpmnElement="Activity_18im4wt" isExpanded="true">
        <dc:Bounds x="440" y="80" width="360" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0k1yypy_di" bpmnElement="Flow_0k1yypy">
        <di:waypoint x="508" y="180" />
        <di:waypoint x="570" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0azpvjc_di" bpmnElement="Flow_0azpvjc">
        <di:waypoint x="670" y="180" />
        <di:waypoint x="712" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="Event_000kz8o_di" bpmnElement="Event_000kz8o">
        <dc:Bounds x="472" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0fvrpgs_di" bpmnElement="Event_0fvrpgs">
        <dc:Bounds x="712" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0es8w0s_di" bpmnElement="taskInsideSubprocess">
        <dc:Bounds x="570" y="140" width="100" height="80" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {flowNodeInstances, flowNodeStates, instance, sequenceFlows, xml};

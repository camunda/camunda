/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const flowNodeInstances = {
  '9007199254741571': {
    children: [
      {
        id: '9007199254741579',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'StartEvent_1',
        startDate: '2021-08-23T08:37:24.173+0000',
        endDate: '2021-08-23T08:37:24.175+0000',
        treePath: '9007199254741571/9007199254741579',
        sortValues: [1629707844173, '9007199254741579'],
      },
      {
        id: '9007199254741581',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'Task_1b1r7ow',
        startDate: '2021-08-23T08:37:24.183+0000',
        endDate: '2021-08-23T08:38:42.399+0000',
        treePath: '9007199254741571/9007199254741581',
        sortValues: [1629707844183, '9007199254741581'],
      },
      {
        id: '9007199254745237',
        type: 'EXCLUSIVE_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'ExclusiveGateway_1qqmrb8',
        startDate: '2021-08-23T08:38:42.415+0000',
        endDate: '2021-08-23T08:38:42.415+0000',
        treePath: '9007199254741571/9007199254745237',
        sortValues: [1629707922415, '9007199254745237'],
      },
      {
        id: '9007199254745240',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        flowNodeId: 'Task_162x79i',
        startDate: '2021-08-23T08:38:42.431+0000',
        endDate: '2021-08-23T08:38:42.703+0000',
        treePath: '9007199254741571/9007199254745240',
        sortValues: [1629707922431, '9007199254745240'],
      },
      {
        id: '9007199254745275',
        type: 'END_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'EndEvent_042s0oc',
        startDate: '2021-08-23T08:38:42.708+0000',
        endDate: '2021-08-23T08:38:42.708+0000',
        treePath: '9007199254741571/9007199254745275',
        sortValues: [1629707922708, '9007199254745275'],
      },
    ],
    running: null,
  },
};

const flowNodeStates = {
  StartEvent_1: 'COMPLETED',
  EndEvent_042s0oc: 'COMPLETED',
  Task_162x79i: 'COMPLETED',
  ExclusiveGateway_1qqmrb8: 'COMPLETED',
  Task_1b1r7ow: 'COMPLETED',
};

const instance = {
  id: '9007199254741571',
  processId: '2251799813685460',
  processName: 'Order process',
  processVersion: 1,
  startDate: '2021-08-23T08:37:24.165+0000',
  endDate: '2021-08-23T08:38:42.716+0000',
  state: 'COMPLETED',
  bpmnProcessId: 'orderProcess',
  hasActiveOperation: false,
  operations: [],
  parentInstanceId: null,
  sortValues: null,
  callHierarchy: [],
};

const sequenceFlows = [
  {
    processInstanceId: '9007199254741571',
    activityId: 'SequenceFlow_0j6tsnn',
  },
  {
    processInstanceId: '9007199254741571',
    activityId: 'SequenceFlow_19klrd3',
  },
  {
    processInstanceId: '9007199254741571',
    activityId: 'SequenceFlow_1dq2rqw',
  },
  {
    processInstanceId: '9007199254741571',
    activityId: 'SequenceFlow_1s6g17c',
  },
];

const variables = [
  {
    id: '9007199254741571-clientNo',
    name: 'clientNo',
    value: '"CNT-1211132-02"',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: true,
    sortValues: ['clientNo'],
  },
  {
    id: '9007199254741571-items',
    name: 'items',
    value:
      '[{"code":"123.135.625","name":"Laptop Lenovo ABC-001","quantity":1,"price":883.0},{"code":"111.653.365","name":"Headset Sony QWE-23","quantity":2,"price":19.0}]',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['items'],
  },
  {
    id: '9007199254741571-mwst',
    name: 'mwst',
    value: '171.38',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['mwst'],
  },
  {
    id: '9007199254741571-orderNo',
    name: 'orderNo',
    value: '"CMD0001-01"',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['orderNo'],
  },
  {
    id: '9007199254741571-orderStatus',
    name: 'orderStatus',
    value: '"SHIPPED"',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['orderStatus'],
  },
  {
    id: '9007199254741571-paid',
    name: 'paid',
    value: 'true',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['paid'],
  },
  {
    id: '9007199254741571-total',
    name: 'total',
    value: '902.0',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: ['total'],
  },
];

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.8.0">
  <bpmn:process id="orderProcess" name="Order process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Order received">
      <bpmn:outgoing>SequenceFlow_0j6tsnn</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="SequenceFlow_0j6tsnn" sourceRef="StartEvent_1" targetRef="Task_1b1r7ow" />
    <bpmn:serviceTask id="Task_1b1r7ow" name="Check payment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="checkPayment" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0j6tsnn</bpmn:incoming>
      <bpmn:incoming>SequenceFlow_1q6ade7</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1s6g17c</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Task_162x79i" name="Ship Articles">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="shipArticles" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_1dq2rqw</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_19klrd3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="EndEvent_042s0oc">
      <bpmn:incoming>SequenceFlow_19klrd3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_19klrd3" sourceRef="Task_162x79i" targetRef="EndEvent_042s0oc" />
    <bpmn:exclusiveGateway id="ExclusiveGateway_1qqmrb8" name="Payment OK?">
      <bpmn:incoming>SequenceFlow_1s6g17c</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0jzbqu1</bpmn:outgoing>
      <bpmn:outgoing>SequenceFlow_1dq2rqw</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="SequenceFlow_1s6g17c" sourceRef="Task_1b1r7ow" targetRef="ExclusiveGateway_1qqmrb8" />
    <bpmn:serviceTask id="ServiceTask_00g0gy6" name="Request for payment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="requestPayment" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0jzbqu1</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1q6ade7</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_0jzbqu1" name="Not paid" sourceRef="ExclusiveGateway_1qqmrb8" targetRef="ServiceTask_00g0gy6">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=paid = false</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="SequenceFlow_1q6ade7" sourceRef="ServiceTask_00g0gy6" targetRef="Task_1b1r7ow" />
    <bpmn:sequenceFlow id="SequenceFlow_1dq2rqw" name="paid" sourceRef="ExclusiveGateway_1qqmrb8" targetRef="Task_162x79i">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=paid = true</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="orderProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="175" y="120" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="157" y="156" width="73" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0j6tsnn_di" bpmnElement="SequenceFlow_0j6tsnn">
        <di:waypoint x="211" y="138" />
        <di:waypoint x="300" y="138" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="-169.5" y="227" width="90" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_0c3g2sx_di" bpmnElement="Task_1b1r7ow">
        <dc:Bounds x="300" y="98" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0k2efs8_di" bpmnElement="Task_162x79i">
        <dc:Bounds x="633" y="98" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_042s0oc_di" bpmnElement="EndEvent_042s0oc">
        <dc:Bounds x="792" y="120" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="385" y="270" width="90" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_19klrd3_di" bpmnElement="SequenceFlow_19klrd3">
        <di:waypoint x="733" y="138" />
        <di:waypoint x="792" y="138" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="337.5" y="227" width="90" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ExclusiveGateway_1qqmrb8_di" bpmnElement="ExclusiveGateway_1qqmrb8" isMarkerVisible="true">
        <dc:Bounds x="469" y="113" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="460" y="85" width="69" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1s6g17c_di" bpmnElement="SequenceFlow_1s6g17c">
        <di:waypoint x="400" y="138" />
        <di:waypoint x="469" y="138" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="54.5" y="227" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_00g0gy6_di" bpmnElement="ServiceTask_00g0gy6">
        <dc:Bounds x="444" y="260" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0jzbqu1_di" bpmnElement="SequenceFlow_0jzbqu1">
        <di:waypoint x="494" y="163" />
        <di:waypoint x="494" y="260" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="450" y="188" width="41" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1q6ade7_di" bpmnElement="SequenceFlow_1q6ade7">
        <di:waypoint x="444" y="300" />
        <di:waypoint x="350" y="300" />
        <di:waypoint x="350" y="178" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="17" y="389" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1dq2rqw_di" bpmnElement="SequenceFlow_1dq2rqw">
        <di:waypoint x="519" y="138" />
        <di:waypoint x="633" y="138" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="566" y="117" width="21" height="14" />
        </bpmndi:BPMNLabel>
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
  variables,
  xml,
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const PROCESS_INSTANCE_KEY = '2251799813735874';
const PROCESS_DEFINITION_KEY = '2251799813735100';
const APPROVE_ORDER_INSTANCE_KEY = '2251799813735880';
const CHARGE_PAYMENT_INSTANCE_KEY = '2251799813735882';

const waitStateProcessInstance: InstanceMock = {
  detail: {
    processInstanceKey: PROCESS_INSTANCE_KEY,
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionName: 'Order process',
    processDefinitionVersion: 1,
    startDate: '2023-09-29T07:16:22.701+0000',
    state: 'ACTIVE',
    processDefinitionId: 'orderProcess',
    tenantId: '<default>',
    hasIncident: false,
    processDefinitionVersionTag: null,
    endDate: null,
    parentProcessInstanceKey: null,
    parentElementInstanceKey: null,
    rootProcessInstanceKey: null,
    tags: [],
    businessId: null,
  },
  callHierarchy: [],
  xml: `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.6.0">
  <bpmn:process id="orderProcess" name="Order process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Order received">
      <bpmn:outgoing>Flow_start</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_start" sourceRef="StartEvent_1" targetRef="Gateway_split" />
    <bpmn:parallelGateway id="Gateway_split">
      <bpmn:incoming>Flow_start</bpmn:incoming>
      <bpmn:outgoing>Flow_split_user</bpmn:outgoing>
      <bpmn:outgoing>Flow_split_service</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:sequenceFlow id="Flow_split_user" sourceRef="Gateway_split" targetRef="approveOrder" />
    <bpmn:sequenceFlow id="Flow_split_service" sourceRef="Gateway_split" targetRef="chargePayment" />
    <bpmn:userTask id="approveOrder" name="Approve order">
      <bpmn:incoming>Flow_split_user</bpmn:incoming>
      <bpmn:outgoing>Flow_user_join</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="chargePayment" name="Charge payment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="charge-payment" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_split_service</bpmn:incoming>
      <bpmn:outgoing>Flow_service_join</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="Flow_user_join" sourceRef="approveOrder" targetRef="Gateway_join" />
    <bpmn:sequenceFlow id="Flow_service_join" sourceRef="chargePayment" targetRef="Gateway_join" />
    <bpmn:parallelGateway id="Gateway_join">
      <bpmn:incoming>Flow_user_join</bpmn:incoming>
      <bpmn:incoming>Flow_service_join</bpmn:incoming>
      <bpmn:outgoing>Flow_join_end</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:sequenceFlow id="Flow_join_end" sourceRef="Gateway_join" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1" name="Order completed">
      <bpmn:incoming>Flow_join_end</bpmn:incoming>
    </bpmn:endEvent>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="orderProcess">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="160" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="146" y="225" width="68" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_split_di" bpmnElement="Gateway_split">
        <dc:Bounds x="255" y="175" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="approveOrder_di" bpmnElement="approveOrder">
        <dc:Bounds x="370" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="chargePayment_di" bpmnElement="chargePayment">
        <dc:Bounds x="370" y="240" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_join_di" bpmnElement="Gateway_join">
        <dc:Bounds x="535" y="175" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="642" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="623" y="225" width="76" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_start_di" bpmnElement="Flow_start">
        <di:waypoint x="196" y="200" />
        <di:waypoint x="255" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_split_user_di" bpmnElement="Flow_split_user">
        <di:waypoint x="280" y="175" />
        <di:waypoint x="280" y="120" />
        <di:waypoint x="370" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_split_service_di" bpmnElement="Flow_split_service">
        <di:waypoint x="280" y="225" />
        <di:waypoint x="280" y="280" />
        <di:waypoint x="370" y="280" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_user_join_di" bpmnElement="Flow_user_join">
        <di:waypoint x="470" y="120" />
        <di:waypoint x="560" y="120" />
        <di:waypoint x="560" y="175" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_service_join_di" bpmnElement="Flow_service_join">
        <di:waypoint x="470" y="280" />
        <di:waypoint x="560" y="280" />
        <di:waypoint x="560" y="225" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_join_end_di" bpmnElement="Flow_join_end">
        <di:waypoint x="585" y="200" />
        <di:waypoint x="642" y="200" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813735876',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'orderProcess',
        elementId: 'StartEvent_1',
        elementName: 'Order received',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-09-29T07:16:22.701+0000',
        endDate: '2023-09-29T07:16:22.730+0000',
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: '2251799813735878',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'orderProcess',
        elementId: 'Gateway_split',
        elementName: 'Fork',
        type: 'PARALLEL_GATEWAY',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-09-29T07:16:22.730+0000',
        endDate: '2023-09-29T07:16:22.740+0000',
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: APPROVE_ORDER_INSTANCE_KEY,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'orderProcess',
        elementId: 'approveOrder',
        elementName: 'Approve order',
        type: 'USER_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-09-29T07:16:22.740+0000',
        endDate: null,
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: CHARGE_PAYMENT_INSTANCE_KEY,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'orderProcess',
        elementId: 'chargePayment',
        elementName: 'Charge payment',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-09-29T07:16:22.740+0000',
        endDate: null,
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
    ],
    page: {
      totalItems: 4,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  variables: [
    {
      variableKey: `${PROCESS_INSTANCE_KEY}-orderId`,
      name: 'orderId',
      value: '"ORD-2023-0042"',
      isTruncated: false,
      tenantId: '<default>',
      processInstanceKey: PROCESS_INSTANCE_KEY,
      scopeKey: PROCESS_INSTANCE_KEY,
      rootProcessInstanceKey: null,
    },
  ],
  sequenceFlows: {
    items: [
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_start',
        tenantId: '<default>',
        processDefinitionId: 'orderProcess',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: `${PROCESS_INSTANCE_KEY}_Flow_start`,
      },
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_split_user',
        tenantId: '<default>',
        processDefinitionId: 'orderProcess',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: `${PROCESS_INSTANCE_KEY}_Flow_split_user`,
      },
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_split_service',
        tenantId: '<default>',
        processDefinitionId: 'orderProcess',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: `${PROCESS_INSTANCE_KEY}_Flow_split_service`,
      },
    ],
  },
  statistics: {
    items: [
      {
        elementId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Gateway_split',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'approveOrder',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'chargePayment',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ],
  },
  waitStates: {
    items: [
      {
        rootProcessInstanceKey: null,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementInstanceKey: APPROVE_ORDER_INSTANCE_KEY,
        elementId: 'approveOrder',
        elementType: 'USER_TASK',
        tenantId: '<default>',
        bpmnProcessId: 'orderProcess',
        details: {
          waitStateType: 'USER_TASK',
          taskKey: '2251799813735900',
          dueDate: null,
        },
      },
      {
        rootProcessInstanceKey: null,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementInstanceKey: CHARGE_PAYMENT_INSTANCE_KEY,
        elementId: 'chargePayment',
        elementType: 'SERVICE_TASK',
        tenantId: '<default>',
        bpmnProcessId: 'orderProcess',
        details: {
          waitStateType: 'JOB',
          jobKey: '2251799813735902',
          jobType: 'charge-payment',
          jobKind: 'BPMN_ELEMENT',
          listenerEventType: null,
          retries: 3,
        },
      },
    ],
    page: {
      totalItems: 2,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
};

export {waitStateProcessInstance};

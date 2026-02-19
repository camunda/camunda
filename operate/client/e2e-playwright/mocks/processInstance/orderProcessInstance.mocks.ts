/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const orderProcessXml = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="cada200" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.2.0" camunda:diagramRelationId="d608f533-c434-4e6b-a7b5-9078caad9567">
  <bpmn:process id="order-process" isExecutable="true">
    <bpmn:startEvent id="order-placed" name="Order Placed">
      <bpmn:outgoing>Flow_0biglsj</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="order-delivered" name="Order Shipped">
      <bpmn:incoming>Flow_0yovrqa</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:exclusiveGateway id="Gateway_1qlqb7o" name="Order Value?" default="Flow_1fosyfk">
      <bpmn:incoming>Flow_1wtuk91</bpmn:incoming>
      <bpmn:outgoing>Flow_1n8m1op</bpmn:outgoing>
      <bpmn:outgoing>Flow_1fosyfk</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="Flow_1n8m1op" name="&#62;=$100" sourceRef="Gateway_1qlqb7o" targetRef="Activity_1w29h40">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=orderValue &gt;= 100</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:exclusiveGateway id="Gateway_0jji7r4">
      <bpmn:incoming>Flow_1g6qdv6</bpmn:incoming>
      <bpmn:incoming>Flow_0vv7a45</bpmn:incoming>
      <bpmn:outgoing>Flow_0yovrqa</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="Flow_0yovrqa" sourceRef="Gateway_0jji7r4" targetRef="order-delivered" />
    <bpmn:sequenceFlow id="Flow_0biglsj" sourceRef="order-placed" targetRef="Activity_0c23arx" />
    <bpmn:serviceTask id="Activity_0c23arx" name="Initiate Payment">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="initiate-payment" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_0biglsj</bpmn:incoming>
      <bpmn:outgoing>Flow_09wy0mk</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="Flow_09wy0mk" sourceRef="Activity_0c23arx" targetRef="Event_0kuuclk" />
    <bpmn:intermediateCatchEvent id="Event_0kuuclk" name="Payment Received">
      <bpmn:incoming>Flow_09wy0mk</bpmn:incoming>
      <bpmn:outgoing>Flow_1wtuk91</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageEventDefinition_14ne4ry" messageRef="Message_2f7fdra" />
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_1wtuk91" sourceRef="Event_0kuuclk" targetRef="Gateway_1qlqb7o" />
    <bpmn:sequenceFlow id="Flow_1fosyfk" sourceRef="Gateway_1qlqb7o" targetRef="Activity_089u4uu" />
    <bpmn:serviceTask id="Activity_089u4uu" name="Ship Without Insurance">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="ship-without-insurance" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1fosyfk</bpmn:incoming>
      <bpmn:outgoing>Flow_0vv7a45</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Activity_1w29h40" name="Ship With Insurance">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="ship-with-insurance" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1n8m1op</bpmn:incoming>
      <bpmn:outgoing>Flow_1g6qdv6</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="Flow_1g6qdv6" sourceRef="Activity_1w29h40" targetRef="Gateway_0jji7r4" />
    <bpmn:sequenceFlow id="Flow_0vv7a45" sourceRef="Activity_089u4uu" targetRef="Gateway_0jji7r4" />
  </bpmn:process>
  <bpmn:message id="Message_3eb3s1a" name="payment-r">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=1234" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmn:message id="Message_2f7fdra" name="payment-received">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=orderId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="order-process">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="order-placed">
        <dc:Bounds x="172" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="158" y="138" width="65" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1253stq_di" bpmnElement="order-delivered">
        <dc:Bounds x="962" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="944" y="141" width="72" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1qlqb7o_di" bpmnElement="Gateway_1qlqb7o" isMarkerVisible="true">
        <dc:Bounds x="525" y="95" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="517" y="71" width="65" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_0jji7r4_di" bpmnElement="Gateway_0jji7r4" isMarkerVisible="true">
        <dc:Bounds x="855" y="95" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1f5nuem_di" bpmnElement="Activity_0c23arx">
        <dc:Bounds x="260" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0vecbjl_di" bpmnElement="Event_0kuuclk">
        <dc:Bounds x="412" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="408" y="145" width="46" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1foa0rn_di" bpmnElement="Activity_089u4uu">
        <dc:Bounds x="670" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1dzmmde_di" bpmnElement="Activity_1w29h40">
        <dc:Bounds x="670" y="210" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1n8m1op_di" bpmnElement="Flow_1n8m1op">
        <di:waypoint x="550" y="145" />
        <di:waypoint x="550" y="230" />
        <di:waypoint x="670" y="230" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="571" y="213" width="38" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0yovrqa_di" bpmnElement="Flow_0yovrqa">
        <di:waypoint x="905" y="120" />
        <di:waypoint x="962" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0biglsj_di" bpmnElement="Flow_0biglsj">
        <di:waypoint x="208" y="120" />
        <di:waypoint x="260" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_09wy0mk_di" bpmnElement="Flow_09wy0mk">
        <di:waypoint x="360" y="120" />
        <di:waypoint x="412" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1wtuk91_di" bpmnElement="Flow_1wtuk91">
        <di:waypoint x="448" y="120" />
        <di:waypoint x="525" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1fosyfk_di" bpmnElement="Flow_1fosyfk">
        <di:waypoint x="575" y="120" />
        <di:waypoint x="670" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1g6qdv6_di" bpmnElement="Flow_1g6qdv6">
        <di:waypoint x="770" y="250" />
        <di:waypoint x="880" y="250" />
        <di:waypoint x="880" y="145" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0vv7a45_di" bpmnElement="Flow_0vv7a45">
        <di:waypoint x="770" y="120" />
        <di:waypoint x="855" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const orderProcessInstance: {
  incidentState: InstanceMock;
  incidentResolvedState: InstanceMock;
  completedState: InstanceMock;
} = {
  incidentState: {
    xml: orderProcessXml,
    detail: {
      id: '2251799813725328',
      processId: '2251799813688192',
      processName: 'order-process',
      processVersion: 2,
      startDate: '2023-09-29T07:16:22.701+0000',
      endDate: null,
      state: 'INCIDENT',
      bpmnProcessId: 'order-process',
      hasActiveOperation: false,
      operations: [],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      sortValues: [],
      tenantId: '<default>',
    },
    detailV2: {
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813688192',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.376+0000',
      state: 'ACTIVE',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: true,
    },
    callHierarchy: [],
    elementInstances: {
      items: [
        {
          elementInstanceKey: '2251799813725332',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'order-placed',
          elementName: 'Order Placed',
          type: 'START_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-09-29T07:16:22.701+0000',
          endDate: '2023-09-29T07:16:22.701+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813725334',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Activity_0c23arx',
          elementName: 'Initiate Payment',
          type: 'SERVICE_TASK',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-09-29T07:16:22.701+0000',
          endDate: '2023-09-29T07:16:38.328+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813725352',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Event_0kuuclk',
          elementName: 'Payment Received',
          type: 'INTERMEDIATE_CATCH_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-09-29T07:16:38.328+0000',
          endDate: '2023-09-29T07:16:57.379+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813725374',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Gateway_1qlqb7o',
          elementName: 'Order Value?',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'ACTIVE',
          hasIncident: true,
          startDate: '2023-09-29T07:16:57.379+0000',
          tenantId: '<default>',
        },
      ],
      page: {totalItems: 4},
    },
    statistics: {
      items: [
        {
          elementId: 'Activity_0c23arx',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Event_0kuuclk',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Gateway_1qlqb7o',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
        {
          elementId: 'order-placed',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    },
    sequenceFlows: [
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_09wy0mk',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_0biglsj',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_1wtuk91',
      },
    ],
    sequenceFlowsV2: {
      items: [
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_09wy0mk',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_0biglsj',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_1wtuk91',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
      ],
    },
    variables: [
      {
        variableKey: '2251799813725328-orderId',
        name: 'orderId',
        value: '"1234"',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '"99"',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
    ],
    incidents: {
      count: 1,
      incidents: [
        {
          id: '2251799813725375',
          errorType: {
            id: 'EXTRACT_VALUE_ERROR',
            name: 'Extract value error',
          },
          errorMessage:
            "failed to evaluate expression 'orderValue >= 100': ValString(99) can not be compared to ValNumber(100)",
          flowNodeId: 'Gateway_1qlqb7o',
          flowNodeInstanceId: '2251799813725374',
          jobId: null,
          creationTime: '2023-09-29T07:16:57.379+0000',
          hasActiveOperation: false,
          lastOperation: null,
          rootCauseInstance: {
            instanceId: '2251799813725328',
            processDefinitionId: '2251799813688192',
            processDefinitionName: 'order-process',
          },
        },
      ],
      errorTypes: [
        {
          id: 'EXTRACT_VALUE_ERROR',
          name: 'Extract value error',
          count: 1,
        },
      ],
      flowNodes: [
        {
          id: 'Gateway_1qlqb7o',
          count: 1,
        },
      ],
    },
    incidentsV2: {
      page: {totalItems: 1},
      items: [
        {
          errorMessage:
            "failed to evaluate expression 'orderValue >= 100': ValString(99) can not be compared to ValNumber(100)",
          errorType: 'EXTRACT_VALUE_ERROR',
          incidentKey: '2251799813725375',
          elementId: 'Gateway_1qlqb7o',
          elementInstanceKey: '2251799813725374',
          creationTime: '2023-09-29T07:16:57.379+0000',
          processInstanceKey: '2251799813725328',
          processDefinitionId: 'order-process',
          processDefinitionKey: '2251799813688192',
          tenantId: '<default>',
          state: 'ACTIVE',
        },
      ],
    },
  },
  incidentResolvedState: {
    xml: orderProcessXml,
    detail: {
      id: '2251799813725328',
      processId: '2251799813688192',
      processName: 'order-process',
      processVersion: 2,
      startDate: '2023-09-29T07:16:22.701+0000',
      endDate: null,
      state: 'ACTIVE',
      bpmnProcessId: 'order-process',
      hasActiveOperation: false,
      operations: [
        {
          id: '4f14121d-1f3d-47f0-8244-96e0630d4094',
          batchOperationId: '6ca28018-6348-4b18-bc8e-1f0dd04b4471',
          type: 'RESOLVE_INCIDENT',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
        {
          id: '67f4077a-fcc5-4fba-80e0-fdcb0312d1b5',
          batchOperationId: '439c2f39-dac1-4d24-a14c-4e64d6fcd0f5',
          type: 'UPDATE_VARIABLE',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
      ],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      sortValues: [],
      tenantId: '<default>',
    },
    detailV2: {
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813688192',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.376+0000',
      state: 'ACTIVE',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: false,
    },
    callHierarchy: [],
    elementInstances: {
      items: [
        {
          elementInstanceKey: '2251799813983789',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'order-placed',
          elementName: 'Order Placed',
          type: 'START_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:05:25.569+0000',
          endDate: '2023-10-02T14:05:25.569+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983791',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Activity_0c23arx',
          elementName: 'Initiate Payment',
          type: 'SERVICE_TASK',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:05:25.569+0000',
          endDate: '2023-10-02T14:06:35.639+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983851',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Event_0kuuclk',
          elementName: 'Payment Received',
          type: 'INTERMEDIATE_CATCH_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:06:35.639+0000',
          endDate: '2023-10-02T14:06:58.625+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983871',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Gateway_1qlqb7o',
          elementName: 'Order Value?',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:06:58.625+0000',
          endDate: '2023-10-02T14:07:59.691+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983945',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813688192',
          processDefinitionId: 'order-process',
          elementId: 'Activity_089u4uu',
          elementName: 'Ship Without Insurance',
          type: 'SERVICE_TASK',
          state: 'ACTIVE',
          hasIncident: false,
          startDate: '2023-10-02T14:07:59.691+0000',
          tenantId: '<default>',
        },
      ],
      page: {totalItems: 5},
    },
    statistics: {
      items: [
        {
          elementId: 'Activity_089u4uu',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
        {
          elementId: 'Activity_0c23arx',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Event_0kuuclk',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Gateway_1qlqb7o',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'order-placed',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    },
    sequenceFlows: [
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_09wy0mk',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_0biglsj',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_1fosyfk',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_1wtuk91',
      },
    ],
    sequenceFlowsV2: {
      items: [
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_09wy0mk',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_0biglsj',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_1fosyfk',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_1wtuk91',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
      ],
    },
    variables: [
      {
        variableKey: '2251799813725328-orderId',
        name: 'orderId',
        value: '1234',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
    ],
  },
  completedState: {
    xml: orderProcessXml,
    detail: {
      id: '2251799813725328',
      processId: '2251799813737336',
      processName: 'order-process',
      processVersion: 3,
      startDate: '2023-09-29T10:59:36.048+0000',
      endDate: '2023-09-29T11:01:50.073+0000',
      state: 'COMPLETED',
      bpmnProcessId: 'order-process',
      hasActiveOperation: false,
      operations: [
        {
          id: '8292773a-4cc5-4129-be11-eafe3e39a052',
          batchOperationId: 'a8104a2d-642d-46f2-ad5d-d4447b85e378',
          type: 'RESOLVE_INCIDENT',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
        {
          id: 'a3ee4353-0008-45cd-bef4-c259413bfb2f',
          batchOperationId: 'db629a0d-f9be-40b5-acbd-c3818effab72',
          type: 'UPDATE_VARIABLE',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
      ],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      sortValues: [],
      tenantId: '<default>',
    },
    detailV2: {
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813737336',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      startDate: '2023-09-29T10:59:36.048+0000',
      endDate: '2023-09-29T11:01:50.073+0000',
      state: 'COMPLETED',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: false,
    },
    callHierarchy: [],
    elementInstances: {
      items: [
        {
          elementInstanceKey: '2251799813983789',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'order-placed',
          elementName: 'Order Placed',
          type: 'START_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:05:25.569+0000',
          endDate: '2023-10-02T14:05:25.569+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983791',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'Activity_0c23arx',
          elementName: 'Initiate Payment',
          type: 'SERVICE_TASK',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:05:25.569+0000',
          endDate: '2023-10-02T14:06:35.639+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983851',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'Event_0kuuclk',
          elementName: 'Payment Received',
          type: 'INTERMEDIATE_CATCH_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:06:35.639+0000',
          endDate: '2023-10-02T14:06:58.625+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983871',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'Gateway_1qlqb7o',
          elementName: 'Order Value?',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:06:58.625+0000',
          endDate: '2023-10-02T14:07:59.691+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813983945',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'Activity_089u4uu',
          elementName: 'Ship Without Insurance',
          type: 'SERVICE_TASK',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:07:59.691+0000',
          endDate: '2023-10-02T14:12:36.295+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813984192',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'Gateway_0jji7r4',
          elementName: 'Gateway_0jji7r4',
          type: 'EXCLUSIVE_GATEWAY',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:12:36.295+0000',
          endDate: '2023-10-02T14:12:36.295+0000',
          tenantId: '<default>',
        },
        {
          elementInstanceKey: '2251799813984194',
          processInstanceKey: '2251799813725328',
          processDefinitionKey: '2251799813737336',
          processDefinitionId: 'order-process',
          elementId: 'order-delivered',
          elementName: 'Order Shipped',
          type: 'END_EVENT',
          state: 'COMPLETED',
          hasIncident: false,
          startDate: '2023-10-02T14:12:36.295+0000',
          endDate: '2023-10-02T14:12:36.295+0000',
          tenantId: '<default>',
        },
      ],
      page: {totalItems: 7},
    },
    statistics: {
      items: [
        {
          elementId: 'Activity_089u4uu',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Activity_0c23arx',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Event_0kuuclk',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Gateway_0jji7r4',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Gateway_1qlqb7o',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'order-delivered',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'order-placed',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
      ],
    },
    sequenceFlows: [
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_09wy0mk',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_0biglsj',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_0vv7a45',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_0yovrqa',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_1fosyfk',
      },
      {
        processInstanceId: '2251799813725328',
        activityId: 'Flow_1wtuk91',
      },
    ],
    sequenceFlowsV2: {
      items: [
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_09wy0mk',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_0biglsj',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_0vv7a45',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_0yovrqa',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_1fosyfk',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
        {
          processInstanceKey: '2251799813725328',
          elementId: 'Flow_1wtuk91',
          tenantId: '',
          processDefinitionId: '',
          processDefinitionKey: '',
          sequenceFlowId: '',
        },
      ],
    },
    variables: [
      {
        variableKey: '2251799813725328-orderId',
        name: 'orderId',
        value: '1234',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
      },
    ],
  },
};

export {orderProcessInstance};

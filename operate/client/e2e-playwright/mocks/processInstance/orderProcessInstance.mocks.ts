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
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813688192',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      processDefinitionVersionTag: null,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: null,
      state: 'ACTIVE',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: true,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
    sequenceFlows: {
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
        rootProcessInstanceKey: null,
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '"99"',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
        rootProcessInstanceKey: null,
      },
    ],
    incidents: {
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
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
          rootProcessInstanceKey: null,
          jobKey: '0',
        },
      ],
    },
  },
  incidentResolvedState: {
    xml: orderProcessXml,
    detail: {
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813688192',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      processDefinitionVersionTag: null,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: null,
      state: 'ACTIVE',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          endDate: null,
          tenantId: '<default>',
          rootProcessInstanceKey: null,
          incidentKey: null,
        },
      ],
      page: {
        totalItems: 5,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
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
    sequenceFlows: {
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
        rootProcessInstanceKey: null,
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
        rootProcessInstanceKey: null,
      },
    ],
  },
  completedState: {
    xml: orderProcessXml,
    detail: {
      processInstanceKey: '2251799813725328',
      processDefinitionKey: '2251799813737336',
      processDefinitionName: 'order-process',
      processDefinitionVersion: 2,
      processDefinitionVersionTag: null,
      startDate: '2023-09-29T10:59:36.048+0000',
      endDate: '2023-09-29T11:01:50.073+0000',
      state: 'COMPLETED',
      processDefinitionId: 'order-process',
      tenantId: '<default>',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
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
          rootProcessInstanceKey: null,
          incidentKey: null,
        },
      ],
      page: {
        totalItems: 7,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
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
    sequenceFlows: {
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
        rootProcessInstanceKey: null,
      },
      {
        variableKey: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isTruncated: false,
        tenantId: '',
        processInstanceKey: '2251799813725328',
        scopeKey: '2251799813725328',
        rootProcessInstanceKey: null,
      },
    ],
  },
};

export {orderProcessInstance};

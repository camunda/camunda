/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const instanceWithIncident: InstanceMock = {
  detail: {
    id: '6755399441062827',
    processId: '2251799813687188',
    processName: 'Order process',
    processVersion: 2,
    startDate: '2023-08-14T05:47:07.376+0000',
    endDate: null,
    state: 'INCIDENT',
    bpmnProcessId: 'orderProcess',
    hasActiveOperation: false,
    operations: [
      {
        id: '87ced7c0-cc22-40c5-bbe3-eafafc111520',
        batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785154',
        type: 'ADD_VARIABLE',
        state: 'COMPLETED',
        errorMessage: null,
        completedDate: null,
      },
    ],
    parentInstanceId: '6755399441062817',
    rootInstanceId: '6755399441062811',
    callHierarchy: [
      {
        instanceId: '6755399441062811',
        processDefinitionName: 'Call Activity Process',
      },
      {
        instanceId: '6755399441062817',
        processDefinitionName: 'called-process',
      },
    ],
    sortValues: [],
    tenantId: '',
  },
  detailV2: {
    processInstanceKey: '6755399441062827',
    processDefinitionKey: '2251799813687188',
    processDefinitionName: 'Order process',
    processDefinitionVersion: 2,
    parentProcessInstanceKey: '6755399441062817',
    startDate: '2023-08-14T05:47:07.376+0000',
    state: 'ACTIVE',
    processDefinitionId: 'orderProcess',
    tenantId: '',
    hasIncident: true,
  },
  callHierarchy: [
    {
      processInstanceKey: '6755399441062811',
      processDefinitionName: 'Call Activity Process',
      processDefinitionKey: '2251799813686145',
    },
    {
      processInstanceKey: '6755399441062817',
      processDefinitionName: 'called-process',
      processDefinitionKey: '2251799813687891',
    },
    {
      processInstanceKey: '6755399441062827',
      processDefinitionName: 'Order process',
      processDefinitionKey: '2251799813687188',
    },
  ],
  xml: `<?xml version="1.0" encoding="UTF-8"?>
  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.8.0">
    <bpmn:process id="orderProcess" name="Order process" isExecutable="true">
      <bpmn:startEvent id="StartEvent_1" name="Order received">
        <bpmn:outgoing>SequenceFlow_0j6tsnn</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:sequenceFlow id="SequenceFlow_0j6tsnn" sourceRef="StartEvent_1" targetRef="Task_1b1r7ow" />
      <bpmn:serviceTask id="Task_1b1r7ow" name="Check payment">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="checkPayment" />
          <zeebe:ioMapping>
            <zeebe:input source="=orderNo" target="orderId" />
            <zeebe:input source="=total" target="amountToPay" />
          </zeebe:ioMapping>
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0j6tsnn</bpmn:incoming>
        <bpmn:incoming>SequenceFlow_1q6ade7</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1s6g17c</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sendTask id="Task_162x79i" name="Ship Articles">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="shipArticles" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0drux68</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_19klrd3</bpmn:outgoing>
      </bpmn:sendTask>
      <bpmn:endEvent id="EndEvent_042s0oc">
        <bpmn:incoming>SequenceFlow_19klrd3</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_19klrd3" sourceRef="Task_162x79i" targetRef="EndEvent_042s0oc" />
      <bpmn:businessRuleTask id="Task_1t0a4uy" name="Check order items">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="checkItems" />
          <zeebe:ioMapping>
            <zeebe:input source="=items" target="orderItems" />
          </zeebe:ioMapping>
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_1dwqvrt</bpmn:incoming>
        <bpmn:incoming>SequenceFlow_01a5668</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1fgekwd</bpmn:outgoing>
      </bpmn:businessRuleTask>
      <bpmn:exclusiveGateway id="ExclusiveGateway_0n882rg" name="All items available?">
        <bpmn:incoming>SequenceFlow_1fgekwd</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_0drux68</bpmn:outgoing>
        <bpmn:outgoing>SequenceFlow_11p0iy6</bpmn:outgoing>
      </bpmn:exclusiveGateway>
      <bpmn:sequenceFlow id="SequenceFlow_1fgekwd" sourceRef="Task_1t0a4uy" targetRef="ExclusiveGateway_0n882rg" />
      <bpmn:sequenceFlow id="SequenceFlow_0drux68" name="OK" sourceRef="ExclusiveGateway_0n882rg" targetRef="Task_162x79i">
        <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=smthIsMissing = false</bpmn:conditionExpression>
      </bpmn:sequenceFlow>
      <bpmn:exclusiveGateway id="ExclusiveGateway_1qqmrb8" name="Payment OK?">
        <bpmn:incoming>SequenceFlow_1s6g17c</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1dwqvrt</bpmn:outgoing>
        <bpmn:outgoing>SequenceFlow_0jzbqu1</bpmn:outgoing>
      </bpmn:exclusiveGateway>
      <bpmn:sequenceFlow id="SequenceFlow_1s6g17c" sourceRef="Task_1b1r7ow" targetRef="ExclusiveGateway_1qqmrb8" />
      <bpmn:sequenceFlow id="SequenceFlow_1dwqvrt" sourceRef="ExclusiveGateway_1qqmrb8" targetRef="Task_1t0a4uy">
        <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=paid = true</bpmn:conditionExpression>
      </bpmn:sequenceFlow>
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
      <bpmn:scriptTask id="ServiceTask_0fiiz29" name="Request from  warehouse">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="requestWarehouse" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_11p0iy6</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_01a5668</bpmn:outgoing>
      </bpmn:scriptTask>
      <bpmn:sequenceFlow id="SequenceFlow_11p0iy6" name="Smth is missing" sourceRef="ExclusiveGateway_0n882rg" targetRef="ServiceTask_0fiiz29">
        <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=smthIsMissing = true</bpmn:conditionExpression>
      </bpmn:sequenceFlow>
      <bpmn:sequenceFlow id="SequenceFlow_01a5668" sourceRef="ServiceTask_0fiiz29" targetRef="Task_1t0a4uy" />
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
          <dc:Bounds x="895" y="98" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="EndEvent_042s0oc_di" bpmnElement="EndEvent_042s0oc">
          <dc:Bounds x="1052" y="120" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="645" y="270" width="90" height="12" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_19klrd3_di" bpmnElement="SequenceFlow_19klrd3">
          <di:waypoint x="995" y="138" />
          <di:waypoint x="1052" y="138" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="598.5" y="227" width="90" height="12" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_1mvu7vz_di" bpmnElement="Task_1t0a4uy">
          <dc:Bounds x="597" y="98" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="ExclusiveGateway_0n882rg_di" bpmnElement="ExclusiveGateway_0n882rg" isMarkerVisible="true">
          <dc:Bounds x="769" y="113" width="50" height="50" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="769" y="80" width="50" height="27" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_1fgekwd_di" bpmnElement="SequenceFlow_1fgekwd">
          <di:waypoint x="697" y="138" />
          <di:waypoint x="769" y="138" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="353" y="227" width="0" height="12" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_0drux68_di" bpmnElement="SequenceFlow_0drux68">
          <di:waypoint x="819" y="138" />
          <di:waypoint x="895" y="138" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="849" y="117" width="17" height="14" />
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
        <bpmndi:BPMNEdge id="SequenceFlow_1dwqvrt_di" bpmnElement="SequenceFlow_1dwqvrt">
          <di:waypoint x="519" y="138" />
          <di:waypoint x="597" y="138" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="178" y="227" width="0" height="12" />
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
        <bpmndi:BPMNShape id="ServiceTask_0fiiz29_di" bpmnElement="ServiceTask_0fiiz29">
          <dc:Bounds x="746" y="255" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_11p0iy6_di" bpmnElement="SequenceFlow_11p0iy6">
          <di:waypoint x="794" y="163" />
          <di:waypoint x="795" y="255" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="755" y="165" width="78" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_01a5668_di" bpmnElement="SequenceFlow_01a5668">
          <di:waypoint x="746" y="295" />
          <di:waypoint x="647" y="295" />
          <di:waypoint x="647" y="178" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="271.5" y="384" width="90" height="12" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNEdge>
      </bpmndi:BPMNPlane>
    </bpmndi:BPMNDiagram>
  </bpmn:definitions>
  `,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '6755399441062837',
        processInstanceKey: '6755399441062827',
        processDefinitionKey: '2251799813687188',
        processDefinitionId: 'orderProcess',
        elementId: 'StartEvent_1',
        elementName: 'Order received',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-08-14T05:47:07.376+0000',
        endDate: '2023-08-14T05:47:07.376+0000',
        tenantId: '',
      },
      {
        elementInstanceKey: '6755399441062840',
        processInstanceKey: '6755399441062827',
        processDefinitionKey: '2251799813687188',
        processDefinitionId: 'orderProcess',
        elementId: 'Task_1b1r7ow',
        elementName: 'Check payment',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: true,
        startDate: '2023-08-14T05:47:07.376+0000',
        tenantId: '',
      },
    ],
    page: {totalItems: 2},
  },
  variables: [
    {
      variableKey: '6755399441062827-loopCounter',
      name: 'loopCounter',
      value: '1',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '6755399441062827',
      scopeKey: '6755399441062827',
    },
    {
      variableKey: '6755399441062827-orderNo',
      name: 'orderNo',
      value: '6',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '6755399441062827',
      scopeKey: '6755399441062827',
    },
    {
      variableKey: '6755399441062827-orders',
      name: 'orders',
      value: '[6,4]',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '6755399441062827',
      scopeKey: '6755399441062827',
    },
    {
      variableKey: '6755399441062827-test',
      name: 'test',
      value: '23',
      isTruncated: false,
      tenantId: '',
      processInstanceKey: '6755399441062827',
      scopeKey: '6755399441062827',
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '6755399441062827',
      activityId: 'SequenceFlow_0j6tsnn',
    },
  ],
  sequenceFlowsV2: {
    items: [
      {
        processInstanceKey: '6755399441062827',
        elementId: 'SequenceFlow_0j6tsnn',
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
        elementId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Task_1b1r7ow',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ],
  },
  incidents: {
    count: 1,
    incidents: [
      {
        id: '6755399441062843',
        errorType: {
          id: 'IO_MAPPING_ERROR',
          name: 'I/O mapping error',
        },
        errorMessage:
          "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
        flowNodeId: 'Task_1b1r7ow',
        flowNodeInstanceId: '6755399441062840',
        jobId: null,
        creationTime: '2023-08-14T05:47:07.376+0000',
        hasActiveOperation: false,
        lastOperation: null,
        rootCauseInstance: {
          instanceId: '6755399441062827',
          processDefinitionId: '2251799813687188',
          processDefinitionName: 'Order process',
        },
      },
    ],
    errorTypes: [
      {
        id: 'IO_MAPPING_ERROR',
        name: 'I/O mapping error',
        count: 1,
      },
    ],
    flowNodes: [
      {
        id: 'Task_1b1r7ow',
        count: 1,
      },
    ],
  },
  incidentsV2: {
    page: {totalItems: 1},
    items: [
      {
        errorMessage:
          "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
        errorType: 'IO_MAPPING_ERROR',
        incidentKey: '6755399441062843',
        elementId: 'Task_1b1r7ow',
        elementInstanceKey: '6755399441062840',
        creationTime: '2023-08-14T05:47:07.376+0000',
        processInstanceKey: '6755399441062827',
        processDefinitionId: 'orderProcess',
        processDefinitionKey: '2251799813687188',
        tenantId: '<default>',
        state: 'ACTIVE',
      },
    ],
  },
  metaData: {
    flowNodeInstanceId: '6755399441062840',
    flowNodeId: null,
    flowNodeType: null,
    instanceCount: null,
    instanceMetadata: {
      flowNodeId: 'Task_1b1r7ow',
      flowNodeInstanceId: '6755399441062840',
      flowNodeType: 'SERVICE_TASK',
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: null,
      calledProcessInstanceId: null,
      calledProcessDefinitionName: null,
      calledDecisionInstanceId: null,
      calledDecisionDefinitionName: null,
      eventId: '6755399441062827_6755399441062840',
      jobId: null,
      jobType: null,
      jobRetries: null,
      jobWorker: null,
      jobDeadline: null,
      jobCustomHeaders: null,
    },
    incidentCount: 1,
    incident: {
      id: '6755399441062843',
      errorType: {
        id: 'IO_MAPPING_ERROR',
        name: 'I/O mapping error',
      },
      errorMessage:
        "failed to evaluate expression '{orderId:orderNo,amountToPay:total}': no variable found for name 'total'",
      flowNodeId: 'Task_1b1r7ow',
      flowNodeInstanceId: '6755399441062840',
      jobId: null,
      creationTime: '2023-08-14T05:47:07.376+0000',
      hasActiveOperation: false,
      lastOperation: null,
      rootCauseInstance: {
        instanceId: '6755399441062827',
        processDefinitionId: '2251799813687188',
        processDefinitionName: 'Order process',
      },
      rootCauseDecision: null,
    },
  },
};

export {instanceWithIncident};

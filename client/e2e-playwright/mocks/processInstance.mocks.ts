/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Route} from '@playwright/test';
import {
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
} from 'modules/api/fetchFlowNodeInstances';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {ProcessInstanceDetailStatisticsDto} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';
import {ProcessInstanceIncidentsDto} from 'modules/api/processInstances/fetchProcessInstanceIncidents';
import {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';

type InstanceMock = {
  xml: string;
  detail: ProcessInstanceEntity;
  flowNodeInstances: FlowNodeInstancesDto<FlowNodeInstanceDto>;
  statistics: ProcessInstanceDetailStatisticsDto[];
  sequenceFlows: SequenceFlowsDto;
  variables: VariableEntity[];
  incidents?: ProcessInstanceIncidentsDto;
  metaData?: MetaDataDto;
};

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
    permissions: [],
    tenantId: '',
  },
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
  flowNodeInstances: {
    '2251799813687144': {
      children: [
        {
          id: '2251799813687146',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: '2023-08-14T05:45:17.331+0000',
          treePath: '2251799813687144/2251799813687146',
          sortValues: ['', ''],
        },
        {
          id: '2251799813687150',
          type: 'USER_TASK',
          state: 'ACTIVE',
          flowNodeId: 'Activity_0dex012',
          startDate: '2023-08-14T05:45:17.331+0000',
          endDate: null,
          treePath: '2251799813687144/2251799813687150',
          sortValues: ['', ''],
        },
      ],
      running: null,
    },
  },
  variables: [
    {
      id: '2251799813687144-signalNumber',
      name: 'signalNumber',
      value: '47',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: true,
      sortValues: [''],
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '2251799813687144',
      activityId: 'Flow_0s222mp',
    },
  ],
  statistics: [
    {
      activityId: 'Activity_0dex012',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
    {
      activityId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
  ],
};

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
    permissions: [],
    tenantId: '',
  },
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
  flowNodeInstances: {
    '6755399441062827': {
      children: [
        {
          id: '6755399441062837',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2023-08-14T05:47:07.376+0000',
          endDate: '2023-08-14T05:47:07.376+0000',
          treePath: '6755399441062827/6755399441062837',
          sortValues: ['', ''],
        },
        {
          id: '6755399441062840',
          type: 'SERVICE_TASK',
          state: 'INCIDENT',
          flowNodeId: 'Task_1b1r7ow',
          startDate: '2023-08-14T05:47:07.376+0000',
          endDate: null,
          treePath: '6755399441062827/6755399441062840',
          sortValues: ['', ''],
        },
      ],
      running: null,
    },
  },
  variables: [
    {
      id: '6755399441062827-loopCounter',
      name: 'loopCounter',
      value: '1',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: true,
      sortValues: [''],
    },
    {
      id: '6755399441062827-orderNo',
      name: 'orderNo',
      value: '6',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
    {
      id: '6755399441062827-orders',
      name: 'orders',
      value: '[6,4]',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
    {
      id: '6755399441062827-test',
      name: 'test',
      value: '23',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '6755399441062827',
      activityId: 'SequenceFlow_0j6tsnn',
    },
  ],
  statistics: [
    {
      activityId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'Task_1b1r7ow',
      active: 0,
      canceled: 0,
      incidents: 1,
      completed: 0,
    },
  ],
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

const completedInstance: InstanceMock = {
  detail: {
    id: '2551799813954282',
    processId: '2251799813694848',
    processName: 'Timer process',
    processVersion: 4,
    startDate: '2023-10-02T06:10:47.979+0000',
    endDate: '2023-10-02T06:15:48.042+0000',
    state: 'COMPLETED',
    bpmnProcessId: 'timerProcess',
    hasActiveOperation: false,
    operations: [],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
    sortValues: [''],
    tenantId: '<default>',
  },
  flowNodeInstances: {
    '2551799813954282': {
      children: [
        {
          id: '2251799815580677',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'StartEvent_1',
          startDate: '2023-10-02T06:10:47.979+0000',
          endDate: '2023-10-02T06:10:47.984+0000',
          treePath: '2551799813954282/2251799815580677',
          sortValues: [],
        },
        {
          id: '2251799815580679',
          type: 'INTERMEDIATE_CATCH_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'IntermediateCatchEvent_1l4zjh6',
          startDate: '2023-10-02T06:10:47.989+0000',
          endDate: '2023-10-02T06:15:48.031+0000',
          treePath: '2551799813954282/2251799815580679',
          sortValues: [],
        },
        {
          id: '2251799815580946',
          type: 'END_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'EndEvent_02qhg5x',
          startDate: '2023-10-02T06:15:48.037+0000',
          endDate: '2023-10-02T06:15:48.037+0000',
          treePath: '2551799813954282/2251799815580946',
          sortValues: [],
        },
      ],
      running: null,
    },
  },
  statistics: [
    {
      activityId: 'EndEvent_02qhg5x',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'IntermediateCatchEvent_1l4zjh6',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
  ],
  xml: `<?xml version="1.0" encoding="UTF-8"?>
  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1hjjfka" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.7.0">
    <bpmn:process id="timerProcess" name="Timer process" isExecutable="true">
      <bpmn:intermediateCatchEvent id="IntermediateCatchEvent_1l4zjh6" name="5 more minutes passed">
        <bpmn:incoming>SequenceFlow_0prd963</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_15toaun</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT5M</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:intermediateCatchEvent>
      <bpmn:sequenceFlow id="SequenceFlow_0prd963" sourceRef="StartEvent_1" targetRef="IntermediateCatchEvent_1l4zjh6" />
      <bpmn:endEvent id="EndEvent_02qhg5x">
        <bpmn:incoming>SequenceFlow_15toaun</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_15toaun" sourceRef="IntermediateCatchEvent_1l4zjh6" targetRef="EndEvent_02qhg5x" />
      <bpmn:startEvent id="StartEvent_1" name="Every 3 minutes">
        <bpmn:outgoing>SequenceFlow_0prd963</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R/PT3M</bpmn:timeCycle>
        </bpmn:timerEventDefinition>
      </bpmn:startEvent>
    </bpmn:process>
    <bpmndi:BPMNDiagram id="BPMNDiagram_1">
      <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="timerProcess">
        <bpmndi:BPMNShape id="IntermediateCatchEvent_1l4zjh6_di" bpmnElement="IntermediateCatchEvent_1l4zjh6">
          <dc:Bounds x="342" y="79" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="323" y="122" width="76" height="27" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_0prd963_di" bpmnElement="SequenceFlow_0prd963">
          <di:waypoint x="215" y="97" />
          <di:waypoint x="342" y="97" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="EndEvent_02qhg5x_di" bpmnElement="EndEvent_02qhg5x">
          <dc:Bounds x="482" y="79" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_15toaun_di" bpmnElement="SequenceFlow_15toaun">
          <di:waypoint x="378" y="97" />
          <di:waypoint x="482" y="97" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="StartEvent_153jjxi_di" bpmnElement="StartEvent_1">
          <dc:Bounds x="179" y="79" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="159" y="122" width="79" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
      </bpmndi:BPMNPlane>
    </bpmndi:BPMNDiagram>
  </bpmn:definitions>
  `,
  sequenceFlows: [
    {
      processInstanceId: '2551799813954282',
      activityId: 'SequenceFlow_0prd963',
    },
    {
      processInstanceId: '2551799813954282',
      activityId: 'SequenceFlow_15toaun',
    },
  ],
  variables: [],
};

const eventBasedGatewayProcessInstance: InstanceMock = {
  detail: {
    id: '2251799813888430',
    processId: '2251799813687567',
    processName: 'Event based gateway with timer start',
    processVersion: 2,
    startDate: '2023-09-29T12:36:31.762+0000',
    endDate: null,
    state: 'INCIDENT',
    bpmnProcessId: 'eventBasedGatewayProcess',
    hasActiveOperation: false,
    operations: [],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
    tenantId: '<default>',
    sortValues: [''],
    permissions: [],
  },
  xml: `<?xml version="1.0" encoding="UTF-8"?>
  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_13jk2qx" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.5.0">
    <bpmn:process id="eventBasedGatewayProcess" name="Event based gateway with timer start" isExecutable="true">
      <bpmn:startEvent id="timerStartEvent" name="Every 10 second">
        <bpmn:outgoing>SequenceFlow_027co6p</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R20/PT10S</bpmn:timeCycle>
        </bpmn:timerEventDefinition>
      </bpmn:startEvent>
      <bpmn:sequenceFlow id="SequenceFlow_027co6p" sourceRef="timerStartEvent" targetRef="eventBasedGateway" />
      <bpmn:eventBasedGateway id="eventBasedGateway">
        <bpmn:incoming>SequenceFlow_027co6p</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1s7s2kb</bpmn:outgoing>
        <bpmn:outgoing>SequenceFlow_17b7oxn</bpmn:outgoing>
      </bpmn:eventBasedGateway>
      <bpmn:intermediateCatchEvent id="timerEvent2" name="1 minute">
        <bpmn:incoming>SequenceFlow_1s7s2kb</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_02hl6q8</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT1M</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:intermediateCatchEvent>
      <bpmn:sequenceFlow id="SequenceFlow_1s7s2kb" sourceRef="eventBasedGateway" targetRef="timerEvent2" />
      <bpmn:serviceTask id="messageTask" name="Message task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="messageTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0lplwad</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_032t727</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_02hl6q8" sourceRef="timerEvent2" targetRef="timerTask" />
      <bpmn:serviceTask id="timerTask" name="Timer task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="timerTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_02hl6q8</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1nexoc1</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:boundaryEvent id="messageBoundaryEvent" cancelActivity="false" attachedToRef="messageTask">
        <bpmn:outgoing>SequenceFlow_0ycz8rc</bpmn:outgoing>
        <bpmn:messageEventDefinition messageRef="Message_101w822" />
      </bpmn:boundaryEvent>
      <bpmn:boundaryEvent id="timerBoundaryEvent" cancelActivity="false" attachedToRef="timerTask">
        <bpmn:outgoing>SequenceFlow_1rd7tm2</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT20S</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:boundaryEvent>
      <bpmn:sequenceFlow id="SequenceFlow_0ycz8rc" sourceRef="messageBoundaryEvent" targetRef="messageTaskInterrupted" />
      <bpmn:serviceTask id="messageTaskInterrupted" name="Message task interrupted">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="messageTaskInterrupted" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0ycz8rc</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1ihvr65</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_1rd7tm2" sourceRef="timerBoundaryEvent" targetRef="timerTaskInterrupted" />
      <bpmn:sequenceFlow id="SequenceFlow_032t727" sourceRef="messageTask" targetRef="afterMessageTask" />
      <bpmn:sequenceFlow id="SequenceFlow_1nexoc1" sourceRef="timerTask" targetRef="afterTimerTask" />
      <bpmn:serviceTask id="afterTimerTask" name="After timer task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="afterTimerTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_1nexoc1</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_0177u0i</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:serviceTask id="afterMessageTask" name="After message task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="afterMessageTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_032t727</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_0c2b04f</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:exclusiveGateway id="xorGateway">
        <bpmn:incoming>SequenceFlow_0c2b04f</bpmn:incoming>
        <bpmn:incoming>SequenceFlow_0177u0i</bpmn:incoming>
        <bpmn:incoming>SequenceFlow_1ihvr65</bpmn:incoming>
        <bpmn:incoming>SequenceFlow_0lsxqvk</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_15zwgmh</bpmn:outgoing>
      </bpmn:exclusiveGateway>
      <bpmn:sequenceFlow id="SequenceFlow_0c2b04f" sourceRef="afterMessageTask" targetRef="xorGateway" />
      <bpmn:sequenceFlow id="SequenceFlow_0177u0i" sourceRef="afterTimerTask" targetRef="xorGateway" />
      <bpmn:sequenceFlow id="SequenceFlow_15zwgmh" sourceRef="xorGateway" targetRef="lastTask" />
      <bpmn:serviceTask id="lastTask" name="Last task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="lastTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_15zwgmh</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_06jk7td</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:endEvent id="EndEvent_1qx533a">
        <bpmn:incoming>SequenceFlow_06jk7td</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_06jk7td" sourceRef="lastTask" targetRef="EndEvent_1qx533a" />
      <bpmn:serviceTask id="timerTaskInterrupted" name="Timer task interrupted">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="timerTaskInterrupted" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_1rd7tm2</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_0lsxqvk</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_1ihvr65" sourceRef="messageTaskInterrupted" targetRef="xorGateway" />
      <bpmn:sequenceFlow id="SequenceFlow_0lsxqvk" sourceRef="timerTaskInterrupted" targetRef="xorGateway" />
      <bpmn:intermediateCatchEvent id="timerEvent" name="1 minute">
        <bpmn:incoming>SequenceFlow_17b7oxn</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_0lplwad</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT1M</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:intermediateCatchEvent>
      <bpmn:sequenceFlow id="SequenceFlow_17b7oxn" sourceRef="eventBasedGateway" targetRef="timerEvent" />
      <bpmn:sequenceFlow id="SequenceFlow_0lplwad" sourceRef="timerEvent" targetRef="messageTask" />
    </bpmn:process>
    <bpmn:message id="Message_101w822" name="interruptMessageTask">
      <bpmn:extensionElements>
        <zeebe:subscription correlationKey="=clientId" />
      </bpmn:extensionElements>
    </bpmn:message>
    <bpmn:message id="Message_0heterz" name="clientMessage">
      <bpmn:extensionElements>
        <zeebe:subscription correlationKey="=clientId" />
      </bpmn:extensionElements>
    </bpmn:message>
    <bpmndi:BPMNDiagram id="BPMNDiagram_1">
      <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="eventBasedGatewayProcess">
        <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="timerStartEvent">
          <dc:Bounds x="198" y="343" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="176" y="386" width="82" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_027co6p_di" bpmnElement="SequenceFlow_027co6p">
          <di:waypoint x="234" y="361" />
          <di:waypoint x="334" y="361" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="EventBasedGateway_0bks5dl_di" bpmnElement="eventBasedGateway">
          <dc:Bounds x="334" y="336" width="50" height="50" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="IntermediateCatchEvent_0e4j08h_di" bpmnElement="timerEvent2">
          <dc:Bounds x="415" y="459" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="412" y="495" width="43" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_1s7s2kb_di" bpmnElement="SequenceFlow_1s7s2kb">
          <di:waypoint x="359" y="386" />
          <di:waypoint x="359" y="477" />
          <di:waypoint x="415" y="477" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_1ez2aer_di" bpmnElement="messageTask">
          <dc:Bounds x="535" y="220" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_02hl6q8_di" bpmnElement="SequenceFlow_02hl6q8">
          <di:waypoint x="451" y="477" />
          <di:waypoint x="493" y="477" />
          <di:waypoint x="493" y="478" />
          <di:waypoint x="535" y="478" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_176yzv4_di" bpmnElement="timerTask">
          <dc:Bounds x="535" y="438" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="BoundaryEvent_02bgfst_di" bpmnElement="messageBoundaryEvent">
          <dc:Bounds x="583" y="202" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="BoundaryEvent_02j2uos_di" bpmnElement="timerBoundaryEvent">
          <dc:Bounds x="583" y="500" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_0ycz8rc_di" bpmnElement="SequenceFlow_0ycz8rc">
          <di:waypoint x="601" y="202" />
          <di:waypoint x="601" y="123" />
          <di:waypoint x="674" y="123" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_0sk6p4c_di" bpmnElement="messageTaskInterrupted">
          <dc:Bounds x="674" y="83" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_1rd7tm2_di" bpmnElement="SequenceFlow_1rd7tm2">
          <di:waypoint x="601" y="536" />
          <di:waypoint x="601" y="591" />
          <di:waypoint x="674" y="591" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_032t727_di" bpmnElement="SequenceFlow_032t727">
          <di:waypoint x="635" y="260" />
          <di:waypoint x="674" y="260" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_1nexoc1_di" bpmnElement="SequenceFlow_1nexoc1">
          <di:waypoint x="635" y="478" />
          <di:waypoint x="674" y="478" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_0cupq44_di" bpmnElement="afterTimerTask">
          <dc:Bounds x="674" y="438" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="ServiceTask_0cbtrof_di" bpmnElement="afterMessageTask">
          <dc:Bounds x="674" y="220" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="ExclusiveGateway_19unh1q_di" bpmnElement="xorGateway" isMarkerVisible="true">
          <dc:Bounds x="889" y="336" width="50" height="50" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_0c2b04f_di" bpmnElement="SequenceFlow_0c2b04f">
          <di:waypoint x="774" y="260" />
          <di:waypoint x="914" y="260" />
          <di:waypoint x="914" y="336" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_0177u0i_di" bpmnElement="SequenceFlow_0177u0i">
          <di:waypoint x="774" y="478" />
          <di:waypoint x="914" y="478" />
          <di:waypoint x="914" y="386" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_15zwgmh_di" bpmnElement="SequenceFlow_15zwgmh">
          <di:waypoint x="939" y="361" />
          <di:waypoint x="1002" y="361" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_1wbh4o2_di" bpmnElement="lastTask">
          <dc:Bounds x="1002" y="321" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="EndEvent_1qx533a_di" bpmnElement="EndEvent_1qx533a">
          <dc:Bounds x="1156" y="343" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_06jk7td_di" bpmnElement="SequenceFlow_06jk7td">
          <di:waypoint x="1102" y="361" />
          <di:waypoint x="1156" y="361" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="ServiceTask_0ftq841_di" bpmnElement="timerTaskInterrupted">
          <dc:Bounds x="674" y="551" width="100" height="80" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_1ihvr65_di" bpmnElement="SequenceFlow_1ihvr65">
          <di:waypoint x="774" y="123" />
          <di:waypoint x="914" y="123" />
          <di:waypoint x="914" y="336" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_0lsxqvk_di" bpmnElement="SequenceFlow_0lsxqvk">
          <di:waypoint x="774" y="591" />
          <di:waypoint x="914" y="591" />
          <di:waypoint x="914" y="386" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNShape id="IntermediateCatchEvent_1j66gos_di" bpmnElement="timerEvent">
          <dc:Bounds x="423" y="242" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="420" y="285" width="43" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="SequenceFlow_17b7oxn_di" bpmnElement="SequenceFlow_17b7oxn">
          <di:waypoint x="359" y="336" />
          <di:waypoint x="359" y="260" />
          <di:waypoint x="423" y="260" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="SequenceFlow_0lplwad_di" bpmnElement="SequenceFlow_0lplwad">
          <di:waypoint x="459" y="260" />
          <di:waypoint x="535" y="260" />
        </bpmndi:BPMNEdge>
      </bpmndi:BPMNPlane>
    </bpmndi:BPMNDiagram>
  </bpmn:definitions>
  `,
  flowNodeInstances: {
    '2251799813888430': {
      children: [
        {
          id: '2251799813888432',
          type: 'START_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'timerStartEvent',
          startDate: '2023-09-29T12:36:31.762+0000',
          endDate: '2023-09-29T12:36:31.762+0000',
          treePath: '2251799813888430/2251799813888432',
          sortValues: [],
        },
        {
          id: '2251799813888434',
          type: 'EVENT_BASED_GATEWAY',
          state: 'COMPLETED',
          flowNodeId: 'eventBasedGateway',
          startDate: '2023-09-29T12:36:31.762+0000',
          endDate: '2023-09-29T12:37:31.772+0000',
          treePath: '2251799813888430/2251799813888434',
          sortValues: [],
        },
        {
          id: '2251799813901249',
          type: 'INTERMEDIATE_CATCH_EVENT',
          state: 'COMPLETED',
          flowNodeId: 'timerEvent',
          startDate: '2023-09-29T12:37:31.772+0000',
          endDate: '2023-09-29T12:37:31.772+0000',
          treePath: '2251799813888430/2251799813901249',
          sortValues: [],
        },
        {
          id: '2251799813901251',
          type: 'SERVICE_TASK',
          state: 'INCIDENT',
          flowNodeId: 'messageTask',
          startDate: '2023-09-29T12:37:31.772+0000',
          endDate: null,
          treePath: '2251799813888430/2251799813901251',
          sortValues: [],
        },
      ],
      running: null,
    },
  },
  variables: [
    {
      id: '6755399441062827-loopCounter',
      name: 'loopCounter',
      value: '1',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: true,
      sortValues: [''],
    },
    {
      id: '6755399441062827-orderNo',
      name: 'orderNo',
      value: '6',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
    {
      id: '6755399441062827-orders',
      name: 'orders',
      value: '[6,4]',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
    {
      id: '6755399441062827-test',
      name: 'test',
      value: '23',
      isPreview: false,
      hasActiveOperation: false,
      isFirst: false,
      sortValues: [''],
    },
  ],
  sequenceFlows: [
    {
      processInstanceId: '2251799813888430',
      activityId: 'SequenceFlow_027co6p',
    },
    {
      processInstanceId: '2251799813888430',
      activityId: 'SequenceFlow_0lplwad',
    },
  ],
  statistics: [
    {
      activityId: 'eventBasedGateway',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'messageTask',
      active: 0,
      canceled: 0,
      incidents: 1,
      completed: 0,
    },
    {
      activityId: 'timerEvent',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      activityId: 'timerStartEvent',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
  ],
  incidents: {
    count: 1,
    incidents: [
      {
        id: '2251799813901252',
        errorType: {
          id: 'EXTRACT_VALUE_ERROR',
          name: 'Extract value error',
        },
        errorMessage:
          "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
        flowNodeId: 'messageTask',
        flowNodeInstanceId: '2251799813901251',
        jobId: null,
        creationTime: '2023-09-29T12:37:31.772+0000',
        hasActiveOperation: false,
        lastOperation: null,
        rootCauseInstance: {
          instanceId: '2251799813888430',
          processDefinitionId: '2251799813687567',
          processDefinitionName: 'Event based gateway with timer start',
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
        id: 'messageTask',
        count: 1,
      },
    ],
  },
  metaData: {
    flowNodeInstanceId: '2251799813899087',
    flowNodeId: null,
    flowNodeType: null,
    instanceCount: null,
    instanceMetadata: {
      flowNodeId: 'messageTask',
      flowNodeInstanceId: '2251799813899087',
      flowNodeType: 'SERVICE_TASK',
      startDate: '2023-09-29T12:37:21.753+0000',
      endDate: null,
      calledProcessInstanceId: null,
      calledProcessDefinitionName: null,
      calledDecisionInstanceId: null,
      calledDecisionDefinitionName: null,
      eventId: '2251799813886105_2251799813899087',
      jobType: null,
      jobRetries: null,
      jobWorker: null,
      jobDeadline: null,
      jobCustomHeaders: null,
      jobId: '',
    },
    incidentCount: 1,
    incident: {
      id: '2251799813899088',
      errorType: {
        id: 'EXTRACT_VALUE_ERROR',
        name: 'Extract value error',
      },
      errorMessage:
        "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
      flowNodeId: 'messageTask',
      flowNodeInstanceId: '2251799813899087',
      jobId: null,
      creationTime: '2023-09-29T12:37:21.753+0000',
      hasActiveOperation: false,
      lastOperation: null,
      rootCauseInstance: {
        instanceId: '2251799813886105',
        processDefinitionId: '2251799813687567',
        processDefinitionName: 'Event based gateway with timer start',
      },
      rootCauseDecision: null,
    },
  },
};

const orderProcessXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="cada200" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.2.0" camunda:diagramRelationId="d608f533-c434-4e6b-a7b5-9078caad9567">
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
</bpmn:definitions>
`;
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
      permissions: [],
      tenantId: '<default>',
    },
    flowNodeInstances: {
      '2251799813725328': {
        children: [
          {
            id: '2251799813725332',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'order-placed',
            startDate: '2023-09-29T07:16:22.701+0000',
            endDate: '2023-09-29T07:16:22.701+0000',
            treePath: '2251799813725328/2251799813725332',
            sortValues: [],
          },
          {
            id: '2251799813725334',
            type: 'SERVICE_TASK',
            state: 'COMPLETED',
            flowNodeId: 'Activity_0c23arx',
            startDate: '2023-09-29T07:16:22.701+0000',
            endDate: '2023-09-29T07:16:38.328+0000',
            treePath: '2251799813725328/2251799813725334',
            sortValues: [],
          },
          {
            id: '2251799813725352',
            type: 'INTERMEDIATE_CATCH_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'Event_0kuuclk',
            startDate: '2023-09-29T07:16:38.328+0000',
            endDate: '2023-09-29T07:16:57.379+0000',
            treePath: '2251799813725328/2251799813725352',
            sortValues: [],
          },
          {
            id: '2251799813725374',
            type: 'EXCLUSIVE_GATEWAY',
            state: 'INCIDENT',
            flowNodeId: 'Gateway_1qlqb7o',
            startDate: '2023-09-29T07:16:57.379+0000',
            endDate: null,
            treePath: '2251799813725328/2251799813725374',
            sortValues: [],
          },
        ],
        running: null,
      },
    },
    statistics: [
      {
        activityId: 'Activity_0c23arx',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Event_0kuuclk',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Gateway_1qlqb7o',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'order-placed',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
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
    variables: [
      {
        id: '2251799813725328-orderId',
        name: 'orderId',
        value: '"1234"',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: true,
        sortValues: [''],
      },
      {
        id: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '"99"',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: false,
        sortValues: [''],
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
        },
        {
          id: '67f4077a-fcc5-4fba-80e0-fdcb0312d1b5',
          batchOperationId: '439c2f39-dac1-4d24-a14c-4e64d6fcd0f5',
          type: 'UPDATE_VARIABLE',
          state: 'COMPLETED',
          errorMessage: null,
        },
      ],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      sortValues: [],
      permissions: [],
      tenantId: '<default>',
    },
    flowNodeInstances: {
      '2251799813725328': {
        children: [
          {
            id: '2251799813983789',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'order-placed',
            startDate: '2023-10-02T14:05:25.569+0000',
            endDate: '2023-10-02T14:05:25.569+0000',
            treePath: '2251799813725328/2251799813983789',
            sortValues: [],
          },
          {
            id: '2251799813983791',
            type: 'SERVICE_TASK',
            state: 'COMPLETED',
            flowNodeId: 'Activity_0c23arx',
            startDate: '2023-10-02T14:05:25.569+0000',
            endDate: '2023-10-02T14:06:35.639+0000',
            treePath: '2251799813725328/2251799813983791',
            sortValues: [],
          },
          {
            id: '2251799813983851',
            type: 'INTERMEDIATE_CATCH_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'Event_0kuuclk',
            startDate: '2023-10-02T14:06:35.639+0000',
            endDate: '2023-10-02T14:06:58.625+0000',
            treePath: '2251799813725328/2251799813983851',
            sortValues: [],
          },
          {
            id: '2251799813983871',
            type: 'EXCLUSIVE_GATEWAY',
            state: 'COMPLETED',
            flowNodeId: 'Gateway_1qlqb7o',
            startDate: '2023-10-02T14:06:58.625+0000',
            endDate: '2023-10-02T14:07:59.691+0000',
            treePath: '2251799813725328/2251799813983871',
            sortValues: [],
          },
          {
            id: '2251799813983945',
            type: 'SERVICE_TASK',
            state: 'ACTIVE',
            flowNodeId: 'Activity_089u4uu',
            startDate: '2023-10-02T14:07:59.691+0000',
            endDate: null,
            treePath: '2251799813725328/2251799813983945',
            sortValues: [],
          },
        ],
        running: null,
      },
    },
    statistics: [
      {
        activityId: 'Activity_089u4uu',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'Activity_0c23arx',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Event_0kuuclk',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Gateway_1qlqb7o',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'order-placed',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
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
    variables: [
      {
        id: '2251799813725328-orderId',
        name: 'orderId',
        value: '"1234"',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: true,
        sortValues: [''],
      },
      {
        id: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: false,
        sortValues: [''],
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
        },
        {
          id: 'a3ee4353-0008-45cd-bef4-c259413bfb2f',
          batchOperationId: 'db629a0d-f9be-40b5-acbd-c3818effab72',
          type: 'UPDATE_VARIABLE',
          state: 'COMPLETED',
          errorMessage: null,
        },
      ],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      sortValues: [],
      permissions: [],
      tenantId: '<default>',
    },
    flowNodeInstances: {
      '2251799813725328': {
        children: [
          {
            id: '2251799813983789',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'order-placed',
            startDate: '2023-10-02T14:05:25.569+0000',
            endDate: '2023-10-02T14:05:25.569+0000',
            treePath: '2251799813725328/2251799813983789',
            sortValues: [],
          },
          {
            id: '2251799813983791',
            type: 'SERVICE_TASK',
            state: 'COMPLETED',
            flowNodeId: 'Activity_0c23arx',
            startDate: '2023-10-02T14:05:25.569+0000',
            endDate: '2023-10-02T14:06:35.639+0000',
            treePath: '2251799813725328/2251799813983791',
            sortValues: [],
          },
          {
            id: '2251799813983851',
            type: 'INTERMEDIATE_CATCH_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'Event_0kuuclk',
            startDate: '2023-10-02T14:06:35.639+0000',
            endDate: '2023-10-02T14:06:58.625+0000',
            treePath: '2251799813725328/2251799813983851',
            sortValues: [],
          },
          {
            id: '2251799813983871',
            type: 'EXCLUSIVE_GATEWAY',
            state: 'COMPLETED',
            flowNodeId: 'Gateway_1qlqb7o',
            startDate: '2023-10-02T14:06:58.625+0000',
            endDate: '2023-10-02T14:07:59.691+0000',
            treePath: '2251799813725328/2251799813983871',
            sortValues: [],
          },
          {
            id: '2251799813983945',
            type: 'SERVICE_TASK',
            state: 'COMPLETED',
            flowNodeId: 'Activity_089u4uu',
            startDate: '2023-10-02T14:07:59.691+0000',
            endDate: '2023-10-02T14:12:36.295+0000',
            treePath: '2251799813725328/2251799813983945',
            sortValues: [],
          },
          {
            id: '2251799813984192',
            type: 'EXCLUSIVE_GATEWAY',
            state: 'COMPLETED',
            flowNodeId: 'Gateway_0jji7r4',
            startDate: '2023-10-02T14:12:36.295+0000',
            endDate: '2023-10-02T14:12:36.295+0000',
            treePath: '2251799813725328/2251799813984192',
            sortValues: [],
          },
          {
            id: '2251799813984194',
            type: 'END_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'order-delivered',
            startDate: '2023-10-02T14:12:36.295+0000',
            endDate: '2023-10-02T14:12:36.295+0000',
            treePath: '2251799813725328/2251799813984194',
            sortValues: [],
          },
        ],
        running: null,
      },
    },
    statistics: [
      {
        activityId: 'Activity_089u4uu',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Activity_0c23arx',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Event_0kuuclk',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Gateway_0jji7r4',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Gateway_1qlqb7o',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'order-delivered',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'order-placed',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
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
    variables: [
      {
        id: '2251799813725328-orderId',
        name: 'orderId',
        value: '"1234"',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: true,
        sortValues: [''],
      },
      {
        id: '2251799813725328-orderValue',
        name: 'orderValue',
        value: '99',
        isPreview: false,
        hasActiveOperation: false,
        isFirst: false,
        sortValues: [''],
      },
    ],
  },
};

function mockResponses({
  processInstanceDetail,
  flowNodeInstances,
  statistics,
  sequenceFlows,
  variables,
  xml,
  incidents,
  metaData,
}: {
  processInstanceDetail?: ProcessInstanceEntity;
  flowNodeInstances?: FlowNodeInstancesDto<FlowNodeInstanceDto>;
  statistics?: ProcessInstanceDetailStatisticsDto[];
  sequenceFlows?: SequenceFlowsDto;
  variables?: VariableEntity[];
  xml?: string;
  incidents?: ProcessInstanceIncidentsDto;
  metaData?: MetaDataDto;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/flow-node-instances')) {
      return route.fulfill({
        status: flowNodeInstances === undefined ? 400 : 200,
        body: JSON.stringify(flowNodeInstances),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('statistics')) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('sequence-flows')) {
      return route.fulfill({
        status: sequenceFlows === undefined ? 400 : 200,
        body: JSON.stringify(sequenceFlows),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('variables')) {
      return route.fulfill({
        status: variables === undefined ? 400 : 200,
        body: JSON.stringify(variables),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: JSON.stringify(xml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('incidents')) {
      return route.fulfill({
        status: incidents === undefined ? 400 : 200,
        body: JSON.stringify(incidents),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('flow-node-metadata')) {
      return route.fulfill({
        status: metaData === undefined ? 400 : 200,
        body: JSON.stringify(metaData),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/process-instances/')) {
      return route.fulfill({
        status: processInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(processInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/modify')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: '5f663d9d-1b0e-4243-90d9-43370f4b707c',
          name: null,
          type: 'MODIFY_PROCESS_INSTANCE',
          startDate: '2023-10-04T11:35:28.241+0200',
          endDate: null,
          username: 'demo',
          instancesCount: 1,
          operationsTotalCount: 1,
          operationsFinishedCount: 0,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/operation')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: '4dccc4e0-7658-49d9-9361-cf9e73ee2052',
          name: null,
          type: 'DELETE_PROCESS_INSTANCE',
          startDate: '2023-10-04T14:25:23.613+0200',
          endDate: null,
          username: 'demo',
          instancesCount: 1,
          operationsTotalCount: 1,
          operationsFinishedCount: 0,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

export {
  runningInstance,
  instanceWithIncident,
  completedInstance,
  eventBasedGatewayProcessInstance,
  orderProcessInstance,
  mockResponses,
};

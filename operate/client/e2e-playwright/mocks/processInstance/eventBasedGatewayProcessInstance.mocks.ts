/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

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
  },
  detailV2: {
    processInstanceKey: '2251799813888430',
    processDefinitionKey: '2251799813687567',
    processDefinitionName: 'Event based gateway with timer start',
    processDefinitionVersion: 2,
    startDate: '2023-09-29T12:36:31.762+0000',
    state: 'ACTIVE',
    processDefinitionId: 'eventBasedGatewayProcess',
    tenantId: '<default>',
    hasIncident: true,
  },
  callHierarchy: [],
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
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813888432',
        processInstanceKey: '2251799813888430',
        processDefinitionKey: '2251799813687567',
        processDefinitionId: 'eventBasedGatewayProcess',
        elementId: 'timerStartEvent',
        elementName: 'Every 10 second',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-09-29T12:36:31.762+0000',
        endDate: '2023-09-29T12:36:31.762+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813888434',
        processInstanceKey: '2251799813888430',
        processDefinitionKey: '2251799813687567',
        processDefinitionId: 'eventBasedGatewayProcess',
        elementId: 'eventBasedGateway',
        elementName: 'eventBasedGateway',
        type: 'EVENT_BASED_GATEWAY',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-09-29T12:36:31.762+0000',
        endDate: '2023-09-29T12:37:31.772+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813901249',
        processInstanceKey: '2251799813888430',
        processDefinitionKey: '2251799813687567',
        processDefinitionId: 'eventBasedGatewayProcess',
        elementId: 'timerEvent',
        elementName: '1 minute',
        type: 'INTERMEDIATE_CATCH_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-09-29T12:37:31.772+0000',
        endDate: '2023-09-29T12:37:31.772+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799813901251',
        processInstanceKey: '2251799813888430',
        processDefinitionKey: '2251799813687567',
        processDefinitionId: 'eventBasedGatewayProcess',
        elementId: 'messageTask',
        elementName: 'Message task',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: true,
        startDate: '2023-09-29T12:37:31.772+0000',
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 4},
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
      processInstanceId: '2251799813888430',
      activityId: 'SequenceFlow_027co6p',
    },
    {
      processInstanceId: '2251799813888430',
      activityId: 'SequenceFlow_0lplwad',
    },
  ],
  sequenceFlowsV2: {
    items: [
      {
        processInstanceKey: '2251799813888430',
        elementId: 'SequenceFlow_027co6p',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
      {
        processInstanceKey: '2251799813888430',
        elementId: 'SequenceFlow_0lplwad',
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
        elementId: 'eventBasedGateway',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'messageTask',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        elementId: 'timerEvent',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'timerStartEvent',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
  },
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
  incidentsV2: {
    page: {totalItems: 1},
    items: [
      {
        errorMessage:
          "failed to evaluate expression 'clientId': no variable found for name 'clientId'",
        errorType: 'EXTRACT_VALUE_ERROR',
        incidentKey: '2251799813901252',
        elementId: 'messageTask',
        elementInstanceKey: '2251799813901251',
        creationTime: '2023-09-29T12:37:31.772+0000',
        processInstanceKey: '2251799813888430',
        processDefinitionId: 'eventBasedGatewayProcess',
        processDefinitionKey: '2251799813687567',
        tenantId: '<default>',
        state: 'ACTIVE',
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

export {eventBasedGatewayProcessInstance};

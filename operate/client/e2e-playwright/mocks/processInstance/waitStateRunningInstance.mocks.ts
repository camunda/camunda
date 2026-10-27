/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.10';
import {runningInstance} from './';
import type {InstanceMock} from '.';

const PROCESS_INSTANCE_KEY = '2251799813687144';
const USER_TASK_INSTANCE_KEY = '2251799813687150';
const SERVICE_TASK_INSTANCE_KEY = '2251799813687170';

// Only the elements whose details panel is opened by the test need search
// items; the diagram waiting-badge counts come from the wait state statistics
// endpoint (waitStateStatistics) instead.
const waitStateItems: ElementInstanceInspection[] = [
  {
    rootProcessInstanceKey: null,
    processInstanceKey: PROCESS_INSTANCE_KEY,
    elementInstanceKey: USER_TASK_INSTANCE_KEY,
    elementId: 'Activity_0dex012',
    elementType: 'USER_TASK',
    tenantId: '<default>',
    bpmnProcessId: 'signalEventProcess',
    details: {
      waitStateType: 'USER_TASK',
      taskKey: '2251799813687160',
      dueDate: null,
    },
  },
  {
    rootProcessInstanceKey: null,
    processInstanceKey: PROCESS_INSTANCE_KEY,
    elementInstanceKey: USER_TASK_INSTANCE_KEY,
    elementId: 'Activity_0dex012',
    elementType: 'USER_TASK',
    tenantId: '<default>',
    bpmnProcessId: 'signalEventProcess',
    details: {
      waitStateType: 'JOB',
      jobKey: '2251799813687162',
      jobType: 'notify-assignee',
      jobKind: 'TASK_LISTENER',
      listenerEventType: 'ASSIGNING',
      retries: 3,
    },
  },
  {
    rootProcessInstanceKey: null,
    processInstanceKey: PROCESS_INSTANCE_KEY,
    elementInstanceKey: SERVICE_TASK_INSTANCE_KEY,
    elementId: 'Activity_charge',
    elementType: 'SERVICE_TASK',
    tenantId: '<default>',
    bpmnProcessId: 'signalEventProcess',
    details: {
      waitStateType: 'JOB',
      jobKey: '2251799813687172',
      jobType: 'charge-payment',
      jobKind: 'BPMN_ELEMENT',
      listenerEventType: null,
      retries: 3,
    },
  },
];

// A running instance with a Camunda user task (2 tokens, 2 wait states), a
// service task (1 token, 1 wait state) and four signal catch events (1, 5, 11
// and 333 wait states), used to exercise the wait state diagram labels and the
// Details tab wait state list.
const waitStateRunningInstance: InstanceMock = {
  ...runningInstance,
  xml: `<?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1w68912" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.6.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.1.0">
      <bpmn:process id="signalEventProcess" name="Signal event" isExecutable="true">
        <bpmn:sequenceFlow id="Flow_0s222mp" sourceRef="StartEvent_1" targetRef="Activity_0dex012" />
        <bpmn:endEvent id="Event_0uqq9ok">
          <bpmn:incoming>Flow_signal333_end</bpmn:incoming>
        </bpmn:endEvent>
        <bpmn:sequenceFlow id="Flow_00wmwdw" sourceRef="Activity_0dex012" targetRef="Activity_charge" />
        <bpmn:sequenceFlow id="Flow_charge_signal1" sourceRef="Activity_charge" targetRef="Event_signal_1" />
        <bpmn:sequenceFlow id="Flow_signal1_signal5" sourceRef="Event_signal_1" targetRef="Event_signal_5" />
        <bpmn:sequenceFlow id="Flow_signal5_signal11" sourceRef="Event_signal_5" targetRef="Event_signal_11" />
        <bpmn:sequenceFlow id="Flow_signal11_signal333" sourceRef="Event_signal_11" targetRef="Event_signal_333" />
        <bpmn:sequenceFlow id="Flow_signal333_end" sourceRef="Event_signal_333" targetRef="Event_0uqq9ok" />
        <bpmn:startEvent id="StartEvent_1">
          <bpmn:outgoing>Flow_0s222mp</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_0fuv1s9" signalRef="Signal_1jnfrnu" />
        </bpmn:startEvent>
        <bpmn:userTask id="Activity_0dex012" name="Signal user task">
          <bpmn:extensionElements>
            <zeebe:userTask />
          </bpmn:extensionElements>
          <bpmn:incoming>Flow_0s222mp</bpmn:incoming>
          <bpmn:outgoing>Flow_00wmwdw</bpmn:outgoing>
        </bpmn:userTask>
        <bpmn:serviceTask id="Activity_charge" name="Charge payment">
          <bpmn:extensionElements>
            <zeebe:taskDefinition type="charge-payment" />
          </bpmn:extensionElements>
          <bpmn:incoming>Flow_00wmwdw</bpmn:incoming>
          <bpmn:outgoing>Flow_charge_signal1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:intermediateCatchEvent id="Event_signal_1" name="Wait for signal">
          <bpmn:incoming>Flow_charge_signal1</bpmn:incoming>
          <bpmn:outgoing>Flow_signal1_signal5</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_1" signalRef="Signal_1jnfrnu" />
        </bpmn:intermediateCatchEvent>
        <bpmn:intermediateCatchEvent id="Event_signal_5" name="Wait for signal">
          <bpmn:incoming>Flow_signal1_signal5</bpmn:incoming>
          <bpmn:outgoing>Flow_signal5_signal11</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_5" signalRef="Signal_1jnfrnu" />
        </bpmn:intermediateCatchEvent>
        <bpmn:intermediateCatchEvent id="Event_signal_11" name="Wait for signal">
          <bpmn:incoming>Flow_signal5_signal11</bpmn:incoming>
          <bpmn:outgoing>Flow_signal11_signal333</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_11" signalRef="Signal_1jnfrnu" />
        </bpmn:intermediateCatchEvent>
        <bpmn:intermediateCatchEvent id="Event_signal_333" name="Wait for signal">
          <bpmn:incoming>Flow_signal11_signal333</bpmn:incoming>
          <bpmn:outgoing>Flow_signal333_end</bpmn:outgoing>
          <bpmn:signalEventDefinition id="SignalEventDefinition_333" signalRef="Signal_1jnfrnu" />
        </bpmn:intermediateCatchEvent>
      </bpmn:process>
      <bpmn:signal id="Signal_1jnfrnu" name="startSignal1" />
      <bpmndi:BPMNDiagram id="BPMNDiagram_1">
        <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="signalEventProcess">
          <bpmndi:BPMNShape id="Event_0uqq9ok_di" bpmnElement="Event_0uqq9ok">
            <dc:Bounds x="1022" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_14abjwl_di" bpmnElement="StartEvent_1">
            <dc:Bounds x="179" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Activity_1427447_di" bpmnElement="Activity_0dex012">
            <dc:Bounds x="280" y="77" width="100" height="80" />
            <bpmndi:BPMNLabel />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Activity_charge_di" bpmnElement="Activity_charge">
            <dc:Bounds x="440" y="77" width="100" height="80" />
            <bpmndi:BPMNLabel />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_signal_1_di" bpmnElement="Event_signal_1">
            <dc:Bounds x="622" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_signal_5_di" bpmnElement="Event_signal_5">
            <dc:Bounds x="722" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_signal_11_di" bpmnElement="Event_signal_11">
            <dc:Bounds x="822" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNShape id="Event_signal_333_di" bpmnElement="Event_signal_333">
            <dc:Bounds x="922" y="99" width="36" height="36" />
          </bpmndi:BPMNShape>
          <bpmndi:BPMNEdge id="Flow_0s222mp_di" bpmnElement="Flow_0s222mp">
            <di:waypoint x="215" y="117" />
            <di:waypoint x="280" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_00wmwdw_di" bpmnElement="Flow_00wmwdw">
            <di:waypoint x="380" y="117" />
            <di:waypoint x="440" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_charge_signal1_di" bpmnElement="Flow_charge_signal1">
            <di:waypoint x="540" y="117" />
            <di:waypoint x="622" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_signal1_signal5_di" bpmnElement="Flow_signal1_signal5">
            <di:waypoint x="658" y="117" />
            <di:waypoint x="722" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_signal5_signal11_di" bpmnElement="Flow_signal5_signal11">
            <di:waypoint x="758" y="117" />
            <di:waypoint x="822" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_signal11_signal333_di" bpmnElement="Flow_signal11_signal333">
            <di:waypoint x="858" y="117" />
            <di:waypoint x="922" y="117" />
          </bpmndi:BPMNEdge>
          <bpmndi:BPMNEdge id="Flow_signal333_end_di" bpmnElement="Flow_signal333_end">
            <di:waypoint x="958" y="117" />
            <di:waypoint x="1022" y="117" />
          </bpmndi:BPMNEdge>
        </bpmndi:BPMNPlane>
      </bpmndi:BPMNDiagram>
    </bpmn:definitions>
    `,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813687146',
        processInstanceKey: PROCESS_INSTANCE_KEY,
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
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: USER_TASK_INSTANCE_KEY,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: '2251799813686165',
        processDefinitionId: 'signalEventProcess',
        elementId: 'Activity_0dex012',
        elementName: 'Signal user task',
        type: 'USER_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: null,
        tenantId: '',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: SERVICE_TASK_INSTANCE_KEY,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: '2251799813686165',
        processDefinitionId: 'signalEventProcess',
        elementId: 'Activity_charge',
        elementName: 'Charge payment',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2023-08-14T05:45:17.331+0000',
        endDate: null,
        tenantId: '',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
    ],
    page: {
      totalItems: 3,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  statistics: {
    items: [
      {
        elementId: 'Activity_0dex012',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Activity_charge',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Event_signal_1',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Event_signal_5',
        active: 5,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Event_signal_11',
        active: 11,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Event_signal_333',
        active: 333,
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
  waitStates: {
    items: waitStateItems,
    page: {
      totalItems: waitStateItems.length,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  // Diagram waiting-badge counts: exercises 1-, 2- and 3-digit label widths
  // ("Waiting", "2 waiting", "5 waiting", "11 waiting", "333 waiting") and the
  // narrow-element centering offset on the signal catch events.
  waitStateStatistics: {
    items: [
      {elementId: 'Activity_0dex012', waitingCount: 2},
      {elementId: 'Activity_charge', waitingCount: 1},
      {elementId: 'Event_signal_1', waitingCount: 1},
      {elementId: 'Event_signal_5', waitingCount: 5},
      {elementId: 'Event_signal_11', waitingCount: 11},
      {elementId: 'Event_signal_333', waitingCount: 333},
    ],
  },
};

export {waitStateRunningInstance};

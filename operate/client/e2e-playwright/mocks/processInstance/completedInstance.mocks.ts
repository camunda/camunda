/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

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
  detailV2: {
    processInstanceKey: '2551799813954282',
    processDefinitionKey: '2251799813694848',
    processDefinitionName: 'Timer process',
    processDefinitionVersion: 4,
    startDate: '2023-10-02T06:10:47.979+0000',
    endDate: '2023-10-02T06:15:48.042+0000',
    state: 'COMPLETED',
    processDefinitionId: 'timerProcess',
    tenantId: '<default>',
    hasIncident: false,
  },
  callHierarchy: [],
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799815580677',
        processInstanceKey: '2551799813954282',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'timerProcess',
        elementId: 'StartEvent_1',
        elementName: 'Every 3 minutes',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-10-02T06:10:47.979+0000',
        endDate: '2023-10-02T06:10:47.984+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799815580679',
        processInstanceKey: '2551799813954282',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'timerProcess',
        elementId: 'IntermediateCatchEvent_1l4zjh6',
        elementName: '5 more minutes passed',
        type: 'INTERMEDIATE_CATCH_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-10-02T06:10:47.989+0000',
        endDate: '2023-10-02T06:15:48.031+0000',
        tenantId: '<default>',
      },
      {
        elementInstanceKey: '2251799815580946',
        processInstanceKey: '2551799813954282',
        processDefinitionKey: '2251799813694848',
        processDefinitionId: 'timerProcess',
        elementId: 'EndEvent_02qhg5x',
        elementName: 'EndEvent_02qhg5x',
        type: 'END_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2023-10-02T06:15:48.037+0000',
        endDate: '2023-10-02T06:15:48.037+0000',
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 3},
  },
  statistics: {
    items: [
      {
        elementId: 'EndEvent_02qhg5x',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'IntermediateCatchEvent_1l4zjh6',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
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
  sequenceFlowsV2: {
    items: [
      {
        processInstanceKey: '2551799813954282',
        elementId: 'SequenceFlow_0prd963',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
      {
        processInstanceKey: '2551799813954282',
        elementId: 'SequenceFlow_15toaun',
        tenantId: '',
        processDefinitionId: '',
        processDefinitionKey: '',
        sequenceFlowId: '',
      },
    ],
  },
  variables: [],
};

export {completedInstance};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Process} from 'modules/types';

const BPMN_XML = `
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_11n4bz1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.20.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.4.0">
  <bpmn:process id="Process_10qar8i" name="oioopiihhio" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_0jqzoa5</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:task id="Activity_00tib3l" name="A task">
      <bpmn:incoming>Flow_0jqzoa5</bpmn:incoming>
      <bpmn:outgoing>Flow_0f4sn94</bpmn:outgoing>
    </bpmn:task>
    <bpmn:sequenceFlow id="Flow_0jqzoa5" sourceRef="StartEvent_1" targetRef="Activity_00tib3l" />
    <bpmn:endEvent id="Event_0uwu5c4">
      <bpmn:incoming>Flow_0f4sn94</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0f4sn94" sourceRef="Activity_00tib3l" targetRef="Event_0uwu5c4" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_10qar8i">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="179" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_00tib3l_di" bpmnElement="Activity_00tib3l">
        <dc:Bounds x="270" y="77" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0uwu5c4_di" bpmnElement="Event_0uwu5c4">
        <dc:Bounds x="432" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0jqzoa5_di" bpmnElement="Flow_0jqzoa5">
        <di:waypoint x="215" y="117" />
        <di:waypoint x="270" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0f4sn94_di" bpmnElement="Flow_0f4sn94">
        <di:waypoint x="370" y="117" />
        <di:waypoint x="432" y="117" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const process: Process = {
  id: 'process',
  name: 'A test process',
  bpmnProcessId: 'someProcessId',
  version: 1,
  startEventFormId: '123456789',
  sortValues: ['value'],
  bpmnXml: null,
};

const processWithBpmnModel: Process = {
  ...process,
  bpmnXml: BPMN_XML,
};

export {process, processWithBpmnModel};

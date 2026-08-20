/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1q4wbab" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.48.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.9.0">
  <bpmn:process id="my_simple_process" isExecutable="true">
    <bpmn:startEvent id="start_event">
      <bpmn:outgoing>Flow_0xb8om9</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0xb8om9" sourceRef="start_event" targetRef="task-1" />
    <bpmn:endEvent id="end_event">
      <bpmn:incoming>Flow_0zln14r</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0zln14r" sourceRef="task-1" targetRef="end_event" />
    <bpmn:userTask id="task-1" name="Review invoice">
      <bpmn:extensionElements>
        <zeebe:userTask />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_0xb8om9</bpmn:incoming>
      <bpmn:outgoing>Flow_0zln14r</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="my_simple_process">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="start_event">
        <dc:Bounds x="182" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0atcqhh_di" bpmnElement="end_event">
        <dc:Bounds x="422" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1au7fiu_di" bpmnElement="task-1">
        <dc:Bounds x="270" y="80" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0xb8om9_di" bpmnElement="Flow_0xb8om9">
        <di:waypoint x="218" y="120" />
        <di:waypoint x="270" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0zln14r_di" bpmnElement="Flow_0zln14r">
        <di:waypoint x="370" y="120" />
        <di:waypoint x="422" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const UPDATED_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1q4wbab" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.48.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.9.0">
  <bpmn:process id="my_simple_process" isExecutable="true">
    <bpmn:startEvent id="start_event">
      <bpmn:outgoing>Flow_0xb8om9</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0xb8om9" sourceRef="start_event" targetRef="task-2" />
    <bpmn:endEvent id="end_event">
      <bpmn:incoming>Flow_0zln14r</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0zln14r" sourceRef="task-2" targetRef="end_event" />
    <bpmn:userTask id="task-2" name="Approve payment">
      <bpmn:extensionElements>
        <zeebe:userTask />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_0xb8om9</bpmn:incoming>
      <bpmn:outgoing>Flow_0zln14r</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="my_simple_process">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="start_event">
        <dc:Bounds x="182" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0atcqhh_di" bpmnElement="end_event">
        <dc:Bounds x="422" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1au7fiu_di" bpmnElement="task-2">
        <dc:Bounds x="270" y="80" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0xb8om9_di" bpmnElement="Flow_0xb8om9">
        <di:waypoint x="218" y="120" />
        <di:waypoint x="270" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0zln14r_di" bpmnElement="Flow_0zln14r">
        <di:waypoint x="370" y="120" />
        <di:waypoint x="422" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {BPMN_XML, UPDATED_BPMN_XML};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockNestedSubprocess = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="4f08a82" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="d08baf5c-51c2-4303-b5e7-c206cd5bed0e">
  <bpmn:process id="nested_sub_process" isExecutable="true">
    <bpmn:startEvent id="Event_16c7z9l">
      <bpmn:outgoing>Flow_1tjo4p4</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:subProcess id="parent_sub_process">
      <bpmn:incoming>Flow_1tjo4p4</bpmn:incoming>
      <bpmn:outgoing>Flow_133hxt7</bpmn:outgoing>
      <bpmn:startEvent id="Event_0oi4pw0">
        <bpmn:outgoing>Flow_17gn0kp</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:subProcess id="inner_sub_process">
        <bpmn:incoming>Flow_17gn0kp</bpmn:incoming>
        <bpmn:outgoing>Flow_10z0jqh</bpmn:outgoing>
        <bpmn:startEvent id="Event_1rw6vny">
          <bpmn:outgoing>Flow_01ab3fh</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:sequenceFlow id="Flow_01ab3fh" sourceRef="Event_1rw6vny" targetRef="user_task" />
        <bpmn:endEvent id="Event_0ypvz5p">
          <bpmn:incoming>Flow_1tsqjn5</bpmn:incoming>
        </bpmn:endEvent>
        <bpmn:sequenceFlow id="Flow_1tsqjn5" sourceRef="user_task" targetRef="Event_0ypvz5p" />
        <bpmn:userTask id="user_task">
          <bpmn:incoming>Flow_01ab3fh</bpmn:incoming>
          <bpmn:outgoing>Flow_1tsqjn5</bpmn:outgoing>
        </bpmn:userTask>
      </bpmn:subProcess>
      <bpmn:sequenceFlow id="Flow_17gn0kp" sourceRef="Event_0oi4pw0" targetRef="inner_sub_process" />
      <bpmn:endEvent id="Event_1k2dpf7">
        <bpmn:incoming>Flow_10z0jqh</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="Flow_10z0jqh" sourceRef="inner_sub_process" targetRef="Event_1k2dpf7" />
    </bpmn:subProcess>
    <bpmn:sequenceFlow id="Flow_1tjo4p4" sourceRef="Event_16c7z9l" targetRef="parent_sub_process" />
    <bpmn:endEvent id="Event_1ezmgil">
      <bpmn:incoming>Flow_133hxt7</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_133hxt7" sourceRef="parent_sub_process" targetRef="Event_1ezmgil" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="nested_sub_process">
      <bpmndi:BPMNShape id="Event_16c7z9l_di" bpmnElement="Event_16c7z9l">
        <dc:Bounds x="152.33333333333337" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1ezmgil_di" bpmnElement="Event_1ezmgil">
        <dc:Bounds x="1202" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1q8549s_di" bpmnElement="parent_sub_process" isExpanded="true">
        <dc:Bounds x="330" y="60" width="730" height="320" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0oi4pw0_di" bpmnElement="Event_0oi4pw0">
        <dc:Bounds x="370" y="212" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1k2dpf7_di" bpmnElement="Event_1k2dpf7">
        <dc:Bounds x="952" y="212" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0x87mmh_di" bpmnElement="inner_sub_process" isExpanded="true">
        <dc:Bounds x="500" y="130" width="350" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1rw6vny_di" bpmnElement="Event_1rw6vny">
        <dc:Bounds x="540" y="212" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0ypvz5p_di" bpmnElement="Event_0ypvz5p">
        <dc:Bounds x="792" y="212" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0dir37a_di" bpmnElement="user_task">
        <dc:Bounds x="630" y="190" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_01ab3fh_di" bpmnElement="Flow_01ab3fh">
        <di:waypoint x="576" y="230" />
        <di:waypoint x="630" y="230" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1tsqjn5_di" bpmnElement="Flow_1tsqjn5">
        <di:waypoint x="730" y="230" />
        <di:waypoint x="792" y="230" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_17gn0kp_di" bpmnElement="Flow_17gn0kp">
        <di:waypoint x="406" y="230" />
        <di:waypoint x="500" y="230" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_10z0jqh_di" bpmnElement="Flow_10z0jqh">
        <di:waypoint x="850" y="230" />
        <di:waypoint x="952" y="230" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1tjo4p4_di" bpmnElement="Flow_1tjo4p4">
        <di:waypoint x="188" y="220" />
        <di:waypoint x="330" y="220" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_133hxt7_di" bpmnElement="Flow_133hxt7">
        <di:waypoint x="1060" y="220" />
        <di:waypoint x="1202" y="220" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

export {mockNestedSubprocess};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const metadataDemoProcess = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1bjuuyq" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="4.8.1">
  <bpmn:process id="MetadataDemoProcess" isExecutable="true">
    <bpmn:startEvent id="StartEvent">
      <bpmn:outgoing>Flow_0qhrku7</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0qhrku7" sourceRef="StartEvent" targetRef="CallActivity" />
    <bpmn:sequenceFlow id="Flow_0fyyj21" sourceRef="CallActivity" targetRef="Task" />
    <bpmn:serviceTask id="Task" name="Task">
      <bpmn:incoming>Flow_0fyyj21</bpmn:incoming>
      <bpmn:outgoing>Flow_1myh7rx</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics />
    </bpmn:serviceTask>
    <bpmn:callActivity id="CallActivity" name="Call Activity">
      <bpmn:incoming>Flow_0qhrku7</bpmn:incoming>
      <bpmn:outgoing>Flow_0fyyj21</bpmn:outgoing>
    </bpmn:callActivity>
    <bpmn:endEvent id="EndEvent">
      <bpmn:incoming>Flow_01kmfq5</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1myh7rx" sourceRef="Task" targetRef="BusinessRuleTask" />
    <bpmn:businessRuleTask id="BusinessRuleTask" name="Take decision">
      <bpmn:incoming>Flow_1myh7rx</bpmn:incoming>
      <bpmn:outgoing>Flow_01kmfq5</bpmn:outgoing>
    </bpmn:businessRuleTask>
    <bpmn:sequenceFlow id="Flow_01kmfq5" sourceRef="BusinessRuleTask" targetRef="EndEvent" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="MetadataDemoProcess">
      <bpmndi:BPMNEdge id="Flow_01kmfq5_di" bpmnElement="Flow_01kmfq5">
        <di:waypoint x="680" y="117" />
        <di:waypoint x="732" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1myh7rx_di" bpmnElement="Flow_1myh7rx">
        <di:waypoint x="530" y="117" />
        <di:waypoint x="580" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0fyyj21_di" bpmnElement="Flow_0fyyj21">
        <di:waypoint x="370" y="117" />
        <di:waypoint x="430" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0qhrku7_di" bpmnElement="Flow_0qhrku7">
        <di:waypoint x="215" y="117" />
        <di:waypoint x="270" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent">
        <dc:Bounds x="179" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0kuuzox_di" bpmnElement="Task">
        <dc:Bounds x="430" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1ixvq4u_di" bpmnElement="CallActivity">
        <dc:Bounds x="270" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0myvkag_di" bpmnElement="EndEvent">
        <dc:Bounds x="732" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0upyfxx_di" bpmnElement="BusinessRuleTask">
        <dc:Bounds x="580" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

export {metadataDemoProcess};

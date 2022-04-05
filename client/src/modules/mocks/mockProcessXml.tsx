/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const mockProcessXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1771k9d" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.5.0">
  <bpmn:process id="bigVarProcess" isExecutable="true" name="Big variable process">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>SequenceFlow_04ev4jl</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="ServiceTask_0kt6c5i">
      <bpmn:incoming>SequenceFlow_04ev4jl</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1njhlr0</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="SequenceFlow_04ev4jl" sourceRef="StartEvent_1" targetRef="ServiceTask_0kt6c5i" />
    <bpmn:endEvent id="EndEvent_0crvjrk">
      <bpmn:incoming>SequenceFlow_1njhlr0</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_1njhlr0" sourceRef="ServiceTask_0kt6c5i" targetRef="EndEvent_0crvjrk" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="process">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0kt6c5i_di" bpmnElement="ServiceTask_0kt6c5i">
        <dc:Bounds x="259" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_04ev4jl_di" bpmnElement="SequenceFlow_04ev4jl">
        <di:waypoint x="209" y="120" />
        <di:waypoint x="259" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_0crvjrk_di" bpmnElement="EndEvent_0crvjrk">
        <dc:Bounds x="409" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1njhlr0_di" bpmnElement="SequenceFlow_1njhlr0">
        <di:waypoint x="359" y="120" />
        <di:waypoint x="409" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

export {mockProcessXml};

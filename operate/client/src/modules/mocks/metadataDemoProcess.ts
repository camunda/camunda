/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

const metadataDemoProcess = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1bjuuyq" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.12.0">
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
      <bpmn:incoming>Flow_14v0xk3</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1myh7rx" sourceRef="Task" targetRef="BusinessRuleTask" />
    <bpmn:businessRuleTask id="BusinessRuleTask" name="Take decision">
      <bpmn:incoming>Flow_1myh7rx</bpmn:incoming>
      <bpmn:outgoing>Flow_01kmfq5</bpmn:outgoing>
    </bpmn:businessRuleTask>
    <bpmn:sequenceFlow id="Flow_01kmfq5" sourceRef="BusinessRuleTask" targetRef="UserTask" />
    <bpmn:sequenceFlow id="Flow_14v0xk3" sourceRef="UserTask" targetRef="EndEvent" />
    <bpmn:userTask id="UserTask" name="User Task">
      <bpmn:incoming>Flow_01kmfq5</bpmn:incoming>
      <bpmn:outgoing>Flow_14v0xk3</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="MetadataDemoProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent">
        <dc:Bounds x="179" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0kuuzox_di" bpmnElement="Task">
        <dc:Bounds x="430" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1ixvq4u_di" bpmnElement="CallActivity">
        <dc:Bounds x="270" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0upyfxx_di" bpmnElement="BusinessRuleTask">
        <dc:Bounds x="580" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0myvkag_di" bpmnElement="EndEvent">
        <dc:Bounds x="862" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1n36kqx_di" bpmnElement="UserTask">
        <dc:Bounds x="720" y="77" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0qhrku7_di" bpmnElement="Flow_0qhrku7">
        <di:waypoint x="215" y="117" />
        <di:waypoint x="270" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0fyyj21_di" bpmnElement="Flow_0fyyj21">
        <di:waypoint x="370" y="117" />
        <di:waypoint x="430" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1myh7rx_di" bpmnElement="Flow_1myh7rx">
        <di:waypoint x="530" y="117" />
        <di:waypoint x="580" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_01kmfq5_di" bpmnElement="Flow_01kmfq5">
        <di:waypoint x="680" y="117" />
        <di:waypoint x="720" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_14v0xk3_di" bpmnElement="Flow_14v0xk3">
        <di:waypoint x="820" y="117" />
        <di:waypoint x="862" y="117" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {metadataDemoProcess};

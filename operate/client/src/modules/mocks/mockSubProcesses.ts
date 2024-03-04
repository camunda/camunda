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

const mockSubProcesses = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="53ccc8b" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="371b6351-e738-4cd7-b3a4-c366126f3f2a">
  <bpmn:process id="Process_1uyj4ax" name="subprocess test" isExecutable="true">
    <bpmn:startEvent id="Event_00fujix">
      <bpmn:outgoing>Flow_0jayzlr</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:subProcess id="subprocess">
      <bpmn:incoming>Flow_0jayzlr</bpmn:incoming>
      <bpmn:outgoing>Flow_145udck</bpmn:outgoing>
      <bpmn:startEvent id="Event_0owwxm1">
        <bpmn:outgoing>Flow_0ots3ag</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:serviceTask id="service-task-1" name="service task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="test" />
        </bpmn:extensionElements>
        <bpmn:incoming>Flow_0ots3ag</bpmn:incoming>
        <bpmn:outgoing>Flow_1hn6q8v</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="Flow_0ots3ag" sourceRef="Event_0owwxm1" targetRef="service-task-1" />
      <bpmn:endEvent id="Event_14p9lyy">
        <bpmn:incoming>Flow_1hn6q8v</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="Flow_1hn6q8v" sourceRef="service-task-1" targetRef="Event_14p9lyy" />
    </bpmn:subProcess>
    <bpmn:endEvent id="Event_14c51ww">
      <bpmn:incoming>Flow_145udck</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_145udck" sourceRef="subprocess" targetRef="Event_14c51ww" />
    <bpmn:sequenceFlow id="Flow_0jayzlr" sourceRef="Event_00fujix" targetRef="subprocess" />
    <bpmn:subProcess id="event-subprocess" triggeredByEvent="true">
      <bpmn:startEvent id="Event_1kofps8">
        <bpmn:outgoing>Flow_0j6eys0</bpmn:outgoing>
        <bpmn:timerEventDefinition id="TimerEventDefinition_0k5eu6f" />
      </bpmn:startEvent>
      <bpmn:sequenceFlow id="Flow_0j6eys0" sourceRef="Event_1kofps8" targetRef="service-task-2" />
      <bpmn:serviceTask id="service-task-2" name="event subprocess service task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="test2" />
        </bpmn:extensionElements>
        <bpmn:incoming>Flow_0j6eys0</bpmn:incoming>
        <bpmn:outgoing>Flow_0a27nf2</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:endEvent id="Event_1uop49i">
        <bpmn:incoming>Flow_0a27nf2</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="Flow_0a27nf2" sourceRef="service-task-2" targetRef="Event_1uop49i" />
    </bpmn:subProcess>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1uyj4ax">
      <bpmndi:BPMNShape id="Event_00fujix_di" bpmnElement="Event_00fujix">
        <dc:Bounds x="152" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_14c51ww_di" bpmnElement="Event_14c51ww">
        <dc:Bounds x="692" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1g0g7rc_di" bpmnElement="subprocess" isExpanded="true">
        <dc:Bounds x="240" y="80" width="390" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0owwxm1_di" bpmnElement="Event_0owwxm1">
        <dc:Bounds x="280" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_13ddrk8_di" bpmnElement="service-task-1">
        <dc:Bounds x="380" y="140" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_14p9lyy_di" bpmnElement="Event_14p9lyy">
        <dc:Bounds x="552" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0ots3ag_di" bpmnElement="Flow_0ots3ag">
        <di:waypoint x="316" y="180" />
        <di:waypoint x="380" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1hn6q8v_di" bpmnElement="Flow_1hn6q8v">
        <di:waypoint x="480" y="180" />
        <di:waypoint x="552" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="Activity_0dvcmqq_di" bpmnElement="event-subprocess" isExpanded="true">
        <dc:Bounds x="270" y="340" width="350" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1guh4du_di" bpmnElement="Event_1kofps8">
        <dc:Bounds x="292" y="422" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_17ku20q_di" bpmnElement="service-task-2">
        <dc:Bounds x="380" y="400" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1uop49i_di" bpmnElement="Event_1uop49i">
        <dc:Bounds x="532" y="422" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0j6eys0_di" bpmnElement="Flow_0j6eys0">
        <di:waypoint x="328" y="440" />
        <di:waypoint x="380" y="440" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0a27nf2_di" bpmnElement="Flow_0a27nf2">
        <di:waypoint x="480" y="440" />
        <di:waypoint x="532" y="440" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_145udck_di" bpmnElement="Flow_145udck">
        <di:waypoint x="630" y="180" />
        <di:waypoint x="692" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0jayzlr_di" bpmnElement="Flow_0jayzlr">
        <di:waypoint x="188" y="180" />
        <di:waypoint x="240" y="180" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

export {mockSubProcesses};

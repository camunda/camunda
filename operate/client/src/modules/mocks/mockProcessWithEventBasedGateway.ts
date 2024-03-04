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

const mockProcessWithEventBasedGateway = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="3c9f79f" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="3f3ff7ed-2627-4879-8a42-3c97e9375d4c">
  <bpmn:process id="Process_aca2bbb8-dfb2-451e-80f2-f25e1965dfd0" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_1wxo9cx</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:eventBasedGateway id="Gateway_0tooyvs">
      <bpmn:incoming>Flow_0ig6u0j</bpmn:incoming>
      <bpmn:outgoing>Flow_1om1ae3</bpmn:outgoing>
      <bpmn:outgoing>Flow_1okwbwi</bpmn:outgoing>
    </bpmn:eventBasedGateway>
    <bpmn:intermediateCatchEvent id="timer_intermediate_catch_non_selectable">
      <bpmn:incoming>Flow_1om1ae3</bpmn:incoming>
      <bpmn:outgoing>Flow_1pnv973</bpmn:outgoing>
      <bpmn:timerEventDefinition id="TimerEventDefinition_1u8bsad" />
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_1om1ae3" sourceRef="Gateway_0tooyvs" targetRef="timer_intermediate_catch_non_selectable" />
    <bpmn:intermediateCatchEvent id="message_intermediate_catch_non_selectable">
      <bpmn:incoming>Flow_1okwbwi</bpmn:incoming>
      <bpmn:outgoing>Flow_0dr1dad</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageEventDefinition_1k5c303" messageRef="Message_30f3amb" />
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_1okwbwi" sourceRef="Gateway_0tooyvs" targetRef="message_intermediate_catch_non_selectable" />
    <bpmn:exclusiveGateway id="Gateway_1ef4nw9">
      <bpmn:incoming>Flow_1pnv973</bpmn:incoming>
      <bpmn:incoming>Flow_0dr1dad</bpmn:incoming>
      <bpmn:outgoing>Flow_0olh64x</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="Flow_1pnv973" sourceRef="timer_intermediate_catch_non_selectable" targetRef="Gateway_1ef4nw9" />
    <bpmn:sequenceFlow id="Flow_0dr1dad" sourceRef="message_intermediate_catch_non_selectable" targetRef="Gateway_1ef4nw9" />
    <bpmn:endEvent id="Event_04grgsl">
      <bpmn:incoming>Flow_0olh64x</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0olh64x" sourceRef="Gateway_1ef4nw9" targetRef="Event_04grgsl" />
    <bpmn:sequenceFlow id="Flow_1wxo9cx" sourceRef="StartEvent_1" targetRef="message_intermediate_catch_selectable" />
    <bpmn:intermediateCatchEvent id="message_intermediate_catch_selectable">
      <bpmn:incoming>Flow_1wxo9cx</bpmn:incoming>
      <bpmn:outgoing>Flow_193k1et</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageEventDefinition_1tvavbn" />
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_193k1et" sourceRef="message_intermediate_catch_selectable" targetRef="message_intermediate_throw_selectable" />
    <bpmn:intermediateThrowEvent id="message_intermediate_throw_selectable">
      <bpmn:incoming>Flow_193k1et</bpmn:incoming>
      <bpmn:outgoing>Flow_1qrs5cy</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageEventDefinition_1j0orwv" />
    </bpmn:intermediateThrowEvent>
    <bpmn:sequenceFlow id="Flow_1qrs5cy" sourceRef="message_intermediate_throw_selectable" targetRef="timer_intermediate_catch_selectable" />
    <bpmn:intermediateCatchEvent id="timer_intermediate_catch_selectable">
      <bpmn:incoming>Flow_1qrs5cy</bpmn:incoming>
      <bpmn:outgoing>Flow_0ig6u0j</bpmn:outgoing>
      <bpmn:timerEventDefinition id="TimerEventDefinition_0oquj3b" />
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="Flow_0ig6u0j" sourceRef="timer_intermediate_catch_selectable" targetRef="Gateway_0tooyvs" />
  </bpmn:process>
  <bpmn:message id="Message_30f3amb" name="Message_30f3amb">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=2" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmn:error id="Error_15uf6ay" name="Error_0eh9vjn" errorCode="3" />
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_aca2bbb8-dfb2-451e-80f2-f25e1965dfd0">
      <bpmndi:BPMNEdge id="Flow_0ig6u0j_di" bpmnElement="Flow_0ig6u0j">
        <di:waypoint x="428" y="140" />
        <di:waypoint x="485" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1qrs5cy_di" bpmnElement="Flow_1qrs5cy">
        <di:waypoint x="348" y="140" />
        <di:waypoint x="392" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_193k1et_di" bpmnElement="Flow_193k1et">
        <di:waypoint x="268" y="140" />
        <di:waypoint x="312" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1wxo9cx_di" bpmnElement="Flow_1wxo9cx">
        <di:waypoint x="188" y="140" />
        <di:waypoint x="232" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0olh64x_di" bpmnElement="Flow_0olh64x">
        <di:waypoint x="725" y="140" />
        <di:waypoint x="782" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0dr1dad_di" bpmnElement="Flow_0dr1dad">
        <di:waypoint x="638" y="80" />
        <di:waypoint x="700" y="80" />
        <di:waypoint x="700" y="115" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1pnv973_di" bpmnElement="Flow_1pnv973">
        <di:waypoint x="628" y="190" />
        <di:waypoint x="700" y="190" />
        <di:waypoint x="700" y="165" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1okwbwi_di" bpmnElement="Flow_1okwbwi">
        <di:waypoint x="510" y="115" />
        <di:waypoint x="510" y="80" />
        <di:waypoint x="602" y="80" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1om1ae3_di" bpmnElement="Flow_1om1ae3">
        <di:waypoint x="510" y="165" />
        <di:waypoint x="510" y="190" />
        <di:waypoint x="592" y="190" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_0777d6u_di" bpmnElement="Gateway_0tooyvs">
        <dc:Bounds x="485" y="115" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0f73u0w_di" bpmnElement="timer_intermediate_catch_non_selectable">
        <dc:Bounds x="592" y="172" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1916" y="415" width="89" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1pvlb9c_di" bpmnElement="message_intermediate_catch_non_selectable">
        <dc:Bounds x="602" y="62" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1916" y="225" width="88" height="40" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1oivwth_di" bpmnElement="message_intermediate_catch_selectable">
        <dc:Bounds x="232" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0jeu1fi_di" bpmnElement="message_intermediate_throw_selectable">
        <dc:Bounds x="312" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0mo5mds_di" bpmnElement="timer_intermediate_catch_selectable">
        <dc:Bounds x="392" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1ef4nw9_di" bpmnElement="Gateway_1ef4nw9" isMarkerVisible="true">
        <dc:Bounds x="675" y="115" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_04grgsl_di" bpmnElement="Event_04grgsl">
        <dc:Bounds x="782" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>

`;

export {mockProcessWithEventBasedGateway};

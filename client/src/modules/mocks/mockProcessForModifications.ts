/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const mockProcessForModifications = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="1274eb3" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="3f3ff7ed-2627-4879-8a42-3c97e9375d4c">
<bpmn:process id="Process_aca2bbb8-dfb2-451e-80f2-f25e1965dfd0" isExecutable="true">
  <bpmn:startEvent id="StartEvent_1">
    <bpmn:outgoing>Flow_1o35afl</bpmn:outgoing>
  </bpmn:startEvent>
  <bpmn:sequenceFlow id="Flow_1o35afl" sourceRef="StartEvent_1" targetRef="service-task-1"/>
  <bpmn:serviceTask id="service-task-1">
    <bpmn:incoming>Flow_1o35afl</bpmn:incoming>
    <bpmn:outgoing>Flow_0uze503</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:subProcess id="multi-instance-subprocess">
    <bpmn:incoming>Flow_0uze503</bpmn:incoming>
    <bpmn:outgoing>Flow_1kjrxrd</bpmn:outgoing>
    <bpmn:multiInstanceLoopCharacteristics/>
    <bpmn:startEvent id="subprocess-start-1">
      <bpmn:outgoing>Flow_1vur7mf</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1vur7mf" sourceRef="subprocess-start-1" targetRef="subprocess-service-task"/>
    <bpmn:endEvent id="subprocess-end-task">
      <bpmn:incoming>Flow_0r3hsrs</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0r3hsrs" sourceRef="subprocess-service-task" targetRef="subprocess-end-task"/>
    <bpmn:serviceTask id="subprocess-service-task">
      <bpmn:incoming>Flow_1vur7mf</bpmn:incoming>
      <bpmn:outgoing>Flow_0r3hsrs</bpmn:outgoing>
    </bpmn:serviceTask>
  </bpmn:subProcess>
  <bpmn:sequenceFlow id="Flow_0uze503" sourceRef="service-task-1" targetRef="multi-instance-subprocess"/>
  <bpmn:boundaryEvent id="message-boundary" attachedToRef="service-task-2">
    <bpmn:messageEventDefinition id="MessageEventDefinition_0uq2j4r"/>
  </bpmn:boundaryEvent>
  <bpmn:sequenceFlow id="Flow_1irgwck" sourceRef="gateway-1" targetRef="service-task-5"/>
  <bpmn:boundaryEvent id="error-boundary" attachedToRef="service-task-3">
    <bpmn:errorEventDefinition id="ErrorEventDefinition_0vrn8o9"/>
  </bpmn:boundaryEvent>
  <bpmn:sequenceFlow id="Flow_07oy8pb" sourceRef="service-task-3" targetRef="service-task-4"/>
  <bpmn:parallelGateway id="gateway-1">
    <bpmn:incoming>Flow_1kjrxrd</bpmn:incoming>
    <bpmn:outgoing>Flow_001fza4</bpmn:outgoing>
    <bpmn:outgoing>Flow_1irgwck</bpmn:outgoing>
  </bpmn:parallelGateway>
  <bpmn:sequenceFlow id="Flow_1kjrxrd" sourceRef="multi-instance-subprocess" targetRef="gateway-1"/>
  <bpmn:sequenceFlow id="Flow_001fza4" sourceRef="gateway-1" targetRef="service-task-2"/>
  <bpmn:sequenceFlow id="Flow_0prh4bx" sourceRef="service-task-2" targetRef="service-task-3"/>
  <bpmn:sequenceFlow id="Flow_1lhw0sx" sourceRef="service-task-5" targetRef="service-task-6"/>
  <bpmn:boundaryEvent id="non-interrupt-timer-boundary" cancelActivity="false" attachedToRef="service-task-6">
    <bpmn:timerEventDefinition id="TimerEventDefinition_1bemsu2"/>
  </bpmn:boundaryEvent>
  <bpmn:boundaryEvent id="non-interrupt-message-boundary" cancelActivity="false" attachedToRef="service-task-5">
    <bpmn:messageEventDefinition id="MessageEventDefinition_1k50poz"/>
  </bpmn:boundaryEvent>
  <bpmn:boundaryEvent id="timer-boundary" attachedToRef="service-task-4">
    <bpmn:timerEventDefinition id="TimerEventDefinition_0oxf7n2"/>
  </bpmn:boundaryEvent>
  <bpmn:sequenceFlow id="Flow_0avfoyj" sourceRef="service-task-4" targetRef="gateway-2"/>
  <bpmn:parallelGateway id="gateway-2">
    <bpmn:incoming>Flow_0avfoyj</bpmn:incoming>
    <bpmn:incoming>Flow_06zhwzc</bpmn:incoming>
    <bpmn:outgoing>Flow_0lck7jk</bpmn:outgoing>
  </bpmn:parallelGateway>
  <bpmn:sequenceFlow id="Flow_1fmslrr" sourceRef="service-task-6" targetRef="Event_1o1ply5"/>
  <bpmn:intermediateCatchEvent id="Event_1o1ply5">
    <bpmn:incoming>Flow_1fmslrr</bpmn:incoming>
    <bpmn:outgoing>Flow_06u9wbj</bpmn:outgoing>
    <bpmn:messageEventDefinition id="MessageEventDefinition_1uv3f61"/>
  </bpmn:intermediateCatchEvent>
  <bpmn:sequenceFlow id="Flow_0lck7jk" sourceRef="gateway-2" targetRef="service-task-7"/>
  <bpmn:serviceTask id="service-task-7">
    <bpmn:incoming>Flow_0lck7jk</bpmn:incoming>
    <bpmn:outgoing>Flow_1ac1phk</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:sequenceFlow id="Flow_1ac1phk" sourceRef="service-task-7" targetRef="message-intermediate"/>
  <bpmn:intermediateThrowEvent id="message-intermediate">
    <bpmn:incoming>Flow_1ac1phk</bpmn:incoming>
    <bpmn:outgoing>Flow_0w8kam2</bpmn:outgoing>
    <bpmn:messageEventDefinition id="MessageEventDefinition_10t1lwa"/>
  </bpmn:intermediateThrowEvent>
  <bpmn:sequenceFlow id="Flow_0w8kam2" sourceRef="message-intermediate" targetRef="timer-intermediate"/>
  <bpmn:intermediateCatchEvent id="timer-intermediate">
    <bpmn:incoming>Flow_0w8kam2</bpmn:incoming>
    <bpmn:outgoing>Flow_1ixd1ey</bpmn:outgoing>
    <bpmn:timerEventDefinition id="TimerEventDefinition_0knib51"/>
  </bpmn:intermediateCatchEvent>
  <bpmn:sequenceFlow id="Flow_1ixd1ey" sourceRef="timer-intermediate" targetRef="user-task-1"/>
  <bpmn:userTask id="user-task-1">
    <bpmn:incoming>Flow_1ixd1ey</bpmn:incoming>
    <bpmn:outgoing>Flow_0f53ufs</bpmn:outgoing>
  </bpmn:userTask>
  <bpmn:endEvent id="end-event">
    <bpmn:incoming>Flow_0f53ufs</bpmn:incoming>
  </bpmn:endEvent>
  <bpmn:sequenceFlow id="Flow_0f53ufs" sourceRef="user-task-1" targetRef="end-event"/>
  <bpmn:serviceTask id="service-task-2">
    <bpmn:incoming>Flow_001fza4</bpmn:incoming>
    <bpmn:outgoing>Flow_0prh4bx</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:serviceTask id="service-task-3">
    <bpmn:incoming>Flow_0prh4bx</bpmn:incoming>
    <bpmn:outgoing>Flow_07oy8pb</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:serviceTask id="service-task-4">
    <bpmn:incoming>Flow_07oy8pb</bpmn:incoming>
    <bpmn:outgoing>Flow_0avfoyj</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:serviceTask id="service-task-5">
    <bpmn:incoming>Flow_1irgwck</bpmn:incoming>
    <bpmn:outgoing>Flow_1lhw0sx</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:serviceTask id="service-task-6">
    <bpmn:incoming>Flow_1lhw0sx</bpmn:incoming>
    <bpmn:outgoing>Flow_1fmslrr</bpmn:outgoing>
  </bpmn:serviceTask>
  <bpmn:boundaryEvent id="boundary-event" attachedToRef="service-task-7"/>
  <bpmn:intermediateThrowEvent id="intermediate-throw">
    <bpmn:incoming>Flow_06u9wbj</bpmn:incoming>
    <bpmn:outgoing>Flow_06zhwzc</bpmn:outgoing>
  </bpmn:intermediateThrowEvent>
  <bpmn:sequenceFlow id="Flow_06u9wbj" sourceRef="Event_1o1ply5" targetRef="intermediate-throw"/>
  <bpmn:sequenceFlow id="Flow_06zhwzc" sourceRef="intermediate-throw" targetRef="gateway-2"/>
</bpmn:process>
<bpmndi:BPMNDiagram id="BPMNDiagram_1">
  <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_aca2bbb8-dfb2-451e-80f2-f25e1965dfd0">
    <bpmndi:BPMNEdge id="Flow_1o35afl_di" bpmnElement="Flow_1o35afl">
      <di:waypoint x="186" y="240"/>
      <di:waypoint x="240" y="240"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0uze503_di" bpmnElement="Flow_0uze503">
      <di:waypoint x="340" y="240"/>
      <di:waypoint x="410" y="240"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1irgwck_di" bpmnElement="Flow_1irgwck">
      <di:waypoint x="850" y="270"/>
      <di:waypoint x="850" y="350"/>
      <di:waypoint x="900" y="350"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_07oy8pb_di" bpmnElement="Flow_07oy8pb">
      <di:waypoint x="1190" y="140"/>
      <di:waypoint x="1220" y="140"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1kjrxrd_di" bpmnElement="Flow_1kjrxrd">
      <di:waypoint x="760" y="240"/>
      <di:waypoint x="825" y="240"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_001fza4_di" bpmnElement="Flow_001fza4">
      <di:waypoint x="850" y="215"/>
      <di:waypoint x="850" y="140"/>
      <di:waypoint x="950" y="140"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0prh4bx_di" bpmnElement="Flow_0prh4bx">
      <di:waypoint x="1050" y="140"/>
      <di:waypoint x="1090" y="140"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1lhw0sx_di" bpmnElement="Flow_1lhw0sx">
      <di:waypoint x="1000" y="350"/>
      <di:waypoint x="1030" y="350"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0avfoyj_di" bpmnElement="Flow_0avfoyj">
      <di:waypoint x="1310" y="180"/>
      <di:waypoint x="1310" y="255"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1fmslrr_di" bpmnElement="Flow_1fmslrr">
      <di:waypoint x="1130" y="350"/>
      <di:waypoint x="1162" y="350"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0lck7jk_di" bpmnElement="Flow_0lck7jk">
      <di:waypoint x="1335" y="280"/>
      <di:waypoint x="1380" y="280"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1ac1phk_di" bpmnElement="Flow_1ac1phk">
      <di:waypoint x="1480" y="280"/>
      <di:waypoint x="1522" y="280"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0w8kam2_di" bpmnElement="Flow_0w8kam2">
      <di:waypoint x="1558" y="280"/>
      <di:waypoint x="1592" y="280"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_1ixd1ey_di" bpmnElement="Flow_1ixd1ey">
      <di:waypoint x="1628" y="280"/>
      <di:waypoint x="1680" y="280"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0f53ufs_di" bpmnElement="Flow_0f53ufs">
      <di:waypoint x="1780" y="280"/>
      <di:waypoint x="1832" y="280"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_06u9wbj_di" bpmnElement="Flow_06u9wbj">
      <di:waypoint x="1198" y="350"/>
      <di:waypoint x="1232" y="350"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_06zhwzc_di" bpmnElement="Flow_06zhwzc">
      <di:waypoint x="1268" y="350"/>
      <di:waypoint x="1310" y="350"/>
      <di:waypoint x="1310" y="305"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNShape id="Activity_1abfuim_di" bpmnElement="service-task-1">
      <dc:Bounds x="240" y="200" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
      <dc:Bounds x="150" y="222" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Gateway_0f5wr6h_di" bpmnElement="gateway-1">
      <dc:Bounds x="825" y="215" width="50" height="50"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0m6tq8e_di" bpmnElement="Event_1o1ply5">
      <dc:Bounds x="1162" y="332" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Gateway_1o0p3nt_di" bpmnElement="gateway-2">
      <dc:Bounds x="1285" y="255" width="50" height="50"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_0pwl7rm_di" bpmnElement="service-task-7">
      <dc:Bounds x="1380" y="240" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_1l0foen_di" bpmnElement="service-task-2">
      <dc:Bounds x="950" y="100" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_1ahouaa_di" bpmnElement="service-task-3">
      <dc:Bounds x="1090" y="100" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_0cf7xcr_di" bpmnElement="service-task-4">
      <dc:Bounds x="1220" y="100" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_0zrara5_di" bpmnElement="service-task-5">
      <dc:Bounds x="900" y="310" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_1dbczbf_di" bpmnElement="service-task-6">
      <dc:Bounds x="1030" y="310" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_152k9jc_di" bpmnElement="message-intermediate">
      <dc:Bounds x="1522" y="262" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0vvmqk5_di" bpmnElement="timer-intermediate">
      <dc:Bounds x="1592" y="262" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_1ag6rla_di" bpmnElement="user-task-1">
      <dc:Bounds x="1680" y="240" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_02sp0wf_di" bpmnElement="end-event">
      <dc:Bounds x="1832" y="262" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0kg0s25_di" bpmnElement="intermediate-throw">
      <dc:Bounds x="1232" y="332" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_0ipkk5y_di" bpmnElement="multi-instance-subprocess" isExpanded="true">
      <dc:Bounds x="410" y="140" width="350" height="200"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNEdge id="Flow_1vur7mf_di" bpmnElement="Flow_1vur7mf">
      <di:waypoint x="468" y="240"/>
      <di:waypoint x="530" y="240"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_0r3hsrs_di" bpmnElement="Flow_0r3hsrs">
      <di:waypoint x="630" y="240"/>
      <di:waypoint x="682" y="240"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNShape id="Event_0fh35j2_di" bpmnElement="subprocess-start-1">
      <dc:Bounds x="432" y="222" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_1xq5uz0_di" bpmnElement="subprocess-end-task">
      <dc:Bounds x="682" y="222" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_179yof3_di" bpmnElement="subprocess-service-task">
      <dc:Bounds x="530" y="200" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_15vaq3g_di" bpmnElement="message-boundary">
      <dc:Bounds x="1012" y="162" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_13105ug_di" bpmnElement="error-boundary">
      <dc:Bounds x="1172" y="162" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0k4uqci_di" bpmnElement="timer-boundary">
      <dc:Bounds x="1302" y="162" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_1qd0xyd_di" bpmnElement="non-interrupt-message-boundary">
      <dc:Bounds x="982" y="372" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0ypefwf_di" bpmnElement="non-interrupt-timer-boundary">
      <dc:Bounds x="1112" y="372" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0o7zk1j_di" bpmnElement="boundary-event">
      <dc:Bounds x="1432" y="302" width="36" height="36"/>
    </bpmndi:BPMNShape>
  </bpmndi:BPMNPlane>
</bpmndi:BPMNDiagram>
</bpmn:definitions>`;

export {mockProcessForModifications};

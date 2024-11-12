/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist;

import io.camunda.webapps.schema.entities.operate.ProcessEntity;

public interface TestData {

  static ProcessEntity processEntityWithForm(final Long id) {
    return new ProcessEntity()
        .setId(String.valueOf(id))
        .setKey(id)
        .setVersion(1)
        .setBpmnXml(bpmnProcessWithForm())
        .setBpmnProcessId("formProcess");
  }

  static ProcessEntity processEntityWithoutForm(final Long id) {
    return new ProcessEntity()
        .setId(String.valueOf(id))
        .setKey(id)
        .setVersion(1)
        .setBpmnXml(bpmnProcessWithoutForm())
        .setBpmnProcessId("formProcess");
  }

  static String bpmnProcessWithForm() {
    return """
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.27.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.5.0" camunda:diagramRelationId="7640587b-ab4b-4660-8e4e-4064a21c9626">
  <bpmn:process id="formProcess" name="processWithStartNodeFormDeployed" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:extensionElements>
        <zeebe:formDefinition formKey="testForm" />
        <zeebe:properties>
          <zeebe:property name="publicAccess" value="true" />
        </zeebe:properties>
      </bpmn:extensionElements>
      <bpmn:outgoing>Flow_0d7gldh</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0d7gldh" sourceRef="StartEvent_1" targetRef="processStartedByForm_user_task" />
    <bpmn:endEvent id="Event_1cu4vf1">
      <bpmn:incoming>Flow_1g7mhgj</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1g7mhgj" sourceRef="processStartedByForm_user_task" targetRef="Event_1cu4vf1" />
    <bpmn:userTask id="processStartedByForm_user_task" name="processStartedByForm_user_task">
      <bpmn:incoming>Flow_0d7gldh</bpmn:incoming>
      <bpmn:outgoing>Flow_1g7mhgj</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="formProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="150" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1cu4vf1_di" bpmnElement="Event_1cu4vf1">
        <dc:Bounds x="402" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_14rkwl1_di" bpmnElement="processStartedByForm_user_task">
        <dc:Bounds x="240" y="78" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0d7gldh_di" bpmnElement="Flow_0d7gldh">
        <di:waypoint x="186" y="118" />
        <di:waypoint x="240" y="118" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1g7mhgj_di" bpmnElement="Flow_1g7mhgj">
        <di:waypoint x="340" y="118" />
        <di:waypoint x="402" y="118" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
  """;
  }

  static String bpmnProcessWithoutForm() {
    return """
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.27.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.5.0" camunda:diagramRelationId="7640587b-ab4b-4660-8e4e-4064a21c9626">
  <bpmn:process id="formProcess" name="processWithStartNodeFormDeployed" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_0d7gldh</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0d7gldh" sourceRef="StartEvent_1" targetRef="processStartedByForm_user_task" />
    <bpmn:endEvent id="Event_1cu4vf1">
      <bpmn:incoming>Flow_1g7mhgj</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1g7mhgj" sourceRef="processStartedByForm_user_task" targetRef="Event_1cu4vf1" />
    <bpmn:userTask id="processStartedByForm_user_task" name="processStartedByForm_user_task">
      <bpmn:incoming>Flow_0d7gldh</bpmn:incoming>
      <bpmn:outgoing>Flow_1g7mhgj</bpmn:outgoing>
    </bpmn:userTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="formProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="150" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1cu4vf1_di" bpmnElement="Event_1cu4vf1">
        <dc:Bounds x="402" y="100" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_14rkwl1_di" bpmnElement="processStartedByForm_user_task">
        <dc:Bounds x="240" y="78" width="100" height="80" />
        <bpmndi:BPMNLabel />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0d7gldh_di" bpmnElement="Flow_0d7gldh">
        <di:waypoint x="186" y="118" />
        <di:waypoint x="240" y="118" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1g7mhgj_di" bpmnElement="Flow_1g7mhgj">
        <di:waypoint x="340" y="118" />
        <di:waypoint x="402" y="118" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
  """;
  }
}

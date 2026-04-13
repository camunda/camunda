/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const AGENT_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bioc="http://bpmn.io/schema/bpmn/biocolor/1.0" xmlns:color="http://www.omg.org/spec/BPMN/non-normative/color/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_18jxukq" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.43.1" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.8.0">
  <bpmn:process id="ai-agent-chat-with-tools" name="AI Agent Chat With Tools" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Task to perform received">
      <bpmn:outgoing>Flow_0pbzrme</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_0pbzrme" sourceRef="StartEvent_1" targetRef="Gateway_0z6ctwk" />
    <bpmn:exclusiveGateway id="Gateway_0z6ctwk">
      <bpmn:incoming>Flow_0pbzrme</bpmn:incoming>
      <bpmn:incoming>Flow_19gp461</bpmn:incoming>
      <bpmn:outgoing>Flow_16otfp1</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="User_Feedback" name="User Feedback">
      <bpmn:incoming>Flow_0m7etfk</bpmn:incoming>
      <bpmn:outgoing>Flow_09y08ef</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_1dcg4ha" name="User satisfied?">
      <bpmn:incoming>Flow_09y08ef</bpmn:incoming>
      <bpmn:outgoing>Flow_19gp461</bpmn:outgoing>
      <bpmn:outgoing>Flow_16c9bwj</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="Flow_09y08ef" sourceRef="User_Feedback" targetRef="Gateway_1dcg4ha" />
    <bpmn:sequenceFlow id="Flow_19gp461" name="no - we follow up" sourceRef="Gateway_1dcg4ha" targetRef="Gateway_0z6ctwk">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=userSatisfied = null or userSatisfied = false</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:endEvent id="Event_0i39jej">
      <bpmn:incoming>Flow_16c9bwj</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_16c9bwj" name="yes" sourceRef="Gateway_1dcg4ha" targetRef="Event_0i39jej">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=userSatisfied</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:adHocSubProcess id="AI_Agent" name="AI Agent">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="io.camunda.agenticai:aiagent-job-worker:1" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_16otfp1</bpmn:incoming>
      <bpmn:outgoing>Flow_0m7etfk</bpmn:outgoing>
      <bpmn:scriptTask id="GetDateAndTime" name="Get Date and Time">
        <bpmn:documentation>Returns the current date and time including the timezone.</bpmn:documentation>
      </bpmn:scriptTask>
      <bpmn:serviceTask id="LoadUserByID" name="Load user by ID">
        <bpmn:documentation>Loads a user by ID</bpmn:documentation>
      </bpmn:serviceTask>
      <bpmn:serviceTask id="ListUsers" name="List users">
        <bpmn:documentation>Lists all available users.</bpmn:documentation>
      </bpmn:serviceTask>
      <bpmn:serviceTask id="Search_Recipe" name="Search recipe">
        <bpmn:documentation>Searches a recipe given a search query</bpmn:documentation>
      </bpmn:serviceTask>
      <bpmn:scriptTask id="SuperfluxProduct" name="Superflux Product Calculation">
        <bpmn:documentation>Calculates the superflux product given two input numbers</bpmn:documentation>
      </bpmn:scriptTask>
      <bpmn:userTask id="AskHumanToSendEmail" name="Ask human to send email">
        <bpmn:documentation>Ask a human to send an email for you</bpmn:documentation>
        <bpmn:outgoing>Flow_0demz1g</bpmn:outgoing>
      </bpmn:userTask>
      <bpmn:sequenceFlow id="Flow_0demz1g" sourceRef="AskHumanToSendEmail" targetRef="Gateway_1lux75t" />
      <bpmn:exclusiveGateway id="Gateway_1lux75t" name="OK to send email?">
        <bpmn:incoming>Flow_0demz1g</bpmn:incoming>
        <bpmn:outgoing>Flow_0uqjclh</bpmn:outgoing>
        <bpmn:outgoing>Flow_1l2ws6w</bpmn:outgoing>
      </bpmn:exclusiveGateway>
      <bpmn:sequenceFlow id="Flow_0uqjclh" name="yes" sourceRef="Gateway_1lux75t" targetRef="SendEmail" />
      <bpmn:sequenceFlow id="Flow_1l2ws6w" name="no" sourceRef="Gateway_1lux75t" targetRef="Event_1wxfv4u" />
      <bpmn:intermediateThrowEvent id="Event_1wxfv4u" name="loop back with feedback">
        <bpmn:incoming>Flow_1l2ws6w</bpmn:incoming>
      </bpmn:intermediateThrowEvent>
      <bpmn:scriptTask id="SendEmail" name="Send email">
        <bpmn:incoming>Flow_0uqjclh</bpmn:incoming>
      </bpmn:scriptTask>
      <bpmn:serviceTask id="Jokes_API" name="Jokes API">
        <bpmn:documentation>Fetches a random joke from a Jokes REST API</bpmn:documentation>
      </bpmn:serviceTask>
      <bpmn:serviceTask id="Fetch_URL" name="Fetch URL">
        <bpmn:documentation>Fetches the contents of a given URL.</bpmn:documentation>
      </bpmn:serviceTask>
    </bpmn:adHocSubProcess>
    <bpmn:sequenceFlow id="Flow_16otfp1" sourceRef="Gateway_0z6ctwk" targetRef="AI_Agent" />
    <bpmn:sequenceFlow id="Flow_0m7etfk" sourceRef="AI_Agent" targetRef="User_Feedback" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="ai-agent-chat-with-tools">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="182" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="132" y="225" width="76" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_0z6ctwk_di" bpmnElement="Gateway_0z6ctwk" isMarkerVisible="true">
        <dc:Bounds x="245" y="175" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_0i39jej_di" bpmnElement="Event_0i39jej">
        <dc:Bounds x="1142" y="182" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1fam9db_di" bpmnElement="User_Feedback">
        <dc:Bounds x="910" y="160" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1dcg4ha_di" bpmnElement="Gateway_1dcg4ha" isMarkerVisible="true">
        <dc:Bounds x="1055" y="175" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1044" y="232" width="73" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_03yngb7_di" bpmnElement="AI_Agent" isExpanded="true">
        <dc:Bounds x="350" y="160" width="510" height="540" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0kkpyxf_di" bpmnElement="ListUsers">
        <dc:Bounds x="380" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_1fihe00" bpmnElement="LoadUserByID">
        <dc:Bounds x="498" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1sbkoqq_di" bpmnElement="GetDateAndTime">
        <dc:Bounds x="616" y="330" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_16uhg8o_di" bpmnElement="AskHumanToSendEmail">
        <dc:Bounds x="385" y="510" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1l8tp85_di" bpmnElement="Jokes_API">
        <dc:Bounds x="616" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1knqabi_di" bpmnElement="Fetch_URL">
        <dc:Bounds x="730" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0d8luif_di" bpmnElement="Search_Recipe">
        <dc:Bounds x="380" y="330" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_0x0a1hy" bpmnElement="SuperfluxProduct">
        <dc:Bounds x="498" y="330" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_1lux75t_di" bpmnElement="Gateway_1lux75t" isMarkerVisible="true">
        <dc:Bounds x="523" y="525" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="520" y="496" width="56" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1ny5xkz_di" bpmnElement="Event_1wxfv4u">
        <dc:Bounds x="530" y="612" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="513" y="655" width="70" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1sddc2g_di" bpmnElement="SendEmail">
        <dc:Bounds x="625" y="510" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0demz1g_di" bpmnElement="Flow_0demz1g">
        <di:waypoint x="485" y="550" />
        <di:waypoint x="523" y="550" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0uqjclh_di" bpmnElement="Flow_0uqjclh">
        <di:waypoint x="573" y="550" />
        <di:waypoint x="625" y="550" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="597" y="552" width="18" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1l2ws6w_di" bpmnElement="Flow_1l2ws6w">
        <di:waypoint x="548" y="575" />
        <di:waypoint x="548" y="612" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="557" y="582" width="13" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0pbzrme_di" bpmnElement="Flow_0pbzrme">
        <di:waypoint x="188" y="200" />
        <di:waypoint x="245" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_19gp461_di" bpmnElement="Flow_19gp461">
        <di:waypoint x="1080" y="175" />
        <di:waypoint x="1080" y="100" />
        <di:waypoint x="270" y="100" />
        <di:waypoint x="270" y="175" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="981" y="83" width="83" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_16otfp1_di" bpmnElement="Flow_16otfp1">
        <di:waypoint x="295" y="200" />
        <di:waypoint x="350" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_16c9bwj_di" bpmnElement="Flow_16c9bwj">
        <di:waypoint x="1105" y="200" />
        <di:waypoint x="1142" y="200" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1115" y="182" width="18" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_0m7etfk_di" bpmnElement="Flow_0m7etfk">
        <di:waypoint x="860" y="200" />
        <di:waypoint x="910" y="200" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_09y08ef_di" bpmnElement="Flow_09y08ef">
        <di:waypoint x="1010" y="200" />
        <di:waypoint x="1055" y="200" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

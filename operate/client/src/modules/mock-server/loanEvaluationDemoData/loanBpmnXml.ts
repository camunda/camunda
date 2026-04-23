/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const LOAN_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xmlns:modeler="http://camunda.org/schema/modeler/1.0"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn"
                  exporter="Camunda Web Modeler"
                  exporterVersion="ce5fd89"
                  modeler:executionPlatform="Camunda Cloud"
                  modeler:executionPlatformVersion="8.8.0">

  <bpmn:process id="LoanEvaluation" name="Loan Evaluation" isExecutable="true">

    <bpmn:startEvent id="start" name="Loan application received">
      <bpmn:outgoing>flow_start_to_prompt</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:scriptTask id="create_prompt" name="Create prompt">
      <bpmn:extensionElements>
        <zeebe:script expression="=&quot;Evaluate loan application &quot; + applicationId + &quot; for applicant &quot; + applicantName + &quot; (ID: &quot; + applicantId + &quot;). Requested amount: \\u20AC&quot; + string(loanAmount) + &quot;. Purpose: &quot; + purpose + &quot;. Term: &quot; + string(termMonths) + &quot; months.&quot;" resultVariable="prompt" />
      </bpmn:extensionElements>
      <bpmn:incoming>flow_start_to_prompt</bpmn:incoming>
      <bpmn:outgoing>flow_prompt_to_merge</bpmn:outgoing>
    </bpmn:scriptTask>

    <bpmn:exclusiveGateway id="merge_gateway">
      <bpmn:incoming>flow_prompt_to_merge</bpmn:incoming>
      <bpmn:incoming>flow_tools_to_merge</bpmn:incoming>
      <bpmn:outgoing>flow_merge_to_agent</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:serviceTask id="ai_task_agent" name="Risk Assessment Agent">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="io.camunda.agenticai:aiagent:1" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>flow_merge_to_agent</bpmn:incoming>
      <bpmn:outgoing>flow_agent_to_check</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:exclusiveGateway id="tool_check" name="Run more tools?" default="flow_no_tools">
      <bpmn:incoming>flow_agent_to_check</bpmn:incoming>
      <bpmn:outgoing>flow_no_tools</bpmn:outgoing>
      <bpmn:outgoing>flow_yes_tools</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:adHocSubProcess id="tools" name="Tools">
      <bpmn:extensionElements>
        <zeebe:adHoc activeElementsCollection="=[toolCall._meta.name]" />
      </bpmn:extensionElements>
      <bpmn:incoming>flow_yes_tools</bpmn:incoming>
      <bpmn:outgoing>flow_tools_to_merge</bpmn:outgoing>

      <bpmn:serviceTask id="get_credit_score" name="Get credit score">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda:http-json:1" retries="3" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>

      <bpmn:serviceTask id="verify_income" name="Verify income">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda:http-json:1" retries="3" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>

      <bpmn:serviceTask id="check_risk_profile" name="Check risk profile">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda:http-json:1" retries="3" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>

      <bpmn:serviceTask id="check_fraud" name="Check fraud database">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda:http-json:1" retries="3" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>

      <bpmn:serviceTask id="notify_slack" name="Send Slack notification">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda:slack:1" retries="3" />
        </bpmn:extensionElements>
      </bpmn:serviceTask>

    </bpmn:adHocSubProcess>

    <bpmn:endEvent id="end" name="Assessment complete">
      <bpmn:incoming>flow_no_tools</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="flow_start_to_prompt" sourceRef="start" targetRef="create_prompt" />
    <bpmn:sequenceFlow id="flow_prompt_to_merge" sourceRef="create_prompt" targetRef="merge_gateway" />
    <bpmn:sequenceFlow id="flow_merge_to_agent" sourceRef="merge_gateway" targetRef="ai_task_agent" />
    <bpmn:sequenceFlow id="flow_agent_to_check" sourceRef="ai_task_agent" targetRef="tool_check" />
    <bpmn:sequenceFlow id="flow_no_tools" name="No" sourceRef="tool_check" targetRef="end" />
    <bpmn:sequenceFlow id="flow_yes_tools" name="Yes" sourceRef="tool_check" targetRef="tools">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">=not(agent.toolCalls = null) and count(agent.toolCalls) &gt; 0</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="flow_tools_to_merge" sourceRef="tools" targetRef="merge_gateway" />

  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="LoanEvaluation">

      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="150" y="100" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="127" y="143" width="82" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="create_prompt_di" bpmnElement="create_prompt">
        <dc:Bounds x="250" y="78" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="merge_gateway_di" bpmnElement="merge_gateway" isMarkerVisible="true">
        <dc:Bounds x="415" y="93" width="50" height="50" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="ai_task_agent_di" bpmnElement="ai_task_agent">
        <dc:Bounds x="530" y="78" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="tool_check_di" bpmnElement="tool_check" isMarkerVisible="true">
        <dc:Bounds x="705" y="93" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="689" y="69" width="81" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="tools_di" bpmnElement="tools" isExpanded="true">
        <dc:Bounds x="310" y="280" width="560" height="310" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="get_credit_score_di" bpmnElement="get_credit_score">
        <dc:Bounds x="340" y="310" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="verify_income_di" bpmnElement="verify_income">
        <dc:Bounds x="480" y="310" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="check_risk_profile_di" bpmnElement="check_risk_profile">
        <dc:Bounds x="620" y="310" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="check_fraud_di" bpmnElement="check_fraud">
        <dc:Bounds x="340" y="470" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="notify_slack_di" bpmnElement="notify_slack">
        <dc:Bounds x="480" y="470" width="100" height="80" />
      </bpmndi:BPMNShape>

      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="832" y="100" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="808" y="143" width="85" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>

      <bpmndi:BPMNEdge id="flow_start_to_prompt_di" bpmnElement="flow_start_to_prompt">
        <di:waypoint x="186" y="118" />
        <di:waypoint x="250" y="118" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_prompt_to_merge_di" bpmnElement="flow_prompt_to_merge">
        <di:waypoint x="350" y="118" />
        <di:waypoint x="415" y="118" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_merge_to_agent_di" bpmnElement="flow_merge_to_agent">
        <di:waypoint x="465" y="118" />
        <di:waypoint x="530" y="118" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_agent_to_check_di" bpmnElement="flow_agent_to_check">
        <di:waypoint x="630" y="118" />
        <di:waypoint x="705" y="118" />
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_no_tools_di" bpmnElement="flow_no_tools">
        <di:waypoint x="755" y="118" />
        <di:waypoint x="832" y="118" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="787" y="100" width="15" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_yes_tools_di" bpmnElement="flow_yes_tools">
        <di:waypoint x="730" y="143" />
        <di:waypoint x="730" y="435" />
        <di:waypoint x="870" y="435" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="736" y="286" width="18" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>

      <bpmndi:BPMNEdge id="flow_tools_to_merge_di" bpmnElement="flow_tools_to_merge">
        <di:waypoint x="310" y="435" />
        <di:waypoint x="280" y="435" />
        <di:waypoint x="280" y="200" />
        <di:waypoint x="440" y="200" />
        <di:waypoint x="440" y="143" />
      </bpmndi:BPMNEdge>

    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>

</bpmn:definitions>`;

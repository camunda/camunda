/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const PROCESS_INSTANCE_KEY = '2251799813700001';
const PROCESS_DEFINITION_KEY = '2251799813700000';

const AGENT_INSTANCE_KEY_1 = '2251799813700020';
const AGENT_INSTANCE_KEY_2 = '2251799813700021';
const AI_AGENT_ELEMENT_INSTANCE_KEY_1 = '2251799813700010';
const AI_AGENT_ELEMENT_INSTANCE_KEY_2 = '2251799813700011';

const xml = `<?xml version="1.0" encoding="UTF-8"?>
  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1sarj5u" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.49.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.10.0">
    <bpmn:process id="agent_process" name="Agent Process" isExecutable="true">
      <bpmn:startEvent id="start" name="Start">
        <bpmn:outgoing>Flow_0o3ojw8</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:serviceTask id="ai_agent" name="AI Agent" zeebe:modelerTemplate="io.camunda.connectors.agenticai.aiagent.v1" zeebe:modelerTemplateVersion="10" zeebe:modelerTemplateIcon="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiNBNTZFRkYiLz4KPG1hc2sgaWQ9InBhdGgtMi1vdXRzaWRlLTFfMTg1XzYiIG1hc2tVbml0cz0idXNlclNwYWNlT25Vc2UiIHg9IjQiIHk9IjQiIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0iYmxhY2siPgo8cmVjdCBmaWxsPSJ3aGl0ZSIgeD0iNCIgeT0iNCIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0Ii8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIvPgo8L21hc2s+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0yMC4wMTA1IDEyLjA5ODdDMTguNDkgMTAuNTg5NCAxNy4xNTk0IDguMTA4MTQgMTYuMTc5OSA2LjAxMTAzQzE2LjE1MiA2LjAwNDUxIDE2LjExNzYgNiAxNi4wNzk0IDZDMTYuMDQxMSA2IDE2LjAwNjYgNi4wMDQ1MSAxNS45Nzg4IDYuMDExMDRDMTQuOTk5NCA4LjEwODE0IDEzLjY2OTcgMTAuNTg4OSAxMi4xNDgxIDEyLjA5ODFDMTAuNjI2OSAxMy42MDcxIDguMTI1NjggMTQuOTI2NCA2LjAxMTU3IDE1Ljg5ODFDNi4wMDQ3NCAxNS45MjYxIDYgMTUuOTYxMSA2IDE2QzYgMTYuMDM4NyA2LjAwNDY4IDE2LjA3MzYgNi4wMTE0NCAxNi4xMDE0QzguMTI1MTkgMTcuMDcyOSAxMC42MjYyIDE4LjM5MTkgMTIuMTQ3NyAxOS45MDE2QzEzLjY2OTcgMjEuNDEwNyAxNC45OTk2IDIzLjg5MiAxNS45NzkxIDI1Ljk4OUMxNi4wMDY4IDI1Ljk5NTYgMTYuMDQxMSAyNiAxNi4wNzkzIDI2QzE2LjExNzUgMjYgMTYuMTUxOSAyNS45OTU0IDE2LjE3OTYgMjUuOTg5QzE3LjE1OTEgMjMuODkyIDE4LjQ4ODggMjEuNDExIDIwLjAwOTkgMTkuOTAyMU0yMC4wMDk5IDE5LjkwMjFDMjEuNTI1MyAxOC4zOTg3IDIzLjk0NjUgMTcuMDY2OSAyNS45OTE1IDE2LjA4MjRDMjUuOTk2NSAxNi4wNTkzIDI2IDE2LjAzMSAyNiAxNS45OTk3QzI2IDE1Ljk2ODQgMjUuOTk2NSAxNS45NDAzIDI1Ljk5MTUgMTUuOTE3MUMyMy45NDc0IDE0LjkzMjcgMjEuNTI1OSAxMy42MDEgMjAuMDEwNSAxMi4wOTg3IiBzdHJva2U9IiM0OTFEOEIiIHN0cm9rZS13aWR0aD0iNCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgbWFzaz0idXJsKCNwYXRoLTItb3V0c2lkZS0xXzE4NV82KSIvPgo8L3N2Zz4K">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="io.camunda.agenticai:aiagent:1" retries="3" />
          <zeebe:ioMapping>
            <zeebe:input source="openai" target="provider.type" />
            <zeebe:input source="xxx" target="provider.openai.authentication.apiKey" />
            <zeebe:input source="gpt-4o" target="provider.openai.model.model" />
            <zeebe:input source="=&quot;You are **TaskAgent**, a helpful, generic chat agent that can handle a wide variety of customer requests using your own domain knowledge **and** any tools explicitly provided to you at runtime.&quot;" target="data.systemPrompt.prompt" />
            <zeebe:input source="=user_prompt" target="data.userPrompt.prompt" />
            <zeebe:input source="=agent.context" target="data.context" />
            <zeebe:input source="in-process" target="data.memory.storage.type" />
            <zeebe:input source="=20" target="data.memory.contextWindowSize" />
            <zeebe:input source="=10" target="data.limits.maxModelCalls" />
            <zeebe:input source="text" target="data.response.format.type" />
            <zeebe:input source="=false" target="data.response.format.parseJson" />
            <zeebe:input source="=false" target="data.response.includeAssistantMessage" />
          </zeebe:ioMapping>
          <zeebe:taskHeaders>
            <zeebe:header key="elementTemplateVersion" value="10" />
            <zeebe:header key="elementTemplateId" value="io.camunda.connectors.agenticai.aiagent.v1" />
            <zeebe:header key="resultVariable" value="agent" />
            <zeebe:header key="resultExpression" />
            <zeebe:header key="errorExpression" />
            <zeebe:header key="retryBackoff" value="PT30S" />
          </zeebe:taskHeaders>
        </bpmn:extensionElements>
        <bpmn:incoming>Flow_0o3ojw8</bpmn:incoming>
        <bpmn:outgoing>Flow_1fxj4mg</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="Flow_0o3ojw8" sourceRef="start" targetRef="ai_agent" />
      <bpmn:intermediateThrowEvent id="end" name="End">
        <bpmn:incoming>Flow_1fxj4mg</bpmn:incoming>
      </bpmn:intermediateThrowEvent>
      <bpmn:sequenceFlow id="Flow_1fxj4mg" sourceRef="ai_agent" targetRef="end" />
    </bpmn:process>
    <bpmndi:BPMNDiagram id="BPMNDiagram_1">
      <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="agent_process">
        <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="start">
          <dc:Bounds x="182" y="102" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="188" y="145" width="24" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Activity_0s7p5ob_di" bpmnElement="ai_agent">
          <dc:Bounds x="270" y="80" width="100" height="80" />
          <bpmndi:BPMNLabel />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Event_0zlse3j_di" bpmnElement="end">
          <dc:Bounds x="422" y="102" width="36" height="36" />
          <bpmndi:BPMNLabel>
            <dc:Bounds x="430" y="145" width="20" height="14" />
          </bpmndi:BPMNLabel>
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Flow_0o3ojw8_di" bpmnElement="Flow_0o3ojw8">
          <di:waypoint x="218" y="120" />
          <di:waypoint x="270" y="120" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="Flow_1fxj4mg_di" bpmnElement="Flow_1fxj4mg">
          <di:waypoint x="370" y="120" />
          <di:waypoint x="422" y="120" />
        </bpmndi:BPMNEdge>
      </bpmndi:BPMNPlane>
    </bpmndi:BPMNDiagram>
  </bpmn:definitions>`;

const agentDefinition = {
  model: 'gpt-4o',
  provider: 'openai',
  systemPrompt:
    'You are **TaskAgent**, a helpful, generic chat agent that can handle a wide variety of customer requests using your own domain knowledge **and** any tools explicitly provided to you at runtime.',
};

const agentLimits = {
  maxModelCalls: 10,
  maxToolCalls: 5,
  maxTokens: 15_000,
};

const agentTools = [
  {
    name: 'get_order_status',
    description: 'Get the status of an order by ID',
    elementId: null,
  },
  {
    name: 'cancel_order',
    description: 'Cancel an order and issue a refund to the customer',
    elementId: null,
  },
];

// Scenario: agent element activated 2 times, 1 active agent instance
const agentProcessWithOneActiveInstance: InstanceMock = {
  detail: {
    processInstanceKey: PROCESS_INSTANCE_KEY,
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionName: 'Agent Process',
    processDefinitionVersion: 1,
    startDate: '2025-01-15T10:00:00.000+0000',
    state: 'ACTIVE',
    processDefinitionId: 'agent_process',
    tenantId: '<default>',
    hasIncident: false,
    processDefinitionVersionTag: null,
    endDate: null,
    parentProcessInstanceKey: null,
    parentElementInstanceKey: null,
    rootProcessInstanceKey: null,
    tags: [],
    businessId: null,
  },
  callHierarchy: [],
  xml,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813700001',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'start',
        elementName: 'Start',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2025-01-15T10:00:00.000+0000',
        endDate: '2025-01-15T10:00:00.000+0000',
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_1,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'ai_agent',
        elementName: 'AI Agent',
        type: 'SERVICE_TASK',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2025-01-15T10:00:01.000+0000',
        endDate: '2025-01-15T10:01:30.000+0000',
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_2,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'ai_agent',
        elementName: 'AI Agent',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2025-01-15T10:02:00.000+0000',
        endDate: null,
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
    ],
    page: {
      totalItems: 3,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  statistics: {
    items: [
      {elementId: 'start', active: 0, canceled: 0, incidents: 0, completed: 1},
      {
        elementId: 'ai_agent',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ],
  },
  sequenceFlows: {
    items: [
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_0o3ojw8',
        tenantId: '<default>',
        processDefinitionId: 'agent_process',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: '',
      },
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_1fxj4mg',
        tenantId: '<default>',
        processDefinitionId: 'agent_process',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: '',
      },
    ],
  },
  variables: [],
  agentInstances: {
    items: [
      {
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        status: 'THINKING',
        definition: agentDefinition,
        metrics: {
          inputTokens: 1100,
          outputTokens: 120,
          modelCalls: 2,
          toolCalls: 1,
        },
        limits: agentLimits,
        tools: agentTools,
        elementId: 'ai_agent',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        processDefinitionVersion: 1,
        processDefinitionVersionTag: null,
        tenantId: '<default>',
        creationDate: '2025-01-15T10:00:01.000Z',
        lastUpdatedDate: '2025-01-15T10:02:45.000Z',
        completionDate: null,
        elementInstanceKeys: [
          AI_AGENT_ELEMENT_INSTANCE_KEY_1,
          AI_AGENT_ELEMENT_INSTANCE_KEY_2,
        ],
      },
    ],
    page: {
      totalItems: 1,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  agentInstanceHistory: {
    items: [
      {
        historyItemKey: '2251799813700033',
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_2,
        jobKey: '2251799813700043',
        jobLease: 'lease-004',
        loopIteration: 2,
        role: 'ASSISTANT',
        content: [
          {
            contentType: 'TEXT',
            text: 'The order #12345 has been shipped via UPS. The tracking number is `1Z999AA10123456784`.',
          },
        ],
        toolCalls: [],
        metrics: {
          inputTokens: 650,
          outputTokens: 60,
          durationMs: 2340,
        },
        commitStatus: 'COMMITTED',
        producedAt: '2025-01-15T10:03:47.000Z',
      },
      {
        historyItemKey: '2251799813700032',
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_2,
        jobKey: '2251799813700042',
        jobLease: 'lease-003',
        loopIteration: 2,
        role: 'TOOL_RESULT',
        content: [
          {
            contentType: 'OBJECT',
            object: {
              orderId: '12345',
              status: 'SHIPPED',
              carrier: 'UPS',
              trackingNumber: '1Z999AA10123456784',
              estimatedDelivery: '2025-01-18',
              items: 3,
            },
          },
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: '3f34f325-143e-4e25-9be2-56195c8fd07b',
              contentHash:
                'c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974',
              metadata: {
                contentType: 'application/json',
                fileName: 'test_json.json',
                expiresAt: null,
                size: 187,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
        ],
        toolCalls: [
          {
            toolCallId: 'call_abc123',
            toolName: 'get_order_status',
            elementId: null,
            arguments: {orderId: '12345'},
          },
        ],
        metrics: null,
        commitStatus: 'COMMITTED',
        producedAt: '2025-01-15T10:02:47.000Z',
      },
      {
        historyItemKey: '2251799813700031',
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_1,
        jobKey: '2251799813700041',
        jobLease: 'lease-002',
        loopIteration: 1,
        role: 'ASSISTANT',
        content: [
          {
            contentType: 'TEXT',
            text: [
              "I'll look into that for you. Here's my plan for **order #12345**:",
              '',
              '1. Fetch the latest status via the `get_order_status` tool',
              '2. Summarize the *shipping* details for the customer',
              '',
              '> Calling the tool now — one moment while I check the order.',
            ].join('\n'),
          },
        ],
        toolCalls: [
          {
            toolCallId: 'call_abc123',
            toolName: 'get_order_status',
            elementId: null,
            arguments: {orderId: '12345'},
          },
        ],
        metrics: {
          inputTokens: 450,
          outputTokens: 60,
          durationMs: 1340,
        },
        commitStatus: 'COMMITTED',
        producedAt: '2025-01-15T10:02:45.000Z',
      },
      {
        historyItemKey: '2251799813700030',
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_1,
        jobKey: '2251799813700040',
        jobLease: 'lease-001',
        loopIteration: 1,
        role: 'USER',
        content: [
          {contentType: 'TEXT', text: 'What is the status of order #12345?'},
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: 'b6c19c47-e241-4e3a-9726-23aa7ca053ed',
              contentHash:
                '39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572',
              metadata: {
                contentType: 'image/png',
                fileName: 'test_image.png',
                expiresAt: null,
                size: 33906,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: '3f34f325-143e-4e25-9be2-56195c8fd07b',
              contentHash:
                'c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974',
              metadata: {
                contentType: 'application/json',
                fileName: 'expired_json.json',
                expiresAt: '2026-06-01',
                size: 187,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: 'ff787145-b82d-4ce4-bbc4-22c44f2201b8',
              contentHash:
                '848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96',
              metadata: {
                contentType: 'application/pdf',
                fileName: 'test_document.pdf',
                expiresAt: null,
                size: 25894,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
          {
            contentType: 'DOCUMENT',
            documentReference: {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: '3f34f325-143e-4e25-9be2-56195c8fd07b',
              contentHash:
                'c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974',
              metadata: {
                contentType: 'application/json',
                fileName: 'test_json.json',
                expiresAt: null,
                size: 187,
                processDefinitionId: null,
                processInstanceKey: null,
                customProperties: {},
              },
            },
          },
        ],
        toolCalls: [],
        metrics: null,
        commitStatus: 'COMMITTED',
        producedAt: '2025-01-15T10:00:01.000Z',
      },
    ],
    page: {
      totalItems: 4,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
};

// Scenario: agent element activated 2 times, 2 active agent instances
const agentProcessWithTwoActiveInstances: InstanceMock = {
  detail: {
    processInstanceKey: PROCESS_INSTANCE_KEY,
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionName: 'Agent Process',
    processDefinitionVersion: 1,
    startDate: '2025-01-15T10:00:00.000+0000',
    state: 'ACTIVE',
    processDefinitionId: 'agent_process',
    tenantId: '<default>',
    hasIncident: false,
    processDefinitionVersionTag: null,
    endDate: null,
    parentProcessInstanceKey: null,
    parentElementInstanceKey: null,
    rootProcessInstanceKey: null,
    tags: [],
    businessId: null,
  },
  callHierarchy: [],
  xml,
  elementInstances: {
    items: [
      {
        elementInstanceKey: '2251799813700001',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'start',
        elementName: 'Start',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        startDate: '2025-01-15T10:00:00.000+0000',
        endDate: '2025-01-15T10:00:00.000+0000',
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_1,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'ai_agent',
        elementName: 'AI Agent',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2025-01-15T10:00:01.000+0000',
        endDate: null,
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
      {
        elementInstanceKey: AI_AGENT_ELEMENT_INSTANCE_KEY_2,
        processInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        elementId: 'ai_agent',
        elementName: 'AI Agent',
        type: 'SERVICE_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        startDate: '2025-01-15T10:00:30.000+0000',
        endDate: null,
        tenantId: '<default>',
        rootProcessInstanceKey: null,
        incidentKey: null,
      },
    ],
    page: {
      totalItems: 3,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  statistics: {
    items: [
      {elementId: 'start', active: 0, canceled: 0, incidents: 0, completed: 1},
      {
        elementId: 'ai_agent',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ],
  },
  sequenceFlows: {
    items: [
      {
        processInstanceKey: PROCESS_INSTANCE_KEY,
        elementId: 'Flow_0o3ojw8',
        tenantId: '<default>',
        processDefinitionId: 'agent_process',
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        sequenceFlowId: '',
      },
    ],
  },
  variables: [],
  agentInstances: {
    items: [
      {
        agentInstanceKey: AGENT_INSTANCE_KEY_1,
        status: 'INITIALIZING',
        definition: agentDefinition,
        metrics: {
          inputTokens: 0,
          outputTokens: 0,
          modelCalls: 0,
          toolCalls: 0,
        },
        limits: agentLimits,
        tools: agentTools,
        elementId: 'ai_agent',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        processDefinitionVersion: 1,
        processDefinitionVersionTag: null,
        tenantId: '<default>',
        creationDate: '2025-01-15T10:00:01.000Z',
        lastUpdatedDate: '2025-01-15T10:00:01.000Z',
        completionDate: null,
        elementInstanceKeys: [AI_AGENT_ELEMENT_INSTANCE_KEY_1],
      },
      {
        agentInstanceKey: AGENT_INSTANCE_KEY_2,
        status: 'INITIALIZING',
        definition: agentDefinition,
        metrics: {
          inputTokens: 0,
          outputTokens: 0,
          modelCalls: 0,
          toolCalls: 0,
        },
        limits: agentLimits,
        tools: agentTools,
        elementId: 'ai_agent',
        processInstanceKey: PROCESS_INSTANCE_KEY,
        rootProcessInstanceKey: PROCESS_INSTANCE_KEY,
        processDefinitionKey: PROCESS_DEFINITION_KEY,
        processDefinitionId: 'agent_process',
        processDefinitionVersion: 1,
        processDefinitionVersionTag: null,
        tenantId: '<default>',
        creationDate: '2025-01-15T10:00:30.000Z',
        lastUpdatedDate: '2025-01-15T10:00:30.000Z',
        completionDate: null,
        elementInstanceKeys: [AI_AGENT_ELEMENT_INSTANCE_KEY_2],
      },
    ],
    page: {
      totalItems: 2,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
  agentInstanceHistory: {
    items: [],
    page: {
      totalItems: 0,
      startCursor: null,
      endCursor: null,
      hasMoreTotalItems: false,
    },
  },
};

export {agentProcessWithOneActiveInstance, agentProcessWithTwoActiveInstances};

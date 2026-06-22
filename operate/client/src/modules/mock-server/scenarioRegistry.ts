/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstance,
  ProcessDefinition,
  Variable,
  ElementInstance,
  Job,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {
  AgentInstance,
  HistoryElement,
} from 'modules/queries/agentInstances/types';

import {
  MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_ACTIVE,
  MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE,
  MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_ID_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_COMPLETED,
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_MULTIPLE,
  MOCK_AGENT_INSTANCE_KEY_FLAT,
  MOCK_AGENT_DEFINITION_KEY_FLAT,
  MOCK_AGENT_DEFINITION_ID_FLAT,
  MOCK_AGENT_SUBPROCESS_KEY_FLAT,
} from './agentDemoData/constants';
import {AGENT_BPMN_XML} from './agentDemoData/agentBpmnXml';
import {
  MOCK_AGENT_PROCESS_INSTANCE_ACTIVE,
  MOCK_AGENT_PROCESS_DEFINITION_ACTIVE,
  MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE,
  MOCK_AGENT_ELEMENT_STATISTICS_ACTIVE,
  MOCK_AGENT_SEQUENCE_FLOWS_ACTIVE,
  MOCK_AGENT_VARIABLES_ACTIVE,
  MOCK_AGENT_JOBS_ACTIVE,
  MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE,
  MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE,
  MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE,
  MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE,
  MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE,
  MOCK_AGENT_VARIABLES_NOT_ACTIVE,
  MOCK_AGENT_JOBS_NOT_ACTIVE,
  MOCK_AGENT_PROCESS_INSTANCE_COMPLETED,
  MOCK_AGENT_PROCESS_DEFINITION_COMPLETED,
  MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED,
  MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED,
  MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED,
  MOCK_AGENT_VARIABLES_COMPLETED,
  MOCK_AGENT_JOBS_COMPLETED,
  MOCK_AGENT_PROCESS_INSTANCE_MULTIPLE,
  MOCK_AGENT_PROCESS_DEFINITION_MULTIPLE,
  MOCK_AGENT_ELEMENT_INSTANCES_MULTIPLE,
  MOCK_AGENT_ELEMENT_STATISTICS_MULTIPLE,
  MOCK_AGENT_SEQUENCE_FLOWS_MULTIPLE,
  MOCK_AGENT_VARIABLES_MULTIPLE,
  MOCK_AGENT_JOBS_MULTIPLE,
  MOCK_AGENT_PROCESS_INSTANCE_FLAT,
  MOCK_AGENT_PROCESS_DEFINITION_FLAT,
  MOCK_AGENT_ELEMENT_INSTANCES_FLAT,
  MOCK_AGENT_ELEMENT_STATISTICS_FLAT,
  MOCK_AGENT_SEQUENCE_FLOWS_FLAT,
  MOCK_AGENT_VARIABLES_FLAT,
  MOCK_AGENT_JOBS_FLAT,
} from './agentDemoData/agentProcessInstance';
import {
  MOCK_AGENT_INSTANCE_ACTIVE,
  MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE,
  MOCK_AGENT_INSTANCE_COMPLETED,
  MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED,
  MOCK_AGENT_INSTANCE_MULTIPLE_1,
  MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_1,
  MOCK_AGENT_INSTANCE_MULTIPLE_2,
  MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_2,
  MOCK_AGENT_INSTANCE_FLAT,
  MOCK_AGENT_HISTORY_ELEMENTS_FLAT,
} from './agentDemoData/agentInstanceData';

type MockElementInstance = ElementInstance & {flowScopeKey: string};

export interface ScenarioDefinition {
  instanceKey: string;
  definitionKey: string;
  definitionId: string;
  name: string;
  description: string;
  pattern: 'subprocess' | 'task';
  // Selects which agent-detail renderer the Details tab uses. Omitted/'default'
  // keeps the current rendering; 'flat-trace' is the short-term demo.
  variant?: 'default' | 'flat-trace' | 'llm-in-tree';
  agentElementId: string;
  agentElementIds: Set<string>;
  agentInstanceKey?: string;
  bpmnXml: string;
  processInstance: ProcessInstance;
  processDefinition: ProcessDefinition;
  elementInstances: MockElementInstance[];
  elementStatistics: {
    items: Array<{
      elementId: string;
      active: number;
      canceled: number;
      incidents: number;
      completed: number;
    }>;
  };
  sequenceFlows: {
    items: Array<{processInstanceKey: string; elementId: string}>;
  };
  variables: Variable[];
  jobs: Job[];
  agentInstances: Array<{
    instance: AgentInstance;
    // BPMN element-instance key this agent run belongs to.
    elementInstanceKey: string;
    history: HistoryElement[];
  }>;
}

export const SCENARIOS: ScenarioDefinition[] = [
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    definitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    definitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    name: 'Agent active',
    description: 'Ad-hoc subprocess with agent + tools bundled together',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE_ACTIVE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION_ACTIVE,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_ACTIVE,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_ACTIVE,
    variables: MOCK_AGENT_VARIABLES_ACTIVE,
    jobs: MOCK_AGENT_JOBS_ACTIVE,
    agentInstances: [
      {
        instance: MOCK_AGENT_INSTANCE_ACTIVE,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
        history: MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE,
      },
    ],
  },
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    definitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    definitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    name: 'Agent not yet active',
    description: 'AI Agent element rendered but no agent run has started.',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_ACTIVE,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE,
    variables: MOCK_AGENT_VARIABLES_NOT_ACTIVE,
    jobs: MOCK_AGENT_JOBS_NOT_ACTIVE,
    agentInstances: [],
  },
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    definitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    definitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    name: 'Agent completed',
    description:
      'AI Agent run finished; details panel shows Completed status + exit message.',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_COMPLETED,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE_COMPLETED,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION_COMPLETED,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED,
    variables: MOCK_AGENT_VARIABLES_COMPLETED,
    jobs: MOCK_AGENT_JOBS_COMPLETED,
    agentInstances: [
      {
        instance: MOCK_AGENT_INSTANCE_COMPLETED,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
        history: MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED,
      },
    ],
  },
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    definitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    definitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    name: 'Multiple agent runs',
    description:
      'Two AI Agent runs separated by a User_Feedback (userSatisfied = false) loop.',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_MULTIPLE,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE_MULTIPLE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION_MULTIPLE,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_MULTIPLE,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_MULTIPLE,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_MULTIPLE,
    variables: MOCK_AGENT_VARIABLES_MULTIPLE,
    jobs: MOCK_AGENT_JOBS_MULTIPLE,
    agentInstances: [
      {
        instance: MOCK_AGENT_INSTANCE_MULTIPLE_1,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
        history: MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_1,
      },
      {
        instance: MOCK_AGENT_INSTANCE_MULTIPLE_2,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
        history: MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_2,
      },
    ],
  },
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY_FLAT,
    definitionKey: MOCK_AGENT_DEFINITION_KEY_FLAT,
    definitionId: MOCK_AGENT_DEFINITION_ID_FLAT,
    name: 'Agent — flat execution trace (short-term)',
    description:
      'Conversation history as a flat trace: tool calls are separate compact blocks at the same level as messages.',
    pattern: 'subprocess',
    variant: 'flat-trace',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS_COMPLETED,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE_FLAT,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION_FLAT,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES_FLAT,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS_FLAT,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS_FLAT,
    variables: MOCK_AGENT_VARIABLES_FLAT,
    jobs: MOCK_AGENT_JOBS_FLAT,
    agentInstances: [
      {
        instance: MOCK_AGENT_INSTANCE_FLAT,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_FLAT,
        history: MOCK_AGENT_HISTORY_ELEMENTS_FLAT,
      },
    ],
  },
];

export function getScenarioByInstanceKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.instanceKey === key);
}

export function getScenarioByDefinitionKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.definitionKey === key);
}

export function getScenarioByAgentInstanceKey(
  key: string,
): ScenarioDefinition | undefined {
  return SCENARIOS.find((s) => s.agentInstanceKey === key);
}

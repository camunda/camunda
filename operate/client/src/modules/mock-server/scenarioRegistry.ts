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
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
  MOCK_AGENT_AGENT_INSTANCE_KEY,
} from './agentDemoData/constants';
import {AGENT_BPMN_XML} from './agentDemoData/agentBpmnXml';
import {
  MOCK_AGENT_PROCESS_INSTANCE,
  MOCK_AGENT_PROCESS_DEFINITION,
  MOCK_AGENT_ELEMENT_INSTANCES,
  MOCK_AGENT_ELEMENT_STATISTICS,
  MOCK_AGENT_SEQUENCE_FLOWS,
  MOCK_AGENT_VARIABLES,
  MOCK_AGENT_JOBS,
} from './agentDemoData/agentProcessInstance';
import {
  MOCK_AGENT_INSTANCE,
  MOCK_AGENT_HISTORY_ELEMENTS,
} from './agentDemoData/agentInstanceData';

type MockElementInstance = ElementInstance & {flowScopeKey: string};

export interface ScenarioDefinition {
  instanceKey: string;
  definitionKey: string;
  definitionId: string;
  name: string;
  description: string;
  pattern: 'subprocess' | 'task';
  agentElementId: string;
  agentElementIds: Set<string>;
  agentInstanceKey: string;
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
    instanceKey: MOCK_AGENT_INSTANCE_KEY,
    definitionKey: MOCK_AGENT_DEFINITION_KEY,
    definitionId: MOCK_AGENT_DEFINITION_ID,
    name: 'Agent chat with tools',
    description: 'Ad-hoc subprocess with agent + tools bundled together',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS,
    variables: MOCK_AGENT_VARIABLES,
    jobs: MOCK_AGENT_JOBS,
    agentInstances: [
      {
        instance: MOCK_AGENT_INSTANCE,
        elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
        history: MOCK_AGENT_HISTORY_ELEMENTS,
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

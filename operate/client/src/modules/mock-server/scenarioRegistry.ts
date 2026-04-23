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
import {
  type AgentElementData,
  MOCK_AGENT_ENRICHMENT_DATA,
} from './agentDemoData/agentEnrichmentData';

import {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
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
  MOCK_LOAN_INSTANCE_KEY,
  MOCK_LOAN_DEFINITION_KEY,
  MOCK_LOAN_DEFINITION_ID,
  MOCK_LOAN_AGENT_TASK_KEY,
  MOCK_LOAN_AGENT_ELEMENT_IDS,
} from './loanEvaluationDemoData/constants';
import {LOAN_BPMN_XML} from './loanEvaluationDemoData/loanBpmnXml';
import {
  MOCK_LOAN_PROCESS_INSTANCE,
  MOCK_LOAN_PROCESS_DEFINITION,
  MOCK_LOAN_ELEMENT_INSTANCES,
  MOCK_LOAN_ELEMENT_STATISTICS,
  MOCK_LOAN_SEQUENCE_FLOWS,
  MOCK_LOAN_VARIABLES,
  MOCK_LOAN_JOBS,
} from './loanEvaluationDemoData/loanProcessInstance';
import {MOCK_LOAN_ENRICHMENT_DATA} from './loanEvaluationDemoData/loanEnrichmentData';

type MockElementInstance = ElementInstance & {flowScopeKey: string};

export interface ScenarioDefinition {
  instanceKey: string;
  definitionKey: string;
  definitionId: string;
  name: string;
  description: string;
  pattern: 'subprocess' | 'task';
  agentElementId: string;
  agentElementInstanceKey: string;
  agentElementIds: Set<string>;
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
  enrichmentData: Record<string, AgentElementData>;
}

export const SCENARIOS: ScenarioDefinition[] = [
  {
    instanceKey: MOCK_AGENT_INSTANCE_KEY,
    definitionKey: MOCK_AGENT_DEFINITION_KEY,
    definitionId: MOCK_AGENT_DEFINITION_ID,
    name: 'AI Agent Subprocess',
    description: 'Ad-hoc subprocess with agent + tools bundled together',
    pattern: 'subprocess',
    agentElementId: 'AI_Agent',
    agentElementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
    agentElementIds: MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
    bpmnXml: AGENT_BPMN_XML,
    processInstance: MOCK_AGENT_PROCESS_INSTANCE,
    processDefinition: MOCK_AGENT_PROCESS_DEFINITION,
    elementInstances: MOCK_AGENT_ELEMENT_INSTANCES,
    elementStatistics: MOCK_AGENT_ELEMENT_STATISTICS,
    sequenceFlows: MOCK_AGENT_SEQUENCE_FLOWS,
    variables: MOCK_AGENT_VARIABLES,
    jobs: MOCK_AGENT_JOBS,
    enrichmentData: MOCK_AGENT_ENRICHMENT_DATA,
  },
  {
    instanceKey: MOCK_LOAN_INSTANCE_KEY,
    definitionKey: MOCK_LOAN_DEFINITION_KEY,
    definitionId: MOCK_LOAN_DEFINITION_ID,
    name: 'AI Agent Task',
    description: 'Service task + separate tools subprocess + explicit loop',
    pattern: 'task',
    agentElementId: 'ai_task_agent',
    agentElementInstanceKey: MOCK_LOAN_AGENT_TASK_KEY,
    agentElementIds: MOCK_LOAN_AGENT_ELEMENT_IDS,
    bpmnXml: LOAN_BPMN_XML,
    processInstance: MOCK_LOAN_PROCESS_INSTANCE,
    processDefinition: MOCK_LOAN_PROCESS_DEFINITION,
    elementInstances: MOCK_LOAN_ELEMENT_INSTANCES,
    elementStatistics: MOCK_LOAN_ELEMENT_STATISTICS,
    sequenceFlows: MOCK_LOAN_SEQUENCE_FLOWS,
    variables: MOCK_LOAN_VARIABLES,
    jobs: MOCK_LOAN_JOBS,
    enrichmentData: MOCK_LOAN_ENRICHMENT_DATA,
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

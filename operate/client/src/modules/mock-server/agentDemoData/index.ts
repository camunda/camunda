/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  MOCK_AGENT_SUBPROCESS_KEY,
  MOCK_AGENT_LLM_CALL_1_KEY,
  MOCK_AGENT_LLM_CALL_2_KEY,
  MOCK_AGENT_LLM_CALL_3_KEY,
  MOCK_AGENT_SUBPROCESS_ELEMENT_IDS,
} from './constants';

import {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
} from './constants';

export function isAgentDemoInstance(processInstanceKey: string): boolean {
  return processInstanceKey === MOCK_AGENT_INSTANCE_KEY;
}

export function isAgentDemoDefinition(processDefinitionKey: string): boolean {
  return processDefinitionKey === MOCK_AGENT_DEFINITION_KEY;
}

export {AGENT_BPMN_XML} from './agentBpmnXml';
export {
  MOCK_AGENT_PROCESS_INSTANCE,
  MOCK_AGENT_PROCESS_DEFINITION,
  MOCK_AGENT_ELEMENT_INSTANCES,
  MOCK_AGENT_ELEMENT_STATISTICS,
  MOCK_AGENT_SEQUENCE_FLOWS,
  MOCK_AGENT_VARIABLES,
  MOCK_AGENT_JOBS,
} from './agentProcessInstance';
export {MOCK_AGENT_ENRICHMENT_DATA} from './agentEnrichmentData';
export type {
  AgentStatus,
  AgentToolCall,
  AgentIteration,
  AgentUsage,
  AgentElementData,
} from './agentEnrichmentData';

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// TODO(API): The /v2/agent-instances/* endpoints are not yet in
// @camunda/camunda-api-zod-schemas/8.10. These hand-written types mirror the
// API spec at docs/superpowers/plans/2026-04-30-agent-prototype-collapse.md
// (and the source file in the design epic). Once the schemas package exposes
// endpoint definitions, replace these with imports from the package and
// delete this file.

export type AgentInstanceStatus =
  | 'IDLE'
  | 'THINKING'
  | 'CALLING_TOOL'
  | 'WAITING_FOR_TOOL_RESULT'
  | 'COMPLETED'
  | 'FAILED';

export type AgentInstanceToolSource = 'AD_HOC' | 'MCP';

export type AgentInstanceTool = {
  name: string;
  source: AgentInstanceToolSource;
};

export type AgentInstanceDefinition = {
  model: string;
  provider: string;
  systemPrompt: string;
  tools: AgentInstanceTool[];
};

export type AgentInstanceMetrics = {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  toolCalls: number;
};

export type AgentInstance = {
  agentInstanceKey: string;
  status: AgentInstanceStatus;
  definition: AgentInstanceDefinition;
  metrics: AgentInstanceMetrics;
  creationTime: string;
  elementId: string;
  processInstanceKey: string;
  processDefinitionKey: string;
  tenantId: string;
};

export type HistoryElementRole =
  | 'user'
  | 'assistant'
  | 'tool_call'
  | 'tool_result';

export type HistoryElementContent = {
  // The spec lists `text` only and notes others may exist. Keep the union
  // open for forward compatibility but only `text` is implemented.
  contentType: 'text';
  content: string;
};

export type HistoryElementMetrics = Partial<{
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}>;

export type HistoryElement = {
  historyElementKey: string;
  agentInstanceKey: string;
  elementInstanceKey: string;
  jobKey: string;
  role: HistoryElementRole;
  content: HistoryElementContent[];
  timestamp: string;
  metrics: HistoryElementMetrics;
  committed: boolean;
};

// ----- Request/response bodies for the three read endpoints -----

export type SearchAgentInstancesRequestBody = {
  pagination?: {from?: number; limit?: number};
  filter?: {
    processInstanceKey?: string;
    elementId?: string;
    elementInstanceKey?: string;
  };
};

export type SearchAgentInstancesResponseBody = {
  items: AgentInstance[];
  total: number;
};

export type SearchAgentInstanceHistoryRequestBody = {
  pagination?: {from?: number; limit?: number};
  filter?: {
    elementInstanceKey?: string;
    committed?: boolean;
  };
};

export type SearchAgentInstanceHistoryResponseBody = {
  items: HistoryElement[];
  total: number;
};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

export async function loadAgenticDashboard() {
  const response = await get('api/dashboard/agentic');
  return response.json();
}

export interface AgentOption {
  agentId: string;
  name: string;
  processDefinitionKey: string;
}

export interface ModelOption {
  model: string;
  provider: string | null;
}

export interface AgenticFilterOptions {
  agents: AgentOption[];
  models: ModelOption[];
}

export async function loadAgenticFilterOptions(
  processDefinitionKey: string | null
): Promise<AgenticFilterOptions> {
  const response = await get(
    'api/dashboard/agentic/filter-options',
    processDefinitionKey ? {processDefinitionKey} : {}
  );
  return response.json();
}

export type AgentType = 'AI_AGENT_SUB_PROCESS' | 'AI_AGENT_TASK' | 'EXTERNAL_AGENT';

export interface AgentInventoryEntry {
  agentId: string;
  name: string;
  agentType: AgentType | null;
  processDefinitionKey: string | null;
  processDefinitionName: string | null;
  models: string[];
  maxModelCalls: number | null;
  runs: number;
  totalTokens: number | null;
  avgTokens: number | null;
  p50Tokens: number | null;
  successRate: number | null;
  durationAvgMs: number | null;
  durationP50Ms: number | null;
  durationP95Ms: number | null;
  avgHumanSteps: number | null;
  incidentRate: number | null;
  limitHitRate: number | null;
  lastRunDate: string | null;
}

export interface AgentInventorySummary {
  agents: number;
  processes: number;
  externalAgents: number;
}

export interface AgentInventory {
  summary: AgentInventorySummary;
  agents: AgentInventoryEntry[];
}

export async function loadAgentInventory(
  endedFrom: Date,
  processDefinitionKey: string | null
): Promise<AgentInventory> {
  const response = await get('api/dashboard/agentic/agent-inventory', {
    endedFrom: endedFrom.toISOString(),
    ...(processDefinitionKey ? {processDefinitionKey} : {}),
  });
  return response.json();
}

export type AgentLimit = 'maxModelCalls' | 'maxToolCalls' | 'maxTokens';

export interface FailureReason {
  kind: 'LIMIT' | 'INCIDENT';
  limit: string | null;
  flowNodeId: string | null;
  // null on an incident means the agent element itself failed, not one of its tools
  toolName: string | null;
  errorType: string | null;
  message: string | null;
  runs: number;
}

export interface AgentTool {
  toolFlowNodeId: string;
  toolName: string | null;
  calls: number;
  callsPerRun: number | null;
  incidents: number;
  failureRate: number | null;
}

export interface AgentModelUsage {
  model: string;
  provider: string | null;
  runs: number;
  avgTokens: number | null;
  avgDurationMs: number | null;
  successRate: number | null;
}

export interface AgentUsage {
  rootProcessDefinitionKey: string;
  rootProcessName: string | null;
  runs: number;
  totalTokens: number | null;
}

export interface AgentDetail {
  agentId: string;
  name: string;
  agentType: AgentType | null;
  processDefinitionKey: string | null;
  processDefinitionName: string | null;
  processDefinitionVersion: string | null;
  model: string | null;
  provider: string | null;
  maxModelCalls: number | null;
  maxToolCalls: number | null;
  maxTokens: number | null;
  runs: number;
  failedRuns: number;
  avgModelCalls: number | null;
  limitHits: {limit: AgentLimit; runs: number}[];
  failureReasons: FailureReason[];
  tools: AgentTool[];
  toolsAvailable: boolean;
  models: AgentModelUsage[];
  whereUsed: AgentUsage[];
}

export async function loadAgentDetail(agentId: string, endedFrom: Date): Promise<AgentDetail> {
  const response = await get(`api/dashboard/agentic/agents/${encodeURIComponent(agentId)}`, {
    endedFrom: endedFrom.toISOString(),
  });
  return response.json();
}

export interface AgentFailures {
  agentId: string;
  name: string;
  runs: number;
  failedRuns: number;
  successRate: number | null;
  topReasons: FailureReason[];
}

export async function loadProcessFailures(
  processDefinitionKey: string,
  endedFrom: Date
): Promise<AgentFailures[]> {
  const response = await get('api/dashboard/agentic/failures', {
    endedFrom: endedFrom.toISOString(),
    processDefinitionKey,
  });
  return (await response.json()).agents;
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type AgentStatus =
  | 'INITIALIZING'
  | 'TOOL_DISCOVERY'
  | 'THINKING'
  | 'WAITING_FOR_TOOL'
  | 'COMPLETED'
  | 'FAILED';

export interface AgentToolCall {
  toolName: string;
  toolElementId: string;
  toolDescription: string;
  rationale: string;
  input: Record<string, unknown>;
  output?: unknown;
  status: 'COMPLETED' | 'FAILED' | 'ACTIVE';
  duration?: string;
}

export type AgentFinishReason =
  | 'TOOL_EXECUTION'
  | 'STOP'
  | 'MAX_TOKENS'
  | 'ERROR';

export interface AgentIteration {
  iterationNumber: number;
  startTimestamp: string;
  endTimestamp?: string;
  finishReason?: AgentFinishReason;
  messageId?: string;
  userMessage?: string;
  agentMessage?: string;
  reasoning: string;
  toolCalls: AgentToolCall[];
  tokenUsage: {input: number; output: number; reasoning?: number};
}

export interface AgentUsage {
  modelCalls: {current: number; limit: number};
  tokensUsed: {
    inputTokens: number;
    outputTokens: number;
    reasoningTokens: number;
    totalTokens: number;
  };
  toolsCalled: {current: number; limit: number};
}

export interface ConversationMessage {
  role: 'system' | 'user' | 'assistant' | 'tool_call_result';
  // Each message can contain multiple text segments; assistant messages may
  // hold two segments (e.g. reasoning + a composed draft) rendered as one
  // block but expanded independently.
  content: string[];
  timestamp?: string;
  iterationNumber?: number;
  documents?: {
    name: string;
  }[];
  toolCalls?: {
    id: string;
    name: string;
    arguments: Record<string, unknown>;
  }[];
  toolResults?: {
    id: string;
    name: string;
    content: string;
  }[];
}

export interface AgentElementData {
  status: AgentStatus;
  statusDetail?: string;
  modelProvider: string;
  modelId: string;
  systemPrompt: string;
  summary?: string;
  userPrompt: string;
  iterations: AgentIteration[];
  usage: AgentUsage;
  toolDefinitions: {
    name: string;
    description: string;
    parameters?: Record<string, unknown>;
  }[];
  conversation?: ConversationMessage[];
}

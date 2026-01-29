/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type AgentContextToolDefinition = {
  name: string;
  description?: string;
  inputSchema?: unknown;
};

export type AgentContextMessageBase = {
  role: string;
  metadata?: {
    timestamp?: string;
    [key: string]: unknown;
  };
};

export type AgentContextContentPart = {
  type: string;
  text?: string;
  // keep backward-compat in case older payloads used `content`
  content?: string;
};

export type AgentContextToolCall = {
  id: string;
  name: string;
  arguments: string;
};

export type AgentContextToolCallResult = {
  id: string;
  name: string;
  content: string;
};

export type AgentContextConversationMessage = AgentContextMessageBase & {
  content?: AgentContextContentPart[];
  toolCalls?: AgentContextToolCall[];
  results?: AgentContextToolCallResult[];
};

export type AgentContextConversation = {
  type?: string;
  conversationId?: string;
  messages: AgentContextConversationMessage[];
};

export type AgentContext = {
  state?: string;
  metadata?: {
    processDefinitionKey?: number | string;
    processInstanceKey?: number | string;
    [key: string]: unknown;
  };
  metrics?: unknown;
  toolDefinitions?: AgentContextToolDefinition[];
  conversation?: AgentContextConversation;
  [key: string]: unknown;
};

export type AgentTimelineItemType =
  | 'AGENT_STATE'
  | 'TOOL_DEFINITIONS'
  | 'SYSTEM_PROMPT'
  | 'LLM_CALL'
  | 'TOOL_CALL'
  | 'TOOL_CALL_RESULT'
  | 'STATUS';

export type AgentTimelineItemBase = {
  id: string;
  type: AgentTimelineItemType;
  title: string;
  timestamp?: string;
};

export type AgentTimelineToolCall = {
  id: string;
  name: string;
  arguments: string;
  result?: AgentContextToolCallResult;
};

export type AgentTimelineItem =
  | (AgentTimelineItemBase & {
      type: 'AGENT_STATE';
      state?: string;
    })
  | (AgentTimelineItemBase & {
      type: 'TOOL_DEFINITIONS';
      toolDefinitions: AgentContextToolDefinition[];
    })
  | (AgentTimelineItemBase & {
      type: 'SYSTEM_PROMPT';
      content: AgentContextContentPart[];
    })
  | (AgentTimelineItemBase & {
      type: 'LLM_CALL';
      message: AgentContextConversationMessage;
    })
  | (AgentTimelineItemBase & {
      type: 'TOOL_CALL';
      toolCalls: AgentTimelineToolCall[];
      /** Optional assistant text content emitted with the toolCalls (e.g. <thinking>...) */
      content?: AgentContextContentPart[];
    })
  | (AgentTimelineItemBase & {
      type: 'TOOL_CALL_RESULT';
      results: AgentContextToolCallResult[];
    })
  | (AgentTimelineItemBase & {
      type: 'STATUS';
      status: 'THINKING' | 'WAITING_FOR_TOOL_RESULTS';
    });

export type AgentTimelineModel = {
  headerItems: AgentTimelineItem[];
  events: AgentTimelineItem[];
};

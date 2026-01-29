/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentContext,
  AgentContextConversation,
  AgentContextConversationMessage,
  AgentContextToolCall,
  AgentContextToolCallResult,
  AgentContextToolDefinition,
} from './types';

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return typeof value === 'object' && value !== null;
};

const asString = (value: unknown): string | undefined => {
  return typeof value === 'string' ? value : undefined;
};

const asArray = <T>(value: unknown, map: (v: unknown) => T | null): T[] => {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map(map).filter((v): v is T => v !== null);
};

const parseToolDefinition = (
  value: unknown,
): AgentContextToolDefinition | null => {
  if (!isRecord(value)) {
    return null;
  }

  const name = asString(value.name);
  if (!name) {
    return null;
  }

  return {
    name,
    description: asString(value.description),
    inputSchema: value.inputSchema,
  };
};

const parseToolCall = (value: unknown): AgentContextToolCall | null => {
  if (!isRecord(value)) {
    return null;
  }

  const id = asString(value.id);
  const name = asString(value.name);
  const args = value.arguments;

  if (!id || !name) {
    return null;
  }

  return {
    id,
    name,
    arguments:
      typeof args === 'string' ? args : JSON.stringify(args ?? {}, null, 2),
  };
};

const parseToolCallResult = (
  value: unknown,
): AgentContextToolCallResult | null => {
  if (!isRecord(value)) {
    return null;
  }

  const id = asString(value.id);
  const name = asString(value.name);

  if (!id || !name) {
    return null;
  }

  const rawContent = value.content;
  const content =
    typeof rawContent === 'string' ? rawContent : JSON.stringify(rawContent);

  return {id, name, content};
};

const parseMessage = (
  value: unknown,
): AgentContextConversationMessage | null => {
  if (!isRecord(value)) {
    return null;
  }

  const role = asString(value.role);
  if (!role) {
    return null;
  }

  const metadata = isRecord(value.metadata) ? value.metadata : undefined;

  const content = asArray(value.content, (part) => {
    if (!isRecord(part)) {
      return null;
    }
    const type = asString(part.type);
    const text = asString(part.text);
    const content = asString(part.content);

    if (!type || (text === undefined && content === undefined)) {
      return null;
    }

    return {type, text, content};
  });

  const toolCalls = asArray(value.toolCalls, parseToolCall);
  const results = asArray(value.results, parseToolCallResult);

  return {
    role,
    metadata: metadata as AgentContextConversationMessage['metadata'],
    ...(content.length > 0 ? {content} : {}),
    ...(toolCalls.length > 0 ? {toolCalls} : {}),
    ...(results.length > 0 ? {results} : {}),
  };
};

const parseConversation = (
  value: unknown,
): AgentContextConversation | undefined => {
  if (!isRecord(value)) {
    return undefined;
  }

  const messages = asArray(value.messages, parseMessage);

  return {
    type: asString(value.type),
    conversationId: asString(value.conversationId),
    messages,
  };
};

/**
 * Tolerantly parses an unknown JSON value into an AgentContext model.
 *
 * - Does not throw on missing/unknown fields
 * - Ensures arrays exist
 */
function parseAgentContext(value: unknown): AgentContext {
  if (!isRecord(value)) {
    return {conversation: {messages: []}};
  }

  return {
    ...value,
    state: asString(value.state),
    metadata: isRecord(value.metadata) ? value.metadata : undefined,
    metrics: value.metrics,
    toolDefinitions: asArray(value.toolDefinitions, parseToolDefinition),
    conversation: parseConversation(value.conversation) ?? {messages: []},
  };
}

export {parseAgentContext};
export type {
  AgentContext,
  AgentContextConversationMessage,
  AgentContextToolCall,
  AgentContextToolCallResult,
};

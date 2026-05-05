/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type AgentEvent,
  type AgentTransport,
  type SendMessagePayload,
  type ToolResultPayload,
} from '@camunda/copilot-chat';
import {requestWithThrow} from 'modules/request';
import {logger} from 'modules/logger';

const BASE = '/v2/copilot/conversations';

const sources = new Map<string, EventSource>();

const subscribe = (
  conversationId: string,
  onEvent: (event: AgentEvent) => void,
): void => {
  sources.get(conversationId)?.close();

  const source = new EventSource(`${BASE}/${conversationId}/events`, {
    withCredentials: true,
  });

  source.onmessage = (message) => {
    try {
      onEvent(JSON.parse(message.data) as AgentEvent);
    } catch (error) {
      logger.error('Failed to parse copilot SSE event', error);
    }
  };

  source.onerror = (error) => {
    logger.error('Copilot SSE connection error', error);
  };

  sources.set(conversationId, source);
};

const unsubscribe = (conversationId: string): void => {
  sources.get(conversationId)?.close();
  sources.delete(conversationId);
};

const sendMessage = async (payload: SendMessagePayload): Promise<void> => {
  const {error} = await requestWithThrow({
    url: `${BASE}/${payload.conversationId}/messages`,
    method: 'POST',
    body: payload,
    responseType: 'none',
  });

  if (error !== null) {
    throw new Error('Failed to send copilot message');
  }
};

const sendToolResult = async (_payload: ToolResultPayload): Promise<void> => {
  // Server-side tool execution: the backend invokes its own tools and never
  // emits EXTERNAL_TOOL_CALL events, so this hook is unused. Kept as a no-op
  // to satisfy the AgentTransport contract.
};

const haltConversation = async (conversationId: string): Promise<void> => {
  const {error} = await requestWithThrow({
    url: `${BASE}/${conversationId}/halt`,
    method: 'POST',
    responseType: 'none',
  });

  if (error !== null) {
    throw new Error('Failed to halt copilot conversation');
  }
};

const copilotTransport: AgentTransport = {
  subscribe,
  unsubscribe,
  sendMessage,
  sendToolResult,
  haltConversation,
};

export {copilotTransport};

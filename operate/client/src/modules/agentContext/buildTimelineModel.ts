/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentContext,
  AgentContextConversationMessage,
  AgentContextToolCallResult,
  AgentTimelineItem,
  AgentTimelineModel,
  AgentTimelineToolCall,
} from './types';
import {correlateToolCalls} from './correlateToolCalls';

const getTimestamp = (
  message?: AgentContextConversationMessage,
): string | undefined => {
  const ts = message?.metadata?.timestamp;
  return typeof ts === 'string' ? ts : undefined;
};

const buildHeaderItems = (ctx: AgentContext): AgentTimelineItem[] => {
  const header: AgentTimelineItem[] = [];

  header.push({
    id: 'agent-state',
    type: 'AGENT_STATE',
    title: 'Agent state',
    state: ctx.state,
  });

  if ((ctx.toolDefinitions?.length ?? 0) > 0) {
    header.push({
      id: 'tool-definitions',
      type: 'TOOL_DEFINITIONS',
      title: 'Tool definitions',
      toolDefinitions: ctx.toolDefinitions ?? [],
    });
  }

  const systemMessage = ctx.conversation?.messages.find(
    (m) => m.role === 'system',
  );
  if (systemMessage?.content && systemMessage.content.length > 0) {
    header.push({
      id: 'system-prompt',
      type: 'SYSTEM_PROMPT',
      title: 'System prompt',
      timestamp: getTimestamp(systemMessage),
      content: systemMessage.content,
    });
  }

  return header;
};

function buildTimelineModel(params: {
  agentContext: AgentContext;
  /**
   * If true, append a final "status" item indicating the agent is still running.
   */
  isRunning?: boolean;
}): AgentTimelineModel {
  const {agentContext: ctx, isRunning = false} = params;

  const headerItems = buildHeaderItems(ctx);

  const rawEvents = (ctx.conversation?.messages ?? []).filter(
    (m) => m.role !== 'system',
  );

  const assistantContentIndexes = rawEvents
    .map((m, idx) => ({m, idx}))
    .filter(
      ({m}) =>
        m.role === 'assistant' &&
        (!m.toolCalls || m.toolCalls.length === 0) &&
        (m.content?.length ?? 0) > 0,
    )
    .map(({idx}) => idx);

  const lastAssistantContentIndex =
    assistantContentIndexes.length > 0
      ? assistantContentIndexes[assistantContentIndexes.length - 1]
      : -1;

  // Collect results globally so tool-call starts can look up their matching result.
  const allToolResults: AgentContextToolCallResult[] = [];
  rawEvents.forEach((m) => {
    if (m.role === 'tool_call_result' && m.results && m.results.length > 0) {
      allToolResults.push(...m.results);
    }
  });

  const events: AgentTimelineItem[] = [];

  rawEvents.forEach((message, index) => {
    const timestamp = getTimestamp(message);

    // Tool call started
    if (
      message.role === 'assistant' &&
      message.toolCalls &&
      message.toolCalls.length > 0
    ) {
      const correlations = correlateToolCalls({
        toolCalls: message.toolCalls,
        results: allToolResults,
      });

      const toolCalls: AgentTimelineToolCall[] = correlations.map((c) => ({
        id: c.call.id,
        name: c.call.name,
        arguments: c.call.arguments,
        result: c.result,
      }));

      events.push({
        id: `tool-call-${index}-${toolCalls.map((t) => t.id).join('-')}`,
        type: 'TOOL_CALL',
        title:
          toolCalls.length === 1
            ? `Tool call: ${toolCalls[0].name}`
            : `Tool calls (${toolCalls.length})`,
        timestamp,
        toolCalls,
      });

      // Do NOT add a separate TOOL_CALL_RESULT event. Results are displayed inside the tool call.
      return;
    }

    // Tool results message: skip creating an event because the corresponding TOOL_CALL
    // event (assistant message with toolCalls) already renders the result once it is present.
    if (message.role === 'tool_call_result') {
      return;
    }

    // LLM call (assistant content)
    if (message.role === 'assistant') {
      const title =
        index === lastAssistantContentIndex ? 'Response' : 'Thinking';

      events.push({
        id: `llm-call-${index}`,
        type: 'LLM_CALL',
        title,
        timestamp,
        message,
      });
      return;
    }

    // Optional: user messages as context (kept as LLM_CALL for now)
    if (message.role === 'user') {
      events.push({
        id: `user-msg-${index}`,
        type: 'LLM_CALL',
        title: 'User message',
        timestamp,
        message,
      });
    }
  });

  if (isRunning) {
    const last = events[events.length - 1];

    const waitingForToolResults =
      last?.type === 'TOOL_CALL' &&
      last.toolCalls.some((t) => t.result === undefined);

    events.push({
      id: 'agent-status',
      type: 'STATUS',
      title: waitingForToolResults
        ? 'Waiting for tool call results…'
        : 'Thinking…',
      status: waitingForToolResults ? 'WAITING_FOR_TOOL_RESULTS' : 'THINKING',
    });
  }

  return {headerItems, events};
}

export {buildTimelineModel};

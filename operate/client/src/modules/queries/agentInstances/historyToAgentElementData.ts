/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentElementData,
  AgentIteration,
  AgentStatus,
  AgentToolCall,
  ConversationMessage,
} from 'modules/contexts/agentData.types';
import type {AgentInstance, AgentInstanceStatus, HistoryElement} from './types';

// TODO(API): the API spec does not yet include `limits`. Hardcoding to keep the
// existing UI counters working — replace once the spec lands the field.
const PROTOTYPE_MODEL_CALL_LIMIT = 10;
const PROTOTYPE_TOOL_CALL_LIMIT = 10;

const STATUS_MAP: Record<AgentInstanceStatus, AgentStatus> = {
  IDLE: 'INITIALIZING',
  THINKING: 'THINKING',
  CALLING_TOOL: 'WAITING_FOR_TOOL',
  WAITING_FOR_TOOL_RESULT: 'WAITING_FOR_TOOL',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
};

// TODO(API): once the agent-instance API exposes per-tool descriptions, source
// these from the definition instead of hardcoding. Today the BPMN carries them
// in <bpmn:documentation> on each tool element, but they're not surfaced through
// any API field the frontend reads.
const TOOL_DESCRIPTIONS: Record<string, string> = {
  ListUsers: 'Lists all available users in the directory.',
  LoadUserByID: "Fetches a single user's full profile by their ID.",
  GetDateAndTime:
    'Returns the current date and time, including the timezone offset.',
  DraftEmailTemplate:
    'Produces a formatted email draft from a recipient, subject, tone, and body outline.',
  AskHumanToSendEmail:
    'Routes a prepared email through a human operator who reviews the recipient and copy before sending.',
  AI_Task_Agent:
    'A nested AI task agent that the orchestrating agent can delegate a subtask to.',
  Search_Recipe: 'Searches a recipe given a free-text search query.',
  SuperfluxProduct: 'Calculates the superflux product of two input numbers.',
  Jokes_API: 'Fetches a random joke from the public Jokes REST API.',
  Fetch_URL: 'Fetches the contents of a given URL.',
};

const textOf = (element: HistoryElement): string =>
  element.content
    .filter((c) => c.contentType === 'text')
    .map((c) => c.content)
    .join('\n');

const safeParse = (raw: string): unknown => {
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
};

type ToolCallPayload = {name: string; input?: Record<string, unknown>};

const parseToolCall = (raw: string): ToolCallPayload => {
  const parsed = safeParse(raw);
  if (
    parsed &&
    typeof parsed === 'object' &&
    'name' in parsed &&
    typeof (parsed as ToolCallPayload).name === 'string'
  ) {
    return parsed as ToolCallPayload;
  }
  return {name: 'unknown', input: {}};
};

function historyToAgentElementData(
  instance: AgentInstance,
  history: HistoryElement[],
): AgentElementData {
  const iterations: AgentIteration[] = [];
  let current: AgentIteration | null = null;
  let pendingUserMessage: string | undefined;
  let pendingResultIndex = 0; // points at the next tool_call awaiting a result

  const closeCurrent = () => {
    if (current) {
      iterations.push(current);
      current = null;
      pendingResultIndex = 0;
    }
  };

  for (const element of history) {
    const text = textOf(element);

    if (element.role === 'user') {
      closeCurrent();
      pendingUserMessage = text;
      continue;
    }

    if (element.role === 'assistant') {
      closeCurrent();
      current = {
        iterationNumber: iterations.length + 1,
        startTimestamp: element.timestamp,
        userMessage: pendingUserMessage,
        agentMessage: text,
        reasoning: text,
        toolCalls: [],
        tokenUsage: {
          input: element.metrics.inputTokens ?? 0,
          output: element.metrics.outputTokens ?? 0,
        },
        finishReason: 'TOOL_EXECUTION',
        messageId: element.historyElementKey,
      };
      pendingUserMessage = undefined;
      continue;
    }

    if (element.role === 'tool_call') {
      if (!current) {
        // Defensive: a tool_call without a preceding assistant. Open an empty
        // iteration so the call is still surfaced.
        current = {
          iterationNumber: iterations.length + 1,
          startTimestamp: element.timestamp,
          userMessage: pendingUserMessage,
          reasoning: '',
          toolCalls: [],
          tokenUsage: {input: 0, output: 0},
          finishReason: 'TOOL_EXECUTION',
        };
        pendingUserMessage = undefined;
      }
      const payload = parseToolCall(text);
      const toolCall: AgentToolCall = {
        toolName: payload.name,
        toolElementId: payload.name,
        toolDescription: TOOL_DESCRIPTIONS[payload.name] ?? '',
        // Surface the iteration's reasoning so the status accordion has copy
        // to show while the tool call is in-flight.
        rationale: current.reasoning,
        input: payload.input ?? {},
        status: 'ACTIVE',
      };
      current.toolCalls.push(toolCall);
      continue;
    }

    if (element.role === 'tool_result') {
      if (!current) {
        continue;
      }
      const target = current.toolCalls[pendingResultIndex];
      if (target) {
        target.output = safeParse(text);
        target.status = 'COMPLETED';
        pendingResultIndex += 1;
      }
      current.endTimestamp = element.timestamp;
      continue;
    }
  }

  closeCurrent();

  const conversation: ConversationMessage[] = [
    {role: 'system', content: [instance.definition.systemPrompt]},
  ];
  let conversationIterationCount = 0;
  for (const element of history) {
    const text = textOf(element);
    if (element.role === 'user') {
      const blocks = element.content
        .filter((c) => c.contentType === 'text')
        .map((c) => c.content);
      conversation.push({
        role: 'user',
        content: blocks,
        timestamp: element.timestamp,
      });
    } else if (element.role === 'assistant') {
      conversationIterationCount += 1;
      const blocks = element.content
        .filter((c) => c.contentType === 'text')
        .map((c) => c.content);
      conversation.push({
        role: 'assistant',
        content: blocks,
        timestamp: element.timestamp,
        iterationNumber: conversationIterationCount,
      });
    } else if (element.role === 'tool_call') {
      const payload = parseToolCall(text);
      const newToolCall = {
        id: element.historyElementKey,
        name: payload.name,
        arguments: payload.input ?? {},
      };
      // Attach to the preceding assistant message so its text and the tools it
      // called appear in a single conversation entry (avoids empty-text
      // assistant blocks that show only tool chips).
      const lastMsg = conversation[conversation.length - 1];
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.toolCalls = lastMsg.toolCalls ?? [];
        lastMsg.toolCalls.push(newToolCall);
      } else {
        conversation.push({
          role: 'assistant',
          content: [],
          timestamp: element.timestamp,
          toolCalls: [newToolCall],
        });
      }
    } else if (element.role === 'tool_result') {
      conversation.push({
        role: 'tool_call_result',
        content: [],
        timestamp: element.timestamp,
        toolResults: [{id: element.historyElementKey, name: '', content: text}],
      });
    }
  }

  const firstUser = history.find((h) => h.role === 'user');
  const userPrompt = firstUser ? textOf(firstUser) : '';

  // statusDetail: name(s) of any in-flight tool call in the most recent iteration.
  const lastIteration = iterations[iterations.length - 1];
  const activeToolNames =
    lastIteration?.toolCalls
      .filter((tc) => tc.status === 'ACTIVE')
      .map((tc) => tc.toolName) ?? [];

  return {
    status: STATUS_MAP[instance.status] ?? 'THINKING',
    statusDetail:
      activeToolNames.length > 0 ? activeToolNames.join(', ') : undefined,
    modelProvider: instance.definition.provider,
    modelId: instance.definition.model,
    systemPrompt: instance.definition.systemPrompt,
    userPrompt,
    iterations,
    usage: {
      modelCalls: {
        current: iterations.length,
        limit: PROTOTYPE_MODEL_CALL_LIMIT,
      },
      tokensUsed: {
        inputTokens: instance.metrics.inputTokens,
        outputTokens: instance.metrics.outputTokens,
        reasoningTokens: 0,
        totalTokens: instance.metrics.totalTokens,
      },
      toolsCalled: {
        current: instance.metrics.toolCalls,
        limit: PROTOTYPE_TOOL_CALL_LIMIT,
      },
    },
    // TODO(API): tool source/description/parameters not in the spec yet — fill
    // with empty strings so the UI shows the name only.
    toolDefinitions: instance.definition.tools.map((t) => ({
      name: t.name,
      description: '',
    })),
    conversation,
  };
}

export {historyToAgentElementData};

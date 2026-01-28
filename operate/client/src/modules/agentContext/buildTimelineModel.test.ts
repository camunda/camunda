/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {parseAgentContext} from './parseAgentContext';
import {buildTimelineModel} from './buildTimelineModel';

describe('agentContext timeline', () => {
  it('correlates tool calls with results by id and produces timeline items', () => {
    const raw = {
      state: 'READY',
      toolDefinitions: [{name: 'foo', description: 'bar'}],
      conversation: {
        messages: [
          {
            role: 'system',
            content: [{type: 'text', text: 'You are system'}],
          },
          {
            role: 'assistant',
            content: [{type: 'text', text: 'Hello'}],
          },
          {
            role: 'assistant',
            toolCalls: [
              {
                id: 'call-1',
                name: 'ToolA',
                arguments: '{}',
              },
            ],
          },
          {
            role: 'tool_call_result',
            results: [
              {
                id: 'call-1',
                name: 'ToolA',
                content: 'ok',
              },
            ],
          },
        ],
      },
    };

    const ctx = parseAgentContext(raw);
    const timeline = buildTimelineModel({agentContext: ctx});

    // header
    expect(timeline.headerItems.some((i) => i.type === 'AGENT_STATE')).toBe(
      true,
    );
    expect(
      timeline.headerItems.some((i) => i.type === 'TOOL_DEFINITIONS'),
    ).toBe(true);
    expect(timeline.headerItems.some((i) => i.type === 'SYSTEM_PROMPT')).toBe(
      true,
    );

    // events: tool call should include matched result
    const toolCall = timeline.events.find((i) => i.type === 'TOOL_CALL');
    expect(toolCall).toBeTruthy();
    expect(toolCall?.type).toBe('TOOL_CALL');

    const typedToolCall = toolCall as Extract<
      (typeof timeline.events)[number],
      {type: 'TOOL_CALL'}
    >;

    expect(typedToolCall.toolCalls).toHaveLength(1);
    expect(typedToolCall.toolCalls[0].id).toBe('call-1');
    expect(typedToolCall.toolCalls[0].result?.id).toBe('call-1');
    expect(typedToolCall.toolCalls[0].result?.content).toContain('ok');

    // assistant content should create an LLM_CALL with title "Response" (last assistant content)
    const response = timeline.events.find(
      (i) => i.type === 'LLM_CALL' && i.title === 'Response',
    );
    expect(response).toBeTruthy();
    expect(timeline.events.some((i) => i.type === 'TOOL_CALL_RESULT')).toBe(
      false,
    );
  });

  it('adds a running status item and marks waiting when tool results are missing', () => {
    const raw = {
      conversation: {
        messages: [
          {
            role: 'assistant',
            toolCalls: [{id: 'call-1', name: 'doThing', arguments: '{"x":1}'}],
          },
        ],
      },
    };

    const ctx = parseAgentContext(raw);
    const timeline = buildTimelineModel({agentContext: ctx, isRunning: true});

    const status = timeline.events[timeline.events.length - 1];
    expect(status.type).toBe('STATUS');

    const typedStatus = status as Extract<
      (typeof timeline.events)[number],
      {type: 'STATUS'}
    >;

    expect(typedStatus.status).toBe('WAITING_FOR_TOOL_RESULTS');
  });

  it('correlates multiple tool calls in a single assistant message by id', () => {
    const raw = {
      conversation: {
        messages: [
          {
            role: 'assistant',
            toolCalls: [
              {id: 'call-a', name: 'ToolA', arguments: {}},
              {id: 'call-b', name: 'ToolB', arguments: {}},
            ],
          },
          {
            role: 'tool_call_result',
            results: [
              {id: 'call-b', name: 'ToolB', content: 'b-ok'},
              {id: 'call-a', name: 'ToolA', content: 'a-ok'},
            ],
          },
        ],
      },
    };

    const ctx = parseAgentContext(raw);
    const timeline = buildTimelineModel({agentContext: ctx});

    const toolCall = timeline.events.find((i) => i.type === 'TOOL_CALL');
    expect(toolCall).toBeTruthy();
    expect(toolCall?.type).toBe('TOOL_CALL');

    const typedToolCall = toolCall as Extract<
      (typeof timeline.events)[number],
      {type: 'TOOL_CALL'}
    >;

    expect(typedToolCall.toolCalls).toHaveLength(2);

    const a = typedToolCall.toolCalls.find((t) => t.id === 'call-a');
    const b = typedToolCall.toolCalls.find((t) => t.id === 'call-b');

    expect(a?.result?.content).toBe('a-ok');
    expect(b?.result?.content).toBe('b-ok');
  });

  it('handles non-string tool result content and still correlates by id', () => {
    const raw = {
      conversation: {
        messages: [
          {
            role: 'assistant',
            toolCalls: [
              {id: 'call-j', name: 'Jokes_API', arguments: {}},
              {id: 'call-s', name: 'SuperfluxProduct', arguments: {a: 3, b: 5}},
            ],
          },
          {
            role: 'tool_call_result',
            results: [
              {id: 'call-j', name: 'Jokes_API', content: 'j-ok'},
              {id: 'call-s', name: 'SuperfluxProduct', content: 24},
            ],
          },
        ],
      },
    };

    const ctx = parseAgentContext(raw);
    const timeline = buildTimelineModel({agentContext: ctx});

    const toolCall = timeline.events.find((i) => i.type === 'TOOL_CALL');
    expect(toolCall?.type).toBe('TOOL_CALL');

    const typedToolCall = toolCall as Extract<
      (typeof timeline.events)[number],
      {type: 'TOOL_CALL'}
    >;

    const jokes = typedToolCall.toolCalls.find((t) => t.id === 'call-j');
    const superflux = typedToolCall.toolCalls.find((t) => t.id === 'call-s');

    expect(jokes?.result?.content).toBe('j-ok');
    expect(superflux?.result?.content).toBe('24');
  });
});

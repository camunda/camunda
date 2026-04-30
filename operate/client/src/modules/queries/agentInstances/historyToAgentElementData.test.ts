/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {historyToAgentElementData} from './historyToAgentElementData';
import type {AgentInstance, HistoryElement} from './types';

const baseInstance: AgentInstance = {
  agentInstanceKey: 'agent-1',
  status: 'CALLING_TOOL',
  definition: {
    model: 'claude-sonnet-4',
    provider: 'anthropic',
    systemPrompt: 'You are helpful.',
    tools: [
      {name: 'search_orders', source: 'AD_HOC'},
      {name: 'send_email', source: 'AD_HOC'},
    ],
  },
  metrics: {
    inputTokens: 1000,
    outputTokens: 200,
    totalTokens: 1200,
    toolCalls: 2,
  },
  creationTime: '2026-04-07T10:00:00.000Z',
  elementId: 'AI_Agent',
  processInstanceKey: 'pi-1',
  processDefinitionKey: 'pd-1',
  tenantId: '<default>',
};

const makeElement = (
  overrides: Omit<Partial<HistoryElement>, 'content'> & {
    role: HistoryElement['role'];
    content: string;
  },
  index: number,
): HistoryElement => {
  const {content, ...rest} = overrides;
  return {
    historyElementKey: `he-${index}`,
    agentInstanceKey: 'agent-1',
    elementInstanceKey: 'ei-1',
    jobKey: 'job-1',
    timestamp: `2026-04-07T10:0${index}:00.000Z`,
    metrics: {},
    committed: true,
    ...rest,
    content: [{contentType: 'text', content}],
  };
};

describe('historyToAgentElementData', () => {
  it('should group one assistant + tool_call + tool_result into a single completed iteration', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Find order ORD-1'}, 1),
      makeElement({role: 'assistant', content: 'Looking it up.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({
            name: 'search_orders',
            input: {orderId: 'ORD-1'},
          }),
        },
        3,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({status: 'shipped'})},
        4,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations).toHaveLength(1);
    expect(result.iterations[0]!.userMessage).toBe('Find order ORD-1');
    expect(result.iterations[0]!.agentMessage).toBe('Looking it up.');
    expect(result.iterations[0]!.toolCalls[0]!.toolName).toBe('search_orders');
    expect(result.iterations[0]!.toolCalls[0]!.status).toBe('COMPLETED');
    expect(result.iterations[0]!.toolCalls[0]!.output).toEqual({
      status: 'shipped',
    });
  });

  it('should leave a tool_call without a matching tool_result as ACTIVE with no output', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Send the email.'}, 1),
      makeElement({role: 'assistant', content: 'Sending now.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({name: 'send_email', input: {to: 'a@b.c'}}),
        },
        3,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations[0]!.toolCalls[0]!.status).toBe('ACTIVE');
    expect(result.iterations[0]!.toolCalls[0]!.output).toBeUndefined();
  });

  it('should match parallel tool_results to their tool_calls by arrival order', () => {
    const history: HistoryElement[] = [
      makeElement({role: 'user', content: 'Lookup and date.'}, 1),
      makeElement({role: 'assistant', content: 'Two parallel calls.'}, 2),
      makeElement(
        {
          role: 'tool_call',
          content: JSON.stringify({name: 'load_user', input: {id: 1}}),
        },
        3,
      ),
      makeElement(
        {role: 'tool_call', content: JSON.stringify({name: 'now', input: {}})},
        4,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({email: 'a@b.c'})},
        5,
      ),
      makeElement(
        {role: 'tool_result', content: JSON.stringify({iso: '2026-04-07'})},
        6,
      ),
    ];

    const result = historyToAgentElementData(baseInstance, history);

    expect(result.iterations[0]!.toolCalls).toHaveLength(2);
    expect(result.iterations[0]!.toolCalls[0]!.toolName).toBe('load_user');
    expect(result.iterations[0]!.toolCalls[0]!.output).toEqual({
      email: 'a@b.c',
    });
    expect(result.iterations[0]!.toolCalls[1]!.toolName).toBe('now');
    expect(result.iterations[0]!.toolCalls[1]!.output).toEqual({
      iso: '2026-04-07',
    });
  });
});

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';

type HistoryStep = {
  key: string;
  role: 'USER' | 'ASSISTANT' | 'TOOL_RESULT';
  kind: string;
  label: string;
  detail: string;
};

const basename = (value: unknown) =>
  typeof value === 'string'
    ? (value.split('/').filter(Boolean).at(-1) ?? '')
    : '';

const getHistorySteps = (items: AgentInstanceHistoryItem[]): HistoryStep[] => {
  let modelResponses = 0;
  return items.flatMap((item): HistoryStep[] => {
    const {historyItemKey: key, toolCalls} = item;
    if (item.role === 'USER') {
      return [
        {
          key,
          role: 'USER',
          kind: 'User input',
          label: 'User message',
          detail: '',
        },
      ];
    }
    if (item.role === 'ASSISTANT') {
      return [
        {
          key,
          role: 'ASSISTANT',
          kind: `Model response ${++modelResponses}`,
          label: toolCalls.length
            ? `Requests ${toolCalls.length} tool call${toolCalls.length === 1 ? '' : 's'}`
            : 'Assistant message',
          detail: toolCalls.map(({toolName}) => toolName).join(', '),
        },
      ];
    }
    if (item.role === 'TOOL_RESULT') {
      const [toolCall] = toolCalls;
      return [
        {
          key,
          role: 'TOOL_RESULT',
          kind: 'Tool result',
          label: toolCall?.toolName ?? 'Tool result',
          detail: basename(toolCall?.arguments?.['path']),
        },
      ];
    }
    return [];
  });
};

export {getHistorySteps};
export type {HistoryStep};

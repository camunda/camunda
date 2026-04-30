/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentInstance,
  HistoryElement,
} from 'modules/queries/agentInstances/types';
import {
  MOCK_AGENT_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_SUBPROCESS_KEY,
} from './constants';

const JOB_KEY = '4451799813685011';

export const MOCK_AGENT_INSTANCE: AgentInstance = {
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
  status: 'CALLING_TOOL',
  definition: {
    model: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
    provider: 'AWS Bedrock',
    systemPrompt: `You are a helpful assistant that performs tasks on behalf of the user.

## Available Tools

You have access to the following tools:
- **ListUsers** — query the user directory
- **LoadUserByID** — fetch a single user profile by \`id\`
- **GetDateAndTime** — get the current UTC timestamp
- **AskHumanToSendEmail** — delegate email sending to a human operator

## Guidelines

1. Always **verify** information before taking action.
2. Use \`=inputText\` for the user's request.
3. When composing emails, include all required fields.
4. Be concise in your reasoning.`,
    tools: [
      {name: 'ListUsers', source: 'AD_HOC'},
      {name: 'LoadUserByID', source: 'AD_HOC'},
      {name: 'GetDateAndTime', source: 'AD_HOC'},
      {name: 'AskHumanToSendEmail', source: 'AD_HOC'},
    ],
  },
  metrics: {
    inputTokens: 1959,
    outputTokens: 242,
    totalTokens: 2201,
    toolCalls: 4,
  },
  creationTime: '2026-03-26T14:30:00.300Z',
  elementId: 'AI_Agent',
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY,
  tenantId: '<default>',
};

const baseElement = (overrides: {
  historyElementKey: string;
  role: HistoryElement['role'];
  timestamp: string;
  content: string;
  metrics?: HistoryElement['metrics'];
}): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY,
  jobKey: JOB_KEY,
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS: HistoryElement[] = [
  // ----- Iteration 1: user asks, agent calls ListUsers -----
  baseElement({
    historyElementKey: '6755399441055700',
    role: 'user',
    timestamp: '2026-03-26T14:30:00.400Z',
    content:
      'Find the email address of user Leanne Graham and send her an invitation to the company offsite.',
  }),
  baseElement({
    historyElementKey: '6755399441055701',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:01.200Z',
    content:
      "I'll start by looking up the user directory to find Leanne Graham.",
    metrics: {inputTokens: 482, outputTokens: 87, totalTokens: 569},
  }),
  baseElement({
    historyElementKey: '6755399441055702',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:01.300Z',
    content: JSON.stringify({name: 'ListUsers', input: {}}),
  }),
  baseElement({
    historyElementKey: '6755399441055703',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:02.900Z',
    content: JSON.stringify([
      {id: 1, name: 'Leanne Graham', email: 'Sincere@april.biz'},
      {id: 2, name: 'Ervin Howell', email: 'Shanna@melissa.tv'},
    ]),
  }),

  // ----- Iteration 2: parallel LoadUserByID + GetDateAndTime -----
  baseElement({
    historyElementKey: '6755399441055704',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:03.000Z',
    content:
      'Found Leanne Graham (id: 1). Loading her full profile and getting the current date in parallel.',
    metrics: {inputTokens: 621, outputTokens: 64, totalTokens: 685},
  }),
  baseElement({
    historyElementKey: '6755399441055705',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:03.050Z',
    content: JSON.stringify({name: 'LoadUserByID', input: {id: 1}}),
  }),
  baseElement({
    historyElementKey: '6755399441055706',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:03.100Z',
    content: JSON.stringify({name: 'GetDateAndTime', input: {}}),
  }),
  baseElement({
    historyElementKey: '6755399441055707',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:04.100Z',
    content: JSON.stringify({
      id: 1,
      name: 'Leanne Graham',
      email: 'Sincere@april.biz',
      phone: '1-770-736-8031 x56442',
      company: {name: 'Romaguera-Crona'},
    }),
  }),
  baseElement({
    historyElementKey: '6755399441055708',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:04.150Z',
    content: JSON.stringify({timestamp: '2026-03-26T14:30:04.245+00:00'}),
  }),

  // ----- Iteration 3: AskHumanToSendEmail in flight -----
  baseElement({
    historyElementKey: '6755399441055709',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:04.290Z',
    content:
      "I have everything needed: Leanne's email is Sincere@april.biz and the current date is 2026-03-26. Requesting a human operator to send the invitation email.",
    metrics: {inputTokens: 856, outputTokens: 91, totalTokens: 947},
  }),
  baseElement({
    historyElementKey: '6755399441055710',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:04.300Z',
    content: JSON.stringify({
      name: 'AskHumanToSendEmail',
      input: {
        recipient_name: 'Leanne Graham',
        recipient_email: 'Sincere@april.biz',
        email_subject: 'Company Offsite Invitation',
        email_body:
          'Dear Leanne,\n\nYou are invited to the company offsite on March 28, 2026.\n\nBest regards',
      },
    }),
  }),
];

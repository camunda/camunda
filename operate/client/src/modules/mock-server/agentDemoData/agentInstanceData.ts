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
  MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
} from './constants';

const JOB_KEY = '4451799813685011';

export const MOCK_AGENT_INSTANCE_ACTIVE: AgentInstance = {
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
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
- **DraftEmailTemplate** — produce a formatted email draft from a subject and body
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
      {name: 'DraftEmailTemplate', source: 'AD_HOC'},
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
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  tenantId: '<default>',
};

const baseElement = (overrides: {
  historyElementKey: string;
  role: HistoryElement['role'];
  timestamp: string;
  content: string;
  metrics?: HistoryElement['metrics'];
}): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
  jobKey: JOB_KEY,
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE: HistoryElement[] = [
  // ----- Iteration 1: user asks, agent calls ListUsers -----
  baseElement({
    historyElementKey: '6755399441055700',
    role: 'user',
    timestamp: '2026-03-26T14:30:00.400Z',
    content:
      "Hi! Could you find the email address of user Leanne Graham and send her an invitation to the company offsite on March 28? Please make sure you pick the correct Leanne if there are several — she's the one at Romaguera-Crona. The invitation should be friendly and include the date prominently. Thanks!",
  }),
  baseElement({
    historyElementKey: '6755399441055701',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:01.200Z',
    content:
      "To find Leanne Graham's email I need to start from the user directory, since the request doesn't include an ID and I don't have her contact info cached. I'll call `ListUsers` first to get the full set of users and confirm she exists; that also lets me disambiguate if there's more than one Leanne — the user mentioned Romaguera-Crona as her company, which I can use as a tiebreaker. Once I have her ID, I'll fetch her full profile to get the verified email address before drafting the invitation. I'm going to avoid sending the email myself and instead delegate to a human operator at the end, since that's the safer pattern for outbound communication.",
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
      "Good — the directory returned a single Leanne (id: 1, email Sincere@april.biz) and the only other user, Ervin Howell, clearly isn't who the request is about, so no disambiguation is needed beyond the obvious. The user also mentioned Romaguera-Crona, which I'd still like to verify against her full profile to be sure I'm not relying on a stale directory snapshot. I'll fetch the full profile via `LoadUserByID` and at the same time pull the current date via `GetDateAndTime` — running these in parallel saves a round-trip, and I need the date anyway so the invitation copy reads naturally relative to the offsite on March 28. Once both come back I'll have everything I need to draft the invitation and hand it off to a human operator.",
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
      "Everything checks out: the full profile confirms Leanne Graham at Romaguera-Crona, her email is Sincere@april.biz, and today is 2026-03-26 — so the offsite on March 28 is two days out, which is worth surfacing in the copy so the urgency reads. I'll draft a short, friendly invitation that opens warmly, names the date prominently in the first line of the body, and signs off without overpromising on logistics, since the request didn't mention venue or agenda. Because sending email is an outbound action with real-world consequences, I'm routing it through `AskHumanToSendEmail` rather than firing it directly — this gives the human operator a final review on tone and recipient before anything goes out, which is the right level of friction for a personal invitation.",
    metrics: {inputTokens: 856, outputTokens: 91, totalTokens: 947},
  }),
  baseElement({
    historyElementKey: '6755399441055710',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:04.300Z',
    content: JSON.stringify({
      name: 'DraftEmailTemplate',
      input: {
        recipient_name: 'Leanne Graham',
        subject: 'Company Offsite Invitation — March 28',
        tone: 'friendly',
        body_outline: [
          'Open warmly and address Leanne by first name.',
          'Lead the body with the offsite date (March 28, 2026) on its own line.',
          'Close with a soft RSVP nudge — no firm logistics promised.',
        ],
      },
    }),
  }),
  baseElement({
    historyElementKey: '6755399441055711',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:04.350Z',
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

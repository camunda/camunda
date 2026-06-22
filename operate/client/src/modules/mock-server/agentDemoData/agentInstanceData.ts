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
  MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
  MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
  MOCK_AGENT_INSTANCE_KEY_FLAT,
  MOCK_AGENT_DEFINITION_KEY_FLAT,
  MOCK_AGENT_SUBPROCESS_KEY_FLAT,
  MOCK_AGENT_AGENT_INSTANCE_KEY_FLAT,
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
  {
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_ACTIVE,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    jobKey: JOB_KEY,
    committed: true,
    historyElementKey: '6755399441055701',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:01.200Z',
    content: [
      {
        contentType: 'text',
        content:
          "To find Leanne Graham's email I need to start from the user directory, since the request doesn't include an ID and I don't have her contact info cached. I'll call `ListUsers` first to confirm she exists and to disambiguate if there's more than one Leanne — the user mentioned Romaguera-Crona as her company, which I can use as a tiebreaker.",
      },
      {
        contentType: 'text',
        content:
          "Once I have her ID, I'll fetch her full profile to get the verified email address before drafting the invitation. I'm going to avoid sending the email myself and instead delegate to a human operator at the end, since that's the safer pattern for outbound communication.",
      },
    ],
    metrics: {inputTokens: 482, outputTokens: 87, totalTokens: 569},
  },
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

export const MOCK_AGENT_INSTANCE_COMPLETED: AgentInstance = {
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
  status: 'COMPLETED',
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
    inputTokens: 2814,
    outputTokens: 358,
    totalTokens: 3172,
    toolCalls: 5,
  },
  creationTime: '2026-03-26T14:30:00.300Z',
  elementId: 'AI_Agent',
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  tenantId: '<default>',
};

// Build the COMPLETED history by reusing MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE and
// appending the missing tool_results + a final assistant exit message.
const completedBaseElement = (overrides: {
  historyElementKey: string;
  role: HistoryElement['role'];
  timestamp: string;
  content: string;
  metrics?: HistoryElement['metrics'];
}): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  jobKey: '6451799813685011',
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED: HistoryElement[] = [
  // The first iterations mirror the active scenario verbatim. Reuse the text
  // but rebind the agentInstanceKey + elementInstanceKey to the COMPLETED keys.
  ...MOCK_AGENT_HISTORY_ELEMENTS_ACTIVE.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_COMPLETED,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  })),
  // Tool results for DraftEmailTemplate + AskHumanToSendEmail (which were in-flight
  // in the active scenario).
  completedBaseElement({
    historyElementKey: '6451799441055720',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:05.100Z',
    content: JSON.stringify({
      subject: 'Company Offsite Invitation — March 28',
      preview:
        'Hi Leanne,\\n\\nThe team is hosting the offsite on March 28, 2026 — would love to have you there.',
      tone_used: 'friendly',
    }),
  }),
  completedBaseElement({
    historyElementKey: '6451799441055721',
    role: 'tool_result',
    timestamp: '2026-03-26T14:30:05.400Z',
    content: JSON.stringify({sent: true, sent_at: '2026-03-26T14:30:05.380Z'}),
  }),
  // Final assistant exit message — what StatusAccordion shows in the Completed view.
  completedBaseElement({
    historyElementKey: '6451799441055722',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:05.500Z',
    content:
      'Done — the invitation to Leanne Graham at Sincere@april.biz has been drafted with March 28 prominently in the body, the human operator confirmed the send, and the email left the queue at 14:30:05 UTC. The full audit trail (5 tool calls across 3 iterations) is recorded under this agent run, and no follow-up is required from the user.',
    metrics: {inputTokens: 1255, outputTokens: 116, totalTokens: 1371},
  }),
];

// State 4 — Multiple element instances: two AI_Agent runs separated by User_Feedback.

// Run 1 — completed agent run
export const MOCK_AGENT_INSTANCE_MULTIPLE_1: AgentInstance = {
  ...MOCK_AGENT_INSTANCE_COMPLETED,
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
};

export const MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_1: HistoryElement[] =
  MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_1,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  }));

// Run 2 — active agent run, responding to user feedback from Run 1.
// Shorter than ACTIVE: one iteration, two in-flight tool calls, no results yet.
export const MOCK_AGENT_INSTANCE_MULTIPLE_2: AgentInstance = {
  ...MOCK_AGENT_INSTANCE_ACTIVE,
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  creationTime: '2026-03-26T14:30:15.000Z',
  metrics: {
    inputTokens: 612,
    outputTokens: 78,
    totalTokens: 690,
    toolCalls: 2,
  },
};

const multiple2BaseElement = (overrides: {
  historyElementKey: string;
  role: HistoryElement['role'];
  timestamp: string;
  content: string;
  metrics?: HistoryElement['metrics'];
}): HistoryElement => ({
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_MULTIPLE_2,
  elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  jobKey: '7451799813685011',
  committed: true,
  metrics: {},
  ...overrides,
  content: [{contentType: 'text', content: overrides.content}],
});

export const MOCK_AGENT_HISTORY_ELEMENTS_MULTIPLE_2: HistoryElement[] = [
  // User feedback replayed as the new user prompt for Run 2.
  multiple2BaseElement({
    historyElementKey: '7451799441055800',
    role: 'user',
    timestamp: '2026-03-26T14:30:15.000Z',
    content:
      "The draft is OK but it doesn't include the day of the week — March 28 is a Saturday and that context matters. Also please mention it's a casual offsite, not a formal event.",
  }),
  // Single iteration: assistant reasons about the feedback, then issues two parallel tool calls.
  multiple2BaseElement({
    historyElementKey: '7451799441055801',
    role: 'assistant',
    timestamp: '2026-03-26T14:30:15.400Z',
    content:
      "Got the feedback — two adjustments: surface that March 28 is a Saturday (so the weekend framing reads naturally) and shift the tone from neutral-formal to casual. I'll regenerate the template with both notes and re-route through the human operator so they can review the revised copy before send.",
    metrics: {inputTokens: 612, outputTokens: 78, totalTokens: 690},
  }),
  multiple2BaseElement({
    historyElementKey: '7451799441055802',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:15.500Z',
    content: JSON.stringify({
      name: 'DraftEmailTemplate',
      input: {
        recipient_name: 'Leanne Graham',
        subject: 'Saturday offsite — bring something casual',
        tone: 'casual',
        body_outline: [
          'Open warmly with first name.',
          "Lead with the date as 'Saturday, March 28'.",
          'Position the event as casual — no agenda, no dress code.',
          'Close with a low-pressure RSVP nudge.',
        ],
      },
    }),
  }),
  multiple2BaseElement({
    historyElementKey: '7451799441055803',
    role: 'tool_call',
    timestamp: '2026-03-26T14:30:15.550Z',
    content: JSON.stringify({
      name: 'AskHumanToSendEmail',
      input: {
        recipient_name: 'Leanne Graham',
        recipient_email: 'Sincere@april.biz',
        email_subject: 'Saturday offsite — bring something casual',
        email_body:
          'Hi Leanne,\n\nQuick update — the offsite is this Saturday, March 28. Nothing formal — bring whatever you want, we just want you there.\n\nLet us know either way!',
      },
    }),
  }),
];

// ----- Flat-trace short-term demo -----
export const MOCK_AGENT_INSTANCE_FLAT: AgentInstance = {
  ...MOCK_AGENT_INSTANCE_COMPLETED,
  agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_FLAT,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_FLAT,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_FLAT,
};

export const MOCK_AGENT_HISTORY_ELEMENTS_FLAT: HistoryElement[] =
  MOCK_AGENT_HISTORY_ELEMENTS_COMPLETED.map((el) => ({
    ...el,
    agentInstanceKey: MOCK_AGENT_AGENT_INSTANCE_KEY_FLAT,
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_FLAT,
  }));

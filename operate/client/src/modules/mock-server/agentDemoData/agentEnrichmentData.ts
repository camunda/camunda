/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type AgentStatus =
  | 'INITIALIZING'
  | 'TOOL_DISCOVERY'
  | 'THINKING'
  | 'WAITING_FOR_TOOL'
  | 'COMPLETED'
  | 'FAILED';

export interface AgentToolCall {
  toolName: string;
  toolElementId: string;
  toolDescription: string;
  rationale: string;
  input: Record<string, unknown>;
  output?: unknown;
  status: 'COMPLETED' | 'FAILED' | 'ACTIVE';
  duration?: string;
}

export type AgentFinishReason =
  | 'TOOL_EXECUTION'
  | 'STOP'
  | 'MAX_TOKENS'
  | 'ERROR';

export interface AgentIteration {
  iterationNumber: number;
  startTimestamp: string;
  endTimestamp?: string;
  finishReason?: AgentFinishReason;
  messageId?: string;
  userMessage?: string;
  agentMessage?: string;
  reasoning: string;
  toolCalls: AgentToolCall[];
  tokenUsage: {input: number; output: number; reasoning?: number};
}

export interface AgentUsage {
  modelCalls: {current: number; limit: number};
  tokensUsed: {
    inputTokens: number;
    outputTokens: number;
    reasoningTokens: number;
    totalTokens: number;
  };
  toolsCalled: {current: number; limit: number};
}

export interface ConversationMessage {
  role: 'system' | 'user' | 'assistant' | 'tool_call_result';
  // Each message can contain multiple text segments; assistant messages may
  // hold two segments (e.g. reasoning + a composed draft) rendered as one
  // block but expanded independently.
  content: string[];
  timestamp?: string;
  documents?: {
    name: string;
  }[];
  toolCalls?: {
    id: string;
    name: string;
    arguments: Record<string, unknown>;
  }[];
  toolResults?: {
    id: string;
    name: string;
    content: string;
  }[];
}

export interface AgentElementData {
  status: AgentStatus;
  statusDetail?: string;
  modelProvider: string;
  modelId: string;
  systemPrompt: string;
  summary?: string;
  userPrompt: string;
  iterations: AgentIteration[];
  usage: AgentUsage;
  toolDefinitions: {
    name: string;
    description: string;
    parameters?: Record<string, unknown>;
  }[];
  conversation?: ConversationMessage[];
}

export const MOCK_AGENT_ENRICHMENT_DATA: Record<string, AgentElementData> = {
  '4451799813685010': {
    status: 'WAITING_FOR_TOOL',
    statusDetail: 'AskHumanToSendEmail',
    modelProvider: 'AWS Bedrock',
    modelId: 'us.anthropic.claude-sonnet-4-20250514-v1:0',
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
3. When composing emails, include all required fields: \`recipient_name\`, \`recipient_email\`, \`email_subject\`, and \`email_body\`.
4. Be concise in your reasoning.`,
    userPrompt:
      'Find the email address of user Leanne Graham and send her an invitation to the company offsite.',
    summary:
      'Found user Leanne Graham via the users API, retrieved her email (Sincere@april.biz), obtained the current date, and requested a human to send an offsite invitation email.',
    iterations: [
      {
        iterationNumber: 1,
        startTimestamp: '2026-03-26T14:30:00.400Z',
        endTimestamp: '2026-03-26T14:30:02.900Z',
        finishReason: 'TOOL_EXECUTION',
        messageId: 'msg_01VfASwdPUkiKEP8LYBwheUE',
        userMessage:
          'Find the email address of user Leanne Graham and send her an invitation to the company offsite.',
        reasoning:
          "The user wants to find Leanne Graham's email and send an invitation. First, I need to find her in the user directory. I'll call the ListUsers tool to get a list of all users and identify Leanne Graham.",
        agentMessage:
          "I'll start by looking up the user directory to find Leanne Graham.",
        toolCalls: [
          {
            toolName: 'List users',
            toolElementId: 'ListUsers',
            toolDescription:
              'Retrieves a list of all users from the user directory API',
            rationale:
              'Need to search the user directory to find Leanne Graham and her user ID before I can get her email address.',
            input: {},
            output: [
              {id: 1, name: 'Leanne Graham', email: 'Sincere@april.biz'},
              {id: 2, name: 'Ervin Howell', email: 'Shanna@melissa.tv'},
            ],
            status: 'COMPLETED',
            duration: '1.6s',
          },
        ],
        tokenUsage: {input: 482, output: 87, reasoning: 64},
      },
      {
        iterationNumber: 2,
        startTimestamp: '2026-03-26T14:30:02.950Z',
        endTimestamp: '2026-03-26T14:30:04.150Z',
        finishReason: 'TOOL_EXECUTION',
        messageId: 'msg_02KpLm8nRtYx4bCd9EfGhIjK',
        reasoning:
          'I found Leanne Graham in the user list with id 1 and a preliminary email address. However, the list endpoint only returns summary data, so I should load her full profile to confirm the email is correct and up to date. I also need the current date and time so I can include it in the invitation email — this is important because the offsite is time-sensitive and the recipient needs to know when the message was composed. Since these two lookups are independent of each other, I can call both tools in parallel to save time. Once I have the confirmed email and the current timestamp, I will have everything needed to draft the invitation.',
        agentMessage:
          'Found Leanne Graham (id: 1). Loading her full profile and getting the current date.',
        toolCalls: [
          {
            toolName: 'Load user by ID',
            toolElementId: 'LoadUserByID',
            toolDescription: 'Loads detailed user profile by numeric user ID',
            rationale:
              'The user list returned a summary — I need the full profile to confirm the email address and gather additional details for the invitation.',
            input: {id: 1},
            output: {
              id: 1,
              name: 'Leanne Graham',
              email: 'Sincere@april.biz',
              phone: '1-770-736-8031 x56442',
              company: {
                name: 'Romaguera-Crona',
                catchPhrase: 'Multi-layered client-server neural-net',
              },
            },
            status: 'COMPLETED',
            duration: '1.1s',
          },
          {
            toolName: 'Get Date and Time',
            toolElementId: 'GetDateAndTime',
            toolDescription:
              'Returns the current date and time in ISO 8601 format',
            rationale:
              'I need the current date to include in the invitation email so the recipient knows when it was sent.',
            input: {},
            output: {timestamp: '2026-03-26T14:30:04.245+00:00'},
            status: 'COMPLETED',
            duration: '50ms',
          },
        ],
        tokenUsage: {input: 621, output: 64, reasoning: 48},
      },
      {
        iterationNumber: 3,
        startTimestamp: '2026-03-26T14:30:04.290Z',
        finishReason: 'TOOL_EXECUTION',
        messageId: 'msg_03QrStUvWxYz5aBcDeFgHiJk',
        reasoning:
          "I now have all the information needed: Leanne Graham's email is Sincere@april.biz and the current date is 2026-03-26. I'll ask a human to send the invitation email.",
        agentMessage:
          'I have everything needed. Requesting a human operator to send the invitation email to Leanne Graham.',
        toolCalls: [
          {
            toolName: 'Ask human to send email',
            toolElementId: 'AskHumanToSendEmail',
            toolDescription:
              'Requests a human operator to compose and send an email',
            rationale:
              'All required information has been gathered. Delegating to a human to compose and send the actual invitation email, as email sending requires human approval.',
            input: {
              recipient_name: 'Leanne Graham',
              recipient_email: 'Sincere@april.biz',
              email_subject: 'Company Offsite Invitation',
              email_body:
                'Dear Leanne,\n\nYou are invited to the company offsite on March 28, 2026.\n\nBest regards',
            },
            status: 'ACTIVE',
          },
        ],
        tokenUsage: {input: 856, output: 91, reasoning: 30},
      },
    ],
    usage: {
      modelCalls: {current: 3, limit: 10},
      tokensUsed: {
        inputTokens: 7843,
        outputTokens: 1173,
        reasoningTokens: 142,
        totalTokens: 9158,
      },
      toolsCalled: {current: 4, limit: 10},
    },
    toolDefinitions: [
      {
        name: 'List users',
        description:
          'Retrieves a list of all users from the user directory API',
        parameters: {type: 'object', properties: {}},
      },
      {
        name: 'Load user by ID',
        description: 'Loads detailed user profile by numeric user ID',
        parameters: {
          type: 'object',
          properties: {id: {type: 'number', description: 'The user ID'}},
          required: ['id'],
        },
      },
      {
        name: 'Get Date and Time',
        description: 'Returns the current date and time in ISO 8601 format',
        parameters: {type: 'object', properties: {}},
      },
      {
        name: 'Ask human to send email',
        description: 'Requests a human operator to compose and send an email',
        parameters: {
          type: 'object',
          properties: {
            recipient_name: {type: 'string'},
            recipient_email: {type: 'string'},
            email_subject: {type: 'string'},
            email_body: {type: 'string'},
          },
          required: [
            'recipient_name',
            'recipient_email',
            'email_subject',
            'email_body',
          ],
        },
      },
      {
        name: 'Fetch URL',
        description:
          'Fetches content from a given URL and returns the response body',
        parameters: {
          type: 'object',
          properties: {url: {type: 'string', description: 'The URL to fetch'}},
          required: ['url'],
        },
      },
    ],
    conversation: [
      {
        role: 'system',
        content: [
          `You are a helpful assistant that performs tasks on behalf of the user.\n\n## Available Tools\n\nYou have access to the following tools:\n- **ListUsers** — query the user directory\n- **LoadUserByID** — fetch a single user profile by \`id\`\n- **GetDateAndTime** — get the current UTC timestamp\n- **AskHumanToSendEmail** — delegate email sending to a human operator\n\n## Guidelines\n\n1. Always **verify** information before taking action.\n2. Use \`=inputText\` for the user's request.\n3. When composing emails, include all required fields.\n4. Be concise in your reasoning.`,
        ],
      },
      {
        role: 'user',
        content: [
          'Find the email address of user Leanne Graham and send her an invitation to the company offsite. The event details are in the attached document.',
        ],
        timestamp: '2026-03-26T14:30:00.400Z',
        documents: [{name: 'Company_Offsite_2026.pdf'}],
      },
      {
        role: 'assistant',
        content: [
          "I'll start by looking up the user directory to find Leanne Graham.\n\n<thinking>\n<context>The user wants to find Leanne Graham's email and send an invitation.</context>\n<reflection>I should first search the user directory. ListUsers will give me all users so I can find her ID.</reflection>\n</thinking>",
        ],
        timestamp: '2026-03-26T14:30:01.200Z',
        toolCalls: [
          {
            id: 'toolu_01_listusers',
            name: 'ListUsers',
            arguments: {},
          },
        ],
      },
      {
        role: 'tool_call_result',
        content: [],
        timestamp: '2026-03-26T14:30:02.900Z',
        toolResults: [
          {
            id: 'toolu_01_listusers',
            name: 'ListUsers',
            content: JSON.stringify([
              {id: 1, name: 'Leanne Graham', email: 'Sincere@april.biz'},
              {id: 2, name: 'Ervin Howell', email: 'Shanna@melissa.tv'},
            ]),
          },
        ],
      },
      {
        role: 'assistant',
        content: [
          "Found Leanne Graham (id: 1). I'll load her full profile to confirm the email and get the current date for the invitation.\n\n<thinking>\n<context>Found Leanne Graham with id 1. Need confirmed email and current date.</context>\n<reflection>LoadUserByID and GetDateAndTime are independent — I can call both in parallel.</reflection>\n</thinking>",
        ],
        timestamp: '2026-03-26T14:30:03.100Z',
        toolCalls: [
          {
            id: 'toolu_02_loaduser',
            name: 'LoadUserByID',
            arguments: {id: 1},
          },
          {
            id: 'toolu_02_getdate',
            name: 'GetDateAndTime',
            arguments: {},
          },
        ],
      },
      {
        role: 'tool_call_result',
        content: [],
        timestamp: '2026-03-26T14:30:04.150Z',
        toolResults: [
          {
            id: 'toolu_02_loaduser',
            name: 'LoadUserByID',
            content: JSON.stringify({
              id: 1,
              name: 'Leanne Graham',
              email: 'Sincere@april.biz',
              phone: '1-770-736-8031 x56442',
              company: {name: 'Romaguera-Crona'},
            }),
          },
          {
            id: 'toolu_02_getdate',
            name: 'GetDateAndTime',
            content: JSON.stringify({
              timestamp: '2026-03-26T14:30:04.245+00:00',
            }),
          },
        ],
      },
      {
        // This assistant turn demonstrates a single message block with two text
        // segments — each segment is rendered in the same bubble but can be
        // expanded independently.
        role: 'assistant',
        content: [
          "All the information I need is now available, so I can put the invitation together.\n\n**Summary of what was gathered**\n- Recipient: **Leanne Graham** (user id 1)\n- Email address (verified against the full user profile): **Sincere@april.biz**\n- Phone on file: 1-770-736-8031 x56442\n- Employer: Romaguera-Crona\n- Offsite date extracted from `Company_Offsite_2026.pdf`: **March 28, 2026** (San Francisco office)\n- Current timestamp: 2026-03-26T14:30:04Z\n\n<thinking>\n<context>Email, profile and current date all confirmed.</context>\n<reflection>The next step is to draft the invitation email and delegate the actual sending to a human operator via AskHumanToSendEmail, because outbound email to customers must be human-approved per our guardrails.</reflection>\n</thinking>\n\nBelow is the draft I'll hand off to the human operator for review.",
          "Here is the proposed invitation draft I will send to `AskHumanToSendEmail`:\n\n> **Subject:** You're invited — Camunda Company Offsite, March 28, 2026\n>\n> Dear Leanne,\n>\n> We'd love to have you join us at our upcoming company offsite on **Saturday, March 28, 2026** in **San Francisco**. The agenda is a mix of strategy sessions, hands-on workshops and a team dinner, and we're confident the day will be a highlight of your quarter.\n>\n> **When:** March 28, 2026 · 09:00 – 22:00 PST\n> **Where:** Pier 27 Event Center, San Francisco\n> **RSVP by:** March 24, 2026\n>\n> Full agenda, dress code and travel details are in the attached brief (`Company_Offsite_2026.pdf`). If you have any dietary requirements or need help with logistics, just reply to this email.\n>\n> Looking forward to seeing you there!\n>\n> Warm regards,\n> — The Operations team\n\nThe operator can tweak phrasing before sending; once they confirm, the tool will mark the invitation as dispatched and the agent will report back to the user.",
        ],
        timestamp: '2026-03-26T14:30:04.500Z',
        toolCalls: [
          {
            id: 'toolu_03_sendemail',
            name: 'AskHumanToSendEmail',
            arguments: {
              recipient_name: 'Leanne Graham',
              recipient_email: 'Sincere@april.biz',
              email_subject:
                "You're invited — Camunda Company Offsite, March 28, 2026",
              email_body:
                "Dear Leanne,\n\nWe'd love to have you join us at our upcoming company offsite on Saturday, March 28, 2026 in San Francisco.\n\nWhen: March 28, 2026 · 09:00 – 22:00 PST\nWhere: Pier 27 Event Center, San Francisco\nRSVP by: March 24, 2026\n\nFull agenda, dress code and travel details are in the attached brief (Company_Offsite_2026.pdf).\n\nLooking forward to seeing you there!\n\n— The Operations team",
            },
          },
        ],
      },
    ],
  },
};

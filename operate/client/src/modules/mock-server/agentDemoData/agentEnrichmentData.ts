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
  tokenUsage: {input: number; output: number};
}

export interface AgentUsage {
  modelCalls: {current: number; limit: number};
  tokensUsed: {
    current: number;
    limit: number;
    inputTokens: number;
    outputTokens: number;
  };
  toolsCalled: {current: number; limit: number};
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
      "Found user Leanne Graham via the users API, retrieved her email (Sincere@april.biz), obtained the current date, and requested a human to send an offsite invitation email.",
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
            output:
              '[{id:1, name:"Leanne Graham", email:"Sincere@april.biz"}, {id:2, name:"Ervin Howell"}, ...]',
            status: 'COMPLETED',
            duration: '1.6s',
          },
        ],
        tokenUsage: {input: 482, output: 87},
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
            output: '2026-03-26T14:30:04.245+00:00',
            status: 'COMPLETED',
            duration: '50ms',
          },
        ],
        tokenUsage: {input: 621, output: 64},
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
        tokenUsage: {input: 856, output: 91},
      },
    ],
    usage: {
      modelCalls: {current: 3, limit: 10},
      tokensUsed: {
        current: 2253,
        inputTokens: 1959,
        outputTokens: 294,
        limit: 10000,
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
        description:
          'Returns the current date and time in ISO 8601 format',
        parameters: {type: 'object', properties: {}},
      },
      {
        name: 'Ask human to send email',
        description:
          'Requests a human operator to compose and send an email',
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
  },
};

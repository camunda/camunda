/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {delay, http, HttpResponse} from 'msw';
import type {ChatRequest, ChatResponse, McpToolsResponse} from 'modules/components/Chatbot/types';

/**
 * Mock handlers for the Chatbot API.
 * Use these during development to test the chatbot without a real backend.
 */

const MOCK_RESPONSES: Record<string, string> = {
  default: "I'm a mock assistant. In production, I would connect to an LLM and the MCP gateway to help you with your processes.",
  process: "Based on my analysis, your process instances are running normally. I found 5 active instances and 2 with incidents that need attention.",
  incident: "The incident appears to be caused by a missing variable 'orderId' in the service task 'Process Payment'. You can resolve this by setting the variable or retrying the job.",
  variable: "The variable 'customerId' has the value '12345' and is of type STRING. It was last modified 2 hours ago.",
  help: "I can help you with:\n- Querying process instances\n- Investigating incidents\n- Fetching variable values\n- Understanding your BPMN diagrams\n\nJust ask me anything!",
};

const MOCK_TOOLS: McpToolsResponse = {
  tools: [
    {
      name: 'listProcessInstances',
      description: 'List process instances with optional filters',
      inputSchema: {
        type: 'object',
        properties: {
          state: {
            type: 'string',
            description: 'Filter by state',
            enum: ['ACTIVE', 'COMPLETED', 'CANCELED'],
          },
          limit: {
            type: 'string',
            description: 'Maximum number of results',
          },
        },
      },
    },
    {
      name: 'getIncident',
      description: 'Get details about a specific incident',
      inputSchema: {
        type: 'object',
        properties: {
          incidentKey: {
            type: 'string',
            description: 'The incident key',
          },
        },
        required: ['incidentKey'],
      },
    },
    {
      name: 'getVariable',
      description: 'Get a variable value from a process instance',
      inputSchema: {
        type: 'object',
        properties: {
          processInstanceKey: {
            type: 'string',
            description: 'The process instance key',
          },
          variableName: {
            type: 'string',
            description: 'The variable name',
          },
        },
        required: ['processInstanceKey', 'variableName'],
      },
    },
  ],
};

function generateMockResponse(messages: ChatRequest['messages']): ChatResponse {
  const lastMessage = messages[messages.length - 1];
  const content = lastMessage?.content.toLowerCase() || '';

  let responseContent = MOCK_RESPONSES.default;

  if (content.includes('process') || content.includes('instance')) {
    responseContent = MOCK_RESPONSES.process;
  } else if (content.includes('incident') || content.includes('error')) {
    responseContent = MOCK_RESPONSES.incident;
  } else if (content.includes('variable')) {
    responseContent = MOCK_RESPONSES.variable;
  } else if (content.includes('help') || content.includes('what can you do')) {
    responseContent = MOCK_RESPONSES.help;
  }

  return {
    content: responseContent,
  };
}

export const chatbotHandlers = [
  // Chat completion endpoint
  http.post('/api/chat', async ({request}) => {
    // Simulate network delay
    await delay(500 + Math.random() * 1000);

    const body = await request.json() as ChatRequest;
    const response = generateMockResponse(body.messages);

    return HttpResponse.json(response);
  }),

  // MCP tools listing endpoint
  http.get('/mcp/tools', async () => {
    await delay(100);
    return HttpResponse.json(MOCK_TOOLS);
  }),

  // MCP tool execution endpoint
  http.post('/mcp/tools/:toolName', async ({params}) => {
    await delay(300 + Math.random() * 500);

    const {toolName} = params;

    // Return mock results based on tool name
    const mockResults: Record<string, unknown> = {
      listProcessInstances: {
        items: [
          {key: '123', bpmnProcessId: 'order-process', state: 'ACTIVE'},
          {key: '124', bpmnProcessId: 'payment-process', state: 'ACTIVE'},
        ],
        total: 2,
      },
      getIncident: {
        key: '456',
        errorType: 'JOB_NO_RETRIES',
        errorMessage: 'Variable not found: orderId',
        processInstanceKey: '123',
      },
      getVariable: {
        name: 'customerId',
        value: '12345',
        type: 'STRING',
      },
    };

    return HttpResponse.json({
      result: mockResults[toolName as string] || {success: true},
    });
  }),
];

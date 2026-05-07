/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {WidgetConfigSchema, type WidgetConfig} from './types';

const ANTHROPIC_API_URL = 'https://api.anthropic.com/v1/messages';
const MODEL = 'claude-sonnet-4-5';

const SYSTEM_PROMPT =
  `You are an assistant that creates dashboard widgets for the Camunda Operate monitoring UI.
Available V2 API endpoints (POST, body is JSON filter object):
- /v2/process-instances/search  — filter: {state, processDefinitionId, ...}
- /v2/incidents/search          — filter: {state, processInstanceKey, ...}
- /v2/process-definitions/search — filter: {processDefinitionId, name, ...}
- /v2/jobs/search               — filter: {state, type, processInstanceKey, ...}
- /v2/element-instances/search  — filter: {processInstanceKey, elementId, state, ...}

Widget types: "metric" (shows a single number) or "table" (shows rows of data).
For metric widgets, set field to a dot-path into the response (default: "page.totalItems").
For table widgets, set columns to an array of field names from items[].
Always generate widget ids using short alphanumeric strings.
Return ONLY via the create_widgets tool.`.trim();

const CREATE_WIDGETS_TOOL = {
  name: 'create_widgets',
  description:
    'Create an array of dashboard widgets based on the user prompt. Each widget has a type, title, and a query describing the API call to make.',
  input_schema: {
    type: 'object',
    properties: {
      widgets: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: {type: 'string'},
            type: {type: 'string', enum: ['metric', 'table']},
            title: {type: 'string'},
            query: {
              type: 'object',
              properties: {
                endpoint: {type: 'string'},
                method: {type: 'string', enum: ['GET', 'POST']},
                body: {type: 'object'},
              },
              required: ['endpoint', 'method'],
            },
            field: {type: 'string'},
            columns: {type: 'array', items: {type: 'string'}},
          },
          required: ['id', 'type', 'title', 'query'],
        },
      },
    },
    required: ['widgets'],
  },
} as const;

type AnthropicToolUseBlock = {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
};

const ToolInputSchema = z.object({
  widgets: z.array(WidgetConfigSchema),
});

type AnthropicResponse = {
  content: Array<{type: string} | AnthropicToolUseBlock>;
  stop_reason: string;
};

async function generateWidgets(
  prompt: string,
  apiKey: string | undefined,
): Promise<WidgetConfig[]> {
  if (!apiKey) {
    throw new Error(
      'Anthropic API key is not configured. Set VITE_ANTHROPIC_API_KEY.',
    );
  }

  const response = await fetch(ANTHROPIC_API_URL, {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
      'anthropic-dangerous-direct-browser-access': 'true',
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: 2048,
      system: SYSTEM_PROMPT,
      tools: [CREATE_WIDGETS_TOOL],
      tool_choice: {type: 'tool', name: 'create_widgets'},
      messages: [{role: 'user', content: prompt}],
    }),
  });

  if (!response.ok) {
    const errorBody = (await response.json()) as {
      error?: {message?: string; type?: string};
    };
    const message =
      errorBody.error?.message ?? errorBody.error?.type ?? 'Unknown error';
    throw new Error(`Anthropic API error ${response.status}: ${message}`);
  }

  const data = (await response.json()) as AnthropicResponse;
  const toolUseBlock = data.content.find(
    (block): block is AnthropicToolUseBlock => block.type === 'tool_use',
  );

  if (!toolUseBlock) {
    throw new Error(
      'LLM did not return a tool_use block. Cannot parse widget configs.',
    );
  }

  const parsed = ToolInputSchema.safeParse(toolUseBlock.input);
  if (!parsed.success) {
    throw new Error(
      `LLM returned malformed widget configs: ${parsed.error.message}`,
    );
  }

  return parsed.data.widgets as WidgetConfig[];
}

export {generateWidgets};

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Direct LLM Client - Connects directly to LLM providers from the browser.
 * No backend proxy required.
 *
 * Supports:
 * - OpenAI (GPT-4, GPT-3.5)
 * - Anthropic (Claude)
 * - Perplexity AI (Sonar models)
 * - Custom OpenAI-compatible endpoints
 */

import type {McpTool} from './types';

export type LLMProvider = 'openai' | 'anthropic' | 'perplexity' | 'custom';

export type LLMConfig = {
  provider: LLMProvider;
  apiKey: string;
  model?: string;
  baseUrl?: string;
  maxTokens?: number;
  temperature?: number;
};

export type ChatMessage = {
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
};

export type ToolCallResponse = {
  id: string;
  name: string;
  arguments: Record<string, unknown>;
};

export type LLMResponse = {
  content: string;
  toolCalls?: ToolCallResponse[];
  finishReason: 'stop' | 'tool_calls' | 'length' | 'error';
  citations?: string[]; // Perplexity-specific: sources used
};

const DEFAULT_MODELS: Record<LLMProvider, string> = {
  openai: 'gpt-4o',
  anthropic: 'claude-sonnet-4-20250514',
  perplexity: 'sonar-pro', // Perplexity's best model for Pro users
  custom: 'gpt-4o',
};

const SYSTEM_PROMPT = `You are Camunda Assistant, an AI helper integrated into the Camunda Operate dashboard.
You help users understand and manage their process instances, incidents, and workflow automation.

IMPORTANT: You have access to tools that can query the live Camunda platform. When users ask about:
- Process instances, counts, or status: Use the searchProcessInstances or getProcessInstance tools
- Incidents or errors: Use the incident-related tools
- Variables: Use the variable-related tools
- Process definitions: Use the process definition tools

ALWAYS use the available tools to fetch real data when answering questions about the user's Camunda environment.
Do not make up data or say you cannot access the system - you CAN access it through the provided tools.

TOOL USAGE NOTES:
- To discover available variables: First perform a variable search WITHOUT any filters to see all available variables, then analyze the results

After receiving tool results, summarize the information clearly for the user.
If a tool returns an error, explain what went wrong and suggest alternatives.`;

/**
 * Converts MCP tools to OpenAI function format
 */
function mcpToolsToOpenAIFunctions(tools: McpTool[]): OpenAIFunction[] {
  console.log('[LLM] Converting tools to OpenAI format:', tools.map(t => t.name));
  return tools.map((tool) => ({
    type: 'function' as const,
    function: {
      name: tool.name,
      description: tool.description,
      parameters: tool.inputSchema,
    },
  }));
}

type OpenAIFunction = {
  type: 'function';
  function: {
    name: string;
    description: string;
    parameters: McpTool['inputSchema'];
  };
};

/**
 * Converts MCP tools to Anthropic tool format
 */
function mcpToolsToAnthropicTools(tools: McpTool[]): AnthropicTool[] {
  return tools.map((tool) => ({
    name: tool.name,
    description: tool.description,
    input_schema: tool.inputSchema,
  }));
}

type AnthropicTool = {
  name: string;
  description: string;
  input_schema: McpTool['inputSchema'];
};

/**
 * Direct client for OpenAI API
 */
async function callOpenAI(
  config: LLMConfig,
  messages: ChatMessage[],
  tools?: McpTool[]
): Promise<LLMResponse> {
  const baseUrl = config.baseUrl || 'https://api.openai.com/v1';
  const model = config.model || DEFAULT_MODELS.openai;

  const requestBody: Record<string, unknown> = {
    model,
    messages: [
      {role: 'system', content: SYSTEM_PROMPT},
      ...messages,
    ],
    max_tokens: config.maxTokens || 4096,
    temperature: config.temperature ?? 0.7,
  };

  if (tools && tools.length > 0) {
    requestBody.tools = mcpToolsToOpenAIFunctions(tools);
    requestBody.tool_choice = 'auto';
  }

  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${config.apiKey}`,
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(
      `OpenAI API error: ${response.status} - ${(errorData as {error?: {message?: string}}).error?.message || response.statusText}`
    );
  }

  const data = await response.json();
  const choice = data.choices?.[0];

  if (!choice) {
    throw new Error('No response from OpenAI');
  }

  const toolCalls: ToolCallResponse[] | undefined = choice.message.tool_calls?.map(
    (tc: {id: string; function: {name: string; arguments: string}}) => ({
      id: tc.id,
      name: tc.function.name,
      arguments: JSON.parse(tc.function.arguments),
    })
  );

  return {
    content: choice.message.content || '',
    toolCalls,
    finishReason: choice.finish_reason === 'tool_calls' ? 'tool_calls' : 'stop',
  };
}

/**
 * Direct client for Anthropic API
 */
async function callAnthropic(
  config: LLMConfig,
  messages: ChatMessage[],
  tools?: McpTool[]
): Promise<LLMResponse> {
  const baseUrl = config.baseUrl || 'https://api.anthropic.com/v1';
  const model = config.model || DEFAULT_MODELS.anthropic;

  // Anthropic uses a different message format - system is separate
  const anthropicMessages = messages.map((msg) => ({
    role: msg.role === 'system' ? 'user' : msg.role,
    content: msg.content,
  }));

  const requestBody: Record<string, unknown> = {
    model,
    system: SYSTEM_PROMPT,
    messages: anthropicMessages,
    max_tokens: config.maxTokens || 4096,
    temperature: config.temperature ?? 0.7,
  };

  if (tools && tools.length > 0) {
    requestBody.tools = mcpToolsToAnthropicTools(tools);
  }

  const response = await fetch(`${baseUrl}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': config.apiKey,
      'anthropic-version': '2023-06-01',
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(
      `Anthropic API error: ${response.status} - ${(errorData as {error?: {message?: string}}).error?.message || response.statusText}`
    );
  }

  const data = await response.json();

  // Extract text content
  let content = '';
  const toolCalls: ToolCallResponse[] = [];

  for (const block of data.content || []) {
    if (block.type === 'text') {
      content += block.text;
    } else if (block.type === 'tool_use') {
      toolCalls.push({
        id: block.id,
        name: block.name,
        arguments: block.input,
      });
    }
  }

  return {
    content,
    toolCalls: toolCalls.length > 0 ? toolCalls : undefined,
    finishReason: data.stop_reason === 'tool_use' ? 'tool_calls' : 'stop',
  };
}

/**
 * Direct client for Perplexity AI API
 * Perplexity uses an OpenAI-compatible API with additional features like citations.
 *
 * Available models for Pro users:
 * - sonar-pro: Best for complex queries, includes citations
 * - sonar: Fast, good for simple queries
 * - sonar-reasoning-pro: Best for reasoning tasks
 * - sonar-reasoning: Fast reasoning
 */
async function callPerplexity(
  config: LLMConfig,
  messages: ChatMessage[],
  tools?: McpTool[]
): Promise<LLMResponse> {
  const baseUrl = config.baseUrl || 'https://api.perplexity.ai';
  const model = config.model || DEFAULT_MODELS.perplexity;

  const requestBody: Record<string, unknown> = {
    model,
    messages: [
      {role: 'system', content: SYSTEM_PROMPT},
      ...messages,
    ],
    max_tokens: config.maxTokens || 4096,
    temperature: config.temperature ?? 0.7,
  };

  // Perplexity supports function calling similar to OpenAI
  if (tools && tools.length > 0) {
    requestBody.tools = mcpToolsToOpenAIFunctions(tools);
    requestBody.tool_choice = 'auto';
    console.log('[LLM] Sending request with', tools.length, 'tools to Perplexity');
  } else {
    console.log('[LLM] Sending request WITHOUT tools to Perplexity');
  }

  const response = await fetch(`${baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${config.apiKey}`,
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(
      `Perplexity API error: ${response.status} - ${(errorData as {error?: {message?: string}}).error?.message || response.statusText}`
    );
  }

  const data = await response.json();
  const choice = data.choices?.[0];

  if (!choice) {
    throw new Error('No response from Perplexity');
  }

  console.log('[LLM] Perplexity response:', {
    finish_reason: choice.finish_reason,
    has_tool_calls: !!choice.message.tool_calls,
    tool_calls_count: choice.message.tool_calls?.length || 0,
    content_preview: choice.message.content?.substring(0, 100),
  });

  const toolCalls: ToolCallResponse[] | undefined = choice.message.tool_calls?.map(
    (tc: {id: string; function: {name: string; arguments: string}}) => {
      console.log('[LLM] Tool call requested:', tc.function.name, tc.function.arguments);
      return {
        id: tc.id,
        name: tc.function.name,
        arguments: JSON.parse(tc.function.arguments),
      };
    }
  );

  // Perplexity includes citations in the response
  const citations: string[] | undefined = data.citations;

  return {
    content: choice.message.content || '',
    toolCalls,
    finishReason: choice.finish_reason === 'tool_calls' ? 'tool_calls' : 'stop',
    citations,
  };
}

/**
 * Main LLM client - routes to the appropriate provider
 */
export async function callLLM(
  config: LLMConfig,
  messages: ChatMessage[],
  tools?: McpTool[]
): Promise<LLMResponse> {
  switch (config.provider) {
    case 'openai':
    case 'custom':
      return callOpenAI(config, messages, tools);
    case 'anthropic':
      return callAnthropic(config, messages, tools);
    case 'perplexity':
      return callPerplexity(config, messages, tools);
    default:
      throw new Error(`Unsupported LLM provider: ${config.provider}`);
  }
}

/**
 * Continue a conversation after tool calls
 * This sends the tool results back to the LLM for final response
 *
 * @param toolCallHistory - Array of all tool calls and their results, in order
 */
export async function continueWithToolResults(
  config: LLMConfig,
  messages: ChatMessage[],
  toolCallHistory: Array<{
    assistantToolCalls: ToolCallResponse[] | undefined;
    toolResults: Array<{toolCallId: string; name: string; result: unknown}>;
  }>,
  tools?: McpTool[]
): Promise<LLMResponse> {
  console.log('[LLM] Continuing with tool results:', toolCallHistory.length, 'rounds of tool calls');

  if (config.provider === 'anthropic') {
    // Anthropic format for tool results - flatten all tool results
    const allToolResults = toolCallHistory.flatMap((h) => h.toolResults);
    const toolResultMessages = allToolResults.map((tr) => ({
      role: 'user' as const,
      content: [{
        type: 'tool_result',
        tool_use_id: tr.toolCallId,
        content: typeof tr.result === 'string' ? tr.result : JSON.stringify(tr.result),
      }],
    }));

    // For Anthropic, we need to handle this differently
    const baseUrl = config.baseUrl || 'https://api.anthropic.com/v1';
    const model = config.model || DEFAULT_MODELS.anthropic;

    const response = await fetch(`${baseUrl}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': config.apiKey,
        'anthropic-version': '2023-06-01',
        'anthropic-dangerous-direct-browser-access': 'true',
      },
      body: JSON.stringify({
        model,
        system: SYSTEM_PROMPT,
        messages: [...messages.map((m) => ({role: m.role, content: m.content})), ...toolResultMessages],
        max_tokens: config.maxTokens || 4096,
        tools: tools ? mcpToolsToAnthropicTools(tools) : undefined,
      }),
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(`Anthropic API error: ${response.status} - ${(errorData as {error?: {message?: string}}).error?.message || response.statusText}`);
    }

    const data = await response.json();
    let content = '';
    for (const block of data.content || []) {
      if (block.type === 'text') {
        content += block.text;
      }
    }

    return {content, finishReason: 'stop'};
  } else {
    // OpenAI/Custom format for tool results
    // OpenAI requires proper sequence: user -> assistant (with tool_calls) -> tool results -> assistant (with tool_calls) -> tool results -> ...

    // Build all the tool call/result messages in proper sequence
    const toolMessages: Array<Record<string, unknown>> = [];

    for (const historyEntry of toolCallHistory) {
      if (historyEntry.assistantToolCalls && historyEntry.assistantToolCalls.length > 0) {
        // Add assistant message with tool_calls
        toolMessages.push({
          role: 'assistant',
          content: null,
          tool_calls: historyEntry.assistantToolCalls.map((tc) => ({
            id: tc.id,
            type: 'function',
            function: {
              name: tc.name,
              arguments: JSON.stringify(tc.arguments),
            },
          })),
        });

        // Add corresponding tool result messages
        for (const tr of historyEntry.toolResults) {
          toolMessages.push({
            role: 'tool',
            tool_call_id: tr.toolCallId,
            content: typeof tr.result === 'string' ? tr.result : JSON.stringify(tr.result),
          });
        }
      }
    }

    let baseUrl: string;
    let model: string;

    if (config.provider === 'perplexity') {
      baseUrl = config.baseUrl || 'https://api.perplexity.ai';
      model = config.model || DEFAULT_MODELS.perplexity;
    } else if (config.baseUrl) {
      baseUrl = config.baseUrl;
      model = config.model || DEFAULT_MODELS.openai;
    } else {
      baseUrl = 'https://api.openai.com/v1';
      model = config.model || DEFAULT_MODELS.openai;
    }

    const requestBody = {
      model,
      messages: [
        {role: 'system', content: SYSTEM_PROMPT},
        ...messages.map((m) => ({role: m.role, content: m.content})),
        ...toolMessages,
      ],
      max_tokens: config.maxTokens || 4096,
      tools: tools ? mcpToolsToOpenAIFunctions(tools) : undefined,
      tool_choice: tools && tools.length > 0 ? 'auto' : undefined,
    };

    console.log('[LLM] Sending tool results to LLM, message count:', requestBody.messages.length);

    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${config.apiKey}`,
      },
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const providerName = config.provider === 'perplexity' ? 'Perplexity' : 'OpenAI';
      throw new Error(`${providerName} API error: ${response.status} - ${(errorData as {error?: {message?: string}}).error?.message || response.statusText}`);
    }

    const data = await response.json();
    const choice = data.choices?.[0];

    if (!choice) {
      throw new Error('No response from LLM');
    }

    // Parse tool calls if present (LLM might want to call more tools)
    const newToolCalls: ToolCallResponse[] | undefined = choice.message.tool_calls?.map(
      (tc: {id: string; function: {name: string; arguments: string}}) => {
        console.log('[LLM] Additional tool call requested:', tc.function.name);
        return {
          id: tc.id,
          name: tc.function.name,
          arguments: JSON.parse(tc.function.arguments),
        };
      }
    );

    const finishReason = choice.finish_reason === 'tool_calls' ? 'tool_calls' : 'stop';
    console.log('[LLM] Response after tool results:', {
      finish_reason: finishReason,
      has_more_tool_calls: !!newToolCalls,
      tool_calls_count: newToolCalls?.length || 0,
    });

    return {
      content: choice.message.content || '',
      toolCalls: newToolCalls,
      finishReason,
      citations: data.citations,
    };
  }
}

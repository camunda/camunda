/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * MCP (Model Context Protocol) Gateway Client
 *
 * Connects to the Camunda MCP Gateway using the MCP Streamable HTTP transport.
 * The MCP protocol uses JSON-RPC style messages.
 *
 * Reference: https://modelcontextprotocol.io/specification/basic/transports#streamable-http
 *
 * The MCP Gateway exposes Camunda-specific tools for:
 * - Process instance management
 * - Incident handling
 * - Variable operations
 * - Cluster information
 */

import type {McpTool, McpToolCallResponse} from './types';

const CSRF_TOKEN_HEADER = 'X-CSRF-TOKEN';

/**
 * Gets the CSRF token from sessionStorage (same as Operate's request module)
 */
function getCsrfToken(): string | null {
  return sessionStorage.getItem(CSRF_TOKEN_HEADER);
}

export type McpClientConfig = {
  /** Base URL of the MCP gateway (e.g., 'http://localhost:8080/mcp') */
  baseUrl: string;
  /** Optional authentication token */
  authToken?: string;
};

/**
 * MCP JSON-RPC request structure
 */
type McpRequest = {
  jsonrpc: '2.0';
  id: string | number;
  method: string;
  params?: Record<string, unknown>;
};

/**
 * MCP JSON-RPC response structure
 */
type McpResponse<T = unknown> = {
  jsonrpc: '2.0';
  id: string | number;
  result?: T;
  error?: {
    code: number;
    message: string;
    data?: unknown;
  };
};

/**
 * MCP Tool definition from tools/list response
 */
type McpToolDefinition = {
  name: string;
  description?: string;
  inputSchema: {
    type: 'object';
    properties?: Record<string, unknown>;
    required?: string[];
  };
};

/**
 * MCP tools/list response
 */
type McpToolsListResult = {
  tools: McpToolDefinition[];
};

/**
 * MCP tools/call response
 * Note: Camunda MCP gateway returns data in structuredContent, not content
 */
type McpToolCallResult = {
  content: Array<{
    type: 'text' | 'image' | 'resource';
    text?: string;
    data?: string;
    mimeType?: string;
  }>;
  isError?: boolean;
  structuredContent?: unknown; // Camunda MCP gateway uses this for structured responses
};

let requestIdCounter = 0;

function generateRequestId(): string {
  return `req-${Date.now()}-${++requestIdCounter}`;
}

/**
 * Sends an MCP JSON-RPC request to the gateway
 */
async function sendMcpRequest<T>(
  config: McpClientConfig,
  method: string,
  params?: Record<string, unknown>
): Promise<T> {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    'Accept': 'application/json, text/event-stream',
  };

  // Add CSRF token (required for POST requests to Camunda APIs)
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers[CSRF_TOKEN_HEADER] = csrfToken;
  }

  if (config.authToken) {
    headers['Authorization'] = `Bearer ${config.authToken}`;
  }

  const request: McpRequest = {
    jsonrpc: '2.0',
    id: generateRequestId(),
    method,
    params,
  };

  console.log('[MCP] Sending request to', config.baseUrl, ':', method, request, 'with CSRF:', !!csrfToken);

  let response: Response;
  try {
    response = await fetch(config.baseUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(request),
    });
  } catch (fetchError) {
    console.error('[MCP] Fetch failed:', fetchError);
    throw fetchError;
  }

  console.log('[MCP] Response status:', response.status, response.statusText);

  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    console.error('[MCP] Request failed:', response.status, errorText);
    throw new Error(`MCP request failed: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get('content-type') || '';
  console.log('[MCP] Response content-type:', contentType);

  // Handle SSE (Server-Sent Events) response for streaming
  if (contentType.includes('text/event-stream')) {
    const text = await response.text();
    // Parse SSE format - look for data: lines
    const lines = text.split('\n');
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const jsonStr = line.substring(5).trim();
        if (jsonStr) {
          const mcpResponse: McpResponse<T> = JSON.parse(jsonStr);
          if (mcpResponse.error) {
            throw new Error(`MCP error: ${mcpResponse.error.message}`);
          }
          return mcpResponse.result as T;
        }
      }
    }
    throw new Error('No valid response in SSE stream');
  }

  // Handle regular JSON response
  const mcpResponse: McpResponse<T> = await response.json();

  if (mcpResponse.error) {
    throw new Error(`MCP error: ${mcpResponse.error.message}`);
  }

  return mcpResponse.result as T;
}

/**
 * Fetches available tools from the MCP gateway using tools/list method.
 * Tools are cached after first fetch.
 */
let toolsCache: McpTool[] | null = null;

export async function fetchMcpTools(config: McpClientConfig): Promise<McpTool[]> {
  if (toolsCache) {
    return toolsCache;
  }

  try {
    const result = await sendMcpRequest<McpToolsListResult>(config, 'tools/list');

    toolsCache = (result.tools || []).map((tool) => ({
      name: tool.name,
      description: tool.description || '',
      inputSchema: {
        type: 'object' as const,
        properties: (tool.inputSchema?.properties || {}) as Record<string, {
          type: string;
          description?: string;
          enum?: string[];
        }>,
        required: tool.inputSchema?.required,
      },
    }));

    console.log(`[MCP] Loaded ${toolsCache.length} tools:`, toolsCache.map(t => t.name));
    return toolsCache;
  } catch (error) {
    console.error('[MCP] Failed to fetch tools:', error);
    throw error;
  }
}

/**
 * Clears the tools cache (useful for testing or when tools might have changed)
 */
export function clearToolsCache(): void {
  toolsCache = null;
}

/**
 * Executes a tool via the MCP gateway using tools/call method.
 *
 * @param config - MCP client configuration
 * @param toolName - Name of the tool to execute
 * @param args - Arguments to pass to the tool
 * @returns The tool's response
 */
export async function executeMcpTool(
  config: McpClientConfig,
  toolName: string,
  args: Record<string, unknown>
): Promise<McpToolCallResponse> {
  try {
    console.log(`[MCP] Calling tool: ${toolName}`, args);

    const result = await sendMcpRequest<McpToolCallResult>(config, 'tools/call', {
      name: toolName,
      arguments: args,
    });

    let parsedResult: unknown;

    // Check for structuredContent first (Camunda MCP gateway uses this)
    if (result.structuredContent !== undefined) {
      parsedResult = result.structuredContent;
      console.log(`[MCP] Tool result (from structuredContent):`, parsedResult);
    } else {
      // Fall back to extracting text content from MCP response
      let resultText = '';
      for (const content of result.content || []) {
        if (content.type === 'text' && content.text) {
          resultText += content.text;
        }
      }

      // Try to parse as JSON, otherwise return as string
      if (resultText) {
        try {
          parsedResult = JSON.parse(resultText);
        } catch {
          parsedResult = resultText;
        }
      } else {
        parsedResult = null;
      }
      console.log(`[MCP] Tool result (from content):`, parsedResult);
    }

    return {
      result: parsedResult,
      isError: result.isError || false,
    };
  } catch (error) {
    console.error(`[MCP] Tool execution failed:`, error);
    return {
      result: null,
      isError: true,
      errorMessage: error instanceof Error ? error.message : 'Unknown error executing tool',
    };
  }
}

/**
 * Executes multiple tools in parallel.
 */
export async function executeMcpToolsBatch(
  config: McpClientConfig,
  toolCalls: Array<{name: string; arguments: Record<string, unknown>}>
): Promise<Array<{name: string; response: McpToolCallResponse}>> {
  const results = await Promise.all(
    toolCalls.map(async (call) => ({
      name: call.name,
      response: await executeMcpTool(config, call.name, call.arguments),
    }))
  );
  return results;
}

/**
 * Gets information about a specific tool
 */
export async function getMcpToolInfo(
  config: McpClientConfig,
  toolName: string
): Promise<McpTool | undefined> {
  const tools = await fetchMcpTools(config);
  return tools.find((t) => t.name === toolName);
}

/**
 * Initialize MCP connection - call this early to pre-load tools
 */
export async function initializeMcp(config: McpClientConfig): Promise<void> {
  try {
    await sendMcpRequest(config, 'initialize', {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: {
        name: 'Operate Chatbot',
        version: '1.0.0',
      },
    });
    console.log('[MCP] Initialized connection');
  } catch (error) {
    console.warn('[MCP] Initialize failed (may be optional):', error);
  }
}

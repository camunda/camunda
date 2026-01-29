/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Chat API Types for integration with LLM providers and MCP gateway.
 *
 * These types define the contract between the frontend chatbot and the backend
 * chat API that proxies requests to LLM providers.
 */

export type ChatRole = 'user' | 'assistant' | 'system';

export type ChatMessage = {
  role: ChatRole;
  content: string;
};

export type ToolCallRequest = {
  name: string;
  arguments?: Record<string, unknown>;
};

export type ChatRequest = {
  /** Conversation history */
  messages: ChatMessage[];
  /** MCP gateway endpoint for tool resolution */
  mcpGatewayEndpoint?: string;
  /** Whether to stream the response */
  stream?: boolean;
  /** Optional system prompt override */
  systemPrompt?: string;
};

export type ChatResponse = {
  /** The assistant's response content */
  content: string;
  /** Tool calls that were made during the response */
  toolCalls?: ToolCallRequest[];
  /** Token usage information */
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
};

export type StreamChunk = {
  /** Incremental content */
  delta: string;
  /** Whether this is the final chunk */
  done: boolean;
  /** Tool call in progress */
  toolCall?: ToolCallRequest;
};

/**
 * Available MCP tools from the Camunda MCP Gateway.
 * These are discovered from the gateway at runtime.
 */
export type McpTool = {
  name: string;
  description: string;
  inputSchema: {
    type: 'object';
    properties: Record<string, {
      type: string;
      description?: string;
      enum?: string[];
    }>;
    required?: string[];
  };
};

export type McpToolsResponse = {
  tools: McpTool[];
};

export type McpToolCallRequest = {
  toolName: string;
  arguments: Record<string, unknown>;
};

export type McpToolCallResponse = {
  result: unknown;
  isError?: boolean;
  errorMessage?: string;
};

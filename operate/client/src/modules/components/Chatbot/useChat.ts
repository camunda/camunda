/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {callLLM, continueWithToolResults, type LLMConfig} from './llmClient';
import {executeMcpTool, fetchMcpTools, type McpClientConfig} from './mcpClient';
import type {McpTool} from './types';

export type ToolCall = {
  id?: string;
  name: string;
  arguments?: Record<string, unknown>;
  result?: unknown;
  isError?: boolean;
};

export type Message = {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  toolCalls?: ToolCall[];
  createdAt: Date;
};

type UseChatOptions = {
  /** LLM configuration for direct API access */
  llmConfig: LLMConfig;
  /** MCP gateway configuration */
  mcpConfig: McpClientConfig;
  /** Initial messages */
  initialMessages?: Message[];
  /** Callback when a message is received */
  onMessage?: (message: Message) => void;
  /** Callback when an error occurs */
  onError?: (error: Error) => void;
};

type UseChatReturn = {
  messages: Message[];
  input: string;
  setInput: (input: string) => void;
  isLoading: boolean;
  error: string | null;
  sendMessage: (content: string) => Promise<void>;
  clearMessages: () => void;
  appendMessage: (message: Message) => void;
  availableTools: McpTool[];
};

/**
 * Custom hook for chat functionality with direct LLM connection and MCP gateway.
 *
 * This hook manages chat state and communication:
 * 1. Connects directly to LLM providers (OpenAI, Anthropic) from the browser
 * 2. Fetches available tools from the MCP gateway
 * 3. Executes tool calls via the MCP gateway when requested by the LLM
 *
 * No backend proxy required!
 *
 * @example
 * ```tsx
 * const { messages, input, setInput, sendMessage, isLoading } = useChat({
 *   llmConfig: {
 *     provider: 'openai',
 *     apiKey: 'sk-...',
 *     model: 'gpt-4o',
 *   },
 *   mcpConfig: {
 *     baseUrl: 'http://localhost:8080/mcp',
 *   },
 * });
 * ```
 */
export function useChat({
  llmConfig,
  mcpConfig,
  initialMessages = [],
  onMessage,
  onError,
}: UseChatOptions): UseChatReturn {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [availableTools, setAvailableTools] = useState<McpTool[]>([]);

  const generateId = () => `msg-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

  // Fetch available tools from MCP gateway on mount
  // Use mcpConfig.baseUrl as dependency to avoid re-fetching on every render
  useEffect(() => {
    console.log('[useChat] Fetching MCP tools from:', mcpConfig.baseUrl);
    fetchMcpTools(mcpConfig)
      .then((tools) => {
        console.log('[useChat] Successfully loaded', tools.length, 'MCP tools:', tools.map(t => t.name));
        setAvailableTools(tools);
      })
      .catch((err) => {
        console.error('[useChat] Failed to fetch MCP tools:', err);
        // Continue without tools - the chat will still work
      });
  }, [mcpConfig.baseUrl]); // Use baseUrl string instead of object reference

  const appendMessage = useCallback((message: Message) => {
    setMessages((prev) => [...prev, message]);
    onMessage?.(message);
  }, [onMessage]);

  const clearMessages = useCallback(() => {
    setMessages([]);
    setError(null);
  }, []);

  /**
   * Sends a message directly to the LLM and handles tool calls.
   */
  const sendMessage = useCallback(async (content: string) => {
    if (!content.trim()) return;

    const userMessage: Message = {
      id: generateId(),
      role: 'user',
      content: content.trim(),
      createdAt: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);
    setError(null);

    try {
      // Build conversation history for LLM
      const conversationHistory = [...messages, userMessage].map((msg) => ({
        role: msg.role,
        content: msg.content,
      }));

      console.log('[useChat] Sending message with', availableTools.length, 'available tools');

      // Call LLM directly with available tools
      let response = await callLLM(llmConfig, conversationHistory, availableTools);

      // Handle tool calls if the LLM wants to use tools
      let toolCalls: ToolCall[] | undefined;
      if (response.toolCalls && response.toolCalls.length > 0) {
        // Execute all tool calls via MCP gateway
        toolCalls = await Promise.all(
          response.toolCalls.map(async (tc) => {
            const result = await executeMcpTool(mcpConfig, tc.name, tc.arguments);
            return {
              id: tc.id,
              name: tc.name,
              arguments: tc.arguments,
              result: result.result,
              isError: result.isError,
            };
          })
        );

        // If there were tool calls, send results back to LLM for final response
        if (response.finishReason === 'tool_calls') {
          const toolResults = toolCalls.map((tc) => ({
            toolCallId: tc.id || tc.name,
            name: tc.name,
            result: tc.result,
          }));

          response = await continueWithToolResults(
            llmConfig,
            conversationHistory,
            response.toolCalls, // Pass original tool calls for OpenAI message format
            toolResults,
            availableTools
          );
        }
      }

      const assistantMessage: Message = {
        id: generateId(),
        role: 'assistant',
        content: response.content,
        toolCalls,
        createdAt: new Date(),
      };

      appendMessage(assistantMessage);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'An unexpected error occurred';
      setError(errorMessage);
      onError?.(err instanceof Error ? err : new Error(errorMessage));
    } finally {
      setIsLoading(false);
    }
  }, [messages, llmConfig, mcpConfig, availableTools, appendMessage, onError]);

  return {
    messages,
    input,
    setInput,
    isLoading,
    error,
    sendMessage,
    clearMessages,
    appendMessage,
    availableTools,
  };
}

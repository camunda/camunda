/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Chatbot Configuration Setup
 *
 * This file initializes the chatbot with OpenAI as the LLM provider
 * and connects to the Camunda MCP Gateway for tool execution.
 *
 * IMPORTANT: Perplexity AI does NOT support function/tool calling.
 * Use OpenAI or Anthropic for MCP tool integration.
 *
 * To use:
 * 1. Import this file early in your app initialization
 * 2. Set the API key: chatbotStore.setApiKey('sk-...')
 *
 * Get your OpenAI API key from: https://platform.openai.com/api-keys
 */

import {chatbotStore} from 'modules/stores/chatbot';

/**
 * Initialize chatbot with OpenAI configuration
 *
 * Note: Perplexity AI does NOT support function/tool calling.
 * Use OpenAI (gpt-4o) or Anthropic (claude) for MCP tool integration.
 */
export function initializeChatbot() {
  console.log('[Chatbot] Initializing...');

  // Enable the chatbot feature
  chatbotStore.enable();
  console.log('[Chatbot] Enabled:', chatbotStore.isEnabled);

  // Configure OpenAI as the LLM provider (supports function calling)
  // Alternative: Use 'anthropic' with an Anthropic API key
  chatbotStore.setLLMConfig({
    provider: 'custom',
    apiKey: 'your_pat_here', // SET YOUR OPENAI API KEY HERE
    baseUrl: 'https://models.inference.ai.azure.com',
    model: 'gpt-4o',
  });
  console.log('[Chatbot] API Key set, isConfigured:', chatbotStore.isConfigured);

  // Configure MCP Gateway endpoint (Camunda MCP Gateway uses /mcp endpoint)
  // The MCP gateway must be enabled in application.yaml:
  //   spring.ai.mcp.server.enabled=true
  chatbotStore.setMcpConfig({
    baseUrl: '/mcp', // Camunda MCP Gateway - same origin as Operate
  });

  // Hide tool results in chat UI by default (set to true for debugging)
  chatbotStore.setShowToolResults(false);

  console.log('[Chatbot] Initialization complete. isEnabled:', chatbotStore.isEnabled, 'isConfigured:', chatbotStore.isConfigured);
}

/**
 * Quick setup for development - call this in browser console:
 *
 * import('modules/components/Chatbot/config').then(m => m.setupWithApiKey('sk-your-openai-key'))
 */
export function setupWithApiKey(apiKey: string) {
  initializeChatbot();
  chatbotStore.setApiKey(apiKey);
  console.log('✅ Chatbot configured with OpenAI');
}

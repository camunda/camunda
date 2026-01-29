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
 * This file initializes the chatbot with GitHub Models as the LLM provider
 * and connects to the Camunda MCP Gateway for tool execution.
 *
 * Recommended models from GitHub Models for tool calling:
 * - gpt-4o (best overall - fast, smart, excellent tool use)
 * - gpt-4o-mini (cost-effective, good tool use)
 * - gpt-4-turbo (reliable alternative)
 *
 * To use:
 * 1. Import this file early in your app initialization
 * 2. Optionally set a different API key: chatbotStore.setApiKey('github_pat_...')
 *
 * Get your GitHub PAT from: https://github.com/settings/tokens (with models:read scope)
 */

import {chatbotStore} from 'modules/stores/chatbot';

/**
 * Initialize chatbot with GitHub Models configuration
 *
 * Using gpt-4o which has the best tool/function calling support.
 */
export function initializeChatbot() {
  console.log('[Chatbot] Initializing...');

  // Enable the chatbot feature
  chatbotStore.enable();
  console.log('[Chatbot] Enabled:', chatbotStore.isEnabled);

  // Configure OpenAI as the LLM provider
  // Recommended models for tool calling:
  // - gpt-4o: Battle-tested, reliable, excellent tool calling (recommended)
  // - gpt-5-turbo: Latest GPT-5, fast, excellent quality
  // - gpt-5: Best quality, slower/more expensive
  // - gpt-4o-mini: Faster/cheaper alternative
  chatbotStore.setLLMConfig({
    provider: 'openai',
    apiKey: 'your_pat_here',
    //baseUrl: 'https://models.inference.ai.azure.com',
    model: 'gpt-4o', // Reliable choice - try 'gpt-5-turbo' for latest GPT-5
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
 * import('modules/components/Chatbot/config').then(m => m.setupWithApiKey('github_pat_...'))
 */
export function setupWithApiKey(apiKey: string) {
  initializeChatbot();
  chatbotStore.setApiKey(apiKey);
  console.log('✅ Chatbot configured with GitHub Models (gpt-4o)');
}

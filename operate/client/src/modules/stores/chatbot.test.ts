/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, beforeEach} from 'vitest';
import {chatbotStore} from './chatbot';

describe('chatbotStore', () => {
  beforeEach(() => {
    chatbotStore.reset();
  });

  it('should be disabled by default', () => {
    expect(chatbotStore.isEnabled).toBe(false);
  });

  it('should not be configured by default (no API key)', () => {
    expect(chatbotStore.isConfigured).toBe(false);
  });

  it('should enable the chatbot', () => {
    chatbotStore.enable();
    expect(chatbotStore.isEnabled).toBe(true);
  });

  it('should disable the chatbot', () => {
    chatbotStore.enable();
    chatbotStore.disable();
    expect(chatbotStore.isEnabled).toBe(false);
  });

  it('should set API key and mark as configured', () => {
    chatbotStore.setApiKey('test-key');
    expect(chatbotStore.isConfigured).toBe(true);
    expect(chatbotStore.llmConfig.apiKey).toBe('test-key');
  });

  it('should update LLM config', () => {
    chatbotStore.setLLMConfig({
      provider: 'anthropic',
      model: 'claude-sonnet-4-20250514',
    });

    expect(chatbotStore.llmConfig.provider).toBe('anthropic');
    expect(chatbotStore.llmConfig.model).toBe('claude-sonnet-4-20250514');
  });

  it('should update MCP config', () => {
    chatbotStore.setMcpConfig({
      baseUrl: 'http://localhost:8080/mcp',
      authToken: 'test-token',
    });

    expect(chatbotStore.mcpConfig.baseUrl).toBe('http://localhost:8080/mcp');
    expect(chatbotStore.mcpConfig.authToken).toBe('test-token');
  });

  it('should reset to default config', () => {
    chatbotStore.enable();
    chatbotStore.setApiKey('test-key');
    chatbotStore.setLLMConfig({provider: 'anthropic'});

    chatbotStore.reset();

    expect(chatbotStore.isEnabled).toBe(false);
    expect(chatbotStore.isConfigured).toBe(false);
    expect(chatbotStore.llmConfig.provider).toBe('openai');
  });

  it('should preserve other LLM config when updating partial config', () => {
    chatbotStore.setLLMConfig({
      model: 'gpt-4-turbo',
    });

    chatbotStore.setLLMConfig({
      temperature: 0.5,
    });

    expect(chatbotStore.llmConfig.model).toBe('gpt-4-turbo');
    expect(chatbotStore.llmConfig.temperature).toBe(0.5);
  });
});

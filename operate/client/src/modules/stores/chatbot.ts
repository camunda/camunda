/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import type {LLMConfig} from 'modules/components/Chatbot/llmClient';
import type {McpClientConfig} from 'modules/components/Chatbot/mcpClient';

type ChatbotConfig = {
  /** Whether the chatbot feature is enabled */
  enabled: boolean;
  /** LLM provider configuration */
  llmConfig: LLMConfig;
  /** MCP gateway configuration */
  mcpConfig: McpClientConfig;
  /** Whether to show tool call results in the chat UI (for debugging) */
  showToolResults: boolean;
};

const DEFAULT_CONFIG: ChatbotConfig = {
  enabled: false,
  llmConfig: {
    provider: 'openai',
    apiKey: '', // Must be set by user
    model: 'gpt-4o',
  },
  mcpConfig: {
    baseUrl: '/mcp',
  },
  showToolResults: false, // Hidden by default, enable for debugging
};

class ChatbotStore {
  config: ChatbotConfig = {...DEFAULT_CONFIG};

  constructor() {
    makeAutoObservable(this);
    // Try to load saved config from localStorage
    this.loadFromStorage();
  }

  private loadFromStorage() {
    try {
      const saved = localStorage.getItem('chatbot-config');
      if (saved) {
        const parsed = JSON.parse(saved);
        // Merge saved config but preserve the default empty apiKey
        // (apiKey should never be loaded from storage)
        this.config = {
          ...DEFAULT_CONFIG,
          ...parsed,
          llmConfig: {
            ...DEFAULT_CONFIG.llmConfig,
            ...parsed.llmConfig,
            apiKey: '', // Never load API key from storage
          },
        };
      }
    } catch {
      // Ignore storage errors
    }
  }

  private saveToStorage() {
    try {
      // Don't save the API key to localStorage for security
      const toSave = {
        ...this.config,
        llmConfig: {
          ...this.config.llmConfig,
          apiKey: '', // Never persist API key
        },
      };
      localStorage.setItem('chatbot-config', JSON.stringify(toSave));
    } catch {
      // Ignore storage errors
    }
  }

  setConfig(config: Partial<ChatbotConfig>) {
    this.config = {...this.config, ...config};
    this.saveToStorage();
  }

  setLLMConfig(llmConfig: Partial<LLMConfig>) {
    this.config.llmConfig = {...this.config.llmConfig, ...llmConfig};
    console.log('[ChatbotStore] setLLMConfig:', {
      provider: this.config.llmConfig.provider,
      hasApiKey: this.config.llmConfig.apiKey.length > 0,
      apiKeyLength: this.config.llmConfig.apiKey.length,
    });
    this.saveToStorage();
  }

  setMcpConfig(mcpConfig: Partial<McpClientConfig>) {
    this.config.mcpConfig = {...this.config.mcpConfig, ...mcpConfig};
    this.saveToStorage();
  }

  setApiKey(apiKey: string) {
    this.config.llmConfig.apiKey = apiKey;
    // Don't save API key to storage
  }

  enable() {
    this.config.enabled = true;
    console.log('[ChatbotStore] Enabled');
    this.saveToStorage();
  }

  disable() {
    this.config.enabled = false;
    this.saveToStorage();
  }

  reset() {
    this.config = {...DEFAULT_CONFIG};
    localStorage.removeItem('chatbot-config');
  }

  get isEnabled() {
    return this.config.enabled;
  }

  get isConfigured() {
    const configured = this.config.llmConfig.apiKey.length > 0;
    console.log('[ChatbotStore] isConfigured check:', configured, 'apiKey length:', this.config.llmConfig.apiKey.length);
    return configured;
  }

  get llmConfig() {
    return this.config.llmConfig;
  }

  get mcpConfig() {
    return this.config.mcpConfig;
  }

  get showToolResults() {
    return this.config.showToolResults;
  }

  setShowToolResults(show: boolean) {
    this.config.showToolResults = show;
    this.saveToStorage();
  }
}

const chatbotStore = new ChatbotStore();

export {chatbotStore, type ChatbotConfig};

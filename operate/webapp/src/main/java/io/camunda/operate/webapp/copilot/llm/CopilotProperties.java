/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.operate.copilot")
public class CopilotProperties {

  public enum Provider {
    ANTHROPIC,
    OPENAI,
    GOOGLE,
    BEDROCK
  }

  private Provider provider = Provider.ANTHROPIC;
  private String apiKey;
  private String model;
  private String systemPrompt =
      "You are Camunda Copilot, an assistant embedded in Camunda Operate. "
          + "Help the user inspect and troubleshoot their running processes. "
          + "Use the available tools to fetch real data from Operate before answering. "
          + "Be concise and reference process IDs, instance IDs and incident counts directly.";

  public Provider getProvider() {
    return provider;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String resolvedModel() {
    if (model != null && !model.isBlank()) {
      return model;
    }
    return switch (provider) {
      case ANTHROPIC -> "claude-sonnet-4-5";
      case OPENAI -> "gpt-4o";
      case GOOGLE -> "gemini-2.0-flash";
      case BEDROCK -> "anthropic.claude-3-5-sonnet-20241022-v2:0";
    };
  }
}

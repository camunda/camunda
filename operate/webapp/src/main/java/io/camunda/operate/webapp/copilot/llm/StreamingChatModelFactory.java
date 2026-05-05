/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot.llm;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CopilotProperties.class)
public class StreamingChatModelFactory {

  @Bean
  public StreamingChatModel copilotStreamingChatModel(CopilotProperties props) {
    if (props.getApiKey() == null || props.getApiKey().isBlank()) {
      throw new IllegalStateException(
          "camunda.operate.copilot.api-key is required when the copilot endpoint is enabled");
    }
    return switch (props.getProvider()) {
      case ANTHROPIC ->
          AnthropicStreamingChatModel.builder()
              .apiKey(props.getApiKey())
              .modelName(props.resolvedModel())
              .build();
      case OPENAI ->
          OpenAiStreamingChatModel.builder()
              .apiKey(props.getApiKey())
              .modelName(props.resolvedModel())
              .build();
      case GOOGLE ->
          GoogleAiGeminiStreamingChatModel.builder()
              .apiKey(props.getApiKey())
              .modelName(props.resolvedModel())
              .build();
    };
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot.llm;

import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CopilotProperties.class)
@ConditionalOnProperty(prefix = "camunda.operate.copilot", name = "api-key")
public class StreamingChatModelFactory {

  @Bean
  public StreamingChatModel copilotStreamingChatModel(CopilotProperties props) {
    if (props.getProvider() != CopilotProperties.Provider.BEDROCK
        && (props.getApiKey() == null || props.getApiKey().isBlank())) {
      throw new IllegalStateException(
          "camunda.operate.copilot.api-key is required for provider " + props.getProvider());
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
      case BEDROCK ->
          // AWS credentials picked up from the standard chain:
          // AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY + AWS_REGION env vars,
          // or ~/.aws/credentials, or instance profile.
          BedrockStreamingChatModel.builder().modelId(props.resolvedModel()).build();
    };
  }
}

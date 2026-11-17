/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Fallback {@link ObjectMapper} and {@link WebClient.Builder} beans for when autoconfig is absent.
 */
@Configuration
public class WebBeansConfig {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }
}

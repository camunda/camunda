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
 * Declares ObjectMapper and WebClient.Builder beans because the load-tester pom omits
 * spring-boot-starter-web and runs as a non-reactive Spring app, so neither Jackson nor WebClient
 * autoconfig fires reliably. {@link ConditionalOnMissingBean} keeps these inert if autoconfig does
 * win.
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

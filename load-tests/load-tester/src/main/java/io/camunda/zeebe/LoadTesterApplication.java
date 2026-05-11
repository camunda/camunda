/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.config.LoadTesterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableConfigurationProperties(LoadTesterProperties.class)
public class LoadTesterApplication {

  public static void main(final String[] args) {
    SpringApplication.run(LoadTesterApplication.class, args);
  }

  // Spring Boot's JacksonAutoConfiguration normally supplies an ObjectMapper. The load-tester
  // pom only pulls in the Jackson modules it needs (no spring-boot-starter-web/json), so the
  // autoconfig conditions don't always fire. Declare it ourselves to make the bean available
  // to OptimizeApiClient. @ConditionalOnMissingBean keeps it inert if autoconfig wins.
  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  // Spring Boot's WebClientAutoConfiguration only provides a WebClient.Builder inside a
  // reactive web application context. The load-tester runs as a generic Spring application
  // (not WebApplicationType.REACTIVE), so we declare the builder ourselves so OptimizeApiClient
  // can be wired.
  @Bean
  @ConditionalOnMissingBean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }
}

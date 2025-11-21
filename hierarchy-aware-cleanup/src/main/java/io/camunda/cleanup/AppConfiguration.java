/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cleanup;

import io.camunda.client.CamundaClient;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfiguration {
  private final AppProperties properties;

  public AppConfiguration(final AppProperties properties) {
    this.properties = properties;
  }

  @Bean
  public ProcessInstanceCleanerConfiguration orphanKillerConfiguration(
      final CamundaClient camundaClient, final Executor orphanKillerExecutor) {
    return new ProcessInstanceCleanerConfiguration(
        camundaClient,
        orphanKillerExecutor,
        properties.retentionPolicy().plus(properties.retentionBuffer()));
  }

  @Bean
  public Executor orphanKillerExecutor() {
    return Executors.newFixedThreadPool(10);
  }
}

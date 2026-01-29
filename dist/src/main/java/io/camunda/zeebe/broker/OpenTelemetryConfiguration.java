/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.engine.OtelBootstrap;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for OpenTelemetry initialization. This ensures OpenTelemetry is initialized before
 * any other beans that might need it (e.g., Elasticsearch client).
 */
@Configuration(proxyBeanMethods = false)
@Profile("broker")
public class OpenTelemetryConfiguration {

  /**
   * BeanFactoryPostProcessor that initializes OpenTelemetry globally before any regular beans are
   * created. This runs very early in Spring's lifecycle to prevent the Elasticsearch client from
   * registering a NOOP OpenTelemetry instance.
   */
  @Bean
  public static BeanFactoryPostProcessor openTelemetryInitializer() {
    return beanFactory -> {
      // Initialize OpenTelemetry globally before any beans are created
      OtelBootstrap.initGlobalOpenTelemetry();
    };
  }

  /**
   * Provides the global OpenTelemetry instance as a Spring bean. This bean returns the
   * already-initialized global instance that was set up by the BeanFactoryPostProcessor.
   */
  @Bean
  public OpenTelemetry openTelemetry() {
    // Return the already-initialized global instance
    return GlobalOpenTelemetry.get();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.camunda.configuration.UnifiedConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
@DependsOn("unifiedConfigurationHelper")
public class JfrRecorderConfiguration {

  @Bean
  public JfrMetricRecorder jfrMetricRecorder(
      final MeterRegistry registry, final UnifiedConfiguration unifiedConfiguration) {

    if (unifiedConfiguration.getCamunda().getMonitoring().isJfr()) {
      return new JfrMetricRecorder(registry);
    } else {
      return null;
    }
  }
}

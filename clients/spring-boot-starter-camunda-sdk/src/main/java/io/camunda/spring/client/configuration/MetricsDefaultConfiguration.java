/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.configuration;

import io.camunda.spring.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.spring.client.metrics.MetricsRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

public class MetricsDefaultConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder noopMetricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }
}

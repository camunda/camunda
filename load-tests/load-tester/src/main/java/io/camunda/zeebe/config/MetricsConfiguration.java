/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

  private final MeterRegistry registry;

  public MetricsConfiguration(final MeterRegistry registry) {
    this.registry = registry;
  }

  @PostConstruct
  void applyPrometheusRenameFilter() {
    registry.config().meterFilter(new PrometheusRenameFilter());
  }

  @Bean
  public ClientInterceptor grpcMetricsInterceptor() {
    return new MetricCollectingClientInterceptor(registry);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.micrometer.core.instrument.config.MeterFilter;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The gRPC gateway observes calls for tracing, and Spring Boot's observation registry records every
 * observation as meters as well: a timer, a long task timer and a counter per event. The gateway
 * already publishes backwards-compatible gRPC metrics ({@code grpc.server.requests.received},
 * {@code grpc.server.responses.sent}, {@code grpc.server.processing.duration} with custom
 * Prometheus SLO buckets) via {@code MetricCollectingServerInterceptor}, so these differently named
 * observation meters are denied to avoid duplicating them or breaking existing dashboards.
 */
@Configuration(proxyBeanMethods = false)
public class GrpcObservationMetricsConfiguration {

  // exact names: the metrics of MetricCollectingServerInterceptor start with "grpc.server." as well
  private static final Set<String> OBSERVATION_METERS =
      Set.of(
          "grpc.server",
          "grpc.server.active",
          "grpc.server.received",
          "grpc.server.sent",
          "grpc.server.cancelled");

  @Bean
  public static MeterFilter denyGrpcServerObservationMeters() {
    return MeterFilter.deny(id -> OBSERVATION_METERS.contains(id.getName()));
  }
}

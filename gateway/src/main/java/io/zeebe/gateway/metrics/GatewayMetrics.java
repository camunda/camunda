/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public final class GatewayMetrics {

  private static final Histogram REQUEST_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("gateway_request_latency")
          .help("Latency of round-trip from gateway to broker")
          .labelNames("partition", "requestType")
          .register();

  private static final Counter FAILED_REQUESTS =
      Counter.build()
          .namespace("zeebe")
          .name("gateway_failed_requests")
          .help("Number of failed requests")
          .labelNames("partition", "requestType", "error")
          .register();

  private static final Counter TOTAL_REQUESTS =
      Counter.build()
          .namespace("zeebe")
          .name("gateway_total_requests")
          .help("Number of requests")
          .labelNames("partition", "requestType")
          .register();

  private GatewayMetrics() {}

  public static void registerSuccessfulRequest(
      final long partition, final String requestType, final long latencyMs) {
    REQUEST_LATENCY.labels(Long.toString(partition), requestType).observe(latencyMs / 1000f);
    TOTAL_REQUESTS.labels(Long.toString(partition), requestType).inc();
  }

  public static void registerFailedRequest(
      final long partition, final String requestType, final String error) {
    FAILED_REQUESTS.labels(Long.toString(partition), requestType, error).inc();
    TOTAL_REQUESTS.labels(Long.toString(partition), requestType).inc();
  }
}

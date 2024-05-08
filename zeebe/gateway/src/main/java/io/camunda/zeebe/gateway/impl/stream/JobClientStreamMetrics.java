/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.stream;

import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

final class JobClientStreamMetrics implements ClientStreamMetrics {
  private static final String NAMESPACE = "zeebe_gateway_job_stream";

  private static final Gauge SERVERS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("servers")
          .help("The count of known job stream servers/brokers")
          .register();
  private static final Gauge CLIENTS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("clients")
          .help("The count of known job stream clients")
          .register();
  private static final Gauge AGGREGATED_STREAMS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("streams")
          .help("Total count of aggregated streams")
          .register();
  private static final Histogram AGGREGATED_CLIENTS =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("aggregated_stream_clients")
          .help("Distribution of client count per aggregated stream")
          .register();
  private static final Counter PUSHES =
      Counter.build()
          .namespace(NAMESPACE)
          .name("push")
          .help("Count of pushed payloads, tagged by result status (success, failure)")
          .labelNames("status")
          .register();
  private static final Counter PUSH_TRY_FAILED_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("push_fail_try")
          .help("Total number of failed attempts when pushing jobs to the clients, grouped by code")
          .labelNames("code")
          .register();

  private final Counter.Child pushSuccessCount;
  private final Counter.Child pushFailureCount;

  JobClientStreamMetrics() {
    pushSuccessCount = PUSHES.labels("success");
    pushFailureCount = PUSHES.labels("failure");
  }

  @Override
  public void serverCount(final int count) {
    SERVERS.set(count);
  }

  @Override
  public void clientCount(final int count) {
    CLIENTS.set(count);
  }

  @Override
  public void aggregatedStreamCount(final int count) {
    AGGREGATED_STREAMS.set(count);
  }

  @Override
  public void observeAggregatedClientCount(final int count) {
    AGGREGATED_CLIENTS.observe(count);
  }

  @Override
  public void pushSucceeded() {
    pushSuccessCount.inc();
  }

  @Override
  public void pushFailed() {
    pushFailureCount.inc();
  }

  @Override
  public void pushTryFailed(final ErrorCode code) {
    PUSH_TRY_FAILED_COUNT.labels(code.name()).inc();
  }
}

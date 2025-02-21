/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.FAILED_REQUESTS;
import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.REQUEST_LATENCY;
import static io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.TOTAL_REQUESTS;

import io.camunda.zeebe.broker.client.api.BrokerClientMetricsDoc.RequestKeyNames;
import io.camunda.zeebe.util.collection.Map3D;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Metrics related to gateway to broker clients, such as request count, number of failed requests,
 * etc. See {@link BrokerClientMetricsDoc} for documentation on the actual metrics.
 */
public final class BrokerClientRequestMetrics {

  private final MeterRegistry registry;
  private final Table<Integer, String, Timer> requestLatency;
  private final Table<Integer, String, Counter> totalRequests;
  private final Map3D<Integer, String, Enum<?>, Counter> failedRequests;

  public BrokerClientRequestMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a meter registry");

    requestLatency = Table.simple();
    totalRequests = Table.simple();
    failedRequests = Map3D.simple();
  }

  /** Increments the count of successful requests for the given parameters, using these as tags. */
  public void registerSuccessfulRequest(
      final int partitionId, final String requestType, final long latencyMs) {
    requestLatency
        .computeIfAbsent(partitionId, requestType, this::registerRequestLatencyCounter)
        .record(latencyMs, TimeUnit.MILLISECONDS);
    totalRequests
        .computeIfAbsent(partitionId, requestType, this::registerTotalRequestCounter)
        .increment();
  }

  /** Increments the count of failed requests for the given parameters, using these as tags. */
  public void registerFailedRequest(
      final int partitionId, final String requestType, final Enum<?> error) {
    failedRequests
        .computeIfAbsent(partitionId, requestType, error, this::registerFailedRequestCounter)
        .increment();
    totalRequests
        .computeIfAbsent(partitionId, requestType, this::registerTotalRequestCounter)
        .increment();
  }

  private Counter registerFailedRequestCounter(
      final int partitionId, final String requestType, final Enum<?> error) {
    return Counter.builder(FAILED_REQUESTS.getName())
        .description(FAILED_REQUESTS.getDescription())
        .tag(RequestKeyNames.REQUEST_TYPE.asString(), requestType)
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .tag(RequestKeyNames.ERROR.asString(), error.name())
        .register(registry);
  }

  private Counter registerTotalRequestCounter(final int partitionId, final String requestType) {
    return Counter.builder(TOTAL_REQUESTS.getName())
        .description(TOTAL_REQUESTS.getDescription())
        .tag(RequestKeyNames.REQUEST_TYPE.asString(), requestType)
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .register(registry);
  }

  private Timer registerRequestLatencyCounter(final int partitionId, final String requestType) {
    return Timer.builder(REQUEST_LATENCY.getName())
        .description(REQUEST_LATENCY.getDescription())
        .serviceLevelObjectives(REQUEST_LATENCY.getTimerSLOs())
        .tag(RequestKeyNames.REQUEST_TYPE.asString(), requestType)
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .register(registry);
  }
}

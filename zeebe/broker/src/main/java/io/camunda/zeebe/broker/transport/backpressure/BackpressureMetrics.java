/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static io.camunda.zeebe.broker.transport.backpressure.BackpressureMetricsDoc.DROPPED_REQUEST_COUNT;
import static io.camunda.zeebe.broker.transport.backpressure.BackpressureMetricsDoc.TOTAL_REQUEST_COUNT;

import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.agrona.collections.Int2ObjectHashMap;

public final class BackpressureMetrics {

  private final Int2ObjectHashMap<Counter> droppedRequestCount = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<Counter> totalRequestCount = new Int2ObjectHashMap<>();

  private final MeterRegistry registry;

  public BackpressureMetrics(final MeterRegistry registry) {
    this.registry = registry;
  }

  public void dropped(final int partitionId) {
    droppedRequestCount
        .computeIfAbsent(
            partitionId, p -> registerCounter(DROPPED_REQUEST_COUNT, String.valueOf(partitionId)))
        .increment();
  }

  public void receivedRequest(final int partitionId) {
    totalRequestCount
        .computeIfAbsent(
            partitionId, p -> registerCounter(TOTAL_REQUEST_COUNT, String.valueOf(partitionId)))
        .increment();
  }

  private Counter registerCounter(
      final BackpressureMetricsDoc meterDoc, final String partitionTag) {
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), partitionTag)
        .register(registry);
  }
}

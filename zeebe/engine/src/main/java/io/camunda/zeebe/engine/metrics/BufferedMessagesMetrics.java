/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferedMessagesMetrics {
  private static final io.micrometer.core.instrument.MeterRegistry METER_REGISTRY =
      Metrics.globalRegistry;

  private final AtomicInteger bufferedMessageCount = new AtomicInteger(0);

  public BufferedMessagesMetrics(final int partitionId) {
    final String partitionIdLabel = String.valueOf(partitionId);

    io.micrometer.core.instrument.Gauge.builder(
            "zeebe_buffered_messages_count", bufferedMessageCount, AtomicInteger::get)
        .description("Current number of buffered messages.")
        .tags("partition", partitionIdLabel)
        .register(METER_REGISTRY);
  }

  public void setBufferedMessagesCounter(final long counter) {
    bufferedMessageCount.set((int) counter);
  }
}

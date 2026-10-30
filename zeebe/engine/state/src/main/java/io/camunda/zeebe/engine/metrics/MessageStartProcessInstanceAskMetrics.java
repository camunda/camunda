/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.CROSS_PARTITION_ASKS_PENDING;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Exports the {@code zeebe.message.start.cross.partition.asks.pending} gauge: the number of pending
 * cross-partition message-start asks currently held on the correlation-key partition.
 *
 * <p>This gauge is a live mirror of the ask column family owned by {@link
 * io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceAskState}. That state
 * increments and decrements it in lockstep with the column-family inserts and removals, and
 * authoritatively re-seeds it from the persisted count on recovery.
 */
public final class MessageStartProcessInstanceAskMetrics {

  private final StatefulGauge pendingAsks;

  public MessageStartProcessInstanceAskMetrics(final MeterRegistry meterRegistry) {
    pendingAsks =
        StatefulGauge.builder(CROSS_PARTITION_ASKS_PENDING.getName())
            .description(CROSS_PARTITION_ASKS_PENDING.getDescription())
            .register(meterRegistry);
  }

  public void incrementPendingAsks() {
    pendingAsks.increment();
  }

  public void decrementPendingAsks() {
    pendingAsks.decrement();
  }

  /**
   * Sets the gauge to an absolute value. Only call this from the stream-processing actor (for
   * example, on recovery); calling it from elsewhere risks incorrect values due to race conditions.
   */
  public void setPendingAsks(final long count) {
    pendingAsks.set(count);
  }
}

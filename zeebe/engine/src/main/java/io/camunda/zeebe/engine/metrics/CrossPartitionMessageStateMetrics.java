/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.CROSS_PARTITION_BUFFERED_MESSAGES;
import static io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.CROSS_PARTITION_LOCKS;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Exports the two cross-partition message-start occupancy gauges backed by the message state on the
 * correlation-key partition:
 *
 * <ul>
 *   <li>{@code zeebe.message.start.cross.partition.locks}: held correlation-key start locks.
 *   <li>{@code zeebe.message.start.cross.partition.buffered.messages}: buffered business-id-indexed
 *       messages awaiting a same-partition start.
 * </ul>
 *
 * <p>Both gauges are live mirrors of column families owned by {@link
 * io.camunda.zeebe.engine.state.message.DbMessageState}. That state increments and decrements them
 * in lockstep with the column-family inserts and removals, and authoritatively re-seeds them from
 * the persisted counts on recovery.
 */
public final class CrossPartitionMessageStateMetrics {

  private final StatefulGauge startLocks;
  private final StatefulGauge bufferedMessages;

  public CrossPartitionMessageStateMetrics(final MeterRegistry meterRegistry) {
    startLocks =
        StatefulGauge.builder(CROSS_PARTITION_LOCKS.getName())
            .description(CROSS_PARTITION_LOCKS.getDescription())
            .register(meterRegistry);
    bufferedMessages =
        StatefulGauge.builder(CROSS_PARTITION_BUFFERED_MESSAGES.getName())
            .description(CROSS_PARTITION_BUFFERED_MESSAGES.getDescription())
            .register(meterRegistry);
  }

  public void incrementStartLocks() {
    startLocks.increment();
  }

  public void decrementStartLocks() {
    startLocks.decrement();
  }

  public void incrementBufferedMessages() {
    bufferedMessages.increment();
  }

  public void decrementBufferedMessages() {
    bufferedMessages.decrement();
  }

  /**
   * Sets the start-locks gauge to an absolute value. Only call this from the stream-processing
   * actor (for example, on recovery); calling it from elsewhere risks incorrect values due to race
   * conditions.
   */
  public void setStartLocks(final long count) {
    startLocks.set(count);
  }

  /**
   * Sets the buffered-messages gauge to an absolute value. Only call this from the
   * stream-processing actor (for example, on recovery); calling it from elsewhere risks incorrect
   * values due to race conditions.
   */
  public void setBufferedMessages(final long count) {
    bufferedMessages.set(count);
  }
}

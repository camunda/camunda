/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.CROSS_PARTITION_DEDUP_ENTRIES;

import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Exports the {@code zeebe.message.start.cross.partition.dedup.entries} gauge: the number of
 * outstanding cross-partition message-start dedup entries currently held on the business-id
 * partition.
 *
 * <p>This gauge is a live mirror of the dedup column family owned by {@link
 * io.camunda.zeebe.engine.state.message.DbMessageStartProcessInstanceDedupState}. That state
 * increments and decrements it in lockstep with the column-family inserts and removals, and
 * authoritatively re-seeds it from the persisted count on recovery.
 */
public final class MessageStartProcessInstanceDedupMetrics {

  private final StatefulGauge dedupEntries;

  public MessageStartProcessInstanceDedupMetrics(final MeterRegistry meterRegistry) {
    dedupEntries =
        StatefulGauge.builder(CROSS_PARTITION_DEDUP_ENTRIES.getName())
            .description(CROSS_PARTITION_DEDUP_ENTRIES.getDescription())
            .register(meterRegistry);
  }

  public void incrementDedupEntries() {
    dedupEntries.increment();
  }

  public void decrementDedupEntries() {
    dedupEntries.decrement();
  }

  /**
   * Sets the gauge to an absolute value. Only call this from the stream-processing actor (for
   * example, on recovery); calling it from elsewhere risks incorrect values due to race conditions.
   */
  public void setDedupEntries(final long count) {
    dedupEntries.set(count);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.time.Duration;

/**
 * Tunables for a compacted partition's cleaner.
 *
 * @param minLagRecords how many records of raw history to keep behind the committed head before the
 *     cleaner point C; guarantees tailing readers always see un-compacted recent records
 * @param maxSegmentBytes soft upper bound on a clean segment's size before the writer rolls to a
 *     new one
 * @param graceWindow how far the log clock (the running maximum record timestamp up to the cleaner
 *     point) must have advanced past a tombstone's own record timestamp before a later pass may
 *     drop it — the two-touch grace of ADR 0001 decision 7, measured entirely on log-derived time
 *     (an idle partition freezes the clock and retains boundary tombstones)
 * @param keyMapCapacity the maximum number of distinct keys the per-pass {@link KeyOffsetMap} holds
 *     before it overflows and forces a lower C and a multi-pass
 * @param passInterval how often the {@link LogCleaner} actor runs a pass
 */
public record CompactionConfig(
    long minLagRecords,
    long maxSegmentBytes,
    Duration graceWindow,
    int keyMapCapacity,
    Duration passInterval) {

  public CompactionConfig {
    if (minLagRecords < 0) {
      throw new IllegalArgumentException("minLagRecords must be >= 0, was " + minLagRecords);
    }
    if (maxSegmentBytes <= 0) {
      throw new IllegalArgumentException(
          "maxSegmentBytes must be positive, was " + maxSegmentBytes);
    }
    if (keyMapCapacity <= 0) {
      throw new IllegalArgumentException("keyMapCapacity must be positive, was " + keyMapCapacity);
    }
    if (graceWindow.isNegative()) {
      throw new IllegalArgumentException("graceWindow must not be negative, was " + graceWindow);
    }
    if (passInterval.isNegative() || passInterval.isZero()) {
      throw new IllegalArgumentException("passInterval must be positive, was " + passInterval);
    }
  }
}

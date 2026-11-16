/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.time.Duration;

/**
 * Settings of one write-behind DB. All sizes are per DB, so per partition.
 *
 * @param maxPendingBytes commits wait while the unflushed transactions hold more bytes than this
 * @param cacheMaxBytes size of the read cache for flushed values; 0 disables it
 * @param maxWriteRetries how often the writer retries a recoverable storage error before it fails
 *     the store
 * @param flushTimeout how long a snapshot waits for the writer to flush what was committed before
 * @param flushDelay how long the writer waits after the oldest unflushed commit before it writes.
 *     State that a later commit deletes in this time never reaches the storage. A snapshot, a full
 *     pending queue and a close flush at once. Zero writes at the writer's next wake-up, within a
 *     millisecond, without waking it for each commit.
 * @param flushThresholdBytes the writer flushes without waiting for the delay when the unflushed
 *     transactions hold this many bytes. Never more than {@code maxPendingBytes}, so that commits
 *     do not wait for the limit before an early flush starts.
 */
public record WriteBehindConfiguration(
    long maxPendingBytes,
    long cacheMaxBytes,
    int maxWriteRetries,
    Duration flushTimeout,
    Duration flushDelay,
    long flushThresholdBytes) {

  public WriteBehindConfiguration {
    // a negative limit is always exceeded, so that the first commit would wait for a flush that
    // never comes; a negative delay or timeout has no meaning
    if (maxPendingBytes <= 0) {
      throw new IllegalArgumentException(
          "Expected maxPendingBytes to be positive, but was " + maxPendingBytes);
    }
    if (cacheMaxBytes < 0) {
      throw new IllegalArgumentException(
          "Expected cacheMaxBytes to be zero or positive, but was " + cacheMaxBytes);
    }
    if (maxWriteRetries < 0) {
      throw new IllegalArgumentException(
          "Expected maxWriteRetries to be zero or positive, but was " + maxWriteRetries);
    }
    if (flushTimeout.isNegative() || flushTimeout.isZero()) {
      throw new IllegalArgumentException(
          "Expected flushTimeout to be positive, but was " + flushTimeout);
    }
    if (flushDelay.isNegative()) {
      throw new IllegalArgumentException(
          "Expected flushDelay to be zero or positive, but was " + flushDelay);
    }
    if (flushThresholdBytes < 0) {
      throw new IllegalArgumentException(
          "Expected flushThresholdBytes to be zero or positive, but was " + flushThresholdBytes);
    }
  }

  /** No flush delay, and an early flush at half of {@code maxPendingBytes}. */
  public WriteBehindConfiguration(
      final long maxPendingBytes,
      final long cacheMaxBytes,
      final int maxWriteRetries,
      final Duration flushTimeout) {
    this(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        Duration.ZERO,
        maxPendingBytes / 2);
  }

  public static WriteBehindConfiguration defaults() {
    return new WriteBehindConfiguration(32L << 20, 64L << 20, 10, Duration.ofSeconds(30));
  }

  public WriteBehindConfiguration withCacheMaxBytes(final long cacheMaxBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withMaxPendingBytes(final long maxPendingBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withFlushDelay(final Duration flushDelay) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withFlushThresholdBytes(final long flushThresholdBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        flushDelay,
        flushThresholdBytes);
  }
}

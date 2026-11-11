/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings of one write-behind cache. All sizes are per cache, so per partition. The byte limits
 * bound memory; {@code batchSize} only shapes the writer's groups.
 *
 * @param cacheMaxBytes the size of the read cache, EhCache's heap tier
 * @param maxPendingBytes the key and value bytes of queued transactions above which commits wait
 * @param batchSize the most distinct keys the writer applies in one storage transaction; a larger
 *     transaction is written alone
 * @param maxWriteDelay how long the oldest queued transaction waits for its group to fill
 * @param snapshotCatchUpTimeout how long a snapshot waits for the writer to catch up
 * @throws IllegalArgumentException if a size or {@code batchSize} isn't positive, {@code
 *     maxPendingBytes} or {@code maxWriteDelay} is negative, or a duration doesn't fit in
 *     nanoseconds
 */
public record WriteBehindConfiguration(
    long cacheMaxBytes,
    long maxPendingBytes,
    int batchSize,
    Duration maxWriteDelay,
    Duration snapshotCatchUpTimeout) {

  private static final long MEGABYTE = 1024 * 1024;

  /** The writer and the snapshot catch-up wait in nanoseconds. */
  private static final Duration LONGEST = Duration.ofNanos(Long.MAX_VALUE);

  public static final WriteBehindConfiguration DEFAULT =
      new WriteBehindConfiguration(
          64 * MEGABYTE, 32 * MEGABYTE, 1_000, Duration.ofMillis(100), Duration.ofSeconds(30));

  public WriteBehindConfiguration {
    if (cacheMaxBytes <= 0) {
      throw invalid("cacheMaxBytes", "positive", cacheMaxBytes);
    }
    if (maxPendingBytes < 0) {
      throw invalid("maxPendingBytes", "zero or more", maxPendingBytes);
    }
    if (batchSize <= 0) {
      throw invalid("batchSize", "positive", batchSize);
    }
    Objects.requireNonNull(maxWriteDelay, "maxWriteDelay");
    if (maxWriteDelay.isNegative() || maxWriteDelay.compareTo(LONGEST) > 0) {
      throw invalid("maxWriteDelay", "between zero and " + LONGEST, maxWriteDelay);
    }
    Objects.requireNonNull(snapshotCatchUpTimeout, "snapshotCatchUpTimeout");
    if (snapshotCatchUpTimeout.isNegative()
        || snapshotCatchUpTimeout.isZero()
        || snapshotCatchUpTimeout.compareTo(LONGEST) > 0) {
      throw invalid(
          "snapshotCatchUpTimeout", "positive and at most " + LONGEST, snapshotCatchUpTimeout);
    }
  }

  private static IllegalArgumentException invalid(
      final String name, final String expected, final Object actual) {
    return new IllegalArgumentException(
        "Expected %s to be %s, but it was %s".formatted(name, expected, actual));
  }
}

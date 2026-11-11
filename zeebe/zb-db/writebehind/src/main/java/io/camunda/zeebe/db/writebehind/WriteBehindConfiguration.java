/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.Duration;
import java.util.Set;

/**
 * Settings of one write-behind DB. All sizes are per DB, so per partition.
 *
 * @param maxPendingBytes commits wait while the unflushed transactions hold more bytes than this
 * @param cacheMaxBytes size of the read cache for flushed values; 0 disables it
 * @param maxWriteRetries how often the writer retries a recoverable storage error before it fails
 *     the store
 * @param flushTimeout how long a snapshot waits for the writer to flush what was committed before
 * @param keyIndexColumnFamilies IDs of the column families whose committed keys are kept in memory;
 *     empty disables the key index
 * @param keyIndexMaxKeys a column family with more keys than this loses its key index until the
 *     next restart
 */
public record WriteBehindConfiguration(
    long maxPendingBytes,
    long cacheMaxBytes,
    int maxWriteRetries,
    Duration flushTimeout,
    Set<Long> keyIndexColumnFamilies,
    int keyIndexMaxKeys) {

  public static final int DEFAULT_KEY_INDEX_MAX_KEYS = 100_000;

  public static final Set<Long> DEFAULT_KEY_INDEX_COLUMN_FAMILIES =
      Set.of(
          (long) ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD.getValue(),
          (long) ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS.getValue(),
          (long) ZbColumnFamilies.TIMER_DUE_DATES.getValue(),
          (long) ZbColumnFamilies.JOB_DEADLINES.getValue(),
          (long) ZbColumnFamilies.JOB_BACKOFF.getValue(),
          (long) ZbColumnFamilies.MESSAGE_DEADLINES.getValue());

  public WriteBehindConfiguration {
    keyIndexColumnFamilies = Set.copyOf(keyIndexColumnFamilies);
  }

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
        DEFAULT_KEY_INDEX_COLUMN_FAMILIES,
        DEFAULT_KEY_INDEX_MAX_KEYS);
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
        keyIndexColumnFamilies,
        keyIndexMaxKeys);
  }

  public WriteBehindConfiguration withMaxPendingBytes(final long maxPendingBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys);
  }

  public WriteBehindConfiguration withKeyIndexColumnFamilies(final Set<Long> columnFamilies) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        columnFamilies,
        keyIndexMaxKeys);
  }

  public WriteBehindConfiguration withKeyIndexMaxKeys(final int keyIndexMaxKeys) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys);
  }
}

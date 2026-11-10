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
 */
public record WriteBehindConfiguration(
    long maxPendingBytes, long cacheMaxBytes, int maxWriteRetries, Duration flushTimeout) {

  public static WriteBehindConfiguration defaults() {
    return new WriteBehindConfiguration(32L << 20, 64L << 20, 10, Duration.ofSeconds(30));
  }

  public WriteBehindConfiguration withCacheMaxBytes(final long cacheMaxBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes, cacheMaxBytes, maxWriteRetries, flushTimeout);
  }

  public WriteBehindConfiguration withMaxPendingBytes(final long maxPendingBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes, cacheMaxBytes, maxWriteRetries, flushTimeout);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import java.time.Duration;

/** Builds a {@link WriteBehindConfiguration} from the defaults, changing only what a test sets. */
final class TestConfiguration {

  private final WriteBehindConfiguration defaults = WriteBehindConfiguration.DEFAULT;
  private long cacheMaxBytes = defaults.cacheMaxBytes();
  private long maxPendingBytes = defaults.maxPendingBytes();
  private int batchSize = defaults.batchSize();
  private Duration maxWriteDelay = defaults.maxWriteDelay();
  private Duration snapshotCatchUpTimeout = defaults.snapshotCatchUpTimeout();

  TestConfiguration cacheMaxBytes(final long cacheMaxBytes) {
    this.cacheMaxBytes = cacheMaxBytes;
    return this;
  }

  TestConfiguration maxPendingBytes(final long maxPendingBytes) {
    this.maxPendingBytes = maxPendingBytes;
    return this;
  }

  TestConfiguration batchSize(final int batchSize) {
    this.batchSize = batchSize;
    return this;
  }

  TestConfiguration maxWriteDelay(final Duration maxWriteDelay) {
    this.maxWriteDelay = maxWriteDelay;
    return this;
  }

  TestConfiguration snapshotCatchUpTimeout(final Duration snapshotCatchUpTimeout) {
    this.snapshotCatchUpTimeout = snapshotCatchUpTimeout;
    return this;
  }

  WriteBehindConfiguration build() {
    return new WriteBehindConfiguration(
        cacheMaxBytes, maxPendingBytes, batchSize, maxWriteDelay, snapshotCatchUpTimeout);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * Puts a write-behind cache in front of each partition's state: committed state goes to an
 * in-memory cache first, and to RocksDB in the background, in groups of whole transactions. All
 * sizes are per partition. Off by default.
 */
public class WriteBehindCache {

  /** Whether each partition's state goes through the write-behind cache. */
  private boolean enabled = false;

  /** The size of the in-memory read cache. */
  private DataSize cacheSize = DataSize.ofMegabytes(64);

  /**
   * The key and value bytes of committed state that RocksDB doesn't have yet, above which commits
   * wait for the writer.
   */
  private DataSize maxPendingSize = DataSize.ofMegabytes(32);

  /**
   * The most distinct keys the writer writes to RocksDB in one transaction. A larger transaction is
   * written alone.
   */
  private int batchSize = 1_000;

  /**
   * How long committed state waits at most before the writer writes it to RocksDB, unless a batch
   * fills first. Longer delays combine more writes of the same keys; shorter ones keep RocksDB
   * closer to the cache.
   */
  private Duration maxWriteDelay = Duration.ofMillis(100);

  /**
   * How long a snapshot waits for the writer to write everything committed before it. If it takes
   * longer, that snapshot fails and the next one is tried at the next snapshot interval.
   */
  private Duration snapshotCatchUpTimeout = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public DataSize getCacheSize() {
    return cacheSize;
  }

  public void setCacheSize(final DataSize cacheSize) {
    this.cacheSize = cacheSize;
  }

  public DataSize getMaxPendingSize() {
    return maxPendingSize;
  }

  public void setMaxPendingSize(final DataSize maxPendingSize) {
    this.maxPendingSize = maxPendingSize;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getMaxWriteDelay() {
    return maxWriteDelay;
  }

  public void setMaxWriteDelay(final Duration maxWriteDelay) {
    this.maxWriteDelay = maxWriteDelay;
  }

  public Duration getSnapshotCatchUpTimeout() {
    return snapshotCatchUpTimeout;
  }

  public void setSnapshotCatchUpTimeout(final Duration snapshotCatchUpTimeout) {
    this.snapshotCatchUpTimeout = snapshotCatchUpTimeout;
  }
}

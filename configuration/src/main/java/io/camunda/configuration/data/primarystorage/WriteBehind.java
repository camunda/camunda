/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the experimental write-behind cache in front of each partition's RocksDB state. The
 * cache holds committed state in memory and writes it to RocksDB in batches, so that state deleted
 * shortly after it was written never reaches RocksDB. All sizes are per partition and come on top
 * of the RocksDB memory limit.
 */
public class WriteBehind {

  /**
   * Runs the partition state on the write-behind cache. When disabled, the state runs directly on
   * RocksDB.
   */
  private boolean enabled = false;

  /**
   * How long the cache waits after the oldest unwritten commit before it writes to RocksDB. State
   * deleted within this time never reaches RocksDB. Snapshots and a full pending queue write at
   * once.
   */
  private Duration flushDelay = Duration.ofMillis(100);

  /**
   * The cache writes to RocksDB without waiting for the flush delay once the unwritten commits hold
   * this many bytes. Capped at max-pending.
   */
  private DataSize flushThreshold = DataSize.ofMegabytes(16);

  /** Commits wait while the unwritten commits hold more than this many bytes. */
  private DataSize maxPending = DataSize.ofMegabytes(32);

  /** Size of the read cache for values already written to RocksDB; 0 disables it. */
  private DataSize cacheSize = DataSize.ofMegabytes(64);

  /**
   * How many keys the key index of one column family holds at most. The key index keeps the keys of
   * a column family in memory, so that lookups and scans of keys that do not exist skip RocksDB.
   */
  private int keyIndexMaxKeys = 10_000;

  /**
   * The key index policy per column family, by column family name (e.g. VARIABLES). Entries set
   * here override the defaults, which give the column families that the engine reads from their
   * lowest or highest keys a KEEP_LOWEST or KEEP_HIGHEST key index; the other default entries stay.
   * DISABLE drops a key index once its column family holds more than key-index-max-keys keys.
   */
  private Map<String, KeyIndexPolicy> keyIndex = new HashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getFlushDelay() {
    return flushDelay;
  }

  public void setFlushDelay(final Duration flushDelay) {
    this.flushDelay = flushDelay;
  }

  public DataSize getFlushThreshold() {
    return flushThreshold;
  }

  public void setFlushThreshold(final DataSize flushThreshold) {
    this.flushThreshold = flushThreshold;
  }

  public DataSize getMaxPending() {
    return maxPending;
  }

  public void setMaxPending(final DataSize maxPending) {
    this.maxPending = maxPending;
  }

  public DataSize getCacheSize() {
    return cacheSize;
  }

  public void setCacheSize(final DataSize cacheSize) {
    this.cacheSize = cacheSize;
  }

  public int getKeyIndexMaxKeys() {
    return keyIndexMaxKeys;
  }

  public void setKeyIndexMaxKeys(final int keyIndexMaxKeys) {
    this.keyIndexMaxKeys = keyIndexMaxKeys;
  }

  public Map<String, KeyIndexPolicy> getKeyIndex() {
    return keyIndex;
  }

  public void setKeyIndex(final Map<String, KeyIndexPolicy> keyIndex) {
    this.keyIndex = keyIndex;
  }

  @Override
  public String toString() {
    return "WriteBehind{"
        + "enabled="
        + enabled
        + ", flushDelay="
        + flushDelay
        + ", flushThreshold="
        + flushThreshold
        + ", maxPending="
        + maxPending
        + ", cacheSize="
        + cacheSize
        + ", keyIndexMaxKeys="
        + keyIndexMaxKeys
        + ", keyIndex="
        + keyIndex
        + '}';
  }

  public enum KeyIndexPolicy {
    DISABLE,
    KEEP_LOWEST,
    KEEP_HIGHEST
  }
}

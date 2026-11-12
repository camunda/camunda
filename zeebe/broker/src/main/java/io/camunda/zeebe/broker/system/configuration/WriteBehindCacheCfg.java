/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * The write-behind cache in front of each partition's state; see {@link WriteBehindConfiguration}.
 */
public class WriteBehindCacheCfg {

  private static final WriteBehindConfiguration DEFAULTS = WriteBehindConfiguration.DEFAULT;

  private boolean enabled = false;
  private DataSize cacheSize = DataSize.ofBytes(DEFAULTS.cacheMaxBytes());
  private DataSize maxPendingSize = DataSize.ofBytes(DEFAULTS.maxPendingBytes());
  private int batchSize = DEFAULTS.batchSize();
  private Duration maxWriteDelay = DEFAULTS.maxWriteDelay();
  private Duration snapshotCatchUpTimeout = DEFAULTS.snapshotCatchUpTimeout();

  public WriteBehindConfiguration createConfiguration() {
    return new WriteBehindConfiguration(
        cacheSize.toBytes(),
        maxPendingSize.toBytes(),
        batchSize,
        maxWriteDelay,
        snapshotCatchUpTimeout);
  }

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

  @Override
  public String toString() {
    return "WriteBehindCacheCfg{"
        + "enabled="
        + enabled
        + ", cacheSize="
        + cacheSize
        + ", maxPendingSize="
        + maxPendingSize
        + ", batchSize="
        + batchSize
        + ", maxWriteDelay="
        + maxWriteDelay
        + ", snapshotCatchUpTimeout="
        + snapshotCatchUpTimeout
        + '}';
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the write-behind state DB. When enabled, each partition commits its state to memory
 * and writes it to RocksDB in the background. The sizes are per partition.
 */
public final class WriteBehindCfg {
  private static final WriteBehindConfiguration DEFAULTS = WriteBehindConfiguration.defaults();

  private boolean enabled = false;
  private DataSize maxPendingSize = DataSize.ofBytes(DEFAULTS.maxPendingBytes());
  private DataSize cacheSize = DataSize.ofBytes(DEFAULTS.cacheMaxBytes());

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public DataSize getMaxPendingSize() {
    return maxPendingSize;
  }

  public void setMaxPendingSize(final DataSize maxPendingSize) {
    this.maxPendingSize = maxPendingSize;
  }

  public DataSize getCacheSize() {
    return cacheSize;
  }

  public void setCacheSize(final DataSize cacheSize) {
    this.cacheSize = cacheSize;
  }

  public WriteBehindConfiguration createConfiguration() {
    return DEFAULTS
        .withMaxPendingBytes(maxPendingSize.toBytes())
        .withCacheMaxBytes(cacheSize.toBytes());
  }

  @Override
  public String toString() {
    return "WriteBehindCfg{"
        + "enabled="
        + enabled
        + ", maxPendingSize="
        + maxPendingSize
        + ", cacheSize="
        + cacheSize
        + '}';
  }
}

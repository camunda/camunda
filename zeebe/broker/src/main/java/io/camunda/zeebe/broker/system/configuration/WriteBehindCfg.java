/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the write-behind state DB. When enabled, each partition commits its state to memory
 * and writes it to RocksDB in the background. The sizes are per partition.
 *
 * <p>The key index keeps every committed key of the listed column families in memory, so that scans
 * of them open no RocksDB iterator. A column family with more than {@code keyIndexMaxKeys} keys
 * loses its index until the next restart.
 */
public final class WriteBehindCfg {
  private static final WriteBehindConfiguration DEFAULTS = WriteBehindConfiguration.defaults();

  private boolean enabled = false;
  private DataSize maxPendingSize = DataSize.ofBytes(DEFAULTS.maxPendingBytes());
  private DataSize cacheSize = DataSize.ofBytes(DEFAULTS.cacheMaxBytes());
  private Set<ZbColumnFamilies> keyIndexColumnFamilies =
      Arrays.stream(ZbColumnFamilies.values())
          .filter(
              columnFamily ->
                  DEFAULTS.keyIndexColumnFamilies().contains((long) columnFamily.getValue()))
          .collect(Collectors.toUnmodifiableSet());
  private int keyIndexMaxKeys = DEFAULTS.keyIndexMaxKeys();

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

  public Set<ZbColumnFamilies> getKeyIndexColumnFamilies() {
    return keyIndexColumnFamilies;
  }

  public void setKeyIndexColumnFamilies(final Set<ZbColumnFamilies> keyIndexColumnFamilies) {
    this.keyIndexColumnFamilies = keyIndexColumnFamilies;
  }

  public int getKeyIndexMaxKeys() {
    return keyIndexMaxKeys;
  }

  public void setKeyIndexMaxKeys(final int keyIndexMaxKeys) {
    this.keyIndexMaxKeys = keyIndexMaxKeys;
  }

  public WriteBehindConfiguration createConfiguration() {
    return DEFAULTS
        .withMaxPendingBytes(maxPendingSize.toBytes())
        .withCacheMaxBytes(cacheSize.toBytes())
        .withKeyIndexColumnFamilies(
            keyIndexColumnFamilies.stream()
                .map(columnFamily -> (long) columnFamily.getValue())
                .collect(Collectors.toUnmodifiableSet()))
        .withKeyIndexMaxKeys(keyIndexMaxKeys);
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
        + ", keyIndexColumnFamilies="
        + keyIndexColumnFamilies
        + ", keyIndexMaxKeys="
        + keyIndexMaxKeys
        + '}';
  }
}

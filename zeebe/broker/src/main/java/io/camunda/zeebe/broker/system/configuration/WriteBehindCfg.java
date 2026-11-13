/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.db.writebehind.KeyIndexPolicy;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.engine.state.ColumnFamilyAccessPatterns;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the write-behind cache in front of each partition's RocksDB state. All sizes are per
 * partition. Disabled by default; when disabled, the partition state runs directly on RocksDB.
 */
@NullMarked
public final class WriteBehindCfg {

  private boolean enabled = false;
  private Duration flushDelay = WriteBehindConfiguration.DEFAULT_FLUSH_DELAY;
  private DataSize flushThreshold =
      DataSize.ofBytes(WriteBehindConfiguration.DEFAULT_FLUSH_THRESHOLD_BYTES);
  private DataSize maxPending =
      DataSize.ofBytes(WriteBehindConfiguration.DEFAULT_MAX_PENDING_BYTES);
  private DataSize cacheSize = DataSize.ofBytes(WriteBehindConfiguration.DEFAULT_CACHE_MAX_BYTES);
  private int keyIndexMaxKeys = WriteBehindConfiguration.DEFAULT_KEY_INDEX_MAX_KEYS;
  private final Map<ZbColumnFamilies, KeyIndexPolicy> keyIndex = defaultKeyIndex();

  /** The key index policy of each column family whose keys the engine reads from one end. */
  public static Map<ZbColumnFamilies, KeyIndexPolicy> defaultKeyIndex() {
    final var policies = new EnumMap<ZbColumnFamilies, KeyIndexPolicy>(ZbColumnFamilies.class);
    ColumnFamilyAccessPatterns.READ_FROM_LOWEST_KEY.forEach(
        cf -> policies.put(cf, KeyIndexPolicy.KEEP_LOWEST));
    ColumnFamilyAccessPatterns.READ_FROM_HIGHEST_KEY.forEach(
        cf -> policies.put(cf, KeyIndexPolicy.KEEP_HIGHEST));
    return policies;
  }

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

  /** The key index policy by column family; column families without an entry have no key index. */
  public Map<ZbColumnFamilies, KeyIndexPolicy> getKeyIndex() {
    return Collections.unmodifiableMap(keyIndex);
  }

  /**
   * Sets the key index policy of one column family and keeps the others.
   *
   * @param columnFamily the name of a {@link ZbColumnFamilies} constant; case, dots and dashes are
   *     ignored, so that names bound from environment variables match
   * @throws IllegalArgumentException if no column family has this name
   */
  public void putKeyIndex(final String columnFamily, final KeyIndexPolicy policy) {
    keyIndex.put(parseColumnFamily(columnFamily), policy);
  }

  public WriteBehindConfiguration createWriteBehindConfiguration() {
    final var policiesById = new HashMap<Long, KeyIndexPolicy>();
    keyIndex.forEach((cf, policy) -> policiesById.put((long) cf.getValue(), policy));
    return WriteBehindConfiguration.defaults()
        .withMaxPendingBytes(maxPending.toBytes())
        .withCacheMaxBytes(cacheSize.toBytes())
        .withFlushDelay(flushDelay)
        .withFlushThresholdBytes(flushThreshold.toBytes())
        .withKeyIndexMaxKeys(keyIndexMaxKeys)
        .withKeyIndexPolicies(policiesById);
  }

  private static ZbColumnFamilies parseColumnFamily(final String name) {
    final var normalized = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    try {
      return ZbColumnFamilies.valueOf(normalized);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Expected a column family name for the write-behind key index, but was '%s'. Valid names: %s"
              .formatted(name, Arrays.toString(ZbColumnFamilies.values())),
          e);
    }
  }

  @Override
  public String toString() {
    return "WriteBehindCfg{"
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
}

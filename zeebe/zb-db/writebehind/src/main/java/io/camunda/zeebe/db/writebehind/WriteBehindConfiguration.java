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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
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
 * @param keyIndexMaxKeys what happens to a column family with more keys than this depends on its
 *     {@link KeyIndexPolicy}
 * @param keyIndexPolicies the policy per column family ID; a column family without one has {@link
 *     KeyIndexPolicy#DISABLE}. Fixed in code: the broker doesn't expose it.
 */
public record WriteBehindConfiguration(
    long maxPendingBytes,
    long cacheMaxBytes,
    int maxWriteRetries,
    Duration flushTimeout,
    Set<Long> keyIndexColumnFamilies,
    int keyIndexMaxKeys,
    Map<Long, KeyIndexPolicy> keyIndexPolicies) {

  public static final int DEFAULT_KEY_INDEX_MAX_KEYS = 100_000;

  public static final Set<Long> DEFAULT_KEY_INDEX_COLUMN_FAMILIES =
      Set.of(
          (long) ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD.getValue(),
          (long) ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS.getValue(),
          (long) ZbColumnFamilies.TIMER_DUE_DATES.getValue(),
          (long) ZbColumnFamilies.JOB_DEADLINES.getValue(),
          (long) ZbColumnFamilies.JOB_BACKOFF.getValue(),
          (long) ZbColumnFamilies.MESSAGE_DEADLINES.getValue());

  /**
   * Column families whose scans always start at one end of the key space, so a window at that end
   * serves them although they hold more keys than the cap.
   */
  private static final Map<ZbColumnFamilies, KeyIndexPolicy> HOTSPOT_POLICIES =
      new EnumMap<>(
          Map.of(
              ZbColumnFamilies.TIMER_DUE_DATES, KeyIndexPolicy.KEEP_LOWEST,
              ZbColumnFamilies.JOB_DEADLINES, KeyIndexPolicy.KEEP_LOWEST,
              ZbColumnFamilies.MESSAGE_DEADLINES, KeyIndexPolicy.KEEP_LOWEST,
              ZbColumnFamilies.JOB_BACKOFF, KeyIndexPolicy.KEEP_LOWEST,
              ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD, KeyIndexPolicy.KEEP_HIGHEST));

  private static final Map<Long, KeyIndexPolicy> DEFAULT_KEY_INDEX_POLICIES =
      toIds(HOTSPOT_POLICIES);

  public WriteBehindConfiguration {
    keyIndexColumnFamilies = Set.copyOf(keyIndexColumnFamilies);
    keyIndexPolicies = Map.copyOf(keyIndexPolicies);
  }

  private static Map<Long, KeyIndexPolicy> toIds(
      final Map<ZbColumnFamilies, KeyIndexPolicy> byName) {
    final var byId = new HashMap<Long, KeyIndexPolicy>();
    byName.forEach((columnFamily, policy) -> byId.put((long) columnFamily.getValue(), policy));
    return byId;
  }

  public KeyIndexPolicy keyIndexPolicy(final long columnFamily) {
    return keyIndexPolicies.getOrDefault(columnFamily, KeyIndexPolicy.DISABLE);
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
        DEFAULT_KEY_INDEX_MAX_KEYS,
        DEFAULT_KEY_INDEX_POLICIES);
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
        keyIndexMaxKeys,
        keyIndexPolicies);
  }

  public WriteBehindConfiguration withMaxPendingBytes(final long maxPendingBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies);
  }

  public WriteBehindConfiguration withKeyIndexColumnFamilies(final Set<Long> columnFamilies) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        columnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies);
  }

  public WriteBehindConfiguration withKeyIndexMaxKeys(final int keyIndexMaxKeys) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies);
  }

  /** For tests: replaces the policies of the column families by ID. */
  WriteBehindConfiguration withKeyIndexPolicies(final Map<Long, KeyIndexPolicy> policies) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        policies);
  }
}

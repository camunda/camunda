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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * @param flushDelay how long the writer waits after the oldest unflushed commit before it writes.
 *     State that a later commit deletes in this time never reaches the storage. A snapshot, a full
 *     pending queue and a close flush at once. Zero writes as soon as a commit is queued.
 * @param flushThresholdBytes the writer flushes without waiting for the delay when the unflushed
 *     transactions hold this many bytes. Never more than {@code maxPendingBytes}, so that commits
 *     do not wait for the limit before an early flush starts.
 */
public record WriteBehindConfiguration(
    long maxPendingBytes,
    long cacheMaxBytes,
    int maxWriteRetries,
    Duration flushTimeout,
    Set<Long> keyIndexColumnFamilies,
    int keyIndexMaxKeys,
    Map<Long, KeyIndexPolicy> keyIndexPolicies,
    Duration flushDelay,
    long flushThresholdBytes) {

  public static final int DEFAULT_KEY_INDEX_MAX_KEYS = 10_000;

  /**
   * Column families whose scans always start at one end of the key space, so a window at that end
   * serves them although they hold more keys than the cap.
   *
   * <p>The first group is scanned from the lowest key (next due entry). The second group has keys
   * that start with an element instance, scope, job or process instance key, which grows with time,
   * so the engine reads and scans the newest keys. Most of their lookups and scans find nothing:
   * the engine checks for variables, event triggers, timers and subscriptions when an element ends.
   */
  private static final Map<ZbColumnFamilies, KeyIndexPolicy> HOTSPOT_POLICIES =
      Map.ofEntries(
          Map.entry(ZbColumnFamilies.TIMER_DUE_DATES, KeyIndexPolicy.KEEP_LOWEST),
          Map.entry(ZbColumnFamilies.JOB_DEADLINES, KeyIndexPolicy.KEEP_LOWEST),
          Map.entry(ZbColumnFamilies.MESSAGE_DEADLINES, KeyIndexPolicy.KEEP_LOWEST),
          Map.entry(ZbColumnFamilies.JOB_BACKOFF, KeyIndexPolicy.KEEP_LOWEST),
          Map.entry(ZbColumnFamilies.ELEMENT_INSTANCE_PARENT_CHILD, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.ELEMENT_INSTANCE_KEY, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.EVENT_SCOPE, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.EVENT_TRIGGER, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.VARIABLES, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.TIMERS, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(
              ZbColumnFamilies.CONDITIONAL_SUBSCRIPTION_BY_SCOPE_KEY, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.SECRET_REFERENCES_BY_JOB, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.BANNED_INSTANCE, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.SUSPENDED_PROCESS_INSTANCES, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.JOBS, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.JOB_STATES, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.RUNTIME_INSTRUCTIONS, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.AWAIT_WORKLOW_RESULT, KeyIndexPolicy.KEEP_HIGHEST),
          Map.entry(ZbColumnFamilies.COMPENSATION_SUBSCRIPTION, KeyIndexPolicy.KEEP_HIGHEST));

  /** Every column family with a policy has a key index by default. */
  public static final Set<Long> DEFAULT_KEY_INDEX_COLUMN_FAMILIES =
      HOTSPOT_POLICIES.keySet().stream()
          .map(columnFamily -> (long) columnFamily.getValue())
          .collect(Collectors.toUnmodifiableSet());

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
        DEFAULT_KEY_INDEX_POLICIES,
        Duration.ZERO,
        maxPendingBytes / 2);
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
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withMaxPendingBytes(final long maxPendingBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withKeyIndexColumnFamilies(final Set<Long> columnFamilies) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        columnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withKeyIndexMaxKeys(final int keyIndexMaxKeys) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }

  /** Replaces the policies by column family ID. Production code uses the fixed table. */
  public WriteBehindConfiguration withKeyIndexPolicies(final Map<Long, KeyIndexPolicy> policies) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        policies,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withFlushDelay(final Duration flushDelay) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }

  public WriteBehindConfiguration withFlushThresholdBytes(final long flushThresholdBytes) {
    return new WriteBehindConfiguration(
        maxPendingBytes,
        cacheMaxBytes,
        maxWriteRetries,
        flushTimeout,
        keyIndexColumnFamilies,
        keyIndexMaxKeys,
        keyIndexPolicies,
        flushDelay,
        flushThresholdBytes);
  }
}

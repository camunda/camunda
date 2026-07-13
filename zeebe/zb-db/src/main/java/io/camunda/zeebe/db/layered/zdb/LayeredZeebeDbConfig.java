/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuration of the layered stores created by a {@link LayeredZeebeDb}; every layered column
 * family receives the same settings. See {@link LayeredKeyValueStore} for what each knob controls.
 *
 * @param maxBytesPerStore soft byte budget per store; buffered (pinned) writes may exceed it, which
 *     {@link LayeredZeebeDb#overCapacity()} surfaces as the signal to run a persist round
 * @param maxBufferedBytes total buffered-bytes budget across a domain's stores; when positive, the
 *     runtime driving the domain should start a persist round as soon as the buffered (not yet
 *     persisted) bytes reach it — bounding memory and the recovery replay window by size,
 *     independently of the persist interval. Zero (the default) disables the size trigger: rounds
 *     then run at the persist interval or on per-store over-capacity only, exactly as before
 * @param absorbDeletes whether a delete of a never-persisted put annihilates the pair in memory so
 *     neither write ever reaches RocksDB. On by default: exact flushed flags plus negative caching
 *     make absorption unconditionally sound (a pair only annihilates when the durable store
 *     provably never held the key), and short-lived put/delete churn is exactly what the layered
 *     store exists to elide
 * @param pipelineSegmentLimit maximum number of non-persisting frozen segments per store before
 *     they are merged down, bounding read amplification
 * @param persistInterval the cadence at which the runtime driving a domain should run persist
 *     rounds; the store itself never schedules anything — this is carried here so the wiring that
 *     opens the database and the runtime that drives it agree on one value
 * @param freezeInterval the cadence at which the runtime driving a domain should freeze the active
 *     overlays into pipeline segments and republish read views; bounds the staleness asynchronous
 *     view readers observe. Like {@code persistInterval}, only carried here — the store never
 *     schedules anything itself
 */
public record LayeredZeebeDbConfig(
    long maxBytesPerStore,
    long maxBufferedBytes,
    boolean absorbDeletes,
    int pipelineSegmentLimit,
    Duration persistInterval,
    Duration freezeInterval) {

  private static final long DEFAULT_MAX_BYTES_PER_STORE = 16 * 1024 * 1024;
  private static final long DEFAULT_MAX_BUFFERED_BYTES = 0;
  private static final boolean DEFAULT_ABSORB_DELETES = true;
  private static final int DEFAULT_PIPELINE_SEGMENT_LIMIT = 4;
  private static final Duration DEFAULT_PERSIST_INTERVAL = Duration.ofSeconds(1);
  private static final Duration DEFAULT_FREEZE_INTERVAL = Duration.ofMillis(250);

  public LayeredZeebeDbConfig {
    if (maxBytesPerStore <= 0) {
      throw new IllegalArgumentException(
          "expected maxBytesPerStore to be positive, but was " + maxBytesPerStore);
    }
    if (maxBufferedBytes < 0) {
      throw new IllegalArgumentException(
          "expected maxBufferedBytes to be zero (disabled) or positive, but was "
              + maxBufferedBytes);
    }
    if (pipelineSegmentLimit < 1) {
      throw new IllegalArgumentException(
          "expected pipelineSegmentLimit to be at least 1, but was " + pipelineSegmentLimit);
    }
    Objects.requireNonNull(persistInterval, "persistInterval");
    if (persistInterval.isZero() || persistInterval.isNegative()) {
      throw new IllegalArgumentException(
          "expected persistInterval to be positive, but was " + persistInterval);
    }
    Objects.requireNonNull(freezeInterval, "freezeInterval");
    if (freezeInterval.isZero() || freezeInterval.isNegative()) {
      throw new IllegalArgumentException(
          "expected freezeInterval to be positive, but was " + freezeInterval);
    }
  }

  public static LayeredZeebeDbConfig defaults() {
    return new LayeredZeebeDbConfig(
        DEFAULT_MAX_BYTES_PER_STORE,
        DEFAULT_MAX_BUFFERED_BYTES,
        DEFAULT_ABSORB_DELETES,
        DEFAULT_PIPELINE_SEGMENT_LIMIT,
        DEFAULT_PERSIST_INTERVAL,
        DEFAULT_FREEZE_INTERVAL);
  }
}

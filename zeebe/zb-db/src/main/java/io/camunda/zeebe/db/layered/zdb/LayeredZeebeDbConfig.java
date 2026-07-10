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
 * @param absorbDeletes whether a delete of a never-persisted put annihilates the pair in memory so
 *     neither write ever reaches RocksDB
 * @param pipelineSegmentLimit maximum number of non-persisting frozen segments per store before
 *     they are merged down, bounding read amplification
 * @param persistInterval the cadence at which the runtime driving a domain should run persist
 *     rounds; the store itself never schedules anything — this is carried here so the wiring that
 *     opens the database and the runtime that drives it agree on one value
 */
public record LayeredZeebeDbConfig(
    long maxBytesPerStore,
    boolean absorbDeletes,
    int pipelineSegmentLimit,
    Duration persistInterval) {

  private static final long DEFAULT_MAX_BYTES_PER_STORE = 16 * 1024 * 1024;
  private static final int DEFAULT_PIPELINE_SEGMENT_LIMIT = 4;
  private static final Duration DEFAULT_PERSIST_INTERVAL = Duration.ofSeconds(1);

  public LayeredZeebeDbConfig {
    if (maxBytesPerStore <= 0) {
      throw new IllegalArgumentException(
          "expected maxBytesPerStore to be positive, but was " + maxBytesPerStore);
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
  }

  public LayeredZeebeDbConfig(
      final long maxBytesPerStore, final boolean absorbDeletes, final int pipelineSegmentLimit) {
    this(maxBytesPerStore, absorbDeletes, pipelineSegmentLimit, DEFAULT_PERSIST_INTERVAL);
  }

  public static LayeredZeebeDbConfig defaults() {
    return new LayeredZeebeDbConfig(
        DEFAULT_MAX_BYTES_PER_STORE,
        false,
        DEFAULT_PIPELINE_SEGMENT_LIMIT,
        DEFAULT_PERSIST_INTERVAL);
  }
}

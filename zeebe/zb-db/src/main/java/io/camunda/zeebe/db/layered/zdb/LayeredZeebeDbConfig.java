/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.layered.LayeredKeyValueStore;

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
 */
public record LayeredZeebeDbConfig(
    long maxBytesPerStore, boolean absorbDeletes, int pipelineSegmentLimit) {

  private static final long DEFAULT_MAX_BYTES_PER_STORE = 16 * 1024 * 1024;
  private static final int DEFAULT_PIPELINE_SEGMENT_LIMIT = 4;

  public LayeredZeebeDbConfig {
    if (maxBytesPerStore <= 0) {
      throw new IllegalArgumentException(
          "expected maxBytesPerStore to be positive, but was " + maxBytesPerStore);
    }
    if (pipelineSegmentLimit < 1) {
      throw new IllegalArgumentException(
          "expected pipelineSegmentLimit to be at least 1, but was " + pipelineSegmentLimit);
    }
  }

  public static LayeredZeebeDbConfig defaults() {
    return new LayeredZeebeDbConfig(
        DEFAULT_MAX_BYTES_PER_STORE, false, DEFAULT_PIPELINE_SEGMENT_LIMIT);
  }
}

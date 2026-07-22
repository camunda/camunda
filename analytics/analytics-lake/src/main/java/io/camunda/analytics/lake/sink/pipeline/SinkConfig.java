/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

/**
 * Static configuration for one {@link SinkPipeline}: one instance per (source partition x Iceberg
 * table). Every field here is a sizing/timing knob only — per the sink package's threading-model
 * contract, none of it may change what ends up committed, only when and how it's chunked into
 * files.
 *
 * @param segmentRows row capacity of each ring segment — the SEGMENT_FULL trigger point
 * @param ringSegments number of segments in the ring, i.e. the sink's fixed memory footprint; must
 *     be at least 2 (one FILLING slot, at least one SEALED slot for backpressure to have room to
 *     bite before it engages)
 * @param flushIntervalMs how long the filling segment may sit non-empty before the TIME_DUE trigger
 *     finalizes the currently open file, regardless of row count
 * @param fileTargetBytes approximate size at which the currently open file is finalized (SIZE_CAP)
 *     — approximate because {@link io.camunda.analytics.lake.sink.BatchEncoder} exposes no real
 *     size probe; see {@link FlushLoop}/{@link FileWindow} for the estimation approach
 * @param sourcePartition the event-bridge partition this pipeline instance reads from
 * @param tableName the Iceberg table this pipeline instance feeds; must equal the {@code table}
 *     carried by the {@link io.camunda.analytics.lake.sink.TableSchema} of every {@link
 *     io.camunda.analytics.lake.sink.Segment} the pipeline is constructed with
 */
public record SinkConfig(
    int segmentRows,
    int ringSegments,
    long flushIntervalMs,
    long fileTargetBytes,
    int sourcePartition,
    String tableName) {

  public SinkConfig {
    if (segmentRows <= 0) {
      throw new IllegalArgumentException("segmentRows must be positive, was " + segmentRows);
    }
    if (ringSegments < 2) {
      throw new IllegalArgumentException("ringSegments must be >= 2, was " + ringSegments);
    }
    if (flushIntervalMs <= 0) {
      throw new IllegalArgumentException(
          "flushIntervalMs must be positive, was " + flushIntervalMs);
    }
    if (fileTargetBytes <= 0) {
      throw new IllegalArgumentException(
          "fileTargetBytes must be positive, was " + fileTargetBytes);
    }
    if (tableName == null || tableName.isBlank()) {
      throw new IllegalArgumentException("tableName must not be blank");
    }
  }
}

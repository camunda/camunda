/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake;

import java.nio.file.Path;

/**
 * PoC configuration, resolved from system properties with local-run defaults (see {@code main}).
 *
 * @param contactPoint the Event Bridge gateway address, e.g. {@code localhost:26500-style}
 *     host:port
 * @param topic the topic carrying Zeebe records
 * @param consumerGroup this translator's own consumer group — isolation from the pipeline
 * @param warehouseDir local folder holding the Iceberg warehouse (catalog H2 file + data files)
 * @param stateDir local folder for the RocksDB translator state
 * @param flushRows unused since raw-table ingest moved to the L0 sink pipelines (see {@code
 *     io.camunda.analytics.lake.sink.pipeline.SinkPipeline}, whose own {@code SEGMENT_FULL}/{@code
 *     SIZE_CAP} triggers replace the old row-count flush trigger this configured); kept as a record
 *     field only so tests that construct {@link LakeConfig} positionally against the legacy {@code
 *     IcebergLakeWriter} buffered-append path keep compiling
 * @param flushIntervalMs the L0 sink's {@code lake.flushIntervalMs} knob: how long a pipeline's
 *     filling segment may sit non-empty before its {@code TIME_DUE} trigger finalizes the currently
 *     open file, regardless of row count (see {@code SinkConfig#flushIntervalMs}) — the same
 *     property name as the old buffered-writer flush interval it replaces, repurposed rather than
 *     renamed
 * @param stateDumpIntervalMs dump the open translator state to Parquet at least this often; {@code
 *     0} disables the dump
 * @param compactIntervalMs run a {@link io.camunda.analytics.lake.write.LakeCompactor} pass at
 *     least this often; {@code 0} disables compaction
 * @param uiPort port for the embedded demo UI ({@link io.camunda.analytics.lake.ui.LakeUiServer});
 *     {@code 0} disables it
 * @param bpmnDir explicit override directory to scan for {@code .bpmn} files for the {@code
 *     /process-map} page (see {@link io.camunda.analytics.lake.ui.BpmnCatalog}'s javadoc for the
 *     default resolution order used when this is {@code null}); {@code null} means "use the default
 *     resolution"
 * @param objectTombstoneRetentionMs how long a {@code CLOSED_TOMBSTONE} object-lifecycle
 *     accumulator is kept before {@code LakePocApp}'s own housekeeping tick sweeps it (see {@code
 *     TranslatorState#sweepObjectLifecycleTombstones}'s own javadoc); default 24h
 * @param gaugeFlushIntervalMs how long {@code
 *     io.camunda.analytics.lake.write.OpenInstancesGaugeSampler} may buffer sampled {@code
 *     open_instances_gauge} rows in memory before flushing them as one Parquet file plus one
 *     Iceberg commit (see that class's own javadoc for why this is decoupled from the sampling
 *     cadence itself, which piggybacks on {@link #stateDumpIntervalMs}); default 5 minutes
 */
public record LakeConfig(
    String contactPoint,
    String topic,
    String consumerGroup,
    Path warehouseDir,
    Path stateDir,
    int flushRows,
    long flushIntervalMs,
    long stateDumpIntervalMs,
    long compactIntervalMs,
    int uiPort,
    Path bpmnDir,
    long objectTombstoneRetentionMs,
    long gaugeFlushIntervalMs) {

  /** Default {@link #objectTombstoneRetentionMs}: 24 hours. */
  public static final long DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS = 24L * 60 * 60 * 1000;

  /** Default {@link #gaugeFlushIntervalMs}: 5 minutes. */
  public static final long DEFAULT_GAUGE_FLUSH_INTERVAL_MS = 5L * 60 * 1000;

  /**
   * Convenience constructor matching this record's shape before {@link #objectTombstoneRetentionMs}
   * was added — defaults it to {@link #DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS}. Kept so every
   * pre-existing 11-arg positional construction (test fixtures that build an {@link
   * io.camunda.analytics.lake.write.IcebergLakeWriter} directly and never touch object-lifecycle
   * capture) keeps compiling unchanged.
   */
  public LakeConfig(
      final String contactPoint,
      final String topic,
      final String consumerGroup,
      final Path warehouseDir,
      final Path stateDir,
      final int flushRows,
      final long flushIntervalMs,
      final long stateDumpIntervalMs,
      final long compactIntervalMs,
      final int uiPort,
      final Path bpmnDir) {
    this(
        contactPoint,
        topic,
        consumerGroup,
        warehouseDir,
        stateDir,
        flushRows,
        flushIntervalMs,
        stateDumpIntervalMs,
        compactIntervalMs,
        uiPort,
        bpmnDir,
        DEFAULT_OBJECT_TOMBSTONE_RETENTION_MS);
  }

  /**
   * Convenience constructor matching this record's shape before {@link #gaugeFlushIntervalMs} was
   * added — defaults it to {@link #DEFAULT_GAUGE_FLUSH_INTERVAL_MS}. Kept so every pre-existing
   * 12-arg positional construction keeps compiling unchanged, the same reasoning as the 11-arg
   * constructor above.
   */
  public LakeConfig(
      final String contactPoint,
      final String topic,
      final String consumerGroup,
      final Path warehouseDir,
      final Path stateDir,
      final int flushRows,
      final long flushIntervalMs,
      final long stateDumpIntervalMs,
      final long compactIntervalMs,
      final int uiPort,
      final Path bpmnDir,
      final long objectTombstoneRetentionMs) {
    this(
        contactPoint,
        topic,
        consumerGroup,
        warehouseDir,
        stateDir,
        flushRows,
        flushIntervalMs,
        stateDumpIntervalMs,
        compactIntervalMs,
        uiPort,
        bpmnDir,
        objectTombstoneRetentionMs,
        DEFAULT_GAUGE_FLUSH_INTERVAL_MS);
  }
}

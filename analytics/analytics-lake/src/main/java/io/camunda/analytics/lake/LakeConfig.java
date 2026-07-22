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
    Path bpmnDir) {}

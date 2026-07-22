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
 * @param flushRows flush the lake buffer when this many rows accumulated
 * @param flushIntervalMs flush the lake buffer at least this often while rows are buffered
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

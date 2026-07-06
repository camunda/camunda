/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionKey;
import java.util.List;

/**
 * The backend-neutral <b>write</b> seam of the serving store (mirroring OC's {@code
 * DocumentBasedWriteClient}): the pipeline's result sinks delegate here. Every write is an
 * idempotent full-value upsert keyed by identity, so a re-emit or replay overwrites rather than
 * duplicates — the property the segment-delta reduce and the projected row path both rely on.
 */
public interface DatasetWriter extends AutoCloseable {

  /**
   * Upserts one meter's merged accumulator for one cube cell, identified by {@code (dimensions,
   * windowStart, windowSize)}. Meters of the same cell coexist; each call sets only its meter.
   */
  void upsertCell(
      CompiledDataset dataset,
      DimensionKey key,
      long windowStart,
      long windowSize,
      String meterName,
      byte[] accumulator);

  /**
   * Upserts one raw row of a projected dataset keyed by {@code rowKey}; {@code values} align to the
   * table's declared columns.
   */
  void upsertRow(CompiledTable table, String rowKey, List<Object> values);

  /** Batch boundary: make buffered writes durable. */
  void flush();

  @Override
  void close();
}

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
 * The <b>producer-facing</b> write seam of the serving store: the pipeline's result sinks delegate
 * here, version-unaware — every write is an idempotent full-value upsert keyed by identity, so a
 * re-emit or replay overwrites rather than duplicates. The stage task's staging writer collects
 * these, seals each commit cut's batch with one {@link WriteVersion}, and drains it to the fenced
 * backend seam ({@link VersionedDatasetWriter}) — the version belongs to the cut, not to the
 * individual write, which is why the sinks never see it.
 */
public interface DatasetWriter extends AutoCloseable {

  /**
   * Upserts one cube cell — identified by {@code (dimensions, windowStart, windowSize)} — from its
   * <em>composite</em> accumulator: every meter's slot, encoded by {@link
   * io.camunda.analytics.meter.CompositeAccumulatorValue} in the dataset's declared meter order
   * (ADR 0009). A backend writes all meter columns in one idempotent write, so the cell has exactly
   * one writer and is never torn between meters.
   */
  void upsertCell(
      CompiledDataset dataset,
      DimensionKey key,
      long windowStart,
      long windowSize,
      byte[] compositeAccumulator);

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

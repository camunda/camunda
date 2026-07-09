/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;

/**
 * The Stage-2 serving sink for one cube tier (ADR 0009): an idempotent upsert of the merged
 * <em>composite</em> accumulator — every meter's slot — into the cube's serving store (via the
 * backend-neutral {@link DatasetWriter}), keyed by the cell's dimensions, window start, and the
 * tier's window size. One call writes the whole serving row, so the row has exactly one writer and
 * can never be torn between meters; re-writing the same cell is a no-op overwrite.
 */
public final class CubeServingSink implements ResultSink<Windowed<DimensionKey>, Object[]> {

  private final DatasetWriter writer;
  private final CompiledDataset dataset;
  private final long windowSize;
  private final RecordValue<Object[]> compositeCodec;

  public CubeServingSink(
      final DatasetWriter writer,
      final CompiledDataset dataset,
      final long windowSize,
      final RecordValue<Object[]> compositeCodec) {
    this.writer = writer;
    this.dataset = dataset;
    this.windowSize = windowSize;
    this.compositeCodec = compositeCodec;
  }

  @Override
  public void upsert(final Windowed<DimensionKey> cell, final Object[] value) {
    writer.upsertCell(
        dataset, cell.key(), cell.windowStart(), windowSize, compositeCodec.toBytes(value));
  }

  /**
   * Serialize-once path: the merging aggregation already encoded the composite (with the same codec
   * type) for its durable checkpoint, so reuse those bytes instead of encoding the identical value
   * a second time per commit.
   */
  @Override
  public void upsert(
      final Windowed<DimensionKey> cell, final Object[] value, final byte[] serialized) {
    writer.upsertCell(dataset, cell.key(), cell.windowStart(), windowSize, serialized);
  }
}

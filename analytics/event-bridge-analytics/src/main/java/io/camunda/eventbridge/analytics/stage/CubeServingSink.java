/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.jdbc.JdbcDatasetStore;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.window.Windowed;

/**
 * The Stage-2 serving sink for one cube meter: an idempotent upsert of the merged accumulator into
 * the cube's serving store, keyed by the cell's dimensions, window start, and the meter's tier
 * (window size). The accumulator is encoded with the meter's codec and written to the meter's
 * column; re-writing the same cell is a no-op overwrite.
 *
 * @param <ACC> the meter's accumulator type
 */
public final class CubeServingSink<ACC> implements ResultSink<Windowed<DimensionKey>, ACC> {

  private final JdbcDatasetStore store;
  private final CompiledDataset dataset;
  private final String meterName;
  private final long windowSize;
  private final RecordValue<ACC> accCodec;

  public CubeServingSink(
      final JdbcDatasetStore store,
      final CompiledDataset dataset,
      final String meterName,
      final long windowSize,
      final RecordValue<ACC> accCodec) {
    this.store = store;
    this.dataset = dataset;
    this.meterName = meterName;
    this.windowSize = windowSize;
    this.accCodec = accCodec;
  }

  @Override
  public void upsert(final Windowed<DimensionKey> cell, final ACC value) {
    store.upsert(
        dataset, cell.key(), cell.windowStart(), windowSize, meterName, accCodec.toBytes(value));
  }
}

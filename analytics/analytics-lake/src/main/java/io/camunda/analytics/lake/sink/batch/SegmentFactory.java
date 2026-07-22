/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.List;

/**
 * Builds the {@link Segment} array a {@link io.camunda.analytics.lake.sink.ColumnarSegmentRing}
 * owns: one heap-backed {@link ColumnVector} per schema column, sized once here per the sink's
 * zero-steady-state-allocation contract (see {@code io.camunda.analytics.lake.sink} {@code
 * package-info}).
 */
public final class SegmentFactory {

  private SegmentFactory() {}

  /**
   * @param schema drives column count, types and the family-day column
   * @param segmentCount number of segments in the ring (the ring itself requires at least 2)
   * @param rowCapacity rows per segment
   * @param binaryAvgBytesPerRow average bytes budgeted per row for each {@code BINARY} column, by
   *     column index in {@code schema.columns()} (ignored for other column types); a column's arena
   *     is sized {@code rowCapacity * binaryAvgBytesPerRow[columnIndex]} — see {@link
   *     HeapBinaryColumn}
   * @param dictInterner the single {@link Interner} shared by every {@code STRING_DICT} column of
   *     this pipeline, across every table and every segment — the sink's threading contract
   *     requires exactly one instance per pipeline
   */
  public static Segment[] createSegments(
      final TableSchema schema,
      final int segmentCount,
      final int rowCapacity,
      final int[] binaryAvgBytesPerRow,
      final Interner dictInterner) {
    final Segment[] segments = new Segment[segmentCount];
    for (int i = 0; i < segmentCount; i++) {
      segments[i] =
          new Segment(
              schema,
              createVectors(schema, rowCapacity, binaryAvgBytesPerRow, dictInterner),
              rowCapacity);
    }
    return segments;
  }

  private static ColumnVector[] createVectors(
      final TableSchema schema,
      final int rowCapacity,
      final int[] binaryAvgBytesPerRow,
      final Interner dictInterner) {
    final List<TableSchema.Column> columns = schema.columns();
    final ColumnVector[] vectors = new ColumnVector[columns.size()];
    for (int i = 0; i < columns.size(); i++) {
      final TableSchema.Column column = columns.get(i);
      vectors[i] =
          switch (column.type()) {
            case LONG -> new HeapLongColumn(rowCapacity, column.nullable());
            case INT -> new HeapIntColumn(rowCapacity, column.nullable());
            case STRING_DICT -> new HeapDictColumn(rowCapacity, column.nullable(), dictInterner);
            case BINARY ->
                new HeapBinaryColumn(rowCapacity, column.nullable(), binaryAvgBytesPerRow[i]);
          };
    }
    return vectors;
  }
}

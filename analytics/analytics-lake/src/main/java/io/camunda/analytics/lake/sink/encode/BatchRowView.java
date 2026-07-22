/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * Flyweight {@link Record} cursor over one position of a {@link SortedRun}: constructed once per
 * open file, repositioned with {@link #moveTo} for every row, never allocated per row.
 *
 * <p>iceberg-parquet's generic struct writer (see {@code
 * org.apache.iceberg.parquet.ParquetValueWriters.RecordWriter}) only ever calls {@link
 * StructLike#get(int, Class)} with {@code Object.class} — confirmed by decompiling the write path
 * against iceberg 1.10.2 — so that is the one method on the hot path; every other {@link Record}
 * method exists only to satisfy the interface.
 *
 * <p>{@code pos} is a position in the caller-supplied {@code org.apache.iceberg.Schema}'s field
 * list, not a {@link TableSchema} column index — the two schemas can list columns in different
 * orders, so the mapping from {@code pos} to the matching {@link TableSchema} column is resolved
 * once at construction, by iceberg field id ({@link TableSchema}'s authoritative identity; see its
 * class javadoc), never by name or position.
 *
 * <p>{@link #getField(String)} and the {@link StructLike#set(int, Object)}/ {@link
 * #setField(String, Object)}/{@link #copy()} family all throw: this is a read-only cursor over
 * someone else's storage, and a name-based lookup here would mean a linear struct scan on every row
 * of the flush path for a method the writer never actually calls.
 */
final class BatchRowView implements Record {

  private final Types.StructType struct;
  private final ColumnType[] columnTypeByPos;
  private final int[] sortedRunColumnByPos;
  private final boolean[] timestamptzByPos;

  // Mutable cursor state; rebound per row by moveTo(), never per column.
  private SortedRun run;
  private int index;

  // Reused across every BINARY column read; grows (rarely) to fit the widest value seen so far.
  // The per-row copy out of the SortedRun's own arena is unavoidable here (Record.get returns a
  // value, not a cursor), but the backing array itself is never reallocated at steady state.
  private byte[] binaryScratch = new byte[64];

  BatchRowView(TableSchema tableSchema, Types.StructType struct) {
    this.struct = struct;
    final List<TableSchema.Column> columns = tableSchema.columns();
    final int size = struct.fields().size();
    columnTypeByPos = new io.camunda.analytics.lake.sink.ColumnType[size];
    sortedRunColumnByPos = new int[size];
    timestamptzByPos = new boolean[size];
    for (int pos = 0; pos < size; pos++) {
      final int fieldId = struct.fields().get(pos).fieldId();
      final int columnIndex = columnIndexForFieldId(columns, fieldId, tableSchema.table());
      sortedRunColumnByPos[pos] = columnIndex;
      columnTypeByPos[pos] = columns.get(columnIndex).type();
      timestamptzByPos[pos] = columns.get(columnIndex).timestamptz();
    }
  }

  private static int columnIndexForFieldId(
      final List<TableSchema.Column> columns, final int fieldId, final String table) {
    for (int i = 0; i < columns.size(); i++) {
      if (columns.get(i).icebergFieldId() == fieldId) {
        return i;
      }
    }
    throw new IllegalStateException(
        "TableSchema for table " + table + " has no column with iceberg field id " + fieldId);
  }

  /** Repositions this flyweight onto {@code run}'s sorted position {@code i}. */
  void moveTo(final SortedRun run, final int i) {
    this.run = run;
    index = i;
  }

  @Override
  public Types.StructType struct() {
    return struct;
  }

  @Override
  public int size() {
    return columnTypeByPos.length;
  }

  @Override
  public <T> T get(final int pos, final Class<T> javaClass) {
    final int column = sortedRunColumnByPos[pos];
    if (run.isNullAt(column, index)) {
      return null;
    }
    final Object value =
        switch (columnTypeByPos[pos]) {
          case LONG ->
              timestamptzByPos[pos]
                  ? microsToOffsetDateTime(run.longAt(column, index))
                  : run.longAt(column, index);
          case INT -> run.intAt(column, index);
          case STRING_DICT -> run.stringAt(column, index);
          case BINARY -> copyBinary(column);
        };
    return javaClass.cast(value);
  }

  /**
   * A {@code timestamptz} column's batch-vector value is epoch microseconds (see {@link
   * TableSchema.Column#timestamptz()}); iceberg-data's generic Parquet writer expects an {@link
   * OffsetDateTime} for such a field (confirmed against {@code
   * GenericParquetWriter.TimestamptzWriter}, which extends {@code
   * ParquetValueWriters.PrimitiveWriter<OffsetDateTime>}) — one allocation per timestamp value on
   * the flush thread, same budgeted trade-off as {@link #copyBinary}.
   */
  private static OffsetDateTime microsToOffsetDateTime(final long epochMicros) {
    return DateTimeUtil.timestamptzFromMicros(epochMicros);
  }

  private ByteBuffer copyBinary(final int column) {
    final int len = run.binaryLength(column, index);
    if (binaryScratch.length < len) {
      binaryScratch = new byte[len];
    }
    run.copyBinaryTo(column, index, binaryScratch, 0);
    return ByteBuffer.wrap(binaryScratch, 0, len);
  }

  @Override
  public Object get(final int pos) {
    return get(pos, Object.class);
  }

  @Override
  public Object getField(final String name) {
    throw new UnsupportedOperationException(
        "BatchRowView resolves columns by position (iceberg field id), not name: getField(\""
            + name
            + "\") would require a linear struct scan on the hot flush path");
  }

  @Override
  public <T> void set(final int pos, final T value) {
    throw new UnsupportedOperationException("BatchRowView is a read-only cursor over a SortedRun");
  }

  @Override
  public void setField(final String name, final Object value) {
    throw new UnsupportedOperationException("BatchRowView is a read-only cursor over a SortedRun");
  }

  @Override
  public Record copy() {
    throw new UnsupportedOperationException("BatchRowView is a flyweight, never a value holder");
  }

  @Override
  public Record copy(final Map<String, Object> overwriteValues) {
    throw new UnsupportedOperationException("BatchRowView is a flyweight, never a value holder");
  }
}

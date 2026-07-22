/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnVector;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Flush-thread-only: sorts a sealed {@link Segment} by (family day, then the schema's sort key) and
 * gathers every column into reused scratch, exposing the result as a {@link SortedRun}.
 *
 * <p>Mechanics: a reusable {@code int[]} permutation of row indices is sorted in place, comparing
 * directly into the segment's vectors (no row data movement, no boxing); then one linear gather
 * pass per column copies values into per-column scratch arrays, in sorted order, sized {@code
 * rowCapacity} and reused across every {@link #sort(Segment)} call. Day-range boundaries are
 * derived in the same pass. A single instance is meant to be constructed once per (table, ring) and
 * reused for the lifetime of the pipeline — it is not thread-safe and must only ever be driven by
 * the flush thread, matching the segment it sorts.
 *
 * <p>This class implements {@link SortedRun} directly: it returns {@code this} from {@link
 * #sort(Segment)}, so — per {@link SortedRun}'s contract — the result is invalidated the moment
 * {@link #sort(Segment)} is called again.
 *
 * <p>The sort itself is a median-of-three single-pivot quicksort (Lomuto partition) that always
 * recurses into the smaller partition and loops on the larger one, bounding recursion depth to
 * {@code O(log n)} regardless of input order; runs below a small threshold fall back to insertion
 * sort. This is simpler than a dual-pivot or radix sort while meeting the same requirements (no
 * boxing, no row movement, bounded stack depth) — a deliberate KISS choice, not a cut corner.
 */
public final class SegmentSorter implements SortedRun {

  private static final long MILLIS_PER_DAY = 86_400_000L;
  private static final int INSERTION_SORT_THRESHOLD = 24;

  private final TableSchema schema;
  private final int[] sortKeyColumns;
  private final int familyDayColumn;
  private final Interner dictInterner;

  private final int[] permutation;
  private final long[] epochDayByRow; // indexed by ORIGINAL row number, recomputed every sort()

  private final Object[] columnScratch; // long[] | int[] | BinaryScratch, gathered in sorted order
  private final long[][]
      nullScratch; // bitset per column, gathered in sorted order; null if not nullable

  private int size;
  private List<DayRange> dayRanges = List.of();

  public SegmentSorter(
      final TableSchema schema,
      final int rowCapacity,
      final int[] binaryAvgBytesPerRow,
      final Interner dictInterner) {
    this.schema = schema;
    this.dictInterner = dictInterner;
    sortKeyColumns = schema.sortKeyColumns();
    familyDayColumn = schema.familyDayColumn();

    permutation = new int[rowCapacity];
    epochDayByRow = new long[rowCapacity];

    final List<TableSchema.Column> columns = schema.columns();
    columnScratch = new Object[columns.size()];
    nullScratch = new long[columns.size()][];
    for (int i = 0; i < columns.size(); i++) {
      final TableSchema.Column column = columns.get(i);
      columnScratch[i] =
          switch (column.type()) {
            case LONG -> new long[rowCapacity];
            case INT -> new int[rowCapacity];
            case STRING_DICT -> new int[rowCapacity];
            case BINARY -> new BinaryScratch(rowCapacity * binaryAvgBytesPerRow[i], rowCapacity);
          };
      if (column.nullable()) {
        nullScratch[i] = NullBitset.allocate(rowCapacity);
      }
    }
  }

  /**
   * Sorts {@code segment} by (family day, then the schema's sort key) and gathers every column into
   * this sorter's reused scratch. Flush thread only. Returns {@code this}; the previous result is
   * invalidated.
   */
  public SortedRun sort(final Segment segment) {
    size = segment.size();
    final ColumnVector.LongColumn familyDayVector =
        (ColumnVector.LongColumn) segment.vector(familyDayColumn);
    for (int row = 0; row < size; row++) {
      permutation[row] = row;
      epochDayByRow[row] = Math.floorDiv(familyDayVector.get(row), MILLIS_PER_DAY);
    }
    if (size > 1) {
      quicksort(segment, 0, size - 1);
    }
    gather(segment);
    return this;
  }

  private void quicksort(final Segment segment, final int lowIn, final int highIn) {
    int low = lowIn;
    int high = highIn;
    while (high - low > INSERTION_SORT_THRESHOLD) {
      final int mid = low + (high - low) / 2;
      medianOfThreeToHigh(segment, low, mid, high);
      final int pivot = permutation[high];
      int store = low;
      for (int i = low; i < high; i++) {
        if (compare(segment, permutation[i], pivot) < 0) {
          swap(i, store);
          store++;
        }
      }
      swap(store, high);
      // recurse into the smaller side, loop into the larger: bounds recursion depth to O(log n)
      if (store - low < high - store) {
        quicksort(segment, low, store - 1);
        low = store + 1;
      } else {
        quicksort(segment, store + 1, high);
        high = store - 1;
      }
    }
    insertionSort(segment, low, high);
  }

  private void medianOfThreeToHigh(
      final Segment segment, final int low, final int mid, final int high) {
    if (compare(segment, permutation[mid], permutation[low]) < 0) {
      swap(mid, low);
    }
    if (compare(segment, permutation[high], permutation[low]) < 0) {
      swap(high, low);
    }
    if (compare(segment, permutation[high], permutation[mid]) < 0) {
      swap(high, mid);
    }
    swap(mid, high); // move the median to the partition point
  }

  private void insertionSort(final Segment segment, final int low, final int high) {
    for (int i = low + 1; i <= high; i++) {
      final int key = permutation[i];
      int j = i - 1;
      while (j >= low && compare(segment, permutation[j], key) > 0) {
        permutation[j + 1] = permutation[j];
        j--;
      }
      permutation[j + 1] = key;
    }
  }

  private void swap(final int a, final int b) {
    final int tmp = permutation[a];
    permutation[a] = permutation[b];
    permutation[b] = tmp;
  }

  private int compare(final Segment segment, final int rowA, final int rowB) {
    final long dayA = epochDayByRow[rowA];
    final long dayB = epochDayByRow[rowB];
    if (dayA != dayB) {
      return Long.compare(dayA, dayB);
    }
    for (final int column : sortKeyColumns) {
      final int cmp = compareColumn(segment, column, rowA, rowB);
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }

  /**
   * {@code STRING_DICT} sort-key columns compare by interner code, not by string value: grouping
   * identical values adjacently is the sort's actual goal (row-group/day-router adjacency and
   * future partition pruning) — not alphabetical order — and comparing by code avoids resolving
   * every row to a string on the flush thread only to discard the result. Nulls sort first.
   */
  private int compareColumn(
      final Segment segment, final int column, final int rowA, final int rowB) {
    final ColumnVector vector = segment.vector(column);
    final boolean nullA = vector.isNull(rowA);
    final boolean nullB = vector.isNull(rowB);
    if (nullA || nullB) {
      if (nullA == nullB) {
        return 0;
      }
      return nullA ? -1 : 1;
    }
    return switch (vector.type()) {
      case LONG ->
          Long.compare(
              ((ColumnVector.LongColumn) vector).get(rowA),
              ((ColumnVector.LongColumn) vector).get(rowB));
      case INT ->
          Integer.compare(
              ((ColumnVector.IntColumn) vector).get(rowA),
              ((ColumnVector.IntColumn) vector).get(rowB));
      case STRING_DICT ->
          Integer.compare(
              ((ColumnVector.DictColumn) vector).code(rowA),
              ((ColumnVector.DictColumn) vector).code(rowB));
      case BINARY ->
          throw new UnsupportedOperationException(
              "BINARY column " + column + " cannot be part of a sort key");
    };
  }

  private void gather(final Segment segment) {
    final List<TableSchema.Column> columns = schema.columns();
    for (int c = 0; c < columns.size(); c++) {
      gatherColumn(segment, c, columns.get(c).type());
    }
    dayRanges = buildDayRanges();
  }

  private void gatherColumn(final Segment segment, final int column, final ColumnType type) {
    final ColumnVector vector = segment.vector(column);
    final long[] nulls = nullScratch[column];
    if (nulls != null) {
      NullBitset.clearAll(nulls);
    }
    switch (type) {
      case LONG -> {
        final long[] scratch = (long[]) columnScratch[column];
        final ColumnVector.LongColumn typed = (ColumnVector.LongColumn) vector;
        for (int i = 0; i < size; i++) {
          final int row = permutation[i];
          if (typed.isNull(row)) {
            if (nulls != null) {
              NullBitset.set(nulls, i);
            }
          } else {
            scratch[i] = typed.get(row);
          }
        }
      }
      case INT -> {
        final int[] scratch = (int[]) columnScratch[column];
        final ColumnVector.IntColumn typed = (ColumnVector.IntColumn) vector;
        for (int i = 0; i < size; i++) {
          final int row = permutation[i];
          if (typed.isNull(row)) {
            if (nulls != null) {
              NullBitset.set(nulls, i);
            }
          } else {
            scratch[i] = typed.get(row);
          }
        }
      }
      case STRING_DICT -> {
        final int[] scratch = (int[]) columnScratch[column];
        final ColumnVector.DictColumn typed = (ColumnVector.DictColumn) vector;
        for (int i = 0; i < size; i++) {
          final int row = permutation[i];
          if (typed.isNull(row)) {
            if (nulls != null) {
              NullBitset.set(nulls, i);
            }
          } else {
            scratch[i] = typed.code(row);
          }
        }
      }
      case BINARY -> {
        final BinaryScratch scratch = (BinaryScratch) columnScratch[column];
        final ColumnVector.BinaryColumn typed = (ColumnVector.BinaryColumn) vector;
        scratch.arenaPos = 0;
        scratch.offsets[0] = 0;
        for (int i = 0; i < size; i++) {
          final int row = permutation[i];
          if (typed.isNull(row)) {
            if (nulls != null) {
              NullBitset.set(nulls, i);
            }
            scratch.offsets[i + 1] = scratch.arenaPos;
          } else {
            final int len = typed.length(row);
            typed.copyTo(row, scratch.arena, scratch.arenaPos);
            scratch.arenaPos += len;
            scratch.offsets[i + 1] = scratch.arenaPos;
          }
        }
      }
    }
  }

  private List<DayRange> buildDayRanges() {
    if (size == 0) {
      return List.of();
    }
    final List<DayRange> ranges = new ArrayList<>();
    long currentDay = epochDayByRow[permutation[0]];
    int rangeStart = 0;
    for (int i = 1; i < size; i++) {
      final long day = epochDayByRow[permutation[i]];
      if (day != currentDay) {
        ranges.add(new DayRange(currentDay, rangeStart, i));
        currentDay = day;
        rangeStart = i;
      }
    }
    ranges.add(new DayRange(currentDay, rangeStart, size));
    return ranges;
  }

  // ---- SortedRun ----------------------------------------------------

  @Override
  public TableSchema schema() {
    return schema;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isNullAt(final int column, final int i) {
    final long[] nulls = nullScratch[column];
    return nulls != null && NullBitset.isSet(nulls, i);
  }

  @Override
  public long longAt(final int column, final int i) {
    return ((long[]) columnScratch[column])[i];
  }

  @Override
  public int intAt(final int column, final int i) {
    return ((int[]) columnScratch[column])[i];
  }

  @Override
  public String stringAt(final int column, final int i) {
    final int code = ((int[]) columnScratch[column])[i];
    return dictInterner.valueOf(code);
  }

  @Override
  public int binaryLength(final int column, final int i) {
    final BinaryScratch scratch = (BinaryScratch) columnScratch[column];
    return scratch.offsets[i + 1] - scratch.offsets[i];
  }

  @Override
  public int copyBinaryTo(final int column, final int i, final byte[] dst, final int dstOffset) {
    final BinaryScratch scratch = (BinaryScratch) columnScratch[column];
    final int len = scratch.offsets[i + 1] - scratch.offsets[i];
    System.arraycopy(scratch.arena, scratch.offsets[i], dst, dstOffset, len);
    return len;
  }

  @Override
  public List<DayRange> dayRanges() {
    return dayRanges;
  }

  /** Reused gather target for one {@code BINARY} column: arena + prefix-sum offsets. */
  private static final class BinaryScratch {
    private final byte[] arena;
    private final int[] offsets;
    private int arenaPos;

    private BinaryScratch(final int arenaBytes, final int rowCapacity) {
      arena = new byte[arenaBytes];
      offsets = new int[rowCapacity + 1];
    }
  }
}

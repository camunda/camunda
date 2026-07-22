/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled {@link SortedRun} test double over plain arrays: {@code columns[column][row]} holds
 * the boxed value ({@code Long}/{@code Integer}/{@code String}/{@code byte[]}) or {@code null},
 * already in the sort-key order the real sorter would have produced. {@code epochDayPerRow} drives
 * {@link #dayRanges()} by grouping equal, already-adjacent days into contiguous ranges, exactly as
 * {@link SortedRun}'s own contract describes.
 */
final class FakeSortedRun implements SortedRun {

  private final TableSchema schema;
  private final Object[][] columns;
  private final int rowCount;
  private final List<DayRange> dayRanges;

  FakeSortedRun(final TableSchema schema, final Object[][] columns, final long[] epochDayPerRow) {
    this.schema = schema;
    this.columns = columns;
    rowCount = epochDayPerRow.length;
    dayRanges = computeDayRanges(epochDayPerRow);
  }

  private static List<DayRange> computeDayRanges(final long[] epochDayPerRow) {
    final List<DayRange> ranges = new ArrayList<>();
    int i = 0;
    while (i < epochDayPerRow.length) {
      final int start = i;
      final long day = epochDayPerRow[i];
      while (i < epochDayPerRow.length && epochDayPerRow[i] == day) {
        i++;
      }
      ranges.add(new DayRange(day, start, i));
    }
    return ranges;
  }

  @Override
  public TableSchema schema() {
    return schema;
  }

  @Override
  public int size() {
    return rowCount;
  }

  @Override
  public boolean isNullAt(final int column, final int i) {
    return columns[column][i] == null;
  }

  @Override
  public long longAt(final int column, final int i) {
    return (Long) columns[column][i];
  }

  @Override
  public int intAt(final int column, final int i) {
    return (Integer) columns[column][i];
  }

  @Override
  public String stringAt(final int column, final int i) {
    return (String) columns[column][i];
  }

  @Override
  public int binaryLength(final int column, final int i) {
    return ((byte[]) columns[column][i]).length;
  }

  @Override
  public int copyBinaryTo(final int column, final int i, final byte[] dst, final int dstOffset) {
    final byte[] src = (byte[]) columns[column][i];
    System.arraycopy(src, 0, dst, dstOffset, src.length);
    return src.length;
  }

  @Override
  public List<DayRange> dayRanges() {
    return dayRanges;
  }
}

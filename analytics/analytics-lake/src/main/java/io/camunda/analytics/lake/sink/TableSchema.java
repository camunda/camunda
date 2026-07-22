/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import java.util.List;

/**
 * Schema descriptor driving the whole sink: the columnar batch layout, the sort order, the day
 * routing, and the Parquet schema are all derived from this — nothing in the sink is
 * schema-specific code.
 *
 * <p><b>Field IDs are authoritative and must come from the Iceberg table's schema in the
 * catalog</b> (never invented locally): the encoder writes them into the Parquet files, and a
 * mismatch with the catalog schema silently breaks column resolution for every reader.
 *
 * @param table the Iceberg table name this schema feeds (e.g. {@code instances})
 * @param columns column descriptors in file order
 */
public record TableSchema(String table, List<Column> columns) {

  public TableSchema {
    columns = List.copyOf(columns);
  }

  /**
   * @param name column name, must equal the Iceberg column name
   * @param type value shape, decides the vector implementation
   * @param icebergFieldId the field id from the catalog table schema (authoritative, see class
   *     javadoc)
   * @param nullable whether null values may be appended
   * @param sortOrder position in the seal-time sort key ({@code 0} = primary, {@code 1} =
   *     secondary, ...; {@code -1} = not part of the sort key)
   * @param familyDaySource whether this column carries the epoch-millis timestamp the family day is
   *     derived from (exactly one column per schema must set this)
   */
  public record Column(
      String name,
      ColumnType type,
      int icebergFieldId,
      boolean nullable,
      int sortOrder,
      boolean familyDaySource) {}

  /** Index of the column the family day is derived from. */
  public int familyDayColumn() {
    for (int i = 0; i < columns.size(); i++) {
      if (columns.get(i).familyDaySource()) {
        return i;
      }
    }
    throw new IllegalStateException("schema " + table + " declares no familyDaySource column");
  }

  /** Column indexes of the sort key, in sort-order position (primary first). */
  public int[] sortKeyColumns() {
    int keys = 0;
    for (final Column c : columns) {
      if (c.sortOrder() >= 0) {
        keys++;
      }
    }
    final int[] byPosition = new int[keys];
    for (int i = 0; i < columns.size(); i++) {
      final int order = columns.get(i).sortOrder();
      if (order >= 0) {
        byPosition[order] = i;
      }
    }
    return byPosition;
  }
}

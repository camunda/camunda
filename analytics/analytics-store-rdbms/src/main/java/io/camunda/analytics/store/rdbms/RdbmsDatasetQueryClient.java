/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.store.Cell;
import io.camunda.analytics.dataset.store.DatasetFetch;
import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.TableFetch;
import io.camunda.analytics.dataset.store.TableRow;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetQueryClient}: transforms a neutral {@link DatasetFetch} into a dynamic
 * {@code SELECT} (via {@link DatasetQueryMapper}) over the cube's {@code dataset_<id>} table,
 * pushing down the tier, window range, and equality filters on grain columns, and maps each row
 * back into a {@link Cell} (grain {@link DimensionKey} + window + still-encoded meter
 * accumulators). The executor does the merge/finalize, so this stays a filter-and-fetch.
 */
public final class RdbmsDatasetQueryClient implements DatasetQueryClient {

  private final SqlSessionFactory sessionFactory;

  public RdbmsDatasetQueryClient(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public List<Cell> fetch(final DatasetFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    // SQL text uses the quoted identifier (reserved-word safe); the read-back map lookups below use
    // the bare, lowercased name to match the JDBC column labels.
    final List<String> dimColumns = new ArrayList<>();
    for (final DimensionColumn column : grain) {
      dimColumns.add(RdbmsNames.quotedColumn(column.name()));
    }
    final List<String> meterColumns = new ArrayList<>();
    for (final String meter : fetch.meters()) {
      meterColumns.add(RdbmsNames.quotedColumn(meter));
    }

    final List<String> filterColumns = new ArrayList<>();
    final List<Object> filterValues = new ArrayList<>();
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index < 0) {
        continue; // only grain columns are stored and thus filterable at read time
      }
      filterColumns.add(RdbmsNames.quotedColumn(filter.field()));
      filterValues.add(coerce(grain.get(index).type(), filter.value()));
    }

    final Map<String, Object> params = new HashMap<>();
    params.put("table", RdbmsNames.datasetTable(dataset.cubeId()));
    params.put("dimColumns", dimColumns);
    params.put("meterColumns", meterColumns);
    params.put("filterColumns", filterColumns);
    params.put("filterValues", filterValues);
    params.put("windowSize", fetch.windowSize());
    params.put("fromMs", fetch.fromMs());
    params.put("toMs", fetch.toMs());

    try (SqlSession session = sessionFactory.openSession()) {
      final List<Map<String, Object>> rows =
          session.getMapper(DatasetQueryMapper.class).fetch(params);
      final List<Cell> cells = new ArrayList<>(rows.size());
      for (final Map<String, Object> row : rows) {
        cells.add(toCell(dataset, grain, fetch.meters(), lowerKeys(row)));
      }
      return cells;
    }
  }

  @Override
  public List<TableRow> fetchRows(final TableFetch fetch) {
    final CompiledTable table = fetch.table();
    final List<DimensionColumn> columns = table.columns();

    final List<String> selectColumns = new ArrayList<>();
    for (final DimensionColumn column : columns) {
      selectColumns.add(RdbmsNames.quotedColumn(column.name()));
    }
    final List<String> filterColumns = new ArrayList<>();
    final List<Object> filterValues = new ArrayList<>();
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = columnIndex(columns, filter.field());
      if (index < 0) {
        continue; // only declared columns are stored and thus filterable at read time
      }
      filterColumns.add(RdbmsNames.quotedColumn(filter.field()));
      filterValues.add(coerce(columns.get(index).type(), filter.value()));
    }

    final Map<String, Object> params = new HashMap<>();
    params.put("table", RdbmsNames.rowTable(table.cubeId()));
    params.put("columns", selectColumns);
    params.put("filterColumns", filterColumns);
    params.put("filterValues", filterValues);
    params.put("limit", fetch.limit());

    try (SqlSession session = sessionFactory.openSession()) {
      final List<Map<String, Object>> rows =
          session.getMapper(DatasetQueryMapper.class).fetchRows(params);
      final List<TableRow> result = new ArrayList<>(rows.size());
      for (final Map<String, Object> row : rows) {
        result.add(toRow(columns, lowerKeys(row)));
      }
      return result;
    }
  }

  @Override
  public void close() {
    // the session factory (and its DataSource) is owned by the store
  }

  private static TableRow toRow(
      final List<DimensionColumn> columns, final Map<String, Object> row) {
    final Map<String, Object> values = new LinkedHashMap<>();
    for (final DimensionColumn column : columns) {
      values.put(
          column.name(),
          coerceRead(
              column.type(), row.get(RdbmsNames.column(column.name()).toLowerCase(Locale.ROOT))));
    }
    return new TableRow(values);
  }

  private static int columnIndex(final List<DimensionColumn> columns, final String name) {
    for (int i = 0; i < columns.size(); i++) {
      if (columns.get(i).name().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  private static Cell toCell(
      final CompiledDataset dataset,
      final List<DimensionColumn> grain,
      final List<String> meters,
      final Map<String, Object> row) {
    final List<Object> values = new ArrayList<>(grain.size());
    for (final DimensionColumn column : grain) {
      values.add(
          coerceRead(
              column.type(), row.get(RdbmsNames.column(column.name()).toLowerCase(Locale.ROOT))));
    }
    final DimensionKey key = DimensionKey.of(dataset.grain(), values);
    final long windowStart = ((Number) row.get("window_start")).longValue();

    final Map<String, byte[]> accumulators = new LinkedHashMap<>();
    for (final String meter : meters) {
      final Object value = row.get(RdbmsNames.column(meter).toLowerCase(Locale.ROOT));
      if (value instanceof final byte[] bytes) {
        accumulators.put(meter, bytes);
      }
    }
    return new Cell(key, windowStart, accumulators);
  }

  private static Map<String, Object> lowerKeys(final Map<String, Object> row) {
    final Map<String, Object> lowered = new HashMap<>(row.size());
    row.forEach((k, v) -> lowered.put(k.toLowerCase(Locale.ROOT), v));
    return lowered;
  }

  /** Coerces a filter's string value to the grain column's type for binding. */
  private static Object coerce(final DimensionType type, final String value) {
    return switch (type) {
      case STRING -> value;
      case LONG -> Long.parseLong(value);
      case INT -> Integer.parseInt(value);
      case BOOLEAN -> Boolean.parseBoolean(value);
    };
  }

  /** Normalises a JDBC-read value to the type {@link DimensionKey} expects for the column. */
  private static Object coerceRead(final DimensionType type, final Object value) {
    if (value == null) {
      return null;
    }
    return switch (type) {
      case STRING -> value.toString();
      case LONG -> ((Number) value).longValue();
      case INT -> ((Number) value).intValue();
      case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    };
  }
}

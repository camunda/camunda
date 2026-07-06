/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.meter.Agg;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import java.io.IOException;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.ibatis.cursor.Cursor;
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
    try (SqlSession session = sessionFactory.openSession()) {
      final List<Map<String, Object>> rows =
          session.getMapper(DatasetQueryMapper.class).fetch(params(fetch));
      final List<Cell> cells = new ArrayList<>(rows.size());
      for (final Map<String, Object> row : rows) {
        cells.add(toCell(dataset, grain, fetch.meters(), lowerKeys(row)));
      }
      return cells;
    }
  }

  @Override
  public void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();
    // One query, forward-only cursor: MyBatis streams rows in fetchSize batches, so the whole
    // result is never materialized and the executor folds each cell as it arrives.
    try (SqlSession session = sessionFactory.openSession();
        Cursor<Map<String, Object>> cursor =
            session.getMapper(DatasetQueryMapper.class).fetchCursor(params(fetch))) {
      for (final Map<String, Object> row : cursor) {
        sink.accept(toCell(dataset, grain, fetch.meters(), lowerKeys(row)));
      }
    } catch (final IOException e) {
      throw new IllegalStateException("failed to stream cells for " + dataset.name(), e);
    }
  }

  private static final String BUCKET_ALIAS = "wbucket";

  /**
   * The pushed-down / direct read: the store does the reduction (or, for {@code DIRECT}, none) and
   * returns finalized {@link AggregatedRow}s. {@code PUSH_DOWN} runs one {@code GROUP BY dims,
   * derived-bucket} with {@code SUM}/{@code MIN}/{@code MAX} per additive column; {@code DIRECT}
   * selects the stored columns (additive numeric columns or a sketch's {@code _value}) with no
   * aggregation — one row per cell. Each meter's columns are recomposed into its read-facing
   * result.
   */
  @Override
  public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
    return switch (fetch.strategy()) {
      case PUSH_DOWN -> pushDown(fetch);
      case DIRECT -> direct(fetch);
      case STREAM_MERGE ->
          throw new IllegalArgumentException("STREAM_MERGE streams cells, not an AggregatedFetch");
    };
  }

  private List<AggregatedRow> pushDown(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<String> selectColumns = new ArrayList<>();
    final List<String> groupByColumns = new ArrayList<>();
    for (final String dim : fetch.groupBy()) {
      final String column = RdbmsNames.quotedColumn(dim);
      selectColumns.add(column);
      groupByColumns.add(column);
    }
    // Group by the bucket's SELECT alias (both dialects accept it) rather than repeating the
    // bind-parameter expression, which H2 will not match against the projected expression.
    selectColumns.add("(window_start - MOD(window_start, #{granularity})) AS " + BUCKET_ALIAS);
    groupByColumns.add(BUCKET_ALIAS);
    for (final String meter : fetch.meters()) {
      final PushdownSpec<?, ?> spec = requireSpec(dataset, meter, fetch.windowSize());
      for (final PushdownColumn column : spec.columns()) {
        final String physical = RdbmsNames.quotedPushdownColumn(meter, column.suffix());
        selectColumns.add(sqlAgg(column.agg()) + "(" + physical + ") AS " + physical);
      }
    }

    final Map<String, Object> params = aggregatedParams(fetch);
    params.put("selectColumns", selectColumns);
    params.put("groupByColumns", groupByColumns);

    try (SqlSession session = sessionFactory.openSession()) {
      final List<Map<String, Object>> rows =
          session.getMapper(DatasetQueryMapper.class).pushDown(params);
      final List<AggregatedRow> out = new ArrayList<>(rows.size());
      for (final Map<String, Object> raw : rows) {
        final Map<String, Object> row = lowerKeys(raw);
        final long bucket = ((Number) row.get(BUCKET_ALIAS)).longValue();
        out.add(
            new AggregatedRow(
                groupValues(dataset, fetch.groupBy(), row), bucket, measures(dataset, fetch, row)));
      }
      return out;
    }
  }

  private List<AggregatedRow> direct(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    final List<String> dimColumns = new ArrayList<>();
    for (final DimensionColumn column : grain) {
      dimColumns.add(RdbmsNames.quotedColumn(column.name()));
    }
    final List<String> meterColumns = new ArrayList<>();
    for (final String meter : fetch.meters()) {
      final Optional<PushdownSpec<?, ?>> spec = specFor(dataset, meter, fetch.windowSize());
      if (spec.isPresent()) {
        for (final PushdownColumn column : spec.get().columns()) {
          meterColumns.add(RdbmsNames.quotedPushdownColumn(meter, column.suffix()));
        }
      } else {
        meterColumns.add(RdbmsNames.quotedValueColumn(meter));
      }
    }

    final Map<String, Object> params = aggregatedParams(fetch);
    params.put("dimColumns", dimColumns);
    params.put("meterColumns", meterColumns);

    try (SqlSession session = sessionFactory.openSession()) {
      final List<Map<String, Object>> rows =
          session.getMapper(DatasetQueryMapper.class).fetch(params);
      final List<AggregatedRow> out = new ArrayList<>(rows.size());
      for (final Map<String, Object> raw : rows) {
        final Map<String, Object> row = lowerKeys(raw);
        final long windowStart = ((Number) row.get("window_start")).longValue();
        final long bucket = windowStart - Math.floorMod(windowStart, fetch.granularityMs());
        out.add(
            new AggregatedRow(
                groupValues(dataset, fetch.groupBy(), row), bucket, measures(dataset, fetch, row)));
      }
      return out;
    }
  }

  /** The tier/range/filter binds shared by both aggregated reads. */
  private static Map<String, Object> aggregatedParams(final AggregatedFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();
    final List<String> filterColumns = new ArrayList<>();
    final List<Object> filterValues = new ArrayList<>();
    for (final FilterPredicate filter : fetch.filters()) {
      final int index = dataset.grain().indexOf(filter.field());
      if (index < 0) {
        continue;
      }
      filterColumns.add(RdbmsNames.quotedColumn(filter.field()));
      filterValues.add(coerce(grain.get(index).type(), filter.value()));
    }
    final Map<String, Object> params = new HashMap<>();
    params.put("table", RdbmsNames.datasetTable(dataset.cubeId()));
    params.put("filterColumns", filterColumns);
    params.put("filterValues", filterValues);
    params.put("windowSize", fetch.windowSize());
    params.put("fromMs", fetch.fromMs());
    params.put("toMs", fetch.toMs());
    params.put("granularity", fetch.granularityMs());
    return params;
  }

  private static List<Object> groupValues(
      final CompiledDataset dataset, final List<String> groupBy, final Map<String, Object> row) {
    final List<Object> values = new ArrayList<>(groupBy.size());
    for (final String dim : groupBy) {
      final int index = dataset.grain().indexOf(dim);
      final DimensionType type = dataset.grain().columns().get(index).type();
      values.add(coerceRead(type, row.get(RdbmsNames.column(dim).toLowerCase(Locale.ROOT))));
    }
    return values;
  }

  /** Recomposes each requested meter's stored columns / value into its read-facing result. */
  private static Map<String, Object> measures(
      final CompiledDataset dataset, final AggregatedFetch fetch, final Map<String, Object> row) {
    final Map<String, Object> measures = new LinkedHashMap<>();
    for (final String meter : fetch.meters()) {
      final Optional<PushdownSpec<?, ?>> spec = specFor(dataset, meter, fetch.windowSize());
      if (spec.isPresent()) {
        final List<Object> columns = new ArrayList<>();
        for (final PushdownColumn column : spec.get().columns()) {
          columns.add(
              row.get(RdbmsNames.pushdownColumn(meter, column.suffix()).toLowerCase(Locale.ROOT)));
        }
        measures.put(meter, spec.get().recompose().apply(columns));
      } else {
        // DIRECT on a non-pushable meter: serve the denormalized scalar (never PUSH_DOWN).
        final Object value = row.get(RdbmsNames.valueColumn(meter).toLowerCase(Locale.ROOT));
        measures.put(meter, value == null ? 0.0 : ((Number) value).doubleValue());
      }
    }
    return measures;
  }

  private static Optional<PushdownSpec<?, ?>> specFor(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    return compiledMeter(dataset, meter, windowSize).pushdown();
  }

  private static PushdownSpec<?, ?> requireSpec(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    return specFor(dataset, meter, windowSize)
        .orElseThrow(
            () ->
                new IllegalArgumentException("meter '" + meter + "' is not pushable (PUSH_DOWN)"));
  }

  private static CompiledMeter compiledMeter(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    CompiledMeter fallback = null;
    for (final CompiledMeter compiled : dataset.meters()) {
      if (compiled.meterName().equals(meter)) {
        if (compiled.windowMs() == windowSize) {
          return compiled;
        }
        fallback = compiled;
      }
    }
    if (fallback == null) {
      throw new IllegalStateException(
          "no compiled meter '" + meter + "' in '" + dataset.name() + "'");
    }
    return fallback;
  }

  private static String sqlAgg(final Agg agg) {
    return switch (agg) {
      case SUM -> "SUM";
      case MIN -> "MIN";
      case MAX -> "MAX";
    };
  }

  /** The bound query parameters for one fetch — shared by the list and cursor paths. */
  private static Map<String, Object> params(final DatasetFetch fetch) {
    final CompiledDataset dataset = fetch.dataset();
    final List<DimensionColumn> grain = dataset.grain().columns();

    // SQL text uses the quoted identifier (reserved-word safe); the read-back map lookups use the
    // bare, lowercased name to match the JDBC column labels.
    final List<String> dimColumns = new ArrayList<>();
    for (final DimensionColumn column : grain) {
      dimColumns.add(RdbmsNames.quotedColumn(column.name()));
    }
    final List<String> meterColumns = new ArrayList<>();
    for (final String meter : fetch.meters()) {
      // Only sketch/summary meters store a blob (the STREAM_MERGE representation). An additive
      // meter is pushdown-only and has no blob column, so there is nothing to select for it here.
      if (specFor(dataset, meter, fetch.windowSize()).isEmpty()) {
        meterColumns.add(RdbmsNames.quotedBlobColumn(meter));
      }
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
    return params;
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
      final Object value = row.get(RdbmsNames.blobColumn(meter).toLowerCase(Locale.ROOT));
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
      case STRING, TEXT -> value;
      case LONG -> Long.parseLong(value);
      case INT -> Integer.parseInt(value);
      case BOOLEAN -> Boolean.parseBoolean(value);
    };
  }

  private static String clobToString(final Clob clob) {
    try {
      return clob.getSubString(1, (int) clob.length());
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to read CLOB value", e);
    }
  }

  /** Normalises a JDBC-read value to the type {@link DimensionKey} expects for the column. */
  private static Object coerceRead(final DimensionType type, final Object value) {
    if (value == null) {
      return null;
    }
    return switch (type) {
      // A TEXT column comes back as a CLOB on some drivers (e.g. H2) — read it fully to a String;
      // a VARCHAR/text column is already a String.
      case STRING, TEXT -> value instanceof final Clob clob ? clobToString(clob) : value.toString();
      case LONG -> ((Number) value).longValue();
      case INT -> ((Number) value).intValue();
      case BOOLEAN -> value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    };
  }
}

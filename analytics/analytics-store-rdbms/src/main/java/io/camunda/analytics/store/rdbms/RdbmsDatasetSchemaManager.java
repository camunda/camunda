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
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link DatasetSchemaManager}: provisions a cube's {@code dataset_<id>} table or a
 * projected dataset's {@code projection_<id>} table at runtime from its compiled schema (MANAGED
 * mode). DDL is dialect-aware (binary/column types) and idempotent ({@code CREATE TABLE IF NOT
 * EXISTS}); it runs on the MyBatis session's connection so the same datasource/transaction
 * machinery backs schema and data.
 */
public final class RdbmsDatasetSchemaManager implements DatasetSchemaManager {

  private final SqlSessionFactory sessionFactory;
  private final RdbmsDialect dialect;

  public RdbmsDatasetSchemaManager(
      final SqlSessionFactory sessionFactory, final RdbmsDialect dialect) {
    this.sessionFactory = sessionFactory;
    this.dialect = dialect;
  }

  @Override
  public void ensure(final CompiledDataset dataset) {
    execute(parentDdl(dataset), "cube " + dataset.name());
    // Postgres propagates an index declared on the partitioned parent to every (present and future)
    // partition; on H2 the same statement targets the plain table.
    execute(scanIndexDdl(dataset), "cube scan index " + dataset.name());
  }

  /**
   * The {@code CREATE TABLE} for a cube's cell store, pure and dialect-aware so it is unit-testable
   * without a live database.
   *
   * <p>On Postgres (Layer C) the parent is a table partitioned {@code BY RANGE (window_start)}. The
   * partition key must be part of every unique constraint, so the primary key is the composite
   * {@code (cell_key, window_start)} — {@code window_start} is functionally implied by {@code
   * cell_key} (it is encoded into it), so this stays effectively keyed by {@code cell_key}. On H2
   * (no declarative partitioning) it is the plain table with a single-column {@code cell_key}
   * primary key, unchanged from before Layer C.
   */
  String parentDdl(final CompiledDataset dataset) {
    final String dims =
        dataset.grain().columns().stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()) + " " + dialect.columnType(c.type()))
            .collect(Collectors.joining(", "));
    final Map<String, Optional<PushdownSpec<?, ?>>> specs = specsByMeter(dataset);
    final List<String> meterDefs = new ArrayList<>();
    for (final String name : dataset.schema().meterNames()) {
      final Optional<PushdownSpec<?, ?>> spec = specs.getOrDefault(name, Optional.empty());
      if (spec.isPresent()) {
        // Additive: native numeric columns only, aggregated in the engine — no blob (an additive
        // meter is always read via PUSH_DOWN/DIRECT, never streamed and app-merged).
        for (final PushdownColumn column : spec.get().columns()) {
          meterDefs.add(
              RdbmsNames.quotedPushdownColumn(name, column.suffix())
                  + " "
                  + dialect.columnType(column.type()));
        }
      } else {
        // Sketch / summary: the app-mergeable blob (streamed + merged) + a finalized scalar for the
        // DIRECT fast path.
        meterDefs.add(RdbmsNames.quotedBlobColumn(name) + " " + dialect.blobType());
        meterDefs.add(RdbmsNames.quotedValueColumn(name) + " " + dialect.doubleType());
      }
    }
    final String meters = String.join(", ", meterDefs);
    final boolean partitioned = dialect.supportsPartitioning();
    final String primaryKey =
        partitioned ? "PRIMARY KEY (cell_key, window_start)" : "cell_key VARCHAR(4000) PRIMARY KEY";
    final String cellKeyColumn = partitioned ? "cell_key VARCHAR(4000), " : primaryKey + ", ";
    return "CREATE TABLE IF NOT EXISTS "
        + RdbmsNames.datasetTable(dataset.cubeId())
        + " ("
        + cellKeyColumn
        + dims
        + (dims.isEmpty() ? "" : ", ")
        + "window_start BIGINT NOT NULL, window_size BIGINT NOT NULL, "
        + meters
        + (partitioned ? ", " + primaryKey : "")
        + ")"
        + (partitioned ? " PARTITION BY RANGE (window_start)" : "");
  }

  /**
   * The time-leading scan index (Layer A): reads always filter by tier + window range, so leading
   * with {@code (window_size, window_start)} turns a range read into an index seek instead of a
   * full scan. The grain dims follow, for filter locality on grain-column predicates.
   */
  String scanIndexDdl(final CompiledDataset dataset) {
    final String indexDims =
        dataset.grain().columns().stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()))
            .collect(Collectors.joining(", "));
    return "CREATE INDEX IF NOT EXISTS "
        + RdbmsNames.scanIndex(dataset.cubeId())
        + " ON "
        + RdbmsNames.datasetTable(dataset.cubeId())
        + " (window_size, window_start"
        + (indexDims.isEmpty() ? "" : ", " + indexDims)
        + ")";
  }

  /**
   * The {@code CREATE TABLE … PARTITION OF} for the monthly range partition covering {@code
   * windowStart}. Pure and Postgres-only in effect (H2 never calls it). The month boundaries are
   * epoch-ms at UTC: {@code [monthStart, nextMonthStart)}.
   */
  String childPartitionDdl(final CompiledDataset dataset, final long windowStart) {
    final YearMonth month = monthOf(windowStart);
    return "CREATE TABLE IF NOT EXISTS "
        + RdbmsNames.childPartition(dataset.cubeId(), month)
        + " PARTITION OF "
        + RdbmsNames.datasetTable(dataset.cubeId())
        + " FOR VALUES FROM ("
        + monthStartMs(month)
        + ") TO ("
        + monthStartMs(month.plusMonths(1))
        + ")";
  }

  /** The {@code DROP TABLE IF EXISTS} for a single monthly partition (the retention primitive). */
  String dropPartitionDdl(final CompiledDataset dataset, final YearMonth month) {
    return "DROP TABLE IF EXISTS " + RdbmsNames.childPartition(dataset.cubeId(), month);
  }

  /**
   * Creates the monthly range partition covering {@code windowStart} if absent (Layer C, Postgres
   * only; H2 is a no-op). Idempotent ({@code IF NOT EXISTS}) and race-safe: creating a partition
   * takes a lock on the parent, and a concurrent create surfaces as a duplicate-table {@link
   * SQLException}, which is treated as success.
   */
  public void ensurePartition(final CompiledDataset dataset, final long windowStart) {
    if (!dialect.supportsPartitioning()) {
      return;
    }
    executeIdempotent(
        childPartitionDdl(dataset, windowStart),
        "cube partition " + dataset.name() + " " + monthOf(windowStart));
  }

  /**
   * Drops a single monthly partition (Layer C retention primitive, Postgres only; H2 is a no-op).
   * An instant metadata drop — no per-row delete scan.
   */
  public void dropPartition(final CompiledDataset dataset, final YearMonth month) {
    if (!dialect.supportsPartitioning()) {
      return;
    }
    execute(
        dropPartitionDdl(dataset, month), "drop cube partition " + dataset.name() + " " + month);
  }

  /** The UTC calendar month a {@code window_start} (epoch-ms) falls in. */
  static YearMonth monthOf(final long windowStart) {
    return YearMonth.from(Instant.ofEpochMilli(windowStart).atZone(ZoneOffset.UTC));
  }

  private static long monthStartMs(final YearMonth month) {
    return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
  }

  @Override
  public void ensureTable(final CompiledTable table) {
    final String columns =
        table.columns().stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()) + " " + dialect.columnType(c.type()))
            .collect(Collectors.joining(", "));
    final String ddl =
        "CREATE TABLE IF NOT EXISTS "
            + RdbmsNames.rowTable(table.cubeId())
            + " (row_key VARCHAR(4000) PRIMARY KEY"
            + (columns.isEmpty() ? "" : ", " + columns)
            + ")";
    execute(ddl, "table " + table.name());
  }

  /**
   * The pushdown spec per meter name (tier-independent, so the first compiled tier suffices). Empty
   * for a meter with no compiled tier or a non-pushable (blob) meter.
   */
  static Map<String, Optional<PushdownSpec<?, ?>>> specsByMeter(final CompiledDataset dataset) {
    final Map<String, Optional<PushdownSpec<?, ?>>> specs = new LinkedHashMap<>();
    for (final CompiledMeter meter : dataset.meters()) {
      specs.putIfAbsent(meter.meterName(), meter.pushdown());
    }
    return specs;
  }

  private void execute(final String ddl, final String what) {
    try (SqlSession session = sessionFactory.openSession();
        Statement statement = session.getConnection().createStatement()) {
      statement.execute(ddl);
      session.commit(true);
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to provision " + what, e);
    }
  }

  /**
   * Runs a {@code CREATE … IF NOT EXISTS} that races with concurrent writers of the same month. The
   * parent lock serialises partition creation, so a loser sees a duplicate/already-exists error
   * even though the statement says {@code IF NOT EXISTS} (Postgres does not suppress the race
   * window); that outcome is success — the partition exists.
   */
  private void executeIdempotent(final String ddl, final String what) {
    try (SqlSession session = sessionFactory.openSession();
        Statement statement = session.getConnection().createStatement()) {
      statement.execute(ddl);
      session.commit(true);
    } catch (final SQLException e) {
      if (isAlreadyExists(e)) {
        return;
      }
      throw new IllegalStateException("failed to provision " + what, e);
    }
  }

  /**
   * Whether a {@link SQLException} is a benign "relation already exists" from a lost
   * partition-create race. Postgres raises SQLState {@code 42P07} (duplicate_table); the message
   * check is a portable backstop.
   */
  private static boolean isAlreadyExists(final SQLException e) {
    for (SQLException current = e; current != null; current = current.getNextException()) {
      if ("42P07".equals(current.getSQLState())) {
        return true;
      }
      final String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("already exists")) {
        return true;
      }
    }
    return false;
  }
}

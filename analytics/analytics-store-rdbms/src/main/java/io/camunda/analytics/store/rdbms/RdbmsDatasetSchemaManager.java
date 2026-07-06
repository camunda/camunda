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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    final String ddl =
        "CREATE TABLE IF NOT EXISTS "
            + RdbmsNames.datasetTable(dataset.cubeId())
            + " (cell_key VARCHAR(4000) PRIMARY KEY, "
            + dims
            + (dims.isEmpty() ? "" : ", ")
            + "window_start BIGINT NOT NULL, window_size BIGINT NOT NULL, "
            + meters
            + ")";
    execute(ddl, "cube " + dataset.name());

    // Time-leading scan index: reads always filter by tier + window range, so leading with
    // (window_size, window_start) turns a range read into an index seek instead of a full scan.
    // The grain dims follow, for filter locality on grain-column predicates.
    final String indexDims =
        dataset.grain().columns().stream()
            .map(c -> RdbmsNames.quotedColumn(c.name()))
            .collect(Collectors.joining(", "));
    final String indexDdl =
        "CREATE INDEX IF NOT EXISTS "
            + RdbmsNames.scanIndex(dataset.cubeId())
            + " ON "
            + RdbmsNames.datasetTable(dataset.cubeId())
            + " (window_size, window_start"
            + (indexDims.isEmpty() ? "" : ", " + indexDims)
            + ")";
    execute(indexDdl, "cube scan index " + dataset.name());
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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledProjection;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import java.sql.SQLException;
import java.sql.Statement;
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
            .map(c -> RdbmsNames.column(c.name()) + " " + dialect.columnType(c.type()))
            .collect(Collectors.joining(", "));
    final String meters =
        dataset.schema().meterNames().stream()
            .map(name -> RdbmsNames.column(name) + " " + dialect.blobType())
            .collect(Collectors.joining(", "));
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
  }

  @Override
  public void ensureProjection(final CompiledProjection projection) {
    final String columns =
        projection.columns().stream()
            .map(c -> RdbmsNames.column(c.name()) + " " + dialect.columnType(c.type()))
            .collect(Collectors.joining(", "));
    final String ddl =
        "CREATE TABLE IF NOT EXISTS "
            + RdbmsNames.projectionTable(projection.cubeId())
            + " (row_key VARCHAR(4000) PRIMARY KEY"
            + (columns.isEmpty() ? "" : ", " + columns)
            + ")";
    execute(ddl, "projection " + projection.name());
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

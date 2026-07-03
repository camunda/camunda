/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import java.sql.Connection;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Provisions the fixed <b>metadata plane</b> schema — the dataset specs and meter-id tables — via
 * Liquibase, mirroring how {@code db/rdbms} manages its schema. Unlike the per-dataset serving
 * tables (whose columns are only known at runtime and are created by hand-built DDL), the metadata
 * schema is fixed and versioned, so migrations across releases are authored as changesets.
 */
final class AnalyticsMetadataSchema {

  private static final String CHANGELOG = "db/changelog/analytics-metadata/changelog-master.xml";

  private final DataSource dataSource;

  public AnalyticsMetadataSchema(final DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Applies the changelog (idempotent — Liquibase skips already-applied changesets). */
  public void migrate() {
    try (Connection connection = dataSource.getConnection()) {
      final Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (Liquibase liquibase =
          new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
        liquibase.update(new Contexts(), new LabelExpression());
      }
    } catch (final Exception e) {
      throw new IllegalStateException("failed to migrate the analytics metadata schema", e);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * The RDBMS backend of the serving store: a MyBatis {@link SqlSessionFactory} over the given {@link
 * DataSource}, with the dialect detected once from the JDBC driver. Bundles the three neutral seams
 * — {@link RdbmsDatasetSchemaManager}, {@link RdbmsDatasetWriter}, {@link RdbmsDatasetQueryClient}
 * — so the wiring layer selects it for {@code DatabaseType.RDBMS} and hands it to the pipeline.
 */
public final class RdbmsDatasetStore implements DatasetStore {

  private final SqlSessionFactory sessionFactory;
  private final RdbmsDialect dialect;
  private final RdbmsDatasetSchemaManager schemaManager;
  private final RdbmsDatasetWriter writer;

  public RdbmsDatasetStore(final DataSource dataSource) {
    this(dataSource, ServingWriteMetrics.NOOP);
  }

  public RdbmsDatasetStore(final DataSource dataSource, final ServingWriteMetrics metrics) {
    this.dialect = detectDialect(dataSource);
    final Environment environment =
        new Environment("analytics-rdbms", new JdbcTransactionFactory(), dataSource);
    final Configuration configuration = new Configuration(environment);
    configuration.addMapper(DatasetQueryMapper.class);
    this.sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    // Shared so the writer can ensure a cell's time partition (Layer C) on the same seam that
    // provisions the parent table.
    this.schemaManager = new RdbmsDatasetSchemaManager(sessionFactory, dialect);
    this.writer = new RdbmsDatasetWriter(sessionFactory, dialect, schemaManager, metrics);
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    return schemaManager;
  }

  @Override
  public VersionedDatasetWriter writer() {
    return writer;
  }

  @Override
  public DatasetQueryClient queryClient() {
    return new RdbmsDatasetQueryClient(sessionFactory);
  }

  public RdbmsDialect dialect() {
    return dialect;
  }

  @Override
  public void close() {
    writer.close();
  }

  private static RdbmsDialect detectDialect(final DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      return RdbmsDialect.fromProductName(connection.getMetaData().getDatabaseProductName());
    } catch (final SQLException e) {
      throw new IllegalStateException("failed to detect the SQL dialect", e);
    }
  }
}

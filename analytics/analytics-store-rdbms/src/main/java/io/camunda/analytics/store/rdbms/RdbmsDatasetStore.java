/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.DatasetWriter;
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
  private final RdbmsDatasetWriter writer;

  public RdbmsDatasetStore(final DataSource dataSource) {
    this.dialect = detectDialect(dataSource);
    final Environment environment =
        new Environment("analytics-rdbms", new JdbcTransactionFactory(), dataSource);
    final Configuration configuration = new Configuration(environment);
    configuration.addMapper(DatasetQueryMapper.class);
    this.sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    this.writer = new RdbmsDatasetWriter(sessionFactory, dialect);
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    return new RdbmsDatasetSchemaManager(sessionFactory, dialect);
  }

  @Override
  public DatasetWriter writer() {
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

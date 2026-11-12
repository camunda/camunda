/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.rdbms.schema.StateSchemaManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.jspecify.annotations.Nullable;

/**
 * The relational database that holds the partition state. One instance exists per data source and
 * is shared by all partitions of a broker.
 *
 * <p>Opening it creates or updates the schema, then sets up MyBatis. The caller owns the data
 * source: it is used as given and never closed here.
 */
public final class RdbmsStorage {

  private static final String ENVIRONMENT_ID = "zeebe-state";

  private final DataSource dataSource;
  private final SqlSessionFactory sqlSessionFactory;

  private RdbmsStorage(final DataSource dataSource, final SqlSessionFactory sqlSessionFactory) {
    this.dataSource = dataSource;
    this.sqlSessionFactory = sqlSessionFactory;
  }

  /**
   * Creates or updates the schema and prepares the database access.
   *
   * @param dataSource an H2 or PostgreSQL data source; H2 is only meant for tests
   * @param prefix the start of every table name, may be empty
   * @throws IllegalArgumentException if the database is neither H2 nor PostgreSQL
   * @throws io.camunda.zeebe.db.rdbms.schema.StateSchemaMigrationException if the schema could not
   *     be created or updated
   */
  public static RdbmsStorage open(final DataSource dataSource, final String prefix) {
    Objects.requireNonNull(dataSource, "must specify a data source");
    Objects.requireNonNull(prefix, "must specify a prefix, which may be empty");

    final var databaseId = databaseId(dataSource);
    new StateSchemaManager(dataSource, prefix).migrate();
    return new RdbmsStorage(
        dataSource,
        new SqlSessionFactoryBuilder().build(configuration(dataSource, prefix, databaseId)));
  }

  /**
   * @return which database's SQL statements are used, {@code h2} or {@code postgresql}
   */
  public String databaseId() {
    return sqlSessionFactory.getConfiguration().getDatabaseId();
  }

  /**
   * Borrows a connection with auto-commit off. The caller commits or rolls back, then closes it.
   */
  Connection borrowConnection() {
    return borrow(false);
  }

  /**
   * Borrows a connection with auto-commit on, for a read of one statement. The caller closes it.
   */
  Connection borrowAutoCommitConnection() {
    return borrow(true);
  }

  SqlSessionFactory sqlSessionFactory() {
    return sqlSessionFactory;
  }

  private Connection borrow(final boolean autoCommit) {
    @Nullable Connection borrowed = null;
    try {
      borrowed = dataSource.getConnection();
      borrowed.setAutoCommit(autoCommit);
      return borrowed;
    } catch (final SQLException e) {
      if (borrowed != null) {
        try {
          borrowed.close();
        } catch (final SQLException closeError) {
          e.addSuppressed(closeError);
        }
      }
      throw SqlErrors.translate(e);
    }
  }

  /** Maps the database product name to the id that picks its statements, as the history does. */
  private static String databaseId(final DataSource dataSource) {
    final var productNames = new Properties();
    productNames.put("H2", "h2");
    productNames.put("PostgreSQL", "postgresql");
    final var provider = new VendorDatabaseIdProvider();
    provider.setProperties(productNames);

    final var databaseId = provider.getDatabaseId(dataSource);
    if (databaseId == null) {
      throw new IllegalArgumentException(
          "Expected an H2 or PostgreSQL database to keep the partition state, but got another one");
    }
    return databaseId;
  }

  private static Configuration configuration(
      final DataSource dataSource, final String prefix, final String databaseId) {
    // We open, commit, roll back and close the connections ourselves, and hand them to MyBatis
    // only to run statements on. So MyBatis must leave them open.
    final var transactionFactory = new ManagedTransactionFactory();
    final var transactionProperties = new Properties();
    transactionProperties.put("closeConnection", "false");
    transactionFactory.setProperties(transactionProperties);

    final var configuration =
        new Configuration(new Environment(ENVIRONMENT_ID, transactionFactory, dataSource));
    configuration.setDatabaseId(databaseId);
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.setCacheEnabled(false);
    configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);

    final var variables = new Properties();
    variables.put("prefix", prefix);
    configuration.setVariables(variables);
    // loads StateMapper.xml from next to the interface; it needs the variables to resolve ${prefix}
    configuration.addMapper(StateMapper.class);
    return configuration;
  }
}

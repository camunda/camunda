/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import liquibase.Scope;
import liquibase.analytics.configuration.AnalyticsArgs;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The PostgreSQL database of the relational engine state tests, with the engine state schema
 * applied. Tests share it and stay isolated by writing to their own {@link #nextPartitionId()
 * partition}.
 *
 * <p>By default the database is a throwaway Testcontainers PostgreSQL. To inspect the rows after a
 * run, point the tests at your own database with {@code
 * -Dzeebe.engine.rdbms.jdbcUrl=jdbc:postgresql:
 * //localhost:5432/postgres?user=postgres&password=zeebe}: the Liquibase changelog adds what is
 * missing and committed rows are kept.
 */
final class EngineStateDatabase implements AutoCloseable {

  private static final String EXTERNAL_JDBC_URL_PROPERTY = "zeebe.engine.rdbms.jdbcUrl";

  private final PGSimpleDataSource dataSource;
  private final @Nullable PostgreSQLContainer postgres;
  private final AtomicInteger partitionIds = new AtomicInteger();
  private final Map<Integer, PartitionWriteBehind> writeBehind = new ConcurrentHashMap<>();
  private final ObservedDataSource observed;
  private final DataSource flushing;
  private final SqlSessionFactory sessionFactory;

  private EngineStateDatabase(
      final PGSimpleDataSource dataSource, final @Nullable PostgreSQLContainer postgres) {
    this.dataSource = dataSource;
    this.postgres = postgres;
    observed = new ObservedDataSource(dataSource);
    flushing = new FlushingDataSource(dataSource, this::awaitFlushed);
    sessionFactory = EngineStateMyBatis.createSqlSessionFactory(observed.dataSource());
  }

  /** Starts the database, or connects to the external one, and applies the schema. */
  static EngineStateDatabase start() throws Exception {
    final var dataSource = new PGSimpleDataSource();
    PostgreSQLContainer postgres = null;
    final var externalJdbcUrl = System.getProperty(EXTERNAL_JDBC_URL_PROPERTY);
    if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
      dataSource.setUrl(externalJdbcUrl);
    } else {
      postgres = new PostgreSQLContainer(DockerImageName.parse("postgres").withTag("15-alpine"));
      postgres.start();
      dataSource.setUrl(postgres.getJdbcUrl());
      dataSource.setUser(postgres.getUsername());
      dataSource.setPassword(postgres.getPassword());
    }

    final var database = new EngineStateDatabase(dataSource, postgres);
    // only applies the changesets an external database that keeps its tables doesn't have yet
    database.applySchema();
    database.continueAfterExistingPartitions();
    return database;
  }

  /** For reading the tables: a connection first waits for the write-behind sessions to flush. */
  DataSource dataSource() {
    return flushing;
  }

  /** The pool the {@link #sessionFactory()} borrows its connections from. */
  ObservedDataSource observed() {
    return observed;
  }

  /** The session factory of the database, built once like in the broker. */
  SqlSessionFactory sessionFactory() {
    return sessionFactory;
  }

  /** A session that writes to the database on commit. */
  EntitySession entitySession(final JdbcTransactionContext context, final int partitionId) {
    return entitySession(context, partitionId, sessionFactory);
  }

  /**
   * The session of a partition on the context, registered like {@code RdbmsStateStores} does. In
   * the write-behind mode it gets a new store, after the store of an earlier session of the
   * partition flushed and closed, like a restarted partition.
   */
  EntitySession entitySession(
      final StateMode mode, final JdbcTransactionContext context, final int partitionId) {
    return entitySession(mode, context, partitionId, sessionFactory);
  }

  EntitySession entitySession(
      final StateMode mode,
      final JdbcTransactionContext context,
      final int partitionId,
      final SqlSessionFactory factory) {
    if (mode == StateMode.SYNCHRONOUS) {
      return entitySession(context, partitionId, factory);
    }
    final var previous = writeBehind.remove(partitionId);
    if (previous != null) {
      previous.store().close();
    }
    final var store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, factory),
            WriteBehindConfiguration.defaults().withKeyIndexColumnFamilies(Set.of()));
    final var partition = new PartitionWriteBehind(store, new GroupCache(10_000));
    writeBehind.put(partitionId, partition);
    final var session = new EntitySession(context, partitionId, factory, store, partition.groups());
    context.register(session);
    return session;
  }

  /**
   * Waits until the database holds every committed row of the write-behind sessions, for tests that
   * read the tables directly.
   */
  void awaitFlushed() {
    writeBehind.values().forEach(partition -> partition.store().awaitFlushed());
  }

  static EntitySession entitySession(
      final JdbcTransactionContext context,
      final int partitionId,
      final SqlSessionFactory sessionFactory) {
    final var session = new EntitySession(context, partitionId, sessionFactory);
    context.register(session);
    return session;
  }

  /** A partition no test has written to yet. */
  int nextPartitionId() {
    return partitionIds.incrementAndGet();
  }

  /** Counts the committed rows of the partition in the table that match the SQL condition. */
  long countRows(final String table, final int partitionId, final String condition) {
    awaitFlushed();
    try (final var connection = dataSource.getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE partition_id = ? AND " + condition)) {
      statement.setInt(1, partitionId);
      try (final ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  static boolean tableExists(final Connection connection, final String table) throws SQLException {
    try (final var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
      statement.setString(1, table);
      try (final var result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }

  @Override
  public void close() {
    writeBehind.values().forEach(partition -> partition.store().close());
    if (postgres != null) {
      postgres.stop();
    }
  }

  /** Continues after the partitions of earlier runs, so their rows don't leak into the tests. */
  private void continueAfterExistingPartitions() throws SQLException {
    int highestPartitionId = 0;
    try (final var connection = dataSource.getConnection();
        final var statement = connection.createStatement()) {
      for (final String table : RdbmsEngineStateSchema.TABLES) {
        try (final var result =
            statement.executeQuery("SELECT COALESCE(MAX(partition_id), 0) FROM " + table)) {
          result.next();
          highestPartitionId = Math.max(highestPartitionId, result.getInt(1));
        }
      }
    }
    partitionIds.set(highestPartitionId);
  }

  /** Applies the engine state changelog, tracked in the same tables as in the broker. */
  private void applySchema() throws Exception {
    // closing the database closes the connection
    try (final var database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(dataSource.getConnection()))) {
      database.setDatabaseChangeLogTableName(RdbmsEngineStateSchema.DATABASE_CHANGELOG_TABLE);
      database.setDatabaseChangeLogLockTableName(
          RdbmsEngineStateSchema.DATABASE_CHANGELOG_LOCK_TABLE);
      Scope.child(
          Map.of(
              Scope.Attr.resourceAccessor.name(),
              new ClassLoaderResourceAccessor(),
              AnalyticsArgs.ENABLED.getKey(),
              false),
          () ->
              new CommandScope(UpdateCommandStep.COMMAND_NAME)
                  .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                  .addArgumentValue(
                      UpdateCommandStep.CHANGELOG_FILE_ARG, RdbmsEngineStateSchema.CHANGELOG)
                  .execute());
    }
  }
}

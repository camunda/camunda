/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.jspecify.annotations.Nullable;

/**
 * Runs the {@link EngineStateMapper} statements on the connection of a {@link
 * JdbcTransactionContext}, without Spring.
 *
 * <p>The context owns the connection and its transaction. Sessions are therefore only opened on a
 * given connection, with a {@link ManagedTransactionFactory} that doesn't close it: MyBatis'
 * managed transaction never commits or rolls back, and closing a session leaves the connection
 * open. The first-level cache is limited to a statement, so a session never serves rows that a
 * flush changed since.
 *
 * <p>One {@link SqlSessionFactory} is built per database product, on first use.
 */
final class EngineStateMyBatis {

  private static final String MAPPER_RESOURCE = "db/mybatis/engine-state/EngineStateMapper.xml";

  /**
   * Database product name prefixes to the {@code databaseId} of vendor specific statements, like
   * MyBatis' {@code VendorDatabaseIdProvider} resolves them. That provider takes a {@code
   * DataSource} and closes the connection it gets, so it can't be used on the context's connection.
   */
  private static final Map<String, String> DATABASE_IDS =
      Map.of("PostgreSQL", "postgresql", "H2", "h2");

  private static final Map<String, SqlSessionFactory> FACTORIES = new ConcurrentHashMap<>();

  private EngineStateMyBatis() {}

  /** Runs the given reads in a session on the given connection. */
  static <T> T read(final Connection connection, final MapperCall<T> call) throws SQLException {
    try (final var session = openSession(connection, ExecutorType.SIMPLE)) {
      return call.apply(session.getMapper(EngineStateMapper.class));
    } catch (final PersistenceException e) {
      throw unwrap(e);
    }
  }

  /**
   * Opens a session on the given connection. Closing it neither commits, rolls back nor closes the
   * connection; a batch session discards statements that weren't flushed.
   */
  static SqlSession openSession(final Connection connection, final ExecutorType executorType)
      throws SQLException {
    return factory(connection).openSession(executorType, connection);
  }

  /**
   * MyBatis wraps every {@link SQLException} in a runtime exception. The JDBC transaction tells
   * retryable errors apart only by the SQL exception, so it must get that one.
   */
  static SQLException unwrap(final PersistenceException exception) {
    for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof final SQLException sqlException) {
        return sqlException;
      }
    }
    throw exception;
  }

  private static SqlSessionFactory factory(final Connection connection) throws SQLException {
    final var productName = connection.getMetaData().getDatabaseProductName();
    return FACTORIES.computeIfAbsent(productName, EngineStateMyBatis::buildFactory);
  }

  private static SqlSessionFactory buildFactory(final String productName) {
    final var transactionFactory = new ManagedTransactionFactory();
    final var transactionProperties = new Properties();
    transactionProperties.setProperty("closeConnection", "false");
    transactionFactory.setProperties(transactionProperties);

    // sessions are always opened on a given connection, so the data source is never used
    final var environment =
        new Environment("engine-state", transactionFactory, new UnpooledDataSource());
    final var configuration = new Configuration(environment);
    configuration.setDatabaseId(databaseId(productName));
    configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
    configuration.setCacheEnabled(false);

    try (final var mapperXml =
        EngineStateMyBatis.class.getClassLoader().getResourceAsStream(MAPPER_RESOURCE)) {
      if (mapperXml == null) {
        throw new IllegalStateException("Missing MyBatis mapper resource " + MAPPER_RESOURCE);
      }
      new XMLMapperBuilder(
              mapperXml, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
          .parse();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!configuration.hasMapper(EngineStateMapper.class)) {
      // binding the namespace by class name fails if MyBatis' class loader can't see the engine
      configuration.addMapper(EngineStateMapper.class);
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  private static @Nullable String databaseId(final String productName) {
    return DATABASE_IDS.entrySet().stream()
        .filter(entry -> productName.startsWith(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  @FunctionalInterface
  interface MapperCall<T> {
    T apply(EngineStateMapper mapper) throws SQLException;
  }
}

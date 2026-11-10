/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.jspecify.annotations.Nullable;

/**
 * The MyBatis configuration of the engine state mappers, for sessions that run on the connection of
 * a {@link JdbcTransactionContext}, without Spring.
 *
 * <p>The context owns the connection and its transaction. Sessions are therefore only opened on a
 * given connection, with a {@link ManagedTransactionFactory} that doesn't close it: MyBatis'
 * managed transaction never commits or rolls back, and closing a session leaves the connection
 * open. The first-level cache is limited to a statement, so a session never serves rows that a
 * flush changed since.
 */
public final class EngineStateMyBatis {

  private static final List<Class<?>> MAPPERS =
      List.of(
          ElementInstanceMapper.class, ProcessDefinitionMapper.class, PartitionStateMapper.class);

  /**
   * Database product name prefixes to the {@code databaseId} of vendor specific statements, like
   * MyBatis' {@code VendorDatabaseIdProvider} resolves them.
   */
  private static final Map<String, String> DATABASE_IDS =
      Map.of("PostgreSQL", "postgresql", "H2", "h2");

  private EngineStateMyBatis() {}

  /**
   * Builds the factory of all sessions on the given database. Build it once: it parses the mappers
   * and resolves the database product, which takes a connection from the data source.
   */
  public static SqlSessionFactory createSqlSessionFactory(final DataSource dataSource) {
    final String productName;
    try (final var connection = dataSource.getConnection()) {
      productName = connection.getMetaData().getDatabaseProductName();
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to resolve the engine state database product", e);
    }

    final var transactionFactory = new ManagedTransactionFactory();
    final var transactionProperties = new Properties();
    transactionProperties.setProperty("closeConnection", "false");
    transactionFactory.setProperties(transactionProperties);

    // sessions are always opened on a given connection, so the environment's data source is unused
    final var environment = new Environment("engine-state", transactionFactory, dataSource);
    final var configuration = new Configuration(environment);
    configuration.setDatabaseId(databaseId(productName));
    configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
    configuration.setCacheEnabled(false);

    for (final var mapper : MAPPERS) {
      final var resource = "db/mybatis/engine-state/" + mapper.getSimpleName() + ".xml";
      try (final var mapperXml =
          EngineStateMyBatis.class.getClassLoader().getResourceAsStream(resource)) {
        if (mapperXml == null) {
          throw new IllegalStateException("Missing MyBatis mapper resource " + resource);
        }
        new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments())
            .parse();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      if (!configuration.hasMapper(mapper)) {
        // binding the namespace by class name fails if MyBatis' class loader can't see the engine
        configuration.addMapper(mapper);
      }
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
}

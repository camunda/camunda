/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.jspecify.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Databases with the mailbox schema, migrated from the real changelog as the broker does. */
final class TestDatabases {

  private static @Nullable PostgreSQLContainer postgres;

  private TestDatabases() {}

  /** A fresh in-memory H2 database. */
  static DataSource h2() {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:mailbox-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return migrated(dataSource);
  }

  /**
   * A PostgreSQL database in a container that the tests of the JVM share. Skips the test if Docker
   * is not available.
   */
  static synchronized DataSource postgres() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "PostgreSQL tests need Docker to start the database");
    if (postgres == null) {
      final var container =
          new PostgreSQLContainer(DockerImageName.parse("postgres").withTag("15-alpine"));
      container.start();
      postgres = container;
    }
    final var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgres.getJdbcUrl());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return migrated(dataSource);
  }

  private static DataSource migrated(final DataSource dataSource) {
    try (final Connection connection = dataSource.getConnection()) {
      final var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      new Liquibase(RdbmsMailboxSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database)
          .update();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to migrate the mailbox schema", e);
    }
    return dataSource;
  }
}

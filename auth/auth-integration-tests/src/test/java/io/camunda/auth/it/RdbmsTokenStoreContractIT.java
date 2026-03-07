/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import io.camunda.auth.domain.contract.AbstractTokenStoreContractTest;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.persist.rdbms.RdbmsTokenStoreAdapter;
import io.camunda.auth.persist.rdbms.TokenExchangeAuditMapper;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Contract test for {@link RdbmsTokenStoreAdapter} running against a real PostgreSQL instance via
 * Testcontainers.
 */
@Testcontainers
class RdbmsTokenStoreContractIT extends AbstractTokenStoreContractTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static SqlSession session;

  @BeforeAll
  static void setUp() throws Exception {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setURL(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());

    runLiquibaseMigrations(dataSource);

    final SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(dataSource);
    session = sqlSessionFactory.openSession(true);
  }

  @AfterAll
  static void tearDown() {
    if (session != null) {
      session.close();
    }
  }

  @Override
  protected TokenStorePort createStore() {
    return new RdbmsTokenStoreAdapter(session.getMapper(TokenExchangeAuditMapper.class));
  }

  private static void runLiquibaseMigrations(final DataSource dataSource) throws Exception {
    try (var connection = dataSource.getConnection()) {
      final var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (var liquibase =
          new Liquibase(
              "db/changelog/auth/auth-changelog-master.xml",
              new ClassLoaderResourceAccessor(),
              database)) {
        liquibase.update("");
      }
    }
  }

  private static SqlSessionFactory createSqlSessionFactory(final DataSource dataSource) {
    final var transactionFactory = new JdbcTransactionFactory();
    final var environment = new Environment("test", transactionFactory, dataSource);
    final var configuration = new Configuration(environment);
    configuration.addMapper(TokenExchangeAuditMapper.class);
    return new SqlSessionFactoryBuilder().build(configuration);
  }
}

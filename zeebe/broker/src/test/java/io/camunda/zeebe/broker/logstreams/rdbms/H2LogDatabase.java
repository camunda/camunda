/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;

/**
 * A fresh in-memory H2 database with the real log storage schema, and a session factory built as
 * the broker builds it.
 */
final class H2LogDatabase {

  private H2LogDatabase() {}

  static SqlSessionFactory create(final String prefix) throws Exception {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:log-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    try (final var connection = dataSource.getConnection()) {
      final var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      final var liquibase =
          new Liquibase(
              RdbmsLogStorageSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database);
      liquibase.setChangeLogParameter("prefix", prefix);
      liquibase.update();
    }

    return MyBatisLogBlockRepository.createSessionFactory(dataSource, prefix);
  }
}

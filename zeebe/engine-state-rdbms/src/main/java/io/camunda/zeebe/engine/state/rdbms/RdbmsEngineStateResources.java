/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * What all partitions of a broker share for the relational engine state: the connection pool and
 * the MyBatis session factory built on it, both created once per broker. The session factory stays
 * internal to this module, so callers never depend on MyBatis.
 */
public final class RdbmsEngineStateResources implements AutoCloseable {

  private final DataSource dataSource;
  private final SqlSessionFactory sessionFactory;

  private RdbmsEngineStateResources(
      final DataSource dataSource, final SqlSessionFactory sessionFactory) {
    this.dataSource = dataSource;
    this.sessionFactory = sessionFactory;
  }

  /** Builds the session factory for the given pool. */
  public static RdbmsEngineStateResources create(final DataSource dataSource) {
    return new RdbmsEngineStateResources(
        dataSource, EngineStateMyBatis.createSqlSessionFactory(dataSource));
  }

  public DataSource dataSource() {
    return dataSource;
  }

  SqlSessionFactory sessionFactory() {
    return sessionFactory;
  }

  /** Closes the pool. */
  @Override
  public void close() throws Exception {
    if (dataSource instanceof final AutoCloseable closeable) {
      closeable.close();
    }
  }
}

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
 * What the partitions share of the relational engine state: the connection pool and the MyBatis
 * session factory built on it. Both are created once per broker.
 */
public record RdbmsEngineState(DataSource dataSource, SqlSessionFactory sessionFactory) {

  /** Builds the session factory for the given pool. */
  public static RdbmsEngineState of(final DataSource dataSource) {
    return new RdbmsEngineState(dataSource, EngineStateMyBatis.createSqlSessionFactory(dataSource));
  }
}

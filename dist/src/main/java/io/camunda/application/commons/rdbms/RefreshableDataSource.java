/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.secrets.RefreshableClient;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jspecify.annotations.NullMarked;

/**
 * The data source every consumer of a physical tenant's database holds: MyBatis for the readers and
 * the exporter, Liquibase and the health indicator. Each call goes to the pool that is current at
 * that moment, so a pool replaced after a secret refresh reaches all of them at once.
 */
@NullMarked
final class RefreshableDataSource implements DataSource {

  private final RefreshableClient<RdbmsConnection, HikariDataSource> pool;

  RefreshableDataSource(final RefreshableClient<RdbmsConnection, HikariDataSource> pool) {
    this.pool = pool;
  }

  RefreshableClient<RdbmsConnection, HikariDataSource> pool() {
    return pool;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return pool.client().getConnection();
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    return pool.client().getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return pool.client().getLogWriter();
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    pool.client().setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    pool.client().setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return pool.client().getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return pool.client().getParentLogger();
  }

  @Override
  public <T> T unwrap(final Class<T> type) throws SQLException {
    return type.isInstance(this) ? type.cast(this) : pool.client().unwrap(type);
  }

  @Override
  public boolean isWrapperFor(final Class<?> type) throws SQLException {
    return type.isInstance(this) || pool.client().isWrapperFor(type);
  }
}

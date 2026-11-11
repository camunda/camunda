/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * The data source that tests use to read the tables directly. Every connection it hands out waits
 * until the write-behind sessions flushed, so the tables hold what the tests committed.
 */
final class FlushingDataSource implements DataSource {

  private final DataSource delegate;
  private final Runnable awaitFlushed;

  FlushingDataSource(final DataSource delegate, final Runnable awaitFlushed) {
    this.delegate = delegate;
    this.awaitFlushed = awaitFlushed;
  }

  @Override
  public Connection getConnection() throws SQLException {
    awaitFlushed.run();
    return delegate.getConnection();
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    awaitFlushed.run();
    return delegate.getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return delegate.getLogWriter();
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    delegate.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    delegate.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return delegate.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() {
    try {
      return delegate.getParentLogger();
    } catch (final java.sql.SQLFeatureNotSupportedException e) {
      return Logger.getGlobal();
    }
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return delegate.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }
}

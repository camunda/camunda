/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.HikariDataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jspecify.annotations.NullMarked;

/**
 * A physical tenant's connection pool that can be closed while the node keeps running and starts
 * again on its next connection. A closed {@link HikariDataSource} cannot be restarted, so a release
 * replaces it with a new, unstarted one; whoever holds this data source never sees the swap.
 */
@NullMarked
final class ReleasablePool implements DataSource, AutoCloseable {

  private final Supplier<HikariDataSource> factory;
  private volatile HikariDataSource current;

  ReleasablePool(final Supplier<HikariDataSource> factory) {
    this.factory = factory;
    current = factory.get();
  }

  /** Whether the pool has been started since it was built or last released. */
  boolean isRunning() {
    return current.isRunning();
  }

  /** Closes the pool's connections; the next connection starts a new pool. */
  synchronized void release() {
    final var released = current;
    current = factory.get();
    RdbmsDataSources.closeQuietly(released);
  }

  String poolName() {
    return current.getPoolName();
  }

  @Override
  public synchronized void close() {
    RdbmsDataSources.closeQuietly(current);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return current.getConnection();
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    return current.getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return current.getLogWriter();
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    current.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    current.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return current.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return current.getParentLogger();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    return current.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return iface.isInstance(this) || current.isWrapperFor(iface);
  }
}

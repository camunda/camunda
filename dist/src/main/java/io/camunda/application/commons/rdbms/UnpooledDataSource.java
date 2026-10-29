/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import com.zaxxer.hikari.util.DriverDataSource;
import io.camunda.configuration.data.secondarystorage.Rdbms;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * Opens a new connection to a physical tenant's database on every use. The driver is only looked up
 * on the first use, so that a URL no driver on the classpath accepts fails where it is used rather
 * than where the node is configured.
 */
@NullMarked
final class UnpooledDataSource implements DataSource {

  private final RdbmsConnection connection;
  private volatile @Nullable DataSource driver;

  UnpooledDataSource(final Rdbms rdbms) {
    this(RdbmsConnection.configured(rdbms));
  }

  UnpooledDataSource(final RdbmsConnection connection) {
    this.connection = connection;
  }

  @Override
  public Connection getConnection() throws SQLException {
    return driver().getConnection();
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    return driver().getConnection(username, password);
  }

  @Override
  public @Nullable PrintWriter getLogWriter() {
    return null;
  }

  @Override
  public void setLogWriter(final PrintWriter out) {}

  @Override
  public void setLoginTimeout(final int seconds) {}

  @Override
  public int getLoginTimeout() {
    return 0;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    if (iface.isInstance(this)) {
      return iface.cast(this);
    }
    throw new SQLException("Not a wrapper for " + iface.getName());
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) {
    return iface.isInstance(this);
  }

  private DataSource driver() {
    var resolved = driver;
    if (resolved == null) {
      synchronized (this) {
        resolved = driver;
        if (resolved == null) {
          resolved =
              new DriverDataSource(
                  connection.url(),
                  DatabaseDriver.fromJdbcUrl(connection.url()).getDriverClassName(),
                  new Properties(),
                  connection.username(),
                  connection.password());
          driver = resolved;
        }
      }
    }
    return resolved;
  }
}

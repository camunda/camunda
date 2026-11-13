/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/** An in-memory H2 database. It stays alive between connections until it's closed. */
public final class TestH2Database implements TestDatabase {

  private final JdbcDataSource dataSource = new JdbcDataSource();

  public TestH2Database() {
    this("");
  }

  /**
   * @param lockTimeout how long a statement waits for a row lock before it fails
   */
  public TestH2Database(final Duration lockTimeout) {
    this(";LOCK_TIMEOUT=" + lockTimeout.toMillis());
  }

  private TestH2Database(final String settings) {
    dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1" + settings);
  }

  @Override
  public DataSource dataSource() {
    return dataSource;
  }

  @Override
  public String databaseId() {
    return "h2";
  }

  public String url() {
    return dataSource.getURL();
  }

  @Override
  public void close() throws SQLException {
    execute("SHUTDOWN");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import io.camunda.configuration.data.secondarystorage.rdbms.RdbmsConnectionPool;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * The relational database that holds the partitions' state when the primary storage type is
 * `rdbms`. It's separate from the secondary storage's database: the state may live in another
 * database, and the pool is sized for the partitions' writers. Each physical tenant opens its own
 * pool.
 */
public class PrimaryStorageRdbms {

  /** The JDBC URL of the database. Only PostgreSQL is supported; H2 is only meant for tests. */
  private String url;

  /** The user name to connect to the database with. */
  private String username;

  /** The password to connect to the database with. */
  private String password;

  /** The start of every table name, so several clusters can share one database. May be empty. */
  private String prefix = "";

  /** The pool of connections to the database. */
  @NestedConfigurationProperty
  private RdbmsConnectionPool connectionPool = new RdbmsConnectionPool();

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  public RdbmsConnectionPool getConnectionPool() {
    return connectionPool;
  }

  public void setConnectionPool(final RdbmsConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
  }
}

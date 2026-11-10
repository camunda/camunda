/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

/**
 * Experimental PoC: the relational database (PostgreSQL) that holds the element instance state when
 * the primary storage type is 'rdbms'. The rest of the engine state stays in RocksDB.
 */
public class PrimaryStorageRdbms {

  private static final int DEFAULT_POOL_SIZE = 4;

  /** JDBC URL of the database that holds the engine state, e.g. jdbc:postgresql://host/db. */
  private String url;

  /** Username for the database that holds the engine state. */
  private String username;

  /** Password for the database that holds the engine state. */
  private String password;

  /**
   * Maximum number of connections in the engine state connection pool. Each partition holds one
   * connection while its stream processor runs, so configure at least one connection per partition
   * on this broker.
   */
  private int poolSize = DEFAULT_POOL_SIZE;

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

  public int getPoolSize() {
    return poolSize;
  }

  public void setPoolSize(final int poolSize) {
    this.poolSize = poolSize;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/**
 * Experimental PoC: keeps element instances in a relational database (PostgreSQL) instead of
 * RocksDB. Only supports a single broker with replication factor 1, and wipes the partition's rows
 * on every start.
 *
 * <p>Populated from the unified configuration ({@code camunda.data.primary-storage.type: rdbms} and
 * {@code camunda.data.primary-storage.rdbms.*}).
 */
public final class RdbmsEngineStateCfg {
  private static final int DEFAULT_POOL_SIZE = 4;

  private boolean enabled = false;
  private String url;
  private String username;
  private String password;

  /**
   * Each partition holds one connection while its stream processor runs, so the pool needs at least
   * one connection per partition on this broker.
   */
  private int poolSize = DEFAULT_POOL_SIZE;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

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

  @Override
  public String toString() {
    return "RdbmsEngineStateCfg{"
        + "enabled="
        + enabled
        + ", url='"
        + url
        + '\''
        + ", username='"
        + username
        + '\''
        + ", password='<redacted>'"
        + ", poolSize="
        + poolSize
        + '}';
  }
}

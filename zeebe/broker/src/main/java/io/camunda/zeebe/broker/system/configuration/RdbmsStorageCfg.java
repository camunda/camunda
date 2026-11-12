/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/**
 * The relational database that holds the partitions' state when {@link
 * DataCfg#getPrimaryStorageType()} is {@link DataCfg.PrimaryStorageType#RDBMS RDBMS}.
 */
public class RdbmsStorageCfg {

  private String url;
  private String username;
  private String password;
  private String prefix = "";
  private RdbmsConnectionPoolCfg connectionPool = new RdbmsConnectionPoolCfg();

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

  public RdbmsConnectionPoolCfg getConnectionPool() {
    return connectionPool;
  }

  public void setConnectionPool(final RdbmsConnectionPoolCfg connectionPool) {
    this.connectionPool = connectionPool;
  }

  @Override
  public String toString() {
    return "RdbmsStorageCfg{"
        + "url='"
        + url
        + '\''
        + ", username='"
        + username
        + '\''
        + ", password='"
        + (password == null ? "" : "*****")
        + '\''
        + ", prefix='"
        + prefix
        + '\''
        + ", connectionPool="
        + connectionPool
        + '}';
  }
}

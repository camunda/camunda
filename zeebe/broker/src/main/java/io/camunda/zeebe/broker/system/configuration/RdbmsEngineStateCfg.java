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
 */
public final class RdbmsEngineStateCfg {
  private boolean enabled = false;
  private String url;
  private String username;
  private String password;

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
        + ", password='<redacted>'}";
  }
}

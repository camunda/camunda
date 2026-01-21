/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class RdbmsConnectionPool {

  private int maximumPoolSize = 10;
  private int minimumIdle = 10;
  private long idleTimeout = 600000;
  private long maxLifetime = 1800000;
  private long connectionTimeout = 30000;

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }

  public void setMaximumPoolSize(final int maximumPoolSize) {
    this.maximumPoolSize = maximumPoolSize;
  }

  public int getMinimumIdle() {
    return minimumIdle;
  }

  public void setMinimumIdle(final int minimumIdle) {
    this.minimumIdle = minimumIdle;
  }

  public long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(final long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public long getMaxLifetime() {
    return maxLifetime;
  }

  public void setMaxLifetime(final long maxLifetime) {
    this.maxLifetime = maxLifetime;
  }

  public long getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(final long connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/** HikariCP knobs for the RDBMS data source. */
public class RdbmsConnectionPool {

  /** Maximum number of connections kept by the pool. */
  private int maximumPoolSize = 10;

  /** Minimum number of idle connections kept by the pool. */
  private int minimumIdle = 2;

  /** Maximum time the pool waits for a connection before throwing. */
  private Duration connectionTimeout = Duration.ofMillis(30_000);

  /** Maximum time a connection is allowed to sit idle in the pool before being evicted. */
  private Duration idleTimeout = Duration.ofMillis(600_000);

  /** Maximum lifetime of a connection in the pool. */
  private Duration maxLifetime = Duration.ofMillis(1_800_000);

  /**
   * Threshold for logging a possible connection leak. Set to {@link Duration#ZERO} (the HikariCP
   * default) to disable leak detection.
   */
  private Duration leakDetectionThreshold = Duration.ZERO;

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

  public Duration getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(final Duration connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(final Duration idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public Duration getMaxLifetime() {
    return maxLifetime;
  }

  public void setMaxLifetime(final Duration maxLifetime) {
    this.maxLifetime = maxLifetime;
  }

  public Duration getLeakDetectionThreshold() {
    return leakDetectionThreshold;
  }

  public void setLeakDetectionThreshold(final Duration leakDetectionThreshold) {
    this.leakDetectionThreshold = leakDetectionThreshold;
  }
}

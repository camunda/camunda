/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

/** The HikariCP pool of a partition group's connections to the database of its state. */
public class RdbmsConnectionPoolCfg {

  private int maximumPoolSize = 10;
  private int minimumIdle = 2;
  private Duration connectionTimeout = Duration.ofSeconds(30);
  private Duration idleTimeout = Duration.ofMinutes(10);
  private Duration maxLifetime = Duration.ofMinutes(30);
  private Duration leakDetectionThreshold = Duration.ZERO;
  private Duration keepaliveTime = Duration.ZERO;
  private Duration validationTimeout = Duration.ofSeconds(5);

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

  public Duration getKeepaliveTime() {
    return keepaliveTime;
  }

  public void setKeepaliveTime(final Duration keepaliveTime) {
    this.keepaliveTime = keepaliveTime;
  }

  public Duration getValidationTimeout() {
    return validationTimeout;
  }

  public void setValidationTimeout(final Duration validationTimeout) {
    this.validationTimeout = validationTimeout;
  }

  @Override
  public String toString() {
    return "RdbmsConnectionPoolCfg{"
        + "maximumPoolSize="
        + maximumPoolSize
        + ", minimumIdle="
        + minimumIdle
        + ", connectionTimeout="
        + connectionTimeout
        + ", idleTimeout="
        + idleTimeout
        + ", maxLifetime="
        + maxLifetime
        + ", leakDetectionThreshold="
        + leakDetectionThreshold
        + ", keepaliveTime="
        + keepaliveTime
        + ", validationTimeout="
        + validationTimeout
        + '}';
  }
}

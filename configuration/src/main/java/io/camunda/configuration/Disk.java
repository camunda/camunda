/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Disk {
  /**
   * Configure disk monitoring to prevent getting into a non-recoverable state due to out of disk
   * space. When monitoring is enabled, the broker rejects commands and pause replication when the
   * required freeSpace is not available.
   */
  private boolean monitoringEnabled = true;

  /** Sets the interval at which the disk usage is monitored */
  private Duration monitoringInterval = Duration.ofSeconds(1);

  @NestedConfigurationProperty private FreeSpace freeSpace = new FreeSpace();

  public FreeSpace getFreeSpace() {
    return freeSpace;
  }

  public void setFreeSpace(final FreeSpace freeSpace) {
    this.freeSpace = freeSpace;
  }

  public boolean isMonitoringEnabled() {
    return monitoringEnabled;
  }

  public void setMonitoringEnabled(final boolean monitoringEnabled) {
    this.monitoringEnabled = monitoringEnabled;
  }

  public Duration getMonitoringInterval() {
    return monitoringInterval;
  }

  public void setMonitoringInterval(final Duration monitoringInterval) {
    this.monitoringInterval = monitoringInterval;
  }
}

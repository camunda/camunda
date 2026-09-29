/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

/**
 * Configures a one-shot synthetic workload that a broker leading no partitions runs through an
 * isolated scratch engine, so that leader-only code is JIT-compiled before the broker takes over a
 * partition.
 */
public final class LeaderWarmupCfg {
  private boolean enabled = false;
  private Duration startDelay = Duration.ofSeconds(30);
  private Duration maxDuration = Duration.ofMinutes(15);
  private int processInstances = 5_000;
  private int maxInFlightInstances = 32;
  private double maxCpuLoad = 0.7;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getStartDelay() {
    return startDelay;
  }

  public void setStartDelay(final Duration startDelay) {
    this.startDelay = startDelay;
  }

  public Duration getMaxDuration() {
    return maxDuration;
  }

  public void setMaxDuration(final Duration maxDuration) {
    this.maxDuration = maxDuration;
  }

  public int getProcessInstances() {
    return processInstances;
  }

  public void setProcessInstances(final int processInstances) {
    this.processInstances = processInstances;
  }

  public int getMaxInFlightInstances() {
    return maxInFlightInstances;
  }

  public void setMaxInFlightInstances(final int maxInFlightInstances) {
    this.maxInFlightInstances = maxInFlightInstances;
  }

  public double getMaxCpuLoad() {
    return maxCpuLoad;
  }

  public void setMaxCpuLoad(final double maxCpuLoad) {
    this.maxCpuLoad = maxCpuLoad;
  }

  @Override
  public String toString() {
    return "LeaderWarmupCfg{"
        + "enabled="
        + enabled
        + ", startDelay="
        + startDelay
        + ", maxDuration="
        + maxDuration
        + ", processInstances="
        + processInstances
        + ", maxInFlightInstances="
        + maxInFlightInstances
        + ", maxCpuLoad="
        + maxCpuLoad
        + '}';
  }
}

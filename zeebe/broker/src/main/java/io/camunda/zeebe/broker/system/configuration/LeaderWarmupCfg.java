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
  private Duration quietPeriod = Duration.ofSeconds(10);
  private Duration maxDuration = Duration.ofMinutes(5);
  private int processInstances = 300;
  private int maxInFlightInstances = 32;
  private double maxCpuLoad = 0.7;
  private long maxProcessingBacklog = 250;

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

  /**
   * How long the broker's CPU load must stay within {@link #getMaxCpuLoad()} before the warm-up
   * starts, so that it does not compete with the broker catching up after its own start.
   */
  public Duration getQuietPeriod() {
    return quietPeriod;
  }

  public void setQuietPeriod(final Duration quietPeriod) {
    this.quietPeriod = quietPeriod;
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

  /**
   * How far, in log positions, processing may lag behind the log on any partition this broker
   * follows before the warm-up pauses. The lag grows while the cluster works off a backlog of
   * requests, for example after a broker restart, and the warm-up should not compete with that.
   */
  public long getMaxProcessingBacklog() {
    return maxProcessingBacklog;
  }

  public void setMaxProcessingBacklog(final long maxProcessingBacklog) {
    this.maxProcessingBacklog = maxProcessingBacklog;
  }

  @Override
  public String toString() {
    return "LeaderWarmupCfg{"
        + "enabled="
        + enabled
        + ", startDelay="
        + startDelay
        + ", quietPeriod="
        + quietPeriod
        + ", maxDuration="
        + maxDuration
        + ", processInstances="
        + processInstances
        + ", maxInFlightInstances="
        + maxInFlightInstances
        + ", maxCpuLoad="
        + maxCpuLoad
        + ", maxProcessingBacklog="
        + maxProcessingBacklog
        + '}';
  }
}

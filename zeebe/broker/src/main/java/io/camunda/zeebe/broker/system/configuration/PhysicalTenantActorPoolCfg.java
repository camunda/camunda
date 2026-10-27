/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Sizing of the dedicated actor thread pool of each physical tenant, if enabled. */
public final class PhysicalTenantActorPoolCfg {
  private boolean enabled = false;
  private int cpuThreadCount = 2;
  private int ioThreadCount = 1;
  private double cpuLimitCores = 0;
  private double cpuHeadroomPercent = 0;
  private Map<String, Double> weights = new HashMap<>();
  private Duration cpuBudgetPeriod = Duration.ofMillis(20);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getCpuThreadCount() {
    return cpuThreadCount;
  }

  public void setCpuThreadCount(final int cpuThreadCount) {
    this.cpuThreadCount = cpuThreadCount;
  }

  public int getIoThreadCount() {
    return ioThreadCount;
  }

  public void setIoThreadCount(final int ioThreadCount) {
    this.ioThreadCount = ioThreadCount;
  }

  /** CPU limit of the broker in cores, which the budgets of the tenants share. 0 if not limited. */
  public double getCpuLimitCores() {
    return cpuLimitCores;
  }

  public void setCpuLimitCores(final double cpuLimitCores) {
    this.cpuLimitCores = cpuLimitCores;
  }

  /**
   * Percent of the CPU limit that no tenant budget includes, for example garbage collection, Raft
   * and messaging. This work grows with the load of the tenants, so a share of the limit fits it
   * better than a fixed number of cores.
   */
  public double getCpuHeadroomPercent() {
    return cpuHeadroomPercent;
  }

  public void setCpuHeadroomPercent(final double cpuHeadroomPercent) {
    this.cpuHeadroomPercent = cpuHeadroomPercent;
  }

  /** Weight of each tenant by tenant id. A tenant that is not listed has the weight 1. */
  public Map<String, Double> getWeights() {
    return weights;
  }

  public void setWeights(final Map<String, Double> weights) {
    this.weights = weights;
  }

  /**
   * The CPU budget of a tenant in cores: its share, by weight, of the CPU limit without the
   * headroom. Returns 0 if the CPU limit is not set.
   */
  public double cpuBudgetCores(final String tenantId, final Collection<String> allTenantIds) {
    if (cpuLimitCores <= 0) {
      return 0;
    }
    final double totalWeight = allTenantIds.stream().mapToDouble(this::weightOf).sum();
    final double sharedCores = cpuLimitCores * (1 - cpuHeadroomPercent / 100);
    return Math.max(0, sharedCores) * weightOf(tenantId) / totalWeight;
  }

  private double weightOf(final String tenantId) {
    return weights.getOrDefault(tenantId, 1.0);
  }

  public Duration getCpuBudgetPeriod() {
    return cpuBudgetPeriod;
  }

  public void setCpuBudgetPeriod(final Duration cpuBudgetPeriod) {
    this.cpuBudgetPeriod = cpuBudgetPeriod;
  }

  @Override
  public String toString() {
    return "PhysicalTenantActorPoolCfg{"
        + "enabled="
        + enabled
        + ", cpuThreadCount="
        + cpuThreadCount
        + ", ioThreadCount="
        + ioThreadCount
        + ", cpuLimitCores="
        + cpuLimitCores
        + ", cpuHeadroomPercent="
        + cpuHeadroomPercent
        + ", weights="
        + weights
        + ", cpuBudgetPeriod="
        + cpuBudgetPeriod
        + '}';
  }
}

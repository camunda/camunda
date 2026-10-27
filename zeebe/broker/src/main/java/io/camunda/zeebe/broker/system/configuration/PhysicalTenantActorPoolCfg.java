/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

/** Sizing of the dedicated actor thread pool of each physical tenant, if enabled. */
public final class PhysicalTenantActorPoolCfg {
  private boolean enabled = false;
  private int cpuThreadCount = 2;
  private int ioThreadCount = 1;
  private double cpuBudgetCores = 0;
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

  /** CPU time of the pool in cores, 0 if not limited. */
  public double getCpuBudgetCores() {
    return cpuBudgetCores;
  }

  public void setCpuBudgetCores(final double cpuBudgetCores) {
    this.cpuBudgetCores = cpuBudgetCores;
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
        + ", cpuBudgetCores="
        + cpuBudgetCores
        + ", cpuBudgetPeriod="
        + cpuBudgetPeriod
        + '}';
  }
}

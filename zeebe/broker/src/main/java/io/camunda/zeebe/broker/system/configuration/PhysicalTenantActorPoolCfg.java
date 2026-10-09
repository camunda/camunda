/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

/** Sizing of the dedicated actor thread pool of each physical tenant, if enabled. */
public final class PhysicalTenantActorPoolCfg {
  private boolean enabled = false;
  private int cpuThreadCount = 2;
  private int ioThreadCount = 1;

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

  @Override
  public String toString() {
    return "PhysicalTenantActorPoolCfg{"
        + "enabled="
        + enabled
        + ", cpuThreadCount="
        + cpuThreadCount
        + ", ioThreadCount="
        + ioThreadCount
        + '}';
  }
}

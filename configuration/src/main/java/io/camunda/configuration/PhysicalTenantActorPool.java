/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

/**
 * Experimental. Runs the partition actors of each physical tenant on a dedicated actor thread pool
 * instead of the broker-wide one, so that a busy tenant cannot take the actor threads of its
 * neighbours.
 */
public class PhysicalTenantActorPool {

  /** Whether each physical tenant gets its own actor thread pool. */
  private boolean enabled = false;

  /** Number of non-blocking CPU threads in the pool of a physical tenant. */
  private int cpuThreadCount = 2;

  /** Number of IO threads in the pool of a physical tenant. */
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
}

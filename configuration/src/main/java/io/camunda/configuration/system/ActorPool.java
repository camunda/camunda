/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.system;

public class ActorPool {

  /**
   * Whether each physical tenant with partitions on this broker gets its own actor thread pool, in
   * addition to the shared pool sized by cpu-thread-count and io-thread-count of the system.
   * Experimental.
   */
  private boolean enabled = false;

  /**
   * The number of CPU-bound actor threads of the pool of each physical tenant with partitions on
   * this broker. Only used when the pool is enabled. Experimental.
   */
  private int cpuThreadCount = 2;

  /**
   * The number of IO-bound actor threads of the pool of each physical tenant with partitions on
   * this broker. Only used when the pool is enabled. Experimental.
   */
  private int ioThreadCount = 2;

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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.system;

import io.camunda.zeebe.util.Preconditions;

public class ActorPool {

  /**
   * Whether each physical tenant with partitions on this broker gets its own actor thread pool, in
   * addition to the shared pool sized by cpu-thread-count and io-thread-count of the system. This
   * switch applies to the whole broker. Experimental.
   */
  private boolean enabled = false;

  /**
   * The default number of CPU-bound actor threads in each physical tenant's pool. A physical tenant
   * can override this value in its own configuration. Must be positive when the pool is enabled.
   * Experimental.
   */
  private int cpuThreadCount = 2;

  /**
   * The default number of IO-bound actor threads in each physical tenant's pool. A physical tenant
   * can override this value in its own configuration. Must be positive when the pool is enabled.
   * Experimental.
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

  public void validate() {
    if (enabled) {
      Preconditions.assertPositive(cpuThreadCount, "camunda.system.actor-pool.cpu-thread-count");
      Preconditions.assertPositive(ioThreadCount, "camunda.system.actor-pool.io-thread-count");
    }
  }
}

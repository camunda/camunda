/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Health indicator that compares the free memory against a given threshold. The threshold is given
 * in percent of max memory.
 */
public final class MemoryHealthIndicator implements HealthIndicator {

  private final double threshold;

  /**
   * Constructs a memory health indicator
   *
   * @param threshold threshold of free memory in percent; must be a value between {@code ]0,1[}
   */
  public MemoryHealthIndicator(final double threshold) {
    if (threshold <= 0 || threshold >= 1) {
      throw new IllegalArgumentException("Threshold must be a value in the interval ]0,1[");
    }
    this.threshold = threshold;
  }

  public double getThreshold() {
    return threshold;
  }

  @Override
  public Health health() {
    if (getAvailableMemoryPercentageCurrently() > threshold) {
      return Health.up().withDetail("threshold", threshold).build();
    } else {
      return Health.down().withDetail("threshold", threshold).build();
    }
  }

  private double getAvailableMemoryPercentageCurrently() {
    final Runtime runtime = Runtime.getRuntime();

    final long freeMemory = runtime.freeMemory(); // currently free memory
    final long totalMemory = runtime.totalMemory(); // currently allocated by JVM
    final long maxMemory =
        runtime.maxMemory(); // the maximum memory the JVM could allocate (specified by -Xmx)

    final long notYetAllocatedMemory = (maxMemory - totalMemory);
    final long availableMemory = freeMemory + notYetAllocatedMemory;

    return (double) (availableMemory) / maxMemory;
  }
}

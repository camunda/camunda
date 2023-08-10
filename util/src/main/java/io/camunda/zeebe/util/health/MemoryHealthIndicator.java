/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import java.util.Map;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

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
  public Publisher<HealthResult> getResult() {
    final HealthStatus healthStatus;
    if (getAvailableMemoryPercentageCurrently() > threshold) {
      healthStatus = HealthStatus.UP;
    } else {
      healthStatus = HealthStatus.DOWN;
    }
    return Mono.just(
        HealthResult.builder(getClass().getSimpleName(), healthStatus)
            .details(Map.of("threshold", threshold))
            .build());
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

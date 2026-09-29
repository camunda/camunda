/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.function.DoubleSupplier;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the warm-up within a share of the broker's CPU, so that the work the broker is actually
 * doing always has headroom. Once per sample interval it reads the whole process's CPU load, which
 * includes the broker's own work and the JIT compiler; while the load is within budget the warm-up
 * may keep one more instance in flight, and while it is over budget that number halves, down to
 * zero, which pauses the warm-up.
 */
@NullMarked
final class CpuBudget {

  static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);

  private final DoubleSupplier processCpuLoad;
  private final double maxLoad;
  private final int maxInFlight;
  private int limit = 1;
  private long nextSampleNanos;
  private long backOffs;

  /**
   * @param processCpuLoad the process's CPU load since it was last called, as a fraction of the
   *     CPUs available to it, or a negative value if it is unknown
   */
  CpuBudget(
      final DoubleSupplier processCpuLoad,
      final double maxLoad,
      final int maxInFlight,
      final long nowNanos) {
    this.processCpuLoad = processCpuLoad;
    this.maxLoad = maxLoad;
    this.maxInFlight = maxInFlight;
    nextSampleNanos = nowNanos + SAMPLE_INTERVAL.toNanos();
  }

  /** How many instances the warm-up may keep in flight now; zero means it should pause. */
  int inFlightLimit(final long nowNanos) {
    if (nowNanos - nextSampleNanos >= 0) {
      nextSampleNanos = nowNanos + SAMPLE_INTERVAL.toNanos();
      final var load = processCpuLoad.getAsDouble();
      if (load > maxLoad) {
        limit /= 2;
        backOffs++;
      } else if (load >= 0) {
        limit = Math.min(maxInFlight, limit + 1);
      }
    }
    return limit;
  }

  /** How many samples found the process over budget. */
  long backOffs() {
    return backOffs;
  }

  /**
   * The CPU time of the whole process over each interval between calls, relative to the CPUs
   * available to it, which in a container are those its quota allows.
   */
  static DoubleSupplier processCpuLoad() {
    if (!(ManagementFactory.getOperatingSystemMXBean()
        instanceof final com.sun.management.OperatingSystemMXBean os)) {
      return () -> -1;
    }
    final int processors = Runtime.getRuntime().availableProcessors();
    return new DoubleSupplier() {
      private long lastCpuNanos = os.getProcessCpuTime();
      private long lastNanos = System.nanoTime();

      @Override
      public double getAsDouble() {
        final var cpuNanos = os.getProcessCpuTime();
        final var nanos = System.nanoTime();
        if (cpuNanos < 0 || lastCpuNanos < 0 || nanos == lastNanos) {
          lastCpuNanos = cpuNanos;
          lastNanos = nanos;
          return -1;
        }
        final var load = (double) (cpuNanos - lastCpuNanos) / ((nanos - lastNanos) * processors);
        lastCpuNanos = cpuNanos;
        lastNanos = nanos;
        return load;
      }
    };
  }
}

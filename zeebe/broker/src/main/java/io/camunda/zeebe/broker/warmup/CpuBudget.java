/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the warm-up within a share of the broker's CPU, so that the work the broker is actually
 * doing always has headroom. Once per sample interval it reads the whole process's CPU load, which
 * includes the broker's own work and the JIT compiler, and whether the container's CPU quota
 * throttled it. While the load is within budget and nothing was throttled, the warm-up may keep one
 * more instance in flight; otherwise that number halves, down to zero, which pauses the warm-up.
 * Throttling counts on its own because the quota is enforced over periods much shorter than the
 * sample interval: a process within its average budget can still exhaust the quota in bursts, and
 * every thread in the container then waits for the next period.
 */
@NullMarked
final class CpuBudget {

  static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);

  private final DoubleSupplier processCpuLoad;
  private final DoubleSupplier throttledShare;
  private final double maxLoad;
  private final int maxInFlight;
  private int limit = 1;
  private long nextSampleNanos;
  private long backOffs;

  /**
   * @param processCpuLoad the process's CPU load since it was last called, as a fraction of the
   *     CPUs available to it, or a negative value if it is unknown
   * @param throttledShare the share of the container's quota periods since it was last called in
   *     which it was throttled, or a negative value if it is unknown
   */
  CpuBudget(
      final DoubleSupplier processCpuLoad,
      final DoubleSupplier throttledShare,
      final double maxLoad,
      final int maxInFlight,
      final long nowNanos) {
    this.processCpuLoad = processCpuLoad;
    this.throttledShare = throttledShare;
    this.maxLoad = maxLoad;
    this.maxInFlight = maxInFlight;
    nextSampleNanos = nowNanos + SAMPLE_INTERVAL.toNanos();
  }

  /** How many instances the warm-up may keep in flight now; zero means it should pause. */
  int inFlightLimit(final long nowNanos) {
    if (nowNanos - nextSampleNanos >= 0) {
      nextSampleNanos = nowNanos + SAMPLE_INTERVAL.toNanos();
      switch (sample()) {
        case OVER -> {
          limit /= 2;
          backOffs++;
        }
        case WITHIN -> limit = Math.min(maxInFlight, limit + 1);
        case UNKNOWN -> {}
      }
    }
    return limit;
  }

  /**
   * Samples the CPU now, outside the budget's own interval; unknown counts as within budget. Calls
   * should be at least a sample interval apart for the load to be meaningful.
   */
  boolean isWithinBudget() {
    return sample() != Sample.OVER;
  }

  private Sample sample() {
    final var load = processCpuLoad.getAsDouble();
    final var throttled = throttledShare.getAsDouble();
    if (load > maxLoad || throttled > 0) {
      return Sample.OVER;
    }
    return load >= 0 || throttled >= 0 ? Sample.WITHIN : Sample.UNKNOWN;
  }

  /** How many samples found the process over budget. */
  long backOffs() {
    return backOffs;
  }

  /**
   * The share of the container's CPU quota periods in which it was throttled, between calls, read
   * from its cgroup v2 {@code cpu.stat}. A container normally sees its own cgroup at the root of
   * its cgroup mount; a privileged one sees the host's, so its own is found by its path in {@code
   * /proc/self/cgroup}. Without a quota there is nothing to throttle, and the share is unknown.
   */
  static DoubleSupplier throttledShare() {
    final var cpuStat = ownCgroup().map(cgroup -> cgroup.resolve("cpu.stat")).orElse(null);
    if (cpuStat == null) {
      return () -> -1;
    }
    return new DoubleSupplier() {
      private long[] last = readThrottling(cpuStat);

      @Override
      public double getAsDouble() {
        final var now = readThrottling(cpuStat);
        final var periods = now[0] - last[0];
        final var throttled = now[1] - last[1];
        final var known = now[0] >= 0 && last[0] >= 0;
        last = now;
        if (!known) {
          return -1;
        }
        return periods > 0 ? (double) throttled / periods : 0;
      }
    };
  }

  private static Optional<Path> ownCgroup() {
    final var root = Path.of("/sys/fs/cgroup");
    if (Files.isRegularFile(root.resolve("cpu.max")) && hasQuota(root)) {
      return Optional.of(root);
    }
    try {
      for (final var line : Files.readAllLines(Path.of("/proc/self/cgroup"))) {
        if (line.startsWith("0::/") && line.length() > 4) {
          final var own = root.resolve(line.substring(4));
          if (hasQuota(own)) {
            return Optional.of(own);
          }
        }
      }
    } catch (final IOException | RuntimeException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static boolean hasQuota(final Path cgroup) {
    try {
      return !Files.readString(cgroup.resolve("cpu.max")).startsWith("max");
    } catch (final IOException | RuntimeException e) {
      return false;
    }
  }

  /** Returns {nr_periods, nr_throttled}, or {-1, -1} if they cannot be read. */
  private static long[] readThrottling(final Path cpuStat) {
    long periods = -1;
    long throttled = -1;
    try {
      for (final var line : Files.readAllLines(cpuStat)) {
        if (line.startsWith("nr_periods ")) {
          periods = Long.parseLong(line.substring("nr_periods ".length()).trim());
        } else if (line.startsWith("nr_throttled ")) {
          throttled = Long.parseLong(line.substring("nr_throttled ".length()).trim());
        }
      }
    } catch (final IOException | RuntimeException e) {
      return new long[] {-1, -1};
    }
    return periods >= 0 && throttled >= 0 ? new long[] {periods, throttled} : new long[] {-1, -1};
  }

  private enum Sample {
    WITHIN,
    OVER,
    UNKNOWN
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

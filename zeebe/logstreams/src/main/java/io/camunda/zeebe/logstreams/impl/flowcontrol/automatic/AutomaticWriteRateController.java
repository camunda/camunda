/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol.automatic;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * Per-broker AIMD controller of the write rate ceiling shared by all leader partitions of the
 * broker. The ceiling stays between the floor and the start value of {@link WriteRateLimitBounds}.
 *
 * <ul>
 *   <li>{@link #onWindow(WindowObservation)} runs once per window, in the background, and decides
 *       whether to decrease the ceiling. The only decrease signal is sustained CPU throttling: the
 *       ceiling tracks what the broker can do. Backlog and latency are left to the request limiter,
 *       which bounds the queue depth of each partition.
 *   <li>{@link #onRejected(long)} runs on the rejection path of a partition at its limit. It costs
 *       one volatile read unless an increase is allowed, and steps up at most once per window.
 * </ul>
 *
 * The mechanism is off when the cluster has a single configured tenant or the CPU limit is unknown;
 * {@link #limitFor(double)} then returns empty and the static write rate limit applies.
 */
public final class AutomaticWriteRateController {

  private final AutomaticWriteRateLimitCfg cfg;
  private final OptionalDouble cpuLimit;
  private final AtomicLong lastIncreaseWindow = new AtomicLong(Long.MIN_VALUE);

  private volatile @Nullable WriteRateLimitBounds bounds;
  private volatile double ceiling;
  private volatile boolean increaseAllowed;

  // only accessed from the window evaluation
  private int consecutiveThrottledWindows;

  public AutomaticWriteRateController(
      final AutomaticWriteRateLimitCfg cfg,
      final WriteRateLimitTopology topology,
      final OptionalDouble cpuLimit) {
    this.cfg = cfg;
    this.cpuLimit = cpuLimit;
    bounds = computeBounds(topology);
    ceiling = bounds != null ? bounds.start() : 0;
  }

  public boolean isEnabled() {
    return bounds != null;
  }

  public @Nullable WriteRateLimitBounds bounds() {
    return bounds;
  }

  public double ceiling() {
    return ceiling;
  }

  /** Per-partition write rate limit of a tenant, or empty when the mechanism is off. */
  public OptionalDouble limitFor(final double weight) {
    final var current = bounds;
    return current == null
        ? OptionalDouble.empty()
        : OptionalDouble.of(current.limitFor(ceiling, weight));
  }

  /** Recomputes the bounds and scales the ceiling by {@code newStart / oldStart}. */
  public synchronized void onTopologyChanged(final WriteRateLimitTopology topology) {
    final var oldBounds = bounds;
    final var newBounds = computeBounds(topology);
    bounds = newBounds;
    if (newBounds == null) {
      ceiling = 0;
      increaseAllowed = false;
    } else if (oldBounds == null) {
      ceiling = newBounds.start();
    } else {
      ceiling = newBounds.clamp(ceiling * newBounds.start() / oldBounds.start());
    }
  }

  public synchronized Decision onWindow(final WindowObservation observation) {
    final var current = bounds;
    if (current == null) {
      return Decision.OFF;
    }

    final var usable =
        observation.leaderPartitions().stream()
            .anyMatch(partition -> partition.receivedWrites() && !partition.excluded());

    final var throttled = observation.cpuThrottledRatio();
    final var isThrottled =
        throttled.isPresent() && throttled.getAsDouble() > cfg.throttledRatioThreshold();
    consecutiveThrottledWindows = isThrottled ? consecutiveThrottledWindows + 1 : 0;

    if (consecutiveThrottledWindows >= cfg.throttledWindows()) {
      decrease(current);
      return Decision.DECREASE_CPU_THROTTLED;
    }
    if (!usable) {
      increaseAllowed = false;
      return Decision.HOLD_NO_DATA;
    }
    increaseAllowed = ceiling < current.cap() && !isThrottled;
    return Decision.NO_CHANGE;
  }

  /**
   * Called when a leader partition rejects a write with {@code WriteRateLimitExhausted} while its
   * exporter throttle is not active. Steps the ceiling up at most once per window.
   */
  public void onRejected(final long nowMillis) {
    if (!increaseAllowed) {
      return;
    }
    final var window = nowMillis / cfg.window().toMillis();
    final var last = lastIncreaseWindow.get();
    if (last >= window || !lastIncreaseWindow.compareAndSet(last, window)) {
      return;
    }
    increase();
  }

  private synchronized void increase() {
    final var current = bounds;
    if (current == null || !increaseAllowed) {
      return;
    }
    ceiling = current.clamp(ceiling + cfg.increaseStepRatio() * current.start());
    increaseAllowed = ceiling < current.cap();
  }

  private void decrease(final WriteRateLimitBounds current) {
    ceiling = current.clamp(ceiling * cfg.decreaseFactor());
    increaseAllowed = false;
  }

  private @Nullable WriteRateLimitBounds computeBounds(final WriteRateLimitTopology topology) {
    if (topology.tenants().size() <= 1 || cpuLimit.isEmpty()) {
      return null;
    }
    return WriteRateLimitBounds.of(topology, cpuLimit.getAsDouble(), cfg);
  }

  public enum Decision {
    OFF,
    NO_CHANGE,
    HOLD_NO_DATA,
    DECREASE_CPU_THROTTLED
  }

  /**
   * What the broker saw during one window.
   *
   * @param cpuThrottledRatio share of throttled CFS periods; empty when the container has no CPU
   *     limit or the counters are not readable
   * @param leaderPartitions all leader partitions of the broker
   */
  public record WindowObservation(
      OptionalDouble cpuThrottledRatio, List<PartitionObservation> leaderPartitions) {}

  /**
   * @param receivedWrites the partition received writes during the window
   * @param excluded the exporter throttle is active, i.e. secondary storage causes the delay
   */
  public record PartitionObservation(
      String tenantId, int partitionId, boolean receivedWrites, boolean excluded) {}
}

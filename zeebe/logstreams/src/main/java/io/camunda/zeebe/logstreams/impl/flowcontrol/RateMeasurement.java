/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.LongSupplier;
import net.jcip.annotations.ThreadSafe;

/** Measures the rate of change for monotonically increasing values. */
@ThreadSafe
public final class RateMeasurement {
  private final LongSupplier clock;
  private final LinkedBlockingDeque<Observation> observations;
  private final long resolution;
  private volatile long currentValue;

  public RateMeasurement(
      final LongSupplier clock, final Duration observationWindow, final Duration resolution) {
    if (observationWindow.isNegative() || resolution.isNegative()) {
      throw new IllegalArgumentException("observationWindow and resolution must be positive");
    }
    if (observationWindow.compareTo(resolution) <= 0) {
      throw new IllegalArgumentException("observationWindow must be greater than resolution");
    }
    this.clock = clock;
    this.resolution = resolution.toMillis();
    final var numberOfObservations = (int) (observationWindow.toMillis() / this.resolution);
    observations = new LinkedBlockingDeque<>(numberOfObservations);
  }

  /**
   * Updates the rate measurement with the given value.
   *
   * <p>The new observation will be ignored if it is not the first or measured after the previous at
   * least by the resolution time.
   *
   * @param value to update the rate measurement with.
   * @return true if the observation was added, false if the observation was skipped.
   */
  public boolean observe(final long value) {
    final var now = clock.getAsLong();
    currentValue = value;
    return updateObservations(now, value);
  }

  public long rate() {
    final var now = clock.getAsLong();

    final var oldest = observations.peekFirst();
    if (oldest == null) {
      return 0;
    }

    final var elapsed = now - oldest.timestamp;
    if (elapsed == 0) {
      return 0;
    }

    final var delta = currentValue - oldest.value;
    return delta * 1000 / elapsed;
  }

  private boolean updateObservations(final long now, final long value) {
    final var last = observations.peekLast();
    if (last == null || now - last.timestamp >= resolution) {
      final var newObservation = new Observation(now, value);
      while (!observations.offerLast(newObservation)) {
        observations.removeFirst();
      }
      return true;
    }
    return false;
  }

  record Observation(long timestamp, long value) {}
}

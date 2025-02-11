/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;

/**
 * Extends the base {@link MeterDocumentation} API to allow for more static description, e.g.
 * help/description associated with a given metric.
 */
public interface ExtendedMeterDocumentation extends MeterDocumentation {

  double[] EMPTY_DISTRIBUTION_SLOS = new double[0];

  /** Returns the description (also known as {@code help} in some systems) for the given meter. */
  String getDescription();

  /** Returns the buckets to be used if the meter type is a {@link Meter.Type#TIMER}. */
  default Duration[] getTimerSLOs() {
    return MicrometerUtil.defaultPrometheusBuckets();
  }

  /**
   * Returns the buckets to be used if the meter type is a {@link Meter.Type#DISTRIBUTION_SUMMARY}.
   */
  default double[] getDistributionSLOs() {
    return EMPTY_DISTRIBUTION_SLOS;
  }

  static Duration[] exponentialBucketDuration(
      final long start, final long factor, final int count, final TemporalUnit unit) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be greater than 0");
    }
    final var buckets = new ArrayList<Duration>(count);
    var value = start;
    for (int i = 0; i < count; i++) {
      final var duration = Duration.of(value, unit);
      buckets.add(duration);
      if (Long.MAX_VALUE / factor < value) {
        // stop here, we are already close to LONG.MAX_VALUE for unit
        break;
      }
      value *= factor;
    }
    return buckets.toArray(Duration[]::new);
  }
}

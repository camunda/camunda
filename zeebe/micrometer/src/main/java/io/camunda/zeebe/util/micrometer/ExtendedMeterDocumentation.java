/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.docs.MeterDocumentation;
import java.time.Duration;

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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public enum AuthorizationMetricsDoc implements ExtendedMeterDocumentation {

  /** Latency of each authorization check in AuthorizationCheckBehavior, including cache hits. */
  CHECK_LATENCY {
    private static final Duration[] TIMER_SLOS = {
      Duration.of(100, ChronoUnit.MICROS),
      Duration.of(500, ChronoUnit.MICROS),
      Duration.ofMillis(1),
      Duration.ofMillis(5),
      Duration.ofMillis(10),
      Duration.ofMillis(50),
      Duration.ofMillis(100),
      Duration.ofMillis(500),
    };

    @Override
    public String getName() {
      return "zeebe.authorization.check.latency";
    }

    @Override
    public String getDescription() {
      return "Latency of each authorization check in AuthorizationCheckBehavior, including cache hits";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return TIMER_SLOS;
    }
  };
}

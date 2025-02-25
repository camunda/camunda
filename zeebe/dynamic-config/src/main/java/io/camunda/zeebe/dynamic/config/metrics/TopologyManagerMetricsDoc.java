/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public enum TopologyManagerMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of completed changes in the current change plan */
  OPERATION_DURATION {
    private static final Duration[] TIMER_SLOS =
        Stream.of(
                100, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000, 120_000, 180_000, 300_000,
                600_000)
            .map(Duration::ofMillis)
            .toArray(Duration[]::new);

    @Override
    public String getName() {
      return "zeebe.cluster.changes.operation.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Duration it takes to apply an operation";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return TIMER_SLOS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {TopologyManagerMetricsKeyName.OPERATION};
    }
  },
  /** Number of attempts per operation type */
  OPERATION_ATTEMPTS {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.operation.attempts";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of attempts per operation type";
    }

    @Override
    public KeyName[] getKeyNames() {
      return TopologyManagerMetricsKeyName.values();
    }
  };

  public enum TopologyManagerMetricsKeyName implements KeyName {
    OPERATION {
      @Override
      public String asString() {
        return "operation";
      }
    },
    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    }
  }

  public enum Outcome {
    FAILED("failed"),
    APPLIED("applied");

    private final String name;

    Outcome(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class LeaderWarmupMetrics {

  private final AtomicInteger state = new AtomicInteger(State.PENDING.code);
  private final AtomicLong durationMillis = new AtomicLong();

  LeaderWarmupMetrics(final MeterRegistry registry) {
    Gauge.builder(LeaderWarmupMetricsDoc.STATE.getName(), state, AtomicInteger::get)
        .description(LeaderWarmupMetricsDoc.STATE.getDescription())
        .register(registry);
    TimeGauge.builder(
            LeaderWarmupMetricsDoc.DURATION.getName(),
            durationMillis,
            TimeUnit.MILLISECONDS,
            AtomicLong::get)
        .description(LeaderWarmupMetricsDoc.DURATION.getDescription())
        .register(registry);
  }

  void setState(final State newState) {
    state.set(newState.code);
  }

  void setDuration(final Duration duration) {
    durationMillis.set(duration.toMillis());
  }

  enum State {
    PENDING(0),
    RUNNING(1),
    COMPLETED(2),
    TIMED_OUT(3),
    CANCELLED(4),
    SKIPPED(5),
    FAILED(6);

    private final int code;

    State(final int code) {
      this.code = code;
    }

    int code() {
      return code;
    }
  }

  @SuppressWarnings("NullableProblems")
  enum LeaderWarmupMetricsDoc implements ExtendedMeterDocumentation {
    /** The state of the leader warm-up. */
    STATE {
      @Override
      public String getDescription() {
        return "The state of the leader warm-up: 0 pending, 1 running, 2 completed, 3 timed out,"
            + " 4 cancelled, 5 skipped, 6 failed";
      }

      @Override
      public String getName() {
        return "zeebe.leader.warmup.state";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }
    },

    /** How long the leader warm-up ran for. */
    DURATION {
      @Override
      public String getDescription() {
        return "How long the leader warm-up ran for, once it has ended";
      }

      @Override
      public String getName() {
        return "zeebe.leader.warmup.duration";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }
    }
  }
}

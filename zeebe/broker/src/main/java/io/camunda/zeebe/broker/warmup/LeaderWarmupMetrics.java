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
  private final AtomicInteger exporters = new AtomicInteger();
  private final AtomicLong searchRequests = new AtomicLong();
  private final AtomicLong unrecognisedSearchRequests = new AtomicLong();

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
    Gauge.builder(LeaderWarmupMetricsDoc.EXPORTERS.getName(), exporters, AtomicInteger::get)
        .description(LeaderWarmupMetricsDoc.EXPORTERS.getDescription())
        .register(registry);
    Gauge.builder(LeaderWarmupMetricsDoc.SEARCH_REQUESTS.getName(), searchRequests, AtomicLong::get)
        .description(LeaderWarmupMetricsDoc.SEARCH_REQUESTS.getDescription())
        .register(registry);
    Gauge.builder(
            LeaderWarmupMetricsDoc.UNRECOGNISED_SEARCH_REQUESTS.getName(),
            unrecognisedSearchRequests,
            AtomicLong::get)
        .description(LeaderWarmupMetricsDoc.UNRECOGNISED_SEARCH_REQUESTS.getDescription())
        .register(registry);
  }

  void setExporting(final int exporterCount, final long requests, final long unrecognised) {
    exporters.set(exporterCount);
    searchRequests.set(requests);
    unrecognisedSearchRequests.set(unrecognised);
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
    },

    /** How many of the broker's exporters the leader warm-up ran. */
    EXPORTERS {
      @Override
      public String getDescription() {
        return "How many of the broker's exporters the leader warm-up ran, once it has ended";
      }

      @Override
      public String getName() {
        return "zeebe.leader.warmup.exporters";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }
    },

    /** How many requests the leader warm-up's exporters sent to its stand-in search engine. */
    SEARCH_REQUESTS {
      @Override
      public String getDescription() {
        return "How many requests the leader warm-up's exporters sent to its stand-in search"
            + " engine, once it has ended";
      }

      @Override
      public String getName() {
        return "zeebe.leader.warmup.search.requests";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }
    },

    /** How many of those requests the stand-in search engine did not recognise. */
    UNRECOGNISED_SEARCH_REQUESTS {
      @Override
      public String getDescription() {
        return "How many requests the leader warm-up's stand-in search engine did not recognise,"
            + " once it has ended";
      }

      @Override
      public String getName() {
        return "zeebe.leader.warmup.search.unrecognised";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }
    }
  }
}

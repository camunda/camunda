/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/**
 * Metrics emitted by the {@link IncidentResolutionMeter}. They measure the incident-resolution
 * latency — the interval from an incident's creation (engine wall-clock, carried on the incident
 * document) to the moment the load tester first observes it in {@code ACTIVE} state — and surface
 * the pending backlog so that a fully stuck partition can be detected from backlog presence plus
 * promotion absence.
 */
public enum IncidentResolutionMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * The incident-resolution latency, tagged by partition (decoded from the incident key). Measures
   * the time from an incident's {@code creationTime} to the moment it is first observed {@code
   * ACTIVE}. The end timestamp is wall-clock ({@code Instant.now()}), not {@code nanoTime} — a
   * deliberate divergence from {@link ProcessInstanceStartMeter}, because the start is an absolute
   * engine wall-clock carried on the incident document.
   */
  PENDING_TO_ACTIVE_LATENCY {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.PARTITION};

    private static final Duration[] BUCKETS = {
      Duration.ofSeconds(1),
      Duration.ofSeconds(5),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofMinutes(1),
      Duration.ofMinutes(2),
      Duration.ofMinutes(5),
      Duration.ofMinutes(10),
      Duration.ofMinutes(15),
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The incident-resolution latency: the time from an incident's creation time to the moment it is first observed in ACTIVE state.";
    }

    @Override
    public String getName() {
      return "starter.incident.pending.to.active.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /**
   * Number of incidents that exceeded the watch cap before being observed {@code ACTIVE}. Their
   * capped elapsed time is still recorded into {@link #PENDING_TO_ACTIVE_LATENCY} so a stuck run
   * does not show optimistically low percentiles, and this counter reflects how many samples were
   * capped.
   */
  RESOLUTION_TIMEOUT {
    @Override
    public String getDescription() {
      return "Number of incidents that exceeded the watch cap before being observed ACTIVE.";
    }

    @Override
    public String getName() {
      return "starter.incident.resolution.timeout";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * The number of pending incidents currently being watched on a partition (the per-partition
   * watch-map size). Together with {@link #PARTITION_LAST_PROMOTION_AGE} it detects a fully stuck
   * partition, which — emitting no latency samples — is invisible in the latency percentiles.
   */
  PENDING_BACKLOG {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.PARTITION};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The number of pending incidents currently being watched on a partition.";
    }

    @Override
    public String getName() {
      return "starter.incident.pending.backlog";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  },

  /**
   * Seconds since the last incident promotion (PENDING → ACTIVE) recorded on a partition. This is
   * the primary stuck detector: it climbs precisely when a partition stops promoting. Stuck
   * condition: {@code backlog{p} > 0 AND last_promotion_age{p} > ~60s} (normal cadence is 2–5s).
   */
  PARTITION_LAST_PROMOTION_AGE {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.PARTITION};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "Seconds since the last incident promotion recorded on a partition.";
    }

    @Override
    public String getName() {
      return "starter.incident.partition.last.promotion.age";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  }
}

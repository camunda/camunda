/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;

/**
 * Watches the share of user requests that flow control rejected, per partition this broker leads.
 * Thresholds are percentages. Windows with fewer than {@link #MINIMUM_REQUESTS} requests count as
 * no rejections, so one rejected request out of two does not start a condition.
 */
public final class BackpressureDetector extends ThresholdDetector {

  static final String RECEIVED_REQUESTS = "zeebe.received.request.count.total";
  static final String DROPPED_REQUESTS = "zeebe.dropped.request.count.total";
  static final int MINIMUM_REQUESTS = 10;

  private final MeterReader meters;
  private final LeaderPartitions leaders;
  private final CounterWindow received = new CounterWindow();
  private final CounterWindow dropped = new CounterWindow();

  public BackpressureDetector(
      final MeterRegistry registry,
      final LeaderPartitions leaders,
      final ConditionThresholds thresholds) {
    super("Backpressure", thresholds);
    meters = new MeterReader(registry);
    this.leaders = leaders;
  }

  @Override
  public void sample(final Instant now) {
    final var receivedRequests =
        received.sample(meters.counts(RECEIVED_REQUESTS), id -> MeterReader.child(id, ""));
    final var droppedRequests =
        dropped.sample(meters.counts(DROPPED_REQUESTS), id -> MeterReader.child(id, ""));

    final var observed = new HashSet<String>();
    receivedRequests.forEach(
        (child, requests) -> {
          if (!leaders.isLeader(child)) {
            return;
          }
          observed.add(child);
          final var rejected = droppedRequests.getOrDefault(child, 0.0);
          final var percentage = requests < MINIMUM_REQUESTS ? 0 : 100 * rejected / requests;
          observe(child, percentage, now);
        });
    forgetOthers(observed);
  }

  @Override
  protected String describe(final String child, final double value) {
    return String.format(
        Locale.ROOT,
        "flow control rejected %.1f%% of requests, above the threshold of %.1f%%",
        value,
        thresholds().threshold());
  }
}

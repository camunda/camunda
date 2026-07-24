/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer-backed {@link ConsumerMetrics} for a {@link StreamRuntime}'s consumer-group
 * membership: rebalance / heartbeat-failure / rejoin-rejected counters plus a gauge over this
 * member's fencing epoch, all tagged by {@code group} only (event-bridge-client itself stays
 * dependency-free — see {@link ConsumerMetrics}'s javadoc).
 *
 * <p>Construct exactly ONE instance per runtime and hand it to {@link Consumer#metrics} on every
 * (re)subscribe attempt (see {@link StreamRuntime#subscribeWithRetry}): the epoch gauge is backed
 * by the {@link AtomicLong} this instance owns and registers once, in this constructor. Micrometer
 * keeps only the FIRST registration for a given id+tags — re-registering the same gauge on a
 * resubscribe would silently be ignored, leaving a stale reading — so reusing this one instance
 * (not constructing a fresh one per attempt) is what keeps the gauge live and accurate across every
 * incarnation of the underlying {@code Consumer}, including a static-membership takeover.
 */
public final class MicrometerConsumerMetrics implements ConsumerMetrics {

  private final Counter rebalances;
  private final Counter heartbeatFailures;
  private final Counter rejoinsRejected;
  private final AtomicLong assignmentEpoch = new AtomicLong();

  public MicrometerConsumerMetrics(final MeterRegistry registry, final String group) {
    rebalances =
        Counter.builder("eb.consumer.rebalances")
            .description("Owned-partition rebalances (a non-empty revoke/assign delta)")
            .tag("group", group)
            .register(registry);
    heartbeatFailures =
        Counter.builder("eb.consumer.heartbeat.failures")
            .description(
                "Failed heartbeat attempts (transport error, non-2xx, or an unusable reply)")
            .tag("group", group)
            .register(registry);
    rejoinsRejected =
        Counter.builder("eb.consumer.rejoins.rejected")
            .description(
                "Rejoin requests themselves rejected with HTTP 409 (e.g. a concurrent second"
                    + " join for the same static instance id), counted separately from a"
                    + " successful fenced rejoin")
            .tag("group", group)
            .register(registry);
    Gauge.builder("eb.consumer.assignment.epoch", assignmentEpoch, AtomicLong::get)
        .description("This consumer's current fencing epoch (memberEpoch)")
        .tag("group", group)
        .register(registry);
  }

  @Override
  public void onRebalance(final int revoked, final int assigned) {
    rebalances.increment();
  }

  @Override
  public void onHeartbeatFailure() {
    heartbeatFailures.increment();
  }

  @Override
  public void onRejoinRejected() {
    rejoinsRejected.increment();
  }

  @Override
  public void onEpochChanged(final long memberEpoch) {
    assignmentEpoch.set(memberEpoch);
  }
}

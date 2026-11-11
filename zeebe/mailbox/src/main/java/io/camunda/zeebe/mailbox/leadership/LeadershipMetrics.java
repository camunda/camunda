/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import io.camunda.zeebe.mailbox.leadership.LeadershipListener.Reason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The metrics of the {@link LeaderElector} of a node.
 *
 * <ul>
 *   <li>{@value #LEADER}: 1 for each partition that the node leads, else 0
 *   <li>{@value #TERM}: the term of the lease that the node holds or held last
 *   <li>{@value #TRANSITIONS}: how often the node started or stopped leading, by {@code event}:
 *       {@code claimed}, or one of the {@link Reason}s
 *   <li>{@value #TAKEOVER_DELAY}: when the node takes over a lease of another node, the time since
 *       it first saw the lease in the state it took over from. For a failed leader that is how long
 *       the old leader's last renewal stood, so it approximates the time to detect the failure.
 *       Tags {@code cause}: {@code expired}, {@code released}, or {@code unreachable} for a leader
 *       of the same zone that gossip no longer sees; {@code previous_zone}: {@code same} or {@code
 *       other}.
 * </ul>
 */
final class LeadershipMetrics {

  static final String LEADER = "zeebe.lease.leader";
  static final String TERM = "zeebe.lease.term";
  static final String TRANSITIONS = "zeebe.lease.transitions";
  static final String TAKEOVER_DELAY = "zeebe.lease.takeover.delay";

  private final MeterRegistry registry;
  private final Map<Integer, AtomicLong> leading;
  private final Map<Integer, AtomicLong> terms;

  LeadershipMetrics(final MeterRegistry registry, final Set<Integer> partitions) {
    this.registry = registry;
    leading = newGauges(partitions, LEADER, "1 if this node leads the partition, else 0");
    terms = newGauges(partitions, TERM, "The term of the lease this node holds or held last");
  }

  void claimed(final int partitionId, final long term) {
    set(leading, partitionId, 1);
    set(terms, partitionId, term);
    transition(partitionId, "claimed");
  }

  void stopped(final int partitionId, final Reason reason) {
    set(leading, partitionId, 0);
    transition(partitionId, reason.name().toLowerCase(Locale.ROOT));
  }

  void tookOver(
      final int partitionId, final String cause, final boolean sameZone, final Duration delay) {
    Timer.builder(TAKEOVER_DELAY)
        .description("The time since the lease that this node took over last changed")
        .tag("partition", String.valueOf(partitionId))
        .tag("cause", cause)
        .tag("previous_zone", sameZone ? "same" : "other")
        .register(registry)
        .record(delay);
  }

  private void transition(final int partitionId, final String event) {
    Counter.builder(TRANSITIONS)
        .description("How often this node started or stopped leading a partition")
        .tag("partition", String.valueOf(partitionId))
        .tag("event", event)
        .register(registry)
        .increment();
  }

  /** The elector reports only on the partitions it was given, which all have gauges. */
  private static void set(
      final Map<Integer, AtomicLong> gauges, final int partitionId, final long value) {
    final var gauge = gauges.get(partitionId);
    if (gauge != null) {
      gauge.set(value);
    }
  }

  private Map<Integer, AtomicLong> newGauges(
      final Set<Integer> partitions, final String name, final String description) {
    final var values =
        partitions.stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), p -> new AtomicLong()));
    values.forEach(
        (partitionId, value) ->
            Gauge.builder(name, value, AtomicLong::get)
                .description(description)
                .tag("partition", String.valueOf(partitionId))
                .register(registry));
    return values;
  }
}

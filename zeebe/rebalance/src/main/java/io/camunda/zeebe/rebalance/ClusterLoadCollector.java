/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures the load across the cluster by reading every broker's running totals at the start and
 * end of a window, and answers other brokers' requests for this broker's totals.
 */
public final class ClusterLoadCollector implements ClusterLoadSource, AutoCloseable {

  static final String TOPIC = "cluster-rebalance-load-totals";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Logger LOG = LoggerFactory.getLogger(ClusterLoadCollector.class);

  private final MemberId localMemberId;
  private final ConcurrencyControl executor;
  private final LoadCounters counters;
  private final ClusterCommunicationService communicationService;
  private final InstantSource clock;
  private final long incarnation = ThreadLocalRandom.current().nextLong();

  public ClusterLoadCollector(
      final MemberId localMemberId,
      final ConcurrencyControl executor,
      final LoadCounters counters,
      final ClusterCommunicationService communicationService,
      final InstantSource clock) {
    this.localMemberId = localMemberId;
    this.executor = executor;
    this.counters = counters;
    this.communicationService = communicationService;
    this.clock = clock;
  }

  public void start() {
    communicationService.replyTo(
        TOPIC,
        Function.identity(),
        request -> CompletableFuture.completedFuture(localTotals()),
        LoadReportSerializer::encodeTotals);
  }

  @Override
  public void close() {
    communicationService.unsubscribe(TOPIC);
  }

  /**
   * Measures each member's load over the window starting now. Must be called from the executor.
   *
   * @param members the brokers expected to report, including this one if it is a member
   */
  @Override
  public ActorFuture<ClusterLoad> collect(
      final Collection<MemberId> members, final Duration window) {
    final ActorFuture<ClusterLoad> result = executor.createFuture();
    final var expected = List.copyOf(members);
    readTotals(
        expected,
        (startedAt, before) ->
            executor.schedule(
                window,
                () ->
                    readTotals(
                        expected,
                        (endedAt, after) ->
                            result.complete(
                                aggregate(
                                    expected,
                                    before,
                                    after,
                                    Duration.between(startedAt, endedAt))))));
    return result;
  }

  private LoadTotals localTotals() {
    final Map<LoadMeasure, Long> totals = new EnumMap<>(LoadMeasure.class);
    for (final var measure : LoadMeasure.values()) {
      totals.put(measure, counters.total(measure));
    }
    return new LoadTotals(incarnation, totals);
  }

  /** Reads every member's totals, then passes on those that answered, on the executor. */
  private void readTotals(
      final List<MemberId> members, final BiConsumer<Instant, Map<MemberId, LoadTotals>> whenRead) {
    final var readAt = clock.instant();
    final Map<MemberId, CompletableFuture<LoadTotals>> requests = new HashMap<>();
    for (final var member : members) {
      requests.put(
          member,
          member.equals(localMemberId)
              ? CompletableFuture.completedFuture(localTotals())
              : communicationService.send(
                  TOPIC,
                  new byte[0],
                  Function.identity(),
                  LoadReportSerializer::decodeTotals,
                  member,
                  TIMEOUT));
    }
    CompletableFuture.allOf(
            requests.values().stream()
                .map(request -> request.handleAsync((ignored, error) -> null, executor))
                .toArray(CompletableFuture[]::new))
        .whenCompleteAsync(
            (ignored, error) -> {
              final Map<MemberId, LoadTotals> answered = new HashMap<>();
              requests.forEach(
                  (member, request) -> {
                    try {
                      answered.put(member, request.join());
                    } catch (final Exception e) {
                      LOG.debug("No load totals from {}", member, e);
                    }
                  });
              whenRead.accept(readAt, answered);
            },
            executor);
  }

  private static ClusterLoad aggregate(
      final List<MemberId> members,
      final Map<MemberId, LoadTotals> before,
      final Map<MemberId, LoadTotals> after,
      final Duration elapsed) {
    final Map<LoadMeasure, Double> rates = new EnumMap<>(LoadMeasure.class);
    for (final var measure : LoadMeasure.values()) {
      rates.put(measure, 0.0);
    }
    final Set<MemberId> unaccounted = new HashSet<>();
    final double seconds = Math.max(elapsed.toMillis(), 1) / 1000.0;
    for (final var member : members) {
      final var start = before.get(member);
      final var end = after.get(member);
      final var increases = increases(start, end);
      if (increases == null) {
        unaccounted.add(member);
        continue;
      }
      increases.forEach(
          (measure, increase) -> rates.merge(measure, increase / seconds, Double::sum));
    }
    return new ClusterLoad(rates, unaccounted);
  }

  /**
   * How much each measure increased between the two reads, or {@code null} unless both come from
   * the same incarnation and hold every measure.
   */
  private static @Nullable Map<LoadMeasure, Long> increases(
      final @Nullable LoadTotals start, final @Nullable LoadTotals end) {
    if (start == null || end == null || start.incarnation() != end.incarnation()) {
      return null;
    }
    final Map<LoadMeasure, Long> increases = new EnumMap<>(LoadMeasure.class);
    for (final var measure : LoadMeasure.values()) {
      final var startCount = start.totals().get(measure);
      final var endCount = end.totals().get(measure);
      if (startCount == null || endCount == null || endCount < startCount) {
        return null;
      }
      increases.put(measure, endCount - startCount);
    }
    return increases;
  }
}

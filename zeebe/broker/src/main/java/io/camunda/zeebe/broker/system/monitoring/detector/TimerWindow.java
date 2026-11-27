/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Turns the cumulative histograms of timers into the distribution of what was recorded since the
 * previous sample, per health tree child. This is the same calculation as {@code
 * histogram_quantile(rate(...))} on a dashboard, so the quantiles match what Grafana shows.
 *
 * <p>It relies on the bucket counts growing over the lifetime of the timer, as with the Prometheus
 * registry. A timer whose counts went down (a registry that resets, or a re-registered meter) is
 * skipped for one sample and measured from its new counts afterwards.
 */
final class TimerWindow {

  private final Map<Meter.Id, Counts> previous = new HashMap<>();

  /**
   * @param child the health tree child of each timer; timers of the same child are added up
   * @return what each child recorded since the previous call; a timer seen for the first time
   *     contributes nothing yet
   */
  Map<String, Distribution> sample(
      final Collection<Timer> timers, final Function<Meter.Id, String> child) {
    final var distributions = new HashMap<String, Distribution>();
    final var seen = new HashSet<Meter.Id>();
    for (final var timer : timers) {
      final var id = timer.getId();
      seen.add(id);
      final var current = Counts.of(timer);
      final var before = previous.put(id, current);
      final var key = child.apply(id);
      final var distribution = distributions.computeIfAbsent(key, ignored -> new Distribution());
      if (before != null) {
        distribution.addDifference(before, current);
      }
    }
    previous.keySet().retainAll(seen);
    return distributions;
  }

  /** Counts of one timer at one point in time; bounds are in milliseconds, counts cumulative. */
  private record Counts(long count, double[] bounds, double[] cumulative, double maxMillis) {

    static Counts of(final Timer timer) {
      final var snapshot = timer.takeSnapshot();
      final CountAtBucket[] buckets = snapshot.histogramCounts();
      final var bounds = new double[buckets.length];
      final var cumulative = new double[buckets.length];
      for (int i = 0; i < buckets.length; i++) {
        bounds[i] = buckets[i].bucket(TimeUnit.MILLISECONDS);
        cumulative[i] = buckets[i].count();
      }
      return new Counts(snapshot.count(), bounds, cumulative, snapshot.max(TimeUnit.MILLISECONDS));
    }
  }

  /** What was recorded in one window: the count, and the cumulative count up to each bound. */
  static final class Distribution {

    private final NavigableMap<Double, Double> cumulative = new TreeMap<>();
    private long count;
    private double maxMillis;

    long count() {
      return count;
    }

    /** Whether the timers have histogram buckets; without, quantiles fall back to the maximum. */
    boolean hasBuckets() {
      return !cumulative.isEmpty();
    }

    /**
     * The smallest bucket bound below which at least {@code quantile} of the window's values lie,
     * in milliseconds. Values above the largest bucket are reported as the timer's recent maximum.
     * Empty if nothing was recorded in the window.
     */
    OptionalDouble quantileMillis(final double quantile) {
      if (count == 0) {
        return OptionalDouble.empty();
      }
      final var target = Math.ceil(quantile * count);
      for (final var bucket : cumulative.entrySet()) {
        if (bucket.getValue() >= target) {
          return OptionalDouble.of(bucket.getKey());
        }
      }
      final var largestBound = cumulative.isEmpty() ? 0 : cumulative.lastKey();
      return OptionalDouble.of(Math.max(largestBound, maxMillis));
    }

    private void addDifference(final Counts before, final Counts current) {
      final var countDifference = current.count() - before.count();
      if (countDifference < 0 || !Arrays.equals(before.bounds(), current.bounds())) {
        return;
      }
      for (int i = 0; i < current.bounds().length; i++) {
        if (current.cumulative()[i] < before.cumulative()[i]) {
          return;
        }
      }

      count += countDifference;
      maxMillis = Math.max(maxMillis, current.maxMillis());
      for (int i = 0; i < current.bounds().length; i++) {
        cumulative.merge(
            current.bounds()[i], current.cumulative()[i] - before.cumulative()[i], Double::sum);
      }
    }
  }
}

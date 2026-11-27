/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Watches the rate of appended records per partition this broker leads, relative to that
 * partition's own recent rate. Thresholds are ratios: 3 means three times the recent rate. The
 * recent rate is an exponentially weighted average with a half-life of {@link #HALF_LIFE}, so a
 * sustained higher load becomes the new normal and ends the condition.
 *
 * <p>Rates below {@link #MINIMUM_RATE} never count as a spike, so a partition going from idle to a
 * handful of records per second does not start one.
 */
public final class LoadSpikeDetector extends ThresholdDetector {

  static final String RECORD_APPENDED = "zeebe.log.appender.record.appended";
  static final double MINIMUM_RATE = 100;
  static final Duration HALF_LIFE = Duration.ofMinutes(2);

  private final MeterReader meters;
  private final LeaderPartitions leaders;
  private final CounterWindow window = new CounterWindow();
  private final Map<String, Rates> rates = new HashMap<>();
  private @Nullable Instant previousSample;

  public LoadSpikeDetector(
      final MeterRegistry registry,
      final LeaderPartitions leaders,
      final ConditionThresholds thresholds) {
    super("LoadSpike", thresholds);
    meters = new MeterReader(registry);
    this.leaders = leaders;
  }

  @Override
  public void sample(final Instant now) {
    final var appended =
        window.sample(meters.counts(RECORD_APPENDED), id -> MeterReader.child(id, ""));
    final var before = previousSample;
    previousSample = now;
    if (before == null || !now.isAfter(before)) {
      return;
    }

    final var seconds = Duration.between(before, now).toMillis() / 1000.0;
    final var observed = new HashSet<String>();
    appended.forEach(
        (child, records) -> {
          if (!leaders.isLeader(child)) {
            return;
          }
          observed.add(child);
          final var rate = records / seconds;
          final var partitionRates = rates.computeIfAbsent(child, ignored -> new Rates());
          if (partitionRates.average == null) {
            // the first window only sets the average; comparing against nothing would be a spike
            partitionRates.average = rate;
            return;
          }

          partitionRates.current = rate;
          partitionRates.averageAtSample = partitionRates.average;
          final var ratio =
              rate < MINIMUM_RATE ? 0 : rate / Math.max(partitionRates.average, MINIMUM_RATE);
          observe(child, ratio, now);
          partitionRates.average += weight(seconds) * (rate - partitionRates.average);
        });
    rates.keySet().retainAll(observed);
    forgetOthers(observed);
  }

  @Override
  protected String describe(final String child, final double value) {
    final var partitionRates = rates.get(child);
    final var current = partitionRates == null ? 0 : partitionRates.current;
    final var average = partitionRates == null ? 0 : partitionRates.averageAtSample;
    return String.format(
        Locale.ROOT,
        "appending %d records/s, %.1fx the recent average of %d records/s, above the threshold of"
            + " %.1fx",
        Math.round(current),
        value,
        Math.round(average),
        thresholds().threshold());
  }

  private static double weight(final double seconds) {
    return 1 - Math.pow(0.5, seconds / HALF_LIFE.toSeconds());
  }

  private static final class Rates {
    private @Nullable Double average;
    private double averageAtSample;
    private double current;
  }
}

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
import java.util.HashMap;
import java.util.Map;

/**
 * Watches a quantile of a timer, per partition, over each sample window. Thresholds are in
 * milliseconds. A window in which nothing was recorded counts as below the threshold.
 *
 * <p>Used for journal flushes (disk latency), commits (replication latency), processing latency and
 * GC pauses, which are all timers with histogram buckets.
 */
public final class LatencyDetector extends ThresholdDetector {

  private final MeterReader meters;
  private final String meterName;
  private final String description;
  private final double quantile;
  private final String nodeChild;
  private final TimerWindow window = new TimerWindow();
  private final Map<String, Boolean> withoutBuckets = new HashMap<>();

  /**
   * @param name the component name in the health tree
   * @param meterName the timer to watch
   * @param description what the timer measures, as it should read in an issue message
   * @param quantile the quantile to compare against the threshold, for example 0.99
   * @param nodeChild the child for timers that do not belong to a partition
   */
  public LatencyDetector(
      final String name,
      final MeterRegistry registry,
      final String meterName,
      final String description,
      final double quantile,
      final String nodeChild,
      final ConditionThresholds thresholds) {
    super(name, thresholds);
    meters = new MeterReader(registry);
    this.meterName = meterName;
    this.description = description;
    this.quantile = quantile;
    this.nodeChild = nodeChild;
  }

  @Override
  public void sample(final Instant now) {
    final var distributions =
        window.sample(meters.timers(meterName), id -> MeterReader.child(id, nodeChild));
    distributions.forEach(
        (child, distribution) -> {
          withoutBuckets.put(child, !distribution.hasBuckets());
          observe(child, distribution.quantileMillis(quantile).orElse(0), now);
        });
    withoutBuckets.keySet().retainAll(distributions.keySet());
    forgetOthers(distributions.keySet());
  }

  @Override
  protected String describe(final String child, final double value) {
    // a timer without histogram buckets, such as jvm.gc.pause, only tells its recent maximum
    final var statistic =
        withoutBuckets.getOrDefault(child, false) ? "maximum" : "p" + Math.round(quantile * 100);
    return "%s %s reached %s ms, above the threshold of %s ms"
        .formatted(statistic, description, Math.round(value), Math.round(thresholds().threshold()));
  }
}

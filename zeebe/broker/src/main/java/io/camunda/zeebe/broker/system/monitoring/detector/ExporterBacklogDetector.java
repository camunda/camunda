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
import java.util.HashSet;

/**
 * Watches how many records each exporter is behind the committed position, per partition this
 * broker leads. Thresholds are in records. A child is named {@code <partition>:<exporter id>}.
 */
public final class ExporterBacklogDetector extends ThresholdDetector {

  static final String COMMITTED_POSITION = "zeebe.log.appender.last.committed.position";
  static final String EXPORTED_POSITION = "zeebe.exporter.last.exported.position";
  private static final String EXPORTER_TAG = "exporter";

  private final MeterReader meters;
  private final LeaderPartitions leaders;

  public ExporterBacklogDetector(
      final MeterRegistry registry,
      final LeaderPartitions leaders,
      final ConditionThresholds thresholds) {
    super("ExporterBacklog", thresholds);
    meters = new MeterReader(registry);
    this.leaders = leaders;
  }

  @Override
  public void sample(final Instant now) {
    final var committed = new HashMap<String, Double>();
    meters
        .gauges(COMMITTED_POSITION)
        .forEach((id, position) -> committed.merge(MeterReader.child(id, ""), position, Math::max));

    final var observed = new HashSet<String>();
    meters
        .gauges(EXPORTED_POSITION)
        .forEach(
            (id, exported) -> {
              final var partition = MeterReader.child(id, "");
              final var committedPosition = committed.get(partition);
              if (committedPosition == null || exported < 0 || !leaders.isLeader(partition)) {
                return;
              }
              final var child = partition + ":" + id.getTag(EXPORTER_TAG);
              observed.add(child);
              observe(child, Math.max(0, committedPosition - exported), now);
            });
    forgetOthers(observed);
  }

  @Override
  protected String describe(final String child, final double value) {
    return "%s is %d records behind the committed position, above the threshold of %d"
        .formatted(
            child.substring(child.indexOf(':') + 1),
            Math.round(value),
            Math.round(thresholds().threshold()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.zeebe.broker.system.configuration.HealthDetectorsCfg;
import io.camunda.zeebe.broker.system.configuration.HealthDetectorsCfg.DetectorCfg;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Creates the sampled detectors with their default thresholds, overridden by the configuration. The
 * defaults are guesses for a first look at real clusters, not tuned values.
 */
public final class HealthDetectorFactory {

  static final ConditionThresholds JOURNAL_FLUSH_LATENCY =
      thresholds(100, 50, Duration.ZERO, Duration.ofSeconds(30));
  static final ConditionThresholds COMMIT_LATENCY =
      thresholds(500, 250, Duration.ZERO, Duration.ofSeconds(30));
  static final ConditionThresholds PROCESSING_LATENCY =
      thresholds(1000, 500, Duration.ofSeconds(10), Duration.ofSeconds(30));
  static final ConditionThresholds GC_PAUSES =
      thresholds(200, 100, Duration.ZERO, Duration.ofSeconds(60));
  static final ConditionThresholds EXPORTER_BACKLOG =
      thresholds(50_000, 10_000, Duration.ofSeconds(60), Duration.ofSeconds(30));
  static final ConditionThresholds LOAD_SPIKE =
      thresholds(3, 1.5, Duration.ofSeconds(20), Duration.ofSeconds(60));
  static final ConditionThresholds BACKPRESSURE =
      thresholds(5, 1, Duration.ofSeconds(30), Duration.ofSeconds(60));

  private static final double QUANTILE = 0.99;

  private HealthDetectorFactory() {}

  public static List<ThresholdDetector> create(
      final HealthDetectorsCfg cfg, final MeterRegistry registry, final LeaderPartitions leaders) {
    final var detectors = new ArrayList<ThresholdDetector>();
    add(
        detectors,
        cfg,
        "journalFlushLatency",
        JOURNAL_FLUSH_LATENCY,
        t ->
            new LatencyDetector(
                "JournalFlushLatency",
                registry,
                "atomix.journal.flush.time",
                "journal flush latency",
                QUANTILE,
                "Node",
                t));
    add(
        detectors,
        cfg,
        "commitLatency",
        COMMIT_LATENCY,
        t ->
            new LatencyDetector(
                "CommitLatency",
                registry,
                "zeebe.log.appender.commit.latency",
                "commit latency",
                QUANTILE,
                "Node",
                t));
    add(
        detectors,
        cfg,
        "processingLatency",
        PROCESSING_LATENCY,
        t ->
            new LatencyDetector(
                "ProcessingLatency",
                registry,
                "zeebe.stream.processor.latency",
                "time from writing a command to processing it",
                QUANTILE,
                "Node",
                t));
    add(
        detectors,
        cfg,
        "gcPauses",
        GC_PAUSES,
        t ->
            new LatencyDetector(
                "GcPauses", registry, "jvm.gc.pause", "GC pause", QUANTILE, "Jvm", t));
    add(
        detectors,
        cfg,
        "exporterBacklog",
        EXPORTER_BACKLOG,
        t -> new ExporterBacklogDetector(registry, leaders, t));
    add(detectors, cfg, "loadSpike", LOAD_SPIKE, t -> new LoadSpikeDetector(registry, leaders, t));
    add(
        detectors,
        cfg,
        "backpressure",
        BACKPRESSURE,
        t -> new BackpressureDetector(registry, leaders, t));
    return detectors;
  }

  /** Whether a detector that has no thresholds, such as {@code diskSpace}, is enabled. */
  public static boolean isEnabled(final HealthDetectorsCfg cfg, final String name) {
    final var enabled = cfg.detector(name).getEnabled();
    return enabled == null || enabled;
  }

  private static void add(
      final List<ThresholdDetector> detectors,
      final HealthDetectorsCfg cfg,
      final String name,
      final ConditionThresholds defaults,
      final Function<ConditionThresholds, ThresholdDetector> create) {
    if (isEnabled(cfg, name)) {
      detectors.add(create.apply(resolve(cfg.detector(name), defaults)));
    }
  }

  static ConditionThresholds resolve(final DetectorCfg cfg, final ConditionThresholds defaults) {
    final var threshold = cfg.getThreshold() != null ? cfg.getThreshold() : defaults.threshold();
    final double clearThreshold;
    if (cfg.getClearThreshold() != null) {
      clearThreshold = cfg.getClearThreshold();
    } else {
      // keep the default ratio between the two when only the threshold is configured
      clearThreshold = threshold * (defaults.clearThreshold() / defaults.threshold());
    }
    return new ConditionThresholds(
        threshold,
        clearThreshold,
        cfg.getMinDuration() != null ? cfg.getMinDuration() : defaults.minDuration(),
        cfg.getClearDuration() != null ? cfg.getClearDuration() : defaults.clearDuration());
  }

  private static ConditionThresholds thresholds(
      final double threshold,
      final double clearThreshold,
      final Duration minDuration,
      final Duration clearDuration) {
    return new ConditionThresholds(threshold, clearThreshold, minDuration, clearDuration);
  }
}

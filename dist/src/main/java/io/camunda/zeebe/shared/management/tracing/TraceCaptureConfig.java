/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;

/**
 * Validated, bounded configuration for a time-boxed cluster trace capture window. Hard caps on
 * duration, sampling rate, buffered spans, and wire bytes keep on-demand captures safe on
 * production clusters.
 */
@NullMarked
public record TraceCaptureConfig(
    Duration duration, double samplingProbability, int maxTracesPerSecond, int maxSpansPerNode) {

  public static final Duration DEFAULT_DURATION = Duration.ofSeconds(60);
  public static final Duration MIN_DURATION = Duration.ofSeconds(1);
  public static final Duration MAX_DURATION = Duration.ofMinutes(15);

  /** How long buffered spans remain downloadable after a capture window expires. */
  public static final Duration DEFAULT_RETENTION_AFTER_EXPIRY = Duration.ofMinutes(15);

  public static final double DEFAULT_SAMPLING_PROBABILITY = 0.0;

  public static final int DEFAULT_MAX_TRACES_PER_SECOND = 20;
  public static final int MIN_TRACES_PER_SECOND = 1;
  public static final int MAX_TRACES_PER_SECOND_LIMIT = 500;

  public static final int DEFAULT_MAX_SPANS_PER_NODE = 10_000;
  public static final int MIN_SPANS_PER_NODE = 100;
  public static final int MAX_SPANS_PER_NODE_LIMIT = 50_000;

  /** Uncompressed OTLP JSON export budget per node (~600 KiB - 1 MiB gzipped on the wire). */
  public static final int MAX_NODE_EXPORT_BYTES = 8 * 1024 * 1024;

  /** Total uncompressed OTLP JSON budget across all cluster members in one export. */
  public static final int MAX_CLUSTER_EXPORT_BYTES = 32 * 1024 * 1024;

  public TraceCaptureConfig {
    if (duration == null
        || duration.compareTo(MIN_DURATION) < 0
        || duration.compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException(
          "duration must be between %s and %s, but was %s"
              .formatted(MIN_DURATION, MAX_DURATION, duration));
    }
    if (Double.isNaN(samplingProbability)
        || samplingProbability < 0.0
        || samplingProbability > 1.0) {
      throw new IllegalArgumentException(
          "samplingProbability must be between 0.0 and 1.0, but was " + samplingProbability);
    }
    if (maxTracesPerSecond < MIN_TRACES_PER_SECOND
        || maxTracesPerSecond > MAX_TRACES_PER_SECOND_LIMIT) {
      throw new IllegalArgumentException(
          "maxTracesPerSecond must be between %d and %d, but was %d"
              .formatted(MIN_TRACES_PER_SECOND, MAX_TRACES_PER_SECOND_LIMIT, maxTracesPerSecond));
    }
    if (maxSpansPerNode < MIN_SPANS_PER_NODE || maxSpansPerNode > MAX_SPANS_PER_NODE_LIMIT) {
      throw new IllegalArgumentException(
          "maxSpansPerNode must be between %d and %d, but was %d"
              .formatted(MIN_SPANS_PER_NODE, MAX_SPANS_PER_NODE_LIMIT, maxSpansPerNode));
    }
  }

  public static TraceCaptureConfig defaults() {
    return new TraceCaptureConfig(
        DEFAULT_DURATION,
        DEFAULT_SAMPLING_PROBABILITY,
        DEFAULT_MAX_TRACES_PER_SECOND,
        DEFAULT_MAX_SPANS_PER_NODE);
  }

  public static TraceCaptureConfig fromRequest(
      final @Nullable String duration,
      final @Nullable Double samplingProbability,
      final @Nullable Integer maxTracesPerSecond,
      final @Nullable Integer maxSpansPerNode) {
    return new TraceCaptureConfig(
        parseDuration(duration),
        samplingProbability != null ? samplingProbability : DEFAULT_SAMPLING_PROBABILITY,
        maxTracesPerSecond != null ? maxTracesPerSecond : DEFAULT_MAX_TRACES_PER_SECOND,
        maxSpansPerNode != null ? maxSpansPerNode : DEFAULT_MAX_SPANS_PER_NODE);
  }

  private static Duration parseDuration(final @Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT_DURATION;
    }
    try {
      return DurationStyle.detectAndParse(raw.trim());
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid duration '%s' (expected e.g. '60s', '5m', or 'PT60S')".formatted(raw), e);
    }
  }
}

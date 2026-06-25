/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link MetricExporter} that writes OTel {@link MetricData} into a Micrometer {@link
 * MeterRegistry}. Used as the downstream exporter for the self-metrics {@link
 * io.opentelemetry.sdk.metrics.SdkMeterProvider} that captures OTel SDK internal telemetry (e.g.
 * {@code otel.sdk.log.created}).
 *
 * <p>Instrument mapping:
 *
 * <ul>
 *   <li>{@code LONG_SUM} / {@code DOUBLE_SUM} → Micrometer {@link Counter}
 *   <li>{@code LONG_GAUGE} / {@code DOUBLE_GAUGE} → Micrometer {@link
 *       io.micrometer.core.instrument.Gauge} backed by {@link AtomicLong}
 *   <li>{@code HISTOGRAM} → Micrometer {@link Timer}; unit {@code "s"} is converted to nanoseconds
 *   <li>All other types → no-op
 * </ul>
 *
 * <p>Every meter receives the fixed tag {@value COMPONENT_TAG_KEY}={@value COMPONENT_TAG_VALUE}.
 */
@NullMarked
final class MicrometerMetricExporter implements MetricExporter {

  static final String COMPONENT_TAG_KEY = "otel.component.origin";
  static final String COMPONENT_TAG_VALUE = "analytics_exporter";

  /** Pre-computed tags for the empty-attributes case — avoids per-call allocation. */
  static final Tags ORIGIN_ONLY_TAGS = Tags.of(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE);

  private final MeterRegistry registry;

  // Caches keyed by "metricName|tags.toString()"
  private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

  MicrometerMetricExporter(final MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public CompletableResultCode export(final Collection<MetricData> metrics) {
    for (final MetricData metric : metrics) {
      switch (metric.getType()) {
        case LONG_SUM -> exportLongSum(metric);
        case DOUBLE_SUM -> exportDoubleSum(metric);
        case LONG_GAUGE -> exportLongGauge(metric);
        case DOUBLE_GAUGE -> exportDoubleGauge(metric);
        case HISTOGRAM -> exportHistogram(metric);
        default -> {
          // Summary and exponential histogram: no-op
        }
      }
    }
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public AggregationTemporality getAggregationTemporality(final InstrumentType instrumentType) {
    return AggregationTemporality.DELTA;
  }

  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  // -------------------------------------------------------------------------
  // Export helpers
  // -------------------------------------------------------------------------

  private void exportLongSum(final MetricData metric) {
    for (final LongPointData point : metric.getLongSumData().getPoints()) {
      final Tags tags = toMicrometerTags(point.getAttributes());
      final String key = metric.getName() + "|" + tags;
      final Counter counter =
          counters.computeIfAbsent(
              key, k -> Counter.builder(metric.getName()).tags(tags).register(registry));
      counter.increment(point.getValue());
    }
  }

  private void exportDoubleSum(final MetricData metric) {
    for (final DoublePointData point : metric.getDoubleSumData().getPoints()) {
      final Tags tags = toMicrometerTags(point.getAttributes());
      final String key = metric.getName() + "|" + tags;
      final Counter counter =
          counters.computeIfAbsent(
              key, k -> Counter.builder(metric.getName()).tags(tags).register(registry));
      counter.increment(point.getValue());
    }
  }

  private void exportLongGauge(final MetricData metric) {
    for (final LongPointData point : metric.getLongGaugeData().getPoints()) {
      final Tags tags = toMicrometerTags(point.getAttributes());
      final String key = metric.getName() + "|" + tags;
      final AtomicLong backing =
          gauges.computeIfAbsent(
              key,
              k -> {
                final AtomicLong atomicLong = new AtomicLong();
                io.micrometer.core.instrument.Gauge.builder(
                        metric.getName(), atomicLong, AtomicLong::doubleValue)
                    .tags(tags)
                    .register(registry);
                return atomicLong;
              });
      backing.set(point.getValue());
    }
  }

  private void exportDoubleGauge(final MetricData metric) {
    for (final DoublePointData point : metric.getDoubleGaugeData().getPoints()) {
      final Tags tags = toMicrometerTags(point.getAttributes());
      final String key = metric.getName() + "|" + tags;
      // Use AtomicLong with bit-cast double to avoid an extra class
      final AtomicLong backing =
          gauges.computeIfAbsent(
              key,
              k -> {
                final AtomicLong atomicLong = new AtomicLong();
                io.micrometer.core.instrument.Gauge.builder(
                        metric.getName(), atomicLong, a -> Double.longBitsToDouble(a.get()))
                    .tags(tags)
                    .register(registry);
                return atomicLong;
              });
      backing.set(Double.doubleToRawLongBits(point.getValue()));
    }
  }

  private void exportHistogram(final MetricData metric) {
    final boolean isSeconds = "s".equals(metric.getUnit());
    for (final HistogramPointData point : metric.getHistogramData().getPoints()) {
      final Tags tags = toMicrometerTags(point.getAttributes());
      final String key = metric.getName() + "|" + tags;
      final Timer timer =
          timers.computeIfAbsent(
              key, k -> Timer.builder(metric.getName()).tags(tags).register(registry));
      final long nanos =
          isSeconds ? (long) (point.getSum() * 1_000_000_000L) : (long) point.getSum();
      timer.record(nanos, TimeUnit.NANOSECONDS);
    }
  }

  // -------------------------------------------------------------------------
  // Tag conversion
  // -------------------------------------------------------------------------

  /**
   * Converts OTel {@link Attributes} to Micrometer {@link Tags}, appending the fixed component
   * origin tag. Returns the pre-computed {@link #ORIGIN_ONLY_TAGS} singleton when attributes are
   * empty to avoid per-call allocation on the hot path.
   */
  static Tags toMicrometerTags(final Attributes attributes) {
    if (attributes.isEmpty()) {
      return ORIGIN_ONLY_TAGS;
    }
    final var pairs = new ArrayList<String>(attributes.size() * 2 + 2);
    attributes.forEach(
        (key, value) -> {
          pairs.add(key.getKey());
          pairs.add(String.valueOf(value));
        });
    pairs.add(COMPONENT_TAG_KEY);
    pairs.add(COMPONENT_TAG_VALUE);
    return Tags.of(pairs.toArray(new String[0]));
  }
}

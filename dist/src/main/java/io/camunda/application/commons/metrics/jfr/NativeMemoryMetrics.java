/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics.jfr;

import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.NMT_USAGE;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.NMT_USAGE_TOTAL;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.NativeMemoryValueType.COMMITTED;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.NativeMemoryValueType.RESERVED;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.NativeMemoryUsageKeys.VALUE;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.RSS;
import static io.camunda.application.commons.metrics.jfr.NativeMemoryMetricsDoc.RSS_PEAK;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * Tracks RSS and native memory usage (as provided by the JVM's Native Memory Tracking) capabilities
 * and registers equivalent gauges to Micrometer.
 *
 * <p>See {@link NativeMemoryMetricsDoc} for documentation.
 *
 * <p>Note that the native memory metrics are only available if NMT is enabled via {@code
 * -XX:NativeMemoryTracking} (e.g. {@code -XX:NativeMemoryTracking=summary}).
 */
public final class NativeMemoryMetrics {
  private final MemoryUsage residentSetSize = new MemoryUsage();
  private final ConcurrentMap<String, NativeMemoryUsage> nativeMemoryUsage =
      new ConcurrentHashMap<>();
  private final NativeMemoryUsage nativeMemoryUsageTotal = new NativeMemoryUsage();

  public void registerEvents(final RecordingStream stream, final MeterRegistry registry) {
    stream.enable(RSS.getJfrEventName()).withPeriod(Duration.ofSeconds(5)).withoutStackTrace();
    stream.onEvent(RSS.getJfrEventName(), residentSetSize::onEvent);
    residentSetSize.register(registry);

    stream
        .enable(NMT_USAGE_TOTAL.getJfrEventName())
        .withPeriod(Duration.ofSeconds(5))
        .withoutStackTrace();
    stream.onEvent(NMT_USAGE_TOTAL.getJfrEventName(), nativeMemoryUsageTotal::onEvent);
    nativeMemoryUsageTotal.register(NMT_USAGE_TOTAL, registry);

    stream
        .enable(NMT_USAGE.getJfrEventName())
        .withPeriod(Duration.ofSeconds(5))
        .withoutStackTrace();
    stream.onEvent(
        NMT_USAGE.getJfrEventName(),
        event ->
            nativeMemoryUsage
                .computeIfAbsent(event.getString("type"), type -> registerNmtGauge(type, registry))
                .onEvent(event));
  }

  private NativeMemoryUsage registerNmtGauge(final String type, final MeterRegistry registry) {
    final var usage = new NativeMemoryUsage();
    usage.register(
        NMT_USAGE,
        registry,
        Tag.of(NativeMemoryMetricsDoc.NativeMemoryUsageKeys.TYPE.asString(), type));

    return usage;
  }

  private record MemoryUsage(AtomicLong size, AtomicLong peak) {
    private MemoryUsage() {
      this(new AtomicLong(), new AtomicLong());
    }

    private void onEvent(final RecordedEvent event) {
      size.set(event.getLong("size"));
      peak.set(event.getLong("peak"));
    }

    private void register(final MeterRegistry registry) {
      Gauge.builder(RSS.getName(), size, AtomicLong::get)
          .description(RSS.getDescription())
          .baseUnit("bytes")
          .register(registry);
      Gauge.builder(RSS_PEAK.getName(), peak, AtomicLong::get)
          .description(RSS_PEAK.getDescription())
          .baseUnit("bytes")
          .register(registry);
    }
  }

  private record NativeMemoryUsage(AtomicLong reserved, AtomicLong committed) {
    private NativeMemoryUsage() {
      this(new AtomicLong(), new AtomicLong());
    }

    private void onEvent(final RecordedEvent event) {
      reserved.set(event.getLong("reserved"));
      committed.set(event.getLong("committed"));
    }

    private void register(
        final NativeMemoryMetricsDoc doc, final MeterRegistry registry, final Tag... tags) {
      Gauge.builder(doc.getName(), reserved, AtomicLong::get)
          .description(doc.getDescription())
          .baseUnit("bytes")
          .tags(Tags.of(tags).and(Tag.of(VALUE.asString(), RESERVED.value())))
          .register(registry);
      Gauge.builder(doc.getName(), committed, AtomicLong::get)
          .description(doc.getDescription())
          .baseUnit("bytes")
          .tags(Tags.of(tags).and(Tag.of(VALUE.asString(), COMMITTED.value())))
          .register(registry);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.MicrometerMetricExporter.COMPONENT_TAG_KEY;
import static io.camunda.exporter.analytics.MicrometerMetricExporter.COMPONENT_TAG_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MicrometerMetricExporter}: verifies that exported OTel {@link MetricData} is
 * correctly bridged into a Micrometer {@link SimpleMeterRegistry}.
 */
class MicrometerMetricExporterTest {

  private SimpleMeterRegistry registry;
  private MicrometerMetricExporter exporter;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    exporter = new MicrometerMetricExporter(registry);
  }

  @Test
  void shouldRegisterCounterForSumMetric() {
    // given — a LONG_SUM metric produced by an OTel counter
    final var inMemoryReader = InMemoryMetricReader.create();
    final Meter otelMeter =
        SdkMeterProvider.builder()
            .registerMetricReader(inMemoryReader)
            .build()
            .meterBuilder("test")
            .build();

    otelMeter.counterBuilder("my.counter").build().add(3, Attributes.empty());

    final Collection<MetricData> metrics = inMemoryReader.collectAllMetrics();
    assertThat(metrics).isNotEmpty();

    // when
    exporter.export(metrics);

    // then — Micrometer Counter with correct name and count
    final Counter counter =
        registry.find("my.counter").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(3.0);
  }

  @Test
  void shouldUpdateGaugeForGaugeMetric() {
    // given — a LONG_GAUGE metric produced by an OTel observable gauge
    final var inMemoryReader = InMemoryMetricReader.create();
    final Meter otelMeter =
        SdkMeterProvider.builder()
            .registerMetricReader(inMemoryReader)
            .build()
            .meterBuilder("test")
            .build();

    otelMeter
        .gaugeBuilder("my.gauge")
        .ofLongs()
        .buildWithCallback(measurement -> measurement.record(42L));

    final Collection<MetricData> metrics = inMemoryReader.collectAllMetrics();
    assertThat(metrics).isNotEmpty();

    // when
    exporter.export(metrics);

    // then — Micrometer Gauge with correct value
    final Gauge gauge =
        registry.find("my.gauge").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(42.0);
  }

  @Test
  void shouldRecordHistogramAsTimer() {
    // given — a HISTOGRAM metric with unit "s" produced by an OTel histogram
    final var inMemoryReader = InMemoryMetricReader.create();
    final Meter otelMeter =
        SdkMeterProvider.builder()
            .registerMetricReader(inMemoryReader)
            .build()
            .meterBuilder("test")
            .build();

    // Record 0.1s — should be converted to 100_000_000 ns
    otelMeter.histogramBuilder("my.latency").setUnit("s").build().record(0.1, Attributes.empty());

    final Collection<MetricData> metrics = inMemoryReader.collectAllMetrics();
    assertThat(metrics).isNotEmpty();

    // when
    exporter.export(metrics);

    // then — Micrometer Timer has non-zero total time
    final Timer timer =
        registry.find("my.latency").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).timer();
    assertThat(timer).isNotNull();
    assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void shouldTagAllMetricsWithComponentOrigin() {
    // given — a counter and a gauge
    final var inMemoryReader = InMemoryMetricReader.create();
    final Meter otelMeter =
        SdkMeterProvider.builder()
            .registerMetricReader(inMemoryReader)
            .build()
            .meterBuilder("test")
            .build();

    otelMeter
        .counterBuilder("tagged.counter")
        .build()
        .add(1, Attributes.of(AttributeKey.stringKey("env"), "test"));
    otelMeter.gaugeBuilder("tagged.gauge").ofLongs().buildWithCallback(m -> m.record(7L));

    // when
    exporter.export(inMemoryReader.collectAllMetrics());

    // then — every exported meter has the origin tag
    assertThat(
            registry.find("tagged.counter").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).counter())
        .isNotNull();
    assertThat(registry.find("tagged.gauge").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).gauge())
        .isNotNull();
  }

  @Test
  void shouldAccumulateCounterAcrossMultipleExports() {
    // given — use delta reader so each collectAllMetrics() returns only the new increment
    final var inMemoryReader = InMemoryMetricReader.createDelta();
    final var otelCounter =
        SdkMeterProvider.builder()
            .registerMetricReader(inMemoryReader)
            .build()
            .meterBuilder("test")
            .build()
            .counterBuilder("acc.counter")
            .build();

    // first export: add 2
    otelCounter.add(2, Attributes.empty());
    exporter.export(inMemoryReader.collectAllMetrics());

    // second export: add 3
    otelCounter.add(3, Attributes.empty());
    exporter.export(inMemoryReader.collectAllMetrics());

    // then — Micrometer counter accumulated both increments
    final Counter counter =
        registry.find("acc.counter").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(5.0);
  }
}

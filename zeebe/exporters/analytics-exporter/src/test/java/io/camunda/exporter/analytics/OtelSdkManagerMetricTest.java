/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MAX;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_EVENT_TIME_MIN;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_END;
import static io.camunda.exporter.analytics.AnalyticsAttributes.LOG_POSITION_START;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_EXPORT_WINDOW;
import static io.camunda.exporter.analytics.AnalyticsAttributes.METRIC_SEQUENCE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtelSdkManagerMetricTest {

  private InMemoryMetricReader metricReader;
  private OtelSdkManager manager;

  @BeforeEach
  void setUp() {
    metricReader = InMemoryMetricReader.create();
    manager =
        TestOtelSdkManager.inMemoryWithMetrics(InMemoryLogRecordExporter.create(), metricReader);
  }

  @Test
  void shouldRecordMetricByName() {
    // when
    manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());

    // then
    assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter")).isPresent();
  }

  @Test
  void shouldReuseCounterForSameName() {
    // when
    manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());
    manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());

    // then
    assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter"))
        .hasValueSatisfying(
            metric -> {
              final long total =
                  metric.getLongSumData().getPoints().stream()
                      .mapToLong(LongPointData::getValue)
                      .sum();
              assertThat(total).isEqualTo(2);
            });
  }

  @Test
  void shouldCreateSeparateCountersForDifferentNames() {
    // when
    manager.incrementMetric("counter.a", 100L, 1000L, Attributes.empty());
    manager.incrementMetric("counter.b", 200L, 2000L, Attributes.empty());

    // then
    final var metrics = metricReader.collectAllMetrics();
    assertThat(findMetric(metrics, "counter.a")).isPresent();
    assertThat(findMetric(metrics, "counter.b")).isPresent();
  }

  @Test
  void shouldPreserveDimensionAttributes() {
    // given
    final var attrs =
        Attributes.of(
            AttributeKey.stringKey("process.id"), "order", AttributeKey.longKey("version"), 3L);

    // when
    manager.incrementMetric("test.counter", 100L, 1000L, attrs);

    // then
    assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter"))
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .first()
                    .satisfies(
                        point -> {
                          assertThat(
                                  point.getAttributes().get(AttributeKey.stringKey("process.id")))
                              .isEqualTo("order");
                          assertThat(point.getAttributes().get(AttributeKey.longKey("version")))
                              .isEqualTo(3L);
                        }));
  }

  @Test
  void shouldEmitExportWindowGaugeWithMetadata() {
    // given
    manager.incrementMetric("test.counter", 100L, 5000L, Attributes.empty());
    manager.incrementMetric("test.counter", 200L, 6000L, Attributes.empty());

    // when
    final var metrics = metricReader.collectAllMetrics();

    // then
    assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongGaugeData().getPoints())
                    .first()
                    .satisfies(
                        point -> {
                          assertThat(point.getValue()).isEqualTo(2);
                          final var attrs = point.getAttributes();
                          assertThat(attrs.get(METRIC_SEQUENCE_NUMBER)).isEqualTo(1L);
                          assertThat(attrs.get(LOG_POSITION_START)).isEqualTo(100L);
                          assertThat(attrs.get(LOG_POSITION_END)).isEqualTo(200L);
                          assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isEqualTo(5000L);
                          assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isEqualTo(6000L);
                        }));
  }

  @Test
  void shouldIncrementFlushSequenceAcrossCollections() {
    // given — two collection cycles
    manager.incrementMetric("test.counter", 100L, 1000L, Attributes.empty());
    metricReader.collectAllMetrics(); // flush_sequence = 1

    manager.incrementMetric("test.counter", 200L, 2000L, Attributes.empty());

    // when
    final var metrics = metricReader.collectAllMetrics();

    // then
    assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongGaugeData().getPoints())
                    .first()
                    .satisfies(
                        point ->
                            assertThat(point.getAttributes().get(METRIC_SEQUENCE_NUMBER))
                                .isEqualTo(2L)));
  }

  @Test
  void shouldResetWindowAfterCollection() {
    // given
    manager.incrementMetric("test.counter", 100L, 5000L, Attributes.empty());
    metricReader.collectAllMetrics(); // resets window

    manager.incrementMetric("test.counter", 300L, 8000L, Attributes.empty());

    // when
    final var metrics = metricReader.collectAllMetrics();

    // then — should reflect only the second event
    assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongGaugeData().getPoints())
                    .first()
                    .satisfies(
                        point -> {
                          assertThat(point.getValue()).isEqualTo(1);
                          final var attrs = point.getAttributes();
                          assertThat(attrs.get(LOG_POSITION_START)).isEqualTo(300L);
                          assertThat(attrs.get(LOG_POSITION_END)).isEqualTo(300L);
                          assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isEqualTo(8000L);
                          assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isEqualTo(8000L);
                        }));
  }

  @Test
  void shouldEmitHeartbeatGaugeWhenNoEvents() {
    // when — no incrementMetric calls
    final var metrics = metricReader.collectAllMetrics();

    // then — gauge still emitted as heartbeat with event_count=0
    assertThat(findMetric(metrics, METRIC_EXPORT_WINDOW))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongGaugeData().getPoints())
                    .first()
                    .satisfies(
                        point -> {
                          assertThat(point.getValue()).isZero();
                          final var attrs = point.getAttributes();
                          assertThat(attrs.get(METRIC_SEQUENCE_NUMBER)).isEqualTo(1L);
                          // Sentinels must not leak — only sequence_number present
                          assertThat(attrs.get(LOG_POSITION_START)).isNull();
                          assertThat(attrs.get(LOG_POSITION_END)).isNull();
                          assertThat(attrs.get(LOG_EVENT_TIME_MIN)).isNull();
                          assertThat(attrs.get(LOG_EVENT_TIME_MAX)).isNull();
                        }));
  }

  private static Optional<MetricData> findMetric(
      final Collection<MetricData> metrics, final String name) {
    return metrics.stream().filter(m -> m.getName().equals(name)).findFirst();
  }
}

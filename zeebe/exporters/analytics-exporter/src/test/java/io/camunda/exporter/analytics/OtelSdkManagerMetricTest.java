/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

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
    manager.incrementMetric("test.counter", Attributes.empty());

    // then
    assertThat(findMetric(metricReader.collectAllMetrics(), "test.counter")).isPresent();
  }

  @Test
  void shouldReuseCounterForSameName() {
    // when
    manager.incrementMetric("test.counter", Attributes.empty());
    manager.incrementMetric("test.counter", Attributes.empty());

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
    manager.incrementMetric("counter.a", Attributes.empty());
    manager.incrementMetric("counter.b", Attributes.empty());

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
    manager.incrementMetric("test.counter", attrs);

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

  private static Optional<MetricData> findMetric(
      final Collection<MetricData> metrics, final String name) {
    return metrics.stream().filter(m -> m.getName().equals(name)).findFirst();
  }
}

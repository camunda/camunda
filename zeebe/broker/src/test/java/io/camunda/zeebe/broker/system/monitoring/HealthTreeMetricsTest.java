/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class HealthTreeMetricsTest {
  private MeterRegistry meterRegistry;
  @AutoClose private HealthTreeMetrics metrics;

  @BeforeEach
  public void setup() {
    meterRegistry = new SimpleMeterRegistry();
    metrics = new HealthTreeMetrics(meterRegistry);
  }

  @Test
  public void shouldAddNodeMetricWhenComponentRegisters() {
    // given
    final var component = new DummyComponent("test-1");

    metrics.registerRelationship("test-1", "parent-2");
    metrics.registerRelationship("parent-2", "parent-1");
    // when
    metrics.registerNode(component);
    final var meter = meterRegistry.get(HealthMetricsDoc.NODES.getName()).gauge();

    // then
    assertThat(meter.getId().getTags())
        .containsAll(Tags.of("id", "test-1", "path", "parent-1/parent-2/test-1"));

    // when
    metrics.unregisterNode(component);
    metrics.unregisterRelationship("test-1", "parent-2");
    metrics.unregisterRelationship("parent-2", "parent-1");

    // then
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("generateReports")
  public void nodeMetricShouldReflectComponentStatus(
      final HealthStatus status, final double expected) {
    // given
    final var component = new DummyComponent("test-1");
    metrics.registerNode(component);
    final var meter = meterRegistry.get(HealthMetricsDoc.NODES.getName()).gauge();

    // when
    component.setReport(HealthReport.fromStatus(status, component));

    // then
    assertThat(meter.value()).isEqualTo(expected);
  }

  public static Stream<Arguments> generateReports() {
    return Stream.of(
        Arguments.of(HealthStatus.HEALTHY, 1.0),
        Arguments.of(HealthStatus.UNHEALTHY, -1.0),
        Arguments.of(HealthStatus.DEAD, -2.0));
  }

  @Test
  public void shouldCloseCorrectly() {
    // given
    final var component = new DummyComponent("test-1");
    metrics.registerRelationship("test-1", "test-2");
    metrics.registerNode(component);
    assertThat(meterRegistry.getMeters()).isNotEmpty();

    // when
    metrics.close();

    // then
    assertThat(meterRegistry.getMeters()).isEmpty();
  }

  private static class DummyComponent implements HealthMonitorable {
    private final String name;

    private Optional<HealthReport> report = Optional.empty();

    public DummyComponent(final String name) {
      this.name = name;
    }

    public void setReport(final HealthReport healthReport) {
      report = Optional.of(healthReport);
    }

    @Override
    public String componentName() {
      return name;
    }

    @Override
    public HealthReport getHealthReport() {
      return report.orElse(
          new HealthReport(name, HealthStatus.HEALTHY, null, Collections.emptyMap()));
    }

    @Override
    public void addFailureListener(final FailureListener failureListener) {}

    @Override
    public void removeFailureListener(final FailureListener failureListener) {}
  }
}

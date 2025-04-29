/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SchemaManagerMetricsTest {

  @Test
  void shouldRegisterTimeGaugeForSchemaInitTime() {
    // given
    final var registry = new SimpleMeterRegistry();

    // when
    final var metrics = new SchemaManagerMetrics(registry);

    // then
    final TimeGauge gauge = registry.find("camunda.schema.init.time").timeGauge();

    assertThat(gauge).isNotNull();
    assertThat(gauge.getId().getDescription())
        .isEqualTo("Duration of init schema operations (in milliseconds)");
  }

  @Test
  void shouldUpdateSchemaInitTime() throws InterruptedException {
    // given
    final var registry = new SimpleMeterRegistry();
    final var metrics = new SchemaManagerMetrics(registry);

    // when
    try (final var timer = metrics.startSchemaInitTimer()) {
      Thread.sleep(50); // simulate processing
    }

    // then
    final var measuredTime =
        registry.find("camunda.schema.init.time").timeGauge().value(TimeUnit.MILLISECONDS);

    assertThat(measuredTime).isGreaterThanOrEqualTo(50);
  }
}

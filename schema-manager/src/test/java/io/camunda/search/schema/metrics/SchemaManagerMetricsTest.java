/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class SchemaManagerMetricsTest {

  @Test
  void shouldRegisterTimeGaugeForSchemaInitTime() {
    // given
    final var registry = new SimpleMeterRegistry();

    // when
    final var metrics = new SchemaManagerMetrics(registry);

    // then
    final Timer timer = registry.find("camunda.schema.init.time").timer();

    assertThat(timer).isNotNull();
    assertThat(timer.getId().getDescription())
        .isEqualTo("Duration of initializing the schema in the secondary storage");
  }

  @Test
  void shouldUpdateSchemaInitTime() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var metrics = new SchemaManagerMetrics(registry);

    // when
    try (final var timer = metrics.startSchemaInitTimer()) {
      Awaitility.await().during(Duration.ofMillis(50)); // simulate processing
    }

    // then
    final var measuredTime = registry.find("camunda.schema.init.time").timer();

    assertThat(measuredTime.count()).isEqualTo(1);
    assertThat(measuredTime.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
  }
}

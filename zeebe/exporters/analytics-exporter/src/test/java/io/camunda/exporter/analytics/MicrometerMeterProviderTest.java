/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMeterProviderTest {

  private SimpleMeterRegistry registry;
  private MicrometerMeterProvider bridge;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    bridge = new MicrometerMeterProvider(registry);
  }

  @Test
  void shouldIncrementMicrometerCounterWhenOtelCounterAdds() {
    // given
    final var counter = bridge.get("test").counterBuilder("test.counter").build();

    // when
    counter.add(3, Attributes.empty());

    // then
    assertThat(registry.find("test.counter").counter()).isNotNull();
    assertThat(registry.find("test.counter").counter().count()).isEqualTo(3.0);
  }

  @Test
  void shouldIncludeOtelAttributesAsMicrometerTags() {
    // given
    final var counter = bridge.get("test").counterBuilder("test.counter").build();

    // when
    counter.add(1, Attributes.of(AttributeKey.stringKey("error.type"), "queue_full"));

    // then
    assertThat(registry.find("test.counter").tag("error.type", "queue_full").counter()).isNotNull();
  }

  @Test
  void shouldTagAllMetersWithComponentOrigin() {
    // given
    final var counter = bridge.get("test").counterBuilder("test.counter").build();

    // when
    counter.add(1, Attributes.empty());

    // then
    assertThat(
            registry
                .find("test.counter")
                .tag(
                    MicrometerMeterProvider.COMPONENT_TAG_KEY,
                    MicrometerMeterProvider.COMPONENT_TAG_VALUE)
                .counter())
        .isNotNull()
        .extracting(io.micrometer.core.instrument.Counter::count)
        .isEqualTo(1.0);
  }

  @Test
  void shouldUpdateGaugeWhenUpDownCounterAdds() {
    // given
    final var upDown = bridge.get("test").upDownCounterBuilder("test.queue").build();

    // when
    upDown.add(10, Attributes.empty());

    // then
    assertThat(registry.find("test.queue").gauge()).isNotNull();
    assertThat(registry.find("test.queue").gauge().value()).isEqualTo(10.0);
  }

  @Test
  void shouldReuseCounterAcrossMultipleAdds() {
    // given
    final var counter = bridge.get("test").counterBuilder("calls.total").build();

    // when
    counter.add(1, Attributes.empty());
    counter.add(1, Attributes.empty());
    counter.add(1, Attributes.empty());

    // then — one Counter registered (not three), count = 3
    assertThat(registry.find("calls.total").counters()).hasSize(1);
    assertThat(registry.find("calls.total").counter().count()).isEqualTo(3.0);
  }

  @Test
  void shouldRegisterGaugeThatEvaluatesCallbackAtScrapeTime() {
    // given
    bridge
        .get("test")
        .upDownCounterBuilder("test.queue.size")
        .buildWithCallback(measurement -> measurement.record(42L, Attributes.empty()));

    // when — read the gauge value (simulates Prometheus scrape)
    final var gauge = registry.find("test.queue.size").gauge();

    // then
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(42.0);
  }
}

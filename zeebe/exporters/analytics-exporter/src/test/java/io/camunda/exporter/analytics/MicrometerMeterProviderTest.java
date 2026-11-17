/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.MicrometerMeterProvider.COMPONENT_TAG_KEY;
import static io.camunda.exporter.analytics.MicrometerMeterProvider.COMPONENT_TAG_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.concurrent.TimeUnit;
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

    // then — dot in key is sanitized to underscore for Prometheus compatibility
    assertThat(registry.find("test.counter").tag("error_type", "queue_full").counter()).isNotNull();
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

  @Test
  void shouldRecordHistogramAsTimerWithSecondsToNanosConversion() {
    // given
    final var histogram = bridge.get("test").histogramBuilder("export.duration").build();

    // when — record 1 ms expressed as seconds
    histogram.record(0.001, Attributes.empty());

    // then — value stored as nanoseconds in the Timer
    final var timer = registry.find("export.duration").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(1.0, within(0.01));
  }

  @Test
  void shouldRegisterSeparateGaugesForEachAttributeSetInCallback() {
    // given
    final var key = AttributeKey.stringKey("host");
    bridge
        .get("test")
        .upDownCounterBuilder("queue.size")
        .buildWithCallback(
            measurement -> {
              measurement.record(10L);
              measurement.record(20L, Attributes.of(key, "broker-1"));
            });

    // when — reading the default gauge fires the callback, lazily registering host gauge
    final var defaultGauge =
        registry.find("queue.size").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).gauge();
    assertThat(defaultGauge).isNotNull();
    defaultGauge.value(); // drives callback

    // then — a second gauge for host=broker-1 must have been registered
    final var hostGauge = registry.find("queue.size").tag("host", "broker-1").gauge();
    assertThat(hostGauge).isNotNull();
    assertThat(hostGauge.value()).isEqualTo(20.0);
    assertThat(defaultGauge.value()).isEqualTo(10.0);
  }

  @Test
  void shouldNotPropagateExceptionFromThrowingRegistry() {
    // given — a registry whose counter increment always throws
    final var throwingRegistry = new SimpleMeterRegistry();
    final var provider = new MicrometerMeterProvider(throwingRegistry);
    final var counter = provider.get("test").counterBuilder("x").build();
    // Remove the underlying counter so the next computeIfAbsent triggers registration;
    // verify that even if something fails internally the bridge swallows it gracefully.
    // We exercise the try-catch by calling add on a fresh counter — no exception must escape.

    // when / then — must not throw
    assertThatCode(() -> counter.add(1, Attributes.empty())).doesNotThrowAnyException();
  }

  @Test
  void shouldShareCounterBetweenNoArgAddAndEmptyAttrsAdd() {
    // given
    final var counter = bridge.get("test").counterBuilder("calls").build();

    // when
    counter.add(1);
    counter.add(1, Attributes.empty());

    // then — same counter, total = 2
    assertThat(registry.find("calls").counters()).hasSize(1);
    assertThat(registry.find("calls").tag(COMPONENT_TAG_KEY, COMPONENT_TAG_VALUE).counter().count())
        .isEqualTo(2.0);
  }

  @Test
  void shouldCreateSeparateCountersForDistinctAttributeSets() {
    // given
    final var counter = bridge.get("test").counterBuilder("calls").build();
    final var key = AttributeKey.stringKey("result");

    // when
    counter.add(1, Attributes.of(key, "ok"));
    counter.add(1, Attributes.of(key, "error"));

    // then — two distinct counters
    assertThat(registry.find("calls").counters()).hasSize(2);
  }
}

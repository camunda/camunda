/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class StatefulGaugeTest {
  @AutoClose private MeterRegistry wrapped = new SimpleMeterRegistry();
  @AutoClose private final StatefulMeterRegistry registry = new StatefulMeterRegistry(wrapped);

  @Test
  void shouldRegisterAsGauge() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.set(3);

    // then
    final var registered = registry.get("foo").gauge();
    assertThat(registered.value()).isEqualTo(3);
  }

  @Test
  void shouldRegisterSameState() {
    // given
    final var first = StatefulGauge.builder("foo").register(registry);

    // when
    final var second = StatefulGauge.builder("foo").register(registry);

    // then
    assertThat(first.state()).isSameAs(second.state());
  }

  @Test
  void shouldReturnTheSameInstance() {
    // given
    final var first = StatefulGauge.builder("foo").register(registry);

    // when
    final var second = StatefulGauge.builder("foo").register(registry);

    // then
    assertThat(first).isSameAs(second);
    // they are not the same, since the underlying registry is registering a gauge; but we can still
    // check that it wraps the right gauge
    assertThat(registry.get("foo").gauge()).isSameAs(first.delegate());
  }

  @Test
  void shouldReportSameValueAsUnderlyingGauge() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.set(5);

    // then - measurements are taken from the underlying gauge
    final var registered = registry.get("foo").gauge();
    assertThat(registered.measure()).allSatisfy(m -> assertThat(m.getValue()).isEqualTo(5));
  }

  @Test
  void shouldRegisterToUnderlyingImplementationAsGauge() {
    // given
    final var prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    registry.add(prometheus);

    final var gauge = StatefulGauge.builder("test").tag("foo", "bar").register(registry);
    gauge.set(5);

    // when
    gauge.set(6);

    // then - measurements are taken from the underlying gauge
    final var metrics = prometheus.getPrometheusRegistry().scrape("test"::equals);
    final var registered = metrics.get(0);
    assertThat(registered.getDataPoints())
        .map(GaugeDataPointSnapshot.class::cast)
        .hasSize(1)
        .first()
        .returns(6.0, GaugeDataPointSnapshot::getValue)
        .returns(Labels.of("foo", "bar"), GaugeDataPointSnapshot::getLabels);
  }

  @Test
  void shouldRemoveAllGaugesWhenOneIsRemoved() {
    // given
    final var first = StatefulGauge.builder("foo").register(registry);
    final var second = StatefulGauge.builder("foo").register(registry);

    // when
    registry.remove(second);

    // then
    assertThatCode(() -> first.set(3))
        .as("does nothing, but still should not fail")
        .doesNotThrowAnyException();
    assertThat(registry.find("foo").meter()).isNull();
  }

  @Test
  void shouldNotFailOnSecondRemove() {
    // given
    final var first = StatefulGauge.builder("foo").register(registry);
    final var second = StatefulGauge.builder("foo").register(registry);
    registry.remove(second);

    // when / then
    assertThatCode(() -> registry.remove(first)).doesNotThrowAnyException();
    assertThat(registry.find("foo").meter()).isNull();
  }

  @Test
  void shouldReturnANoopGaugeWhenClosed() {
    // given
    registry.close();

    // when - note, this also implies that when closed, the same gauge is not always returned
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // then
    assertThatCode(() -> gauge.set(5)).doesNotThrowAnyException();
    assertThat(registry.find("foo").meter()).isNull();
  }

  @Test
  void shouldReturnDoubleValue() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.set(30.5223);

    // then - measurements are taken from the underlying gauge
    final var registered = registry.get("foo").gauge();
    assertThat(registered.measure()).allSatisfy(m -> assertThat(m.getValue()).isEqualTo(30.5223));
  }

  @Test
  void shouldReturnLongValue() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);
    final var highValue = Integer.MAX_VALUE + 1_000_000_000_000L;

    // when
    gauge.set(highValue);

    // then - measurements are taken from the underlying gauge
    final var registered = registry.get("foo").gauge();
    assertThat(registered.measure()).allSatisfy(m -> assertThat(m.getValue()).isEqualTo(highValue));
  }

  @Test
  void shouldIncrement() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.increment();
    gauge.increment();

    // then - measurements are taken from the underlying gauge
    final var registered = registry.get("foo").gauge();
    assertThat(registered.measure()).allSatisfy(m -> assertThat(m.getValue()).isEqualTo(2.0));
  }

  @Test
  void shouldDecrement() {
    // given
    final var gauge = StatefulGauge.builder("foo").register(registry);

    // when
    gauge.increment();
    gauge.decrement();

    // then - measurements are taken from the underlying gauge
    final var registered = registry.get("foo").gauge();
    assertThat(registered.measure()).allSatisfy(m -> assertThat(m.getValue()).isZero());
  }
}

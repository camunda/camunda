/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ZoneMetricsConfigurationTest {

  private final ZoneMetricsConfiguration configuration = new ZoneMetricsConfiguration();

  @Test
  void shouldApplyConfiguredZoneThroughSpringMeterRegistryCustomization() {
    // given
    final var camunda = new Camunda();
    camunda.getCluster().setZone("zone-a");

    // when / then
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class))
        .withBean(Camunda.class, () -> camunda)
        .withUserConfiguration(ZoneMetricsConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MeterRegistry.class);
              final var counter = context.getBean(MeterRegistry.class).counter("test.counter");
              assertThat(counter.getId().getTag("zone")).isEqualTo("zone-a");
            });
  }

  @Test
  void shouldAddConfiguredZoneToMetrics() {
    // given
    final var camunda = new Camunda();
    camunda.getCluster().setZone("zone-a");
    final var registry = new SimpleMeterRegistry();

    // when
    configuration.zoneMeterRegistryCustomizer(camunda).customize(registry);
    final var counter = registry.counter("test.counter");

    // then
    assertThat(counter.getId().getTag("zone")).isEqualTo("zone-a");
  }

  @Test
  void shouldAddConfiguredZoneToMetricsFromWrappedRegistries() {
    // given
    final var camunda = new Camunda();
    camunda.getCluster().setZone("zone-a");
    final var registry = new SimpleMeterRegistry();
    configuration.zoneMeterRegistryCustomizer(camunda).customize(registry);
    final var brokerRegistry = MicrometerUtil.wrap(registry, Tags.of("nodeId", "zone-a_0"));
    final var partitionRegistry = MicrometerUtil.wrap(brokerRegistry, Tags.of("partition", "1"));

    // when
    partitionRegistry.counter("test.counter");

    // then
    assertThat(registry.get("test.counter").counter().getId().getTag("zone")).isEqualTo("zone-a");
  }

  @Test
  void shouldOmitZoneFromMetricsWhenNotConfigured() {
    // given
    final var camunda = new Camunda();
    final var registry = new SimpleMeterRegistry();

    // when
    configuration.zoneMeterRegistryCustomizer(camunda).customize(registry);
    final var counter = registry.counter("test.counter");

    // then
    assertThat(counter.getId().getTag("zone")).isNull();
  }
}

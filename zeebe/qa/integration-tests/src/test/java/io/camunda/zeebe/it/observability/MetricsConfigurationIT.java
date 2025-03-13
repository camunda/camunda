/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ZeebeIntegration
final class MetricsConfigurationIT {

  @Nested
  final class PrometheusIT {
    @TestZeebe private final TestStandaloneBroker broker = new TestStandaloneBroker();

    @Test
    void shouldEnablePrometheusScrapingByDefault() {
      // given
      final var actuator = PrometheusActuator.of(broker);
      final var registry = broker.bean(MeterRegistry.class);

      // when
      final var scraped = actuator.metrics();

      // then
      assertThat(scraped).contains("jvm_info");
      assertThat(registry)
          .as(
              "should be directly a %s, otherwise it means multiple backends are enabled",
              PrometheusMeterRegistry.class)
          .isNotInstanceOf(CompositeMeterRegistry.class)
          .isInstanceOf(PrometheusMeterRegistry.class);
    }
  }

  @SuppressWarnings("resource")
  @Nested
  final class OtlpIT {
    private final List<String> logLines = new CopyOnWriteArrayList<>();

    @Container
    private final GenericContainer<?> otelCollector =
        new GenericContainer<>(
                DockerImageName.parse("otel/opentelemetry-collector-contrib").withTag("0.119.0"))
            .withLogConsumer(frame -> logLines.add(frame.getUtf8String()))
            .withExposedPorts(4318, 8888, 8889, 55679);

    @TestZeebe(autoStart = false) // need to configure it once the container is started
    private final TestStandaloneBroker broker =
        new TestStandaloneBroker()
            .withProperty("management.otlp.metrics.export.enabled", "true")
            .withProperty("management.otlp.metrics.export.step", "1s")
            .withProperty("management.otlp.metrics.export.batch-size", "10")
            .withProperty("management.endpoint.prometheus.enabled", "false")
            .withProperty("management.prometheus.metrics.export.enabled", "false");

    @Test
    void shouldExportViaOtlp() {
      // given
      broker
          .withProperty(
              "management.otlp.metrics.export.url",
              "http://localhost:%d/v1/metrics".formatted(otelCollector.getMappedPort(4318)))
          .start()
          .awaitCompleteTopology();
      final var registry = broker.bean(MeterRegistry.class);

      // when - then
      assertThat(registry)
          .as(
              "should be directly a %s, otherwise it means multiple backends are enabled",
              OtlpMeterRegistry.class)
          .isNotInstanceOf(CompositeMeterRegistry.class)
          .isInstanceOf(OtlpMeterRegistry.class);
      Awaitility.await("until we get some zeebe specific metrics logged by the collector")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  assertThat(logLines)
                      .anyMatch(s -> s.contains("Name: zeebe.gateway.topology.partition.roles")));
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.observability;

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
    /**
     * Zeebe's own series, one per subsystem, that a freshly started broker exposes without any
     * load. Asserted in addition to the generic {@code jvm_info} because in 8.4.21 the scrape
     * endpoint stayed healthy and kept serving JVM/Micrometer meters while every {@code zeebe_*}
     * series silently vanished (Zeebe still registered on the legacy simpleclient registry while
     * the actuator served the new Prometheus client). A {@code jvm_info}-only assertion passes in
     * exactly that broken state, so do not "simplify" this back to it.
     */
    private static final List<String> ZEEBE_SERIES =
        List.of(
            // partition health, from the broker's health monitoring
            "zeebe_health",
            // partition role, from the embedded gateway's broker client topology
            "zeebe_gateway_topology_partition_roles",
            // last appended position, from the leader's log stream appender
            "zeebe_log_appender_last_appended_position");

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

      // the leader-scoped meters are only registered once the partition has transitioned, so poll
      // instead of relying on the single scrape above
      Awaitility.await("until Zeebe's own series are exposed on the scrape endpoint")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                final var polled = actuator.metrics();
                for (final var series : ZEEBE_SERIES) {
                  assertThat(polled)
                      .as(
                          "should expose at least one sample line for '%s', not just a HELP/TYPE"
                              + " header",
                          series)
                      .containsPattern("(?m)^" + series + "(\\{|\\s)");
                }
              });
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

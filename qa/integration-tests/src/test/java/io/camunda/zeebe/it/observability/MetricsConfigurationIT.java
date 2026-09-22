/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.prometheus.client.CollectorRegistry;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Guards the wiring between Zeebe's raw Prometheus simpleclient metrics and the Spring Boot {@code
 * /actuator/prometheus} endpoint.
 *
 * <p>Zeebe registers the vast majority of its metrics directly against {@link
 * CollectorRegistry#defaultRegistry} instead of going through Micrometer. Those series only reach
 * the scrape endpoint when Spring Boot's simpleclient Prometheus backend is on the classpath and
 * picks up the {@code collectorRegistry} bean. Swapping that backend for the Prometheus-client 1.x
 * one (as {@code io.micrometer:micrometer-registry-prometheus} provides since Micrometer 1.13)
 * still yields a 200 response containing JVM/Micrometer meters, but silently drops every {@code
 * zeebe_*} and {@code atomix_*} series. Asserting on concrete Zeebe series — not just on the
 * endpoint responding — is therefore the only thing that catches such a dependency regression.
 */
@ZeebeIntegration
final class MetricsConfigurationIT {

  /**
   * The QA harness replaces the {@code collectorRegistry} bean with a throwaway registry to
   * tolerate duplicate registrations across in-JVM applications. That registry is not the one
   * Zeebe's static metric holders register into, so it would hide the very wiring under test here.
   * Restore the production wiring from {@code io.camunda.zeebe.shared.MetricRegistration}.
   *
   * <p>Giving up the throwaway registry means giving up its duplicate-registration tolerance, so
   * this broker is static: exactly one application per JVM fork may bind {@link
   * CollectorRegistry#defaultRegistry}. A second one would fail with "Collector already registered
   * that provides name: ...". Keep this class to a single broker, and never clear the default
   * registry in a teardown either -- Zeebe's collectors are {@code static final} and would be gone
   * for every later broker in the same fork.
   */
  @TestZeebe
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBean(
              "collectorRegistry", CollectorRegistry.defaultRegistry, CollectorRegistry.class);

  @Test
  void shouldScrapeZeebeMetricsFromPrometheusEndpoint() {
    // given
    final var actuator = PrometheusActuator.of(BROKER);

    // when - metrics are reported asynchronously as the broker transitions to a healthy leader
    // then
    Awaitility.await("until the scrape endpoint reports Zeebe's own metrics")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final var scraped = actuator.metrics();

              assertThat(scraped)
                  .as("the scrape endpoint is enabled and serving the JVM meters")
                  .contains("jvm_info");
              assertThat(scraped)
                  .as("broker bootstrap metrics registered on the default registry are exposed")
                  .containsPattern("(?m)^zeebe_broker_start_step_latency\\{");
              assertThat(scraped)
                  .as("partition health metrics registered on the default registry are exposed")
                  .containsPattern("(?m)^zeebe_health\\{");
              assertThat(scraped)
                  .as("Raft metrics registered on the default registry are exposed")
                  .containsPattern("(?m)^atomix_role\\{");
            });
  }
}

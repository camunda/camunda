/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

final class BrokerStatusHealthIndicatorTest {

  private SpringBrokerBridge brokerBridge;
  private BrokerHealthCheckService healthCheckService;
  private BrokerStatusHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    brokerBridge = mock(SpringBrokerBridge.class);
    healthCheckService = mock(BrokerHealthCheckService.class);
    when(brokerBridge.getBrokerHealthCheckService()).thenReturn(Optional.of(healthCheckService));
    indicator = new BrokerStatusHealthIndicator(brokerBridge);
  }

  @Test
  void shouldReportUpWhenBrokerIsHealthy() {
    // given
    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport())
        .thenReturn(
            new HealthReport("Broker-0", HealthStatus.HEALTHY, null, ImmutableMap.of()));

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReportDownWhenBrokerIsUnhealthy() {
    // given
    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport())
        .thenReturn(
            new HealthReport("Broker-0", HealthStatus.UNHEALTHY, null, ImmutableMap.of()));

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReportDownWhenServiceUnavailable() {
    // given
    when(brokerBridge.getBrokerHealthCheckService()).thenReturn(Optional.empty());

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.DOWN);
    assertThat(health.details()).isEmpty();
  }

  @Test
  void shouldIncludeComponentTreeInDetails() {
    // given
    final var partitionReport =
        new HealthReport(
            "Partition-1",
            HealthStatus.HEALTHY,
            null,
            ImmutableMap.of(
                "Raft-1",
                new HealthReport("Raft-1", HealthStatus.HEALTHY, null, ImmutableMap.of()),
                "StreamProcessor-1",
                new HealthReport(
                    "StreamProcessor-1", HealthStatus.HEALTHY, null, ImmutableMap.of())));

    final var brokerReport =
        new HealthReport(
            "Broker-0",
            HealthStatus.HEALTHY,
            null,
            ImmutableMap.of("Partition-1", partitionReport));

    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport()).thenReturn(brokerReport);

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.UP);
    assertThat(health.details()).containsKey("Partition-1");

    @SuppressWarnings("unchecked")
    final var partitionDetails = (Map<String, Object>) health.details().get("Partition-1");
    assertThat(partitionDetails).containsEntry("status", "HEALTHY");
    assertThat(partitionDetails).containsKey("components");

    @SuppressWarnings("unchecked")
    final var components = (Map<String, Object>) partitionDetails.get("components");
    assertThat(components).containsKeys("Raft-1", "StreamProcessor-1");
  }

  @Test
  void shouldIncludeIssueDetailsForUnhealthyComponent() {
    // given
    final var since = Instant.parse("2026-04-05T12:00:00Z");
    final var unhealthyChild =
        new HealthReport(
            "StreamProcessor-1",
            HealthStatus.UNHEALTHY,
            HealthIssue.of("Processing is stuck", since),
            ImmutableMap.of());

    final var brokerReport =
        new HealthReport(
            "Broker-0",
            HealthStatus.UNHEALTHY,
            null,
            ImmutableMap.of("StreamProcessor-1", unhealthyChild));

    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport()).thenReturn(brokerReport);

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.DOWN);

    @SuppressWarnings("unchecked")
    final var componentDetails =
        (Map<String, Object>) health.details().get("StreamProcessor-1");
    assertThat(componentDetails).containsEntry("status", "UNHEALTHY");

    @SuppressWarnings("unchecked")
    final var issueDetails = (Map<String, Object>) componentDetails.get("issue");
    assertThat(issueDetails)
        .containsEntry("message", "Processing is stuck")
        .containsEntry("since", since.toString());
  }

  @Test
  void shouldIncludeThrowableMessageInIssue() {
    // given
    final var since = Instant.parse("2026-04-05T12:00:00Z");
    final var exception = new RuntimeException("Disk full");
    final var deadChild =
        new HealthReport(
            "Log-1",
            HealthStatus.DEAD,
            HealthIssue.of(exception, since),
            ImmutableMap.of());

    final var brokerReport =
        new HealthReport(
            "Broker-0",
            HealthStatus.DEAD,
            null,
            ImmutableMap.of("Log-1", deadChild));

    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport()).thenReturn(brokerReport);

    // when
    final Health health = indicator.health();

    // then
    @SuppressWarnings("unchecked")
    final var logDetails = (Map<String, Object>) health.details().get("Log-1");
    assertThat(logDetails).containsEntry("status", "DEAD");

    @SuppressWarnings("unchecked")
    final var issueDetails = (Map<String, Object>) logDetails.get("issue");
    assertThat(issueDetails).containsEntry("message", "Disk full");
  }

  @Test
  void shouldHandleEmptyComponentTree() {
    // given
    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport())
        .thenReturn(
            new HealthReport("Broker-0", HealthStatus.HEALTHY, null, ImmutableMap.of()));

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.status()).isEqualTo(Status.UP);
    assertThat(health.details()).isEmpty();
  }
}

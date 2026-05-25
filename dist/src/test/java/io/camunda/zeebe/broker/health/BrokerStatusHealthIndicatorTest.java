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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.management.HealthTree;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

final class BrokerStatusHealthIndicatorTest {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private ObjectMapper objectMapper;
  private SpringBrokerBridge brokerBridge;
  private BrokerHealthCheckService healthCheckService;
  private BrokerStatusHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    brokerBridge = mock(SpringBrokerBridge.class);
    healthCheckService = mock(BrokerHealthCheckService.class);
    when(brokerBridge.getBrokerHealthCheckService()).thenReturn(Optional.of(healthCheckService));
    objectMapper = JsonMapper.builder().findAndAddModules().build();
    indicator = new BrokerStatusHealthIndicator(brokerBridge, objectMapper);
  }

  @Test
  void shouldReportUpWhenBrokerIsHealthy() {
    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport())
        .thenReturn(new HealthReport("Broker-0", HealthStatus.HEALTHY, null, ImmutableMap.of()));

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReportDownWhenBrokerIsUnhealthy() {
    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport())
        .thenReturn(new HealthReport("Broker-0", HealthStatus.UNHEALTHY, null, ImmutableMap.of()));

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldReportDownWhenServiceUnavailable() {
    when(brokerBridge.getBrokerHealthCheckService()).thenReturn(Optional.empty());

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).isEmpty();
  }

  @Test
  void shouldIncludeComponentTreeInDetails() {
    final var raftReport =
        new HealthReport("Raft-1", HealthStatus.HEALTHY, null, ImmutableMap.of());
    final var streamProcessorReport =
        new HealthReport("StreamProcessor-1", HealthStatus.HEALTHY, null, ImmutableMap.of());
    final var partitionReport =
        new HealthReport(
            "Partition-1",
            HealthStatus.HEALTHY,
            null,
            ImmutableMap.of("Raft-1", raftReport, "StreamProcessor-1", streamProcessorReport));

    final var brokerReport =
        new HealthReport(
            "Broker-0",
            HealthStatus.HEALTHY,
            null,
            ImmutableMap.of("Partition-1", partitionReport));

    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport()).thenReturn(brokerReport);

    final Health health = indicator.health();

    final var expectedPartitionDetail =
        objectMapper.convertValue(
            HealthTree.fromHealthReport("Partition-1", partitionReport), MAP_TYPE);

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("Partition-1", expectedPartitionDetail);
  }

  @Test
  void shouldIncludeIssueDetailsForUnhealthyComponent() {
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

    final Health health = indicator.health();

    final var expectedStreamProcessorDetail =
        objectMapper.convertValue(
            HealthTree.fromHealthReport("StreamProcessor-1", unhealthyChild), MAP_TYPE);

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails())
        .containsEntry("StreamProcessor-1", expectedStreamProcessorDetail);
  }

  @Test
  void shouldIncludeThrowableMessageInIssue() {
    final var since = Instant.parse("2026-04-05T12:00:00Z");
    final var exception = new RuntimeException("Disk full");
    final var deadChild =
        new HealthReport(
            "Log-1", HealthStatus.DEAD, HealthIssue.of(exception, since), ImmutableMap.of());

    final var brokerReport =
        new HealthReport("Broker-0", HealthStatus.DEAD, null, ImmutableMap.of("Log-1", deadChild));

    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport()).thenReturn(brokerReport);

    final Health health = indicator.health();

    final var expectedLogDetail =
        objectMapper.convertValue(HealthTree.fromHealthReport("Log-1", deadChild), MAP_TYPE);

    assertThat(health.getDetails()).containsEntry("Log-1", expectedLogDetail);
  }

  @Test
  void shouldHandleEmptyComponentTree() {
    when(healthCheckService.isBrokerHealthy()).thenReturn(true);
    when(healthCheckService.getHealthReport())
        .thenReturn(new HealthReport("Broker-0", HealthStatus.HEALTHY, null, ImmutableMap.of()));

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).isEmpty();
  }
}

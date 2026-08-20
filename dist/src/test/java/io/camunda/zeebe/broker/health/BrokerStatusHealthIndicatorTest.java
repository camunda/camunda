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
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.management.HealthTree;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Instant;
import java.util.Map;
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
    when(healthCheckService.getHealthReport()).thenReturn(healthyReport("Broker-0"));

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReportDownWhenBrokerIsUnhealthy() {
    when(healthCheckService.isBrokerHealthy()).thenReturn(false);
    when(healthCheckService.getHealthReport()).thenReturn(unhealthyReport("Broker-0"));

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
    final var raftReport = healthyReport("Raft-1");
    final var streamProcessorReport = healthyReport("StreamProcessor-1");
    final var partitionReport =
        reportFromChildren(
            "Partition-1",
            Map.of("Raft-1", raftReport, "StreamProcessor-1", streamProcessorReport));

    final var brokerReport = reportFromChildren("Broker-0", Map.of("Partition-1", partitionReport));

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
        unhealthyReport("StreamProcessor-1").withMessage("Processing is stuck", since);

    final var brokerReport =
        reportFromChildren("Broker-0", Map.of("StreamProcessor-1", unhealthyChild));

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
    final var deadChild = HealthReport.dead(component("Log-1")).withIssue(exception, since);

    final var brokerReport = reportFromChildren("Broker-0", Map.of("Log-1", deadChild));

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
    when(healthCheckService.getHealthReport()).thenReturn(healthyReport("Broker-0"));

    final Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).isEmpty();
  }

  private static HealthReport healthyReport(final String componentName) {
    return HealthReport.healthy(component(componentName));
  }

  private static HealthReport unhealthyReport(final String componentName) {
    return HealthReport.unhealthy(component(componentName));
  }

  private static HealthReport reportFromChildren(
      final String componentName, final Map<String, HealthReport> children) {
    return HealthReport.fromChildrenStatus(componentName, children)
        .orElseGet(() -> healthyReport(componentName));
  }

  private static HealthMonitorable component(final String componentName) {
    return new TestHealthMonitorable(componentName);
  }

  private record TestHealthMonitorable(String componentName) implements HealthMonitorable {

    @Override
    public HealthReport getHealthReport() {
      return HealthReport.healthy(this);
    }

    @Override
    public void addFailureListener(final FailureListener failureListener) {}

    @Override
    public void removeFailureListener(final FailureListener failureListener) {}
  }
}

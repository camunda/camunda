/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.management.HealthTree;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class BrokerStatusHealthIndicator implements HealthIndicator {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final SpringBrokerBridge brokerBridge;
  private final ObjectMapper objectMapper;

  @Autowired
  public BrokerStatusHealthIndicator(
      final SpringBrokerBridge brokerBridge, final ObjectMapper objectMapper) {
    this.brokerBridge = brokerBridge;
    this.objectMapper = objectMapper;
  }

  @Override
  public Health health() {
    final var optionalService = brokerBridge.getBrokerHealthCheckService();
    if (optionalService.isEmpty()) {
      return Health.down().build();
    }

    final var service = optionalService.get();
    final var isHealthy = service.isBrokerHealthy();
    final var builder = isHealthy ? Health.up() : Health.down();

    final var healthReport = service.getHealthReport();
    addComponentTreeDetails(builder, healthReport);

    return builder.build();
  }

  private void addComponentTreeDetails(
      final Health.Builder builder, final HealthReport report) {
    for (final var entry : report.children().entrySet()) {
      final var tree = HealthTree.fromHealthReport(entry.getKey(), entry.getValue());
      builder.withDetail(entry.getKey(), objectMapper.convertValue(tree, MAP_TYPE));
    }
  }
}
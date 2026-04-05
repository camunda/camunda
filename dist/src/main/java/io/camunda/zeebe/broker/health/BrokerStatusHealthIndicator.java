/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class BrokerStatusHealthIndicator implements HealthIndicator {

  private final SpringBrokerBridge brokerBridge;

  @Autowired
  public BrokerStatusHealthIndicator(final SpringBrokerBridge brokerBridge) {
    this.brokerBridge = brokerBridge;
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
      builder.withDetail(entry.getKey(), toDetailsMap(entry.getValue()));
    }
  }

  private Map<String, Object> toDetailsMap(final HealthReport report) {
    final var details = new LinkedHashMap<String, Object>();
    details.put("status", report.getStatus().name());

    final var issue = report.issue();
    if (issue != null) {
      details.put("issue", formatIssue(issue));
    }

    if (!report.children().isEmpty()) {
      final var components = new LinkedHashMap<String, Object>();
      for (final var entry : report.children().entrySet()) {
        components.put(entry.getKey(), toDetailsMap(entry.getValue()));
      }
      details.put("components", components);
    }

    return details;
  }

  private Map<String, Object> formatIssue(final HealthIssue issue) {
    final var details = new LinkedHashMap<String, Object>();

    if (issue.message() != null) {
      details.put("message", issue.message());
    } else if (issue.throwable() != null) {
      details.put("message", issue.throwable().getMessage());
    }

    if (issue.since() != null) {
      details.put("since", issue.since().toString());
    }

    if (issue.cause() != null) {
      details.put("cause", toDetailsMap(issue.cause()));
    }

    return details;
  }
}

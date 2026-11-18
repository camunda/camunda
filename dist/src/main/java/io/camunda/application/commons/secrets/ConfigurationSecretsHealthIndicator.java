/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Lists the failing configuration secret refreshes of each physical tenant. Always UP: a tenant
 * whose refresh fails keeps working with its current values, and it isn't part of any probe group.
 */
public final class ConfigurationSecretsHealthIndicator implements HealthIndicator {

  private final ConfigurationSecretRefreshReport report;

  public ConfigurationSecretsHealthIndicator(final ConfigurationSecretRefreshReport report) {
    this.report = report;
  }

  @Override
  public Health health() {
    final Map<String, Object> details = new LinkedHashMap<>();
    report
        .failing()
        .forEach(
            (tenantId, failures) ->
                details.put(
                    tenantId,
                    Map.of(
                        "failing",
                        failures.stream()
                            .sorted(
                                Comparator.comparing(
                                        ConfigurationSecretRefreshReport.Failure::reference)
                                    .thenComparing(
                                        ConfigurationSecretRefreshReport.Failure::client))
                            .map(
                                failure ->
                                    Map.of(
                                        "reference", failure.reference(),
                                        "client", failure.client(),
                                        "reason", failure.reason().name()))
                            .toList())));
    return Health.up().withDetails(details).build();
  }
}

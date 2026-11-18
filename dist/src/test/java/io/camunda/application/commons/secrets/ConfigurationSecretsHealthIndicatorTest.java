/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Failure;
import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Reason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class ConfigurationSecretsHealthIndicatorTest {

  private final ConfigurationSecretRefreshReport report =
      new ConfigurationSecretRefreshReport(new SimpleMeterRegistry(), InstantSource.system());
  private final ConfigurationSecretsHealthIndicator indicator =
      new ConfigurationSecretsHealthIndicator(report);

  @Test
  void shouldListTheFailingReferencesWithoutChangingTheStatus() {
    // given
    report.failing(
        "tenanta", Set.of(new Failure("secret:es-password", "elasticsearch", Reason.NOT_FOUND)));
    report.failing("tenantb", Set.of());

    // when
    final var health = indicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsExactly(
            Map.entry(
                "tenanta",
                Map.of(
                    "failing",
                    List.of(
                        Map.of(
                            "reference", "secret:es-password",
                            "client", "elasticsearch",
                            "reason", "NOT_FOUND")))));
  }
}

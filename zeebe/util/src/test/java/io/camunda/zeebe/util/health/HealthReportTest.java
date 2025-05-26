/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HealthReportTest {

  @Test
  public void shouldReportWorstHealthStatusFromChildren() {
    final var children =
        Map.of(
            "a",
            new HealthReport("a", HealthStatus.HEALTHY, null, Map.of()),
            "b",
            new HealthReport(
                "b",
                HealthStatus.UNHEALTHY,
                new HealthIssue("storage is full", null, null, Instant.ofEpochMilli(19201223L)),
                Map.of()));
    final var root = HealthReport.fromChildrenStatus("root", children);

    final var expected =
        new HealthReport(
            "root",
            HealthStatus.UNHEALTHY,
            new HealthIssue("storage is full", null, null, Instant.ofEpochMilli(19201223L)),
            children);

    assertThat(root).isPresent();
    assertThat(root.get()).isEqualTo(expected);
  }
}

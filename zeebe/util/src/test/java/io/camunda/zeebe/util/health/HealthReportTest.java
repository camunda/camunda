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
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest
  @MethodSource("twoEnums")
  public void shouldReportWorstHealthWhenOneChildIsAdded(
      final HealthStatus parentStatus, final HealthStatus childStatus) {
    final var report = new HealthReport("root", parentStatus, null, Map.of());
    final var withChild = report.withChild(new HealthReport("child", childStatus, null, Map.of()));
    assertThat(withChild.status()).isEqualTo(parentStatus.combine(childStatus));
  }

  public static Stream<Object[]> twoEnums() {
    final var list = new ArrayList<Object[]>();
    for (final var parent : HealthStatus.values()) {
      for (final var child : HealthStatus.values()) {
        list.add(new Object[] {parent, child});
      }
    }
    return list.stream();
  }
}

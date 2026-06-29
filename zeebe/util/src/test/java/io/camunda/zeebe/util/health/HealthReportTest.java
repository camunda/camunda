/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class HealthReportTest {

  @Test
  public void shouldReportWorstHealthStatusFromChildren() {
    final var children =
        ImmutableMap.of(
            "a",
            new HealthReport("a", HealthStatus.HEALTHY, null, ImmutableMap.of()),
            "b",
            new HealthReport(
                "b",
                HealthStatus.UNHEALTHY,
                new HealthIssue("storage is full", null, null, Instant.ofEpochMilli(19201223L)),
                ImmutableMap.of()));
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

  @Test
  public void shouldPreferChildWithIssueAmongEquallyUnhealthyChildren() {
    // given two equally-unhealthy children, only one of which carries an issue
    final var issue = new HealthIssue("disk full", null, null, Instant.ofEpochMilli(42L));
    final var children =
        ImmutableMap.of(
            "with-issue",
            new HealthReport("with-issue", HealthStatus.UNHEALTHY, issue, ImmutableMap.of()),
            "without-issue",
            new HealthReport("without-issue", HealthStatus.UNHEALTHY, null, ImmutableMap.of()));

    // when
    final var root = HealthReport.fromChildrenStatus("root", children);

    // then the aggregate keeps the diagnostic instead of an arbitrary (possibly null) one
    assertThat(root).isPresent();
    assertThat(root.get().status()).isEqualTo(HealthStatus.UNHEALTHY);
    assertThat(root.get().issue()).isEqualTo(issue);
  }

  @Test
  public void shouldBreakTiesDeterministicallyByComponentName() {
    // given two children with the same status and both carrying an issue
    final var issueA = new HealthIssue("issue a", null, null, Instant.ofEpochMilli(1L));
    final var issueB = new HealthIssue("issue b", null, null, Instant.ofEpochMilli(2L));
    final var children =
        ImmutableMap.of(
            "a",
            new HealthReport("a", HealthStatus.UNHEALTHY, issueA, ImmutableMap.of()),
            "b",
            new HealthReport("b", HealthStatus.UNHEALTHY, issueB, ImmutableMap.of()));

    // when
    final var root = HealthReport.fromChildrenStatus("root", children);

    // then the choice is deterministic (highest component name wins), not map-order dependent
    assertThat(root).isPresent();
    assertThat(root.get().issue()).isEqualTo(issueB);
  }
}

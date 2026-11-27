/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class HealthTreeRecorderTest {

  private static final Instant SINCE = Instant.parse("2026-11-27T10:00:00Z");

  private final HealthHistory history =
      new HealthHistory("Broker-0", 100, null, InstantSource.fixed(SINCE));
  private final HealthTreeRecorder recorder = new HealthTreeRecorder(history);

  @Test
  void shouldRecordAChangedLeafOnceAndNotItsParents() {
    // given
    recorder.onHealthReport(tree(leaf("Partition-default-1", HealthStatus.HEALTHY, null)));

    // when
    recorder.onHealthReport(
        tree(leaf("Partition-default-1", HealthStatus.DEGRADED, "flush latency reached 800 ms")));

    // then
    assertThat(history.entries()).hasSize(1);
    final var entry = history.entries().getFirst();
    assertThat(entry.component()).isEqualTo("Broker-0/LoadSpike/Partition-default-1");
    assertThat(entry.type()).isEqualTo(HealthTreeRecorder.HEALTH_CHANGED);
    assertThat(entry.message()).isEqualTo("flush latency reached 800 ms");
    assertThat(entry.details()).containsEntry("from", "HEALTHY").containsEntry("to", "DEGRADED");
  }

  @Test
  void shouldRecordTheResolvedIssueWhenALeafRecovers() {
    // given
    recorder.onHealthReport(tree(leaf("Partition-default-1", HealthStatus.HEALTHY, null)));
    recorder.onHealthReport(
        tree(leaf("Partition-default-1", HealthStatus.DEGRADED, "flush latency reached 800 ms")));

    // when
    recorder.onHealthReport(tree(leaf("Partition-default-1", HealthStatus.HEALTHY, null)));

    // then
    assertThat(history.entries().getLast().message())
        .isEqualTo("resolved: flush latency reached 800 ms");
    assertThat(history.entries().getLast().details())
        .containsEntry("from", "DEGRADED")
        .containsEntry("to", "HEALTHY");
  }

  @Test
  void shouldIgnoreHealthyLeavesThatComeAndGo() {
    // given
    recorder.onHealthReport(tree(leaf("Partition-default-1", HealthStatus.HEALTHY, null)));

    // when
    recorder.onHealthReport(tree(leaf("Partition-default-2", HealthStatus.HEALTHY, null)));

    // then
    assertThat(history.entries()).isEmpty();
  }

  private static HealthReport tree(final HealthReport child) {
    final var detector =
        HealthReport.fromChildrenStatus("LoadSpike", Map.of(child.componentName(), child))
            .orElseThrow();
    return HealthReport.fromChildrenStatus("Broker-0", Map.of("LoadSpike", detector)).orElseThrow();
  }

  private static HealthReport leaf(
      final String name, final HealthStatus status, final String message) {
    return new HealthReport(
        name, status, message == null ? null : HealthIssue.of(message, SINCE), Map.of());
  }
}

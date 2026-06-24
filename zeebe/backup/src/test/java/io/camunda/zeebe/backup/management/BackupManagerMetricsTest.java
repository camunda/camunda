/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.metrics.BackupManagerMetrics;
import io.camunda.zeebe.backup.metrics.BackupManagerMetricsDoc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

final class BackupManagerMetricsTest {

  @SuppressWarnings("resource")
  @Test
  void shouldRegisterMetrics() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var metrics = new BackupManagerMetrics(registry);

    // when
    metrics.startTakingBackup().complete(null, null);
    metrics.startTakingBackup();
    metrics.startDeleting().complete(null, null);
    metrics.startDeleting();
    metrics.startListingBackups().complete(null, null);
    metrics.startListingBackups();

    // then
    assertThat(registry.find(BackupManagerMetricsDoc.BACKUP_OPERATIONS_LATENCY.getName()).timers())
        .hasSize(3);
    assertThat(
            registry.find(BackupManagerMetricsDoc.BACKUP_OPERATIONS_IN_PROGRESS.getName()).gauges())
        .hasSize(3);
    assertThat(registry.find(BackupManagerMetricsDoc.BACKUP_OPERATIONS_TOTAL.getName()).counters())
        .hasSize(3);
  }

  @Test
  void shouldDeRegisterMetrics() {
    // given
    final var registry = new SimpleMeterRegistry();
    final var metrics = new BackupManagerMetrics(registry);
    metrics.startTakingBackup().complete(null, null);
    metrics.startTakingBackup();
    metrics.startDeleting().complete(null, null);
    metrics.startDeleting();
    metrics.startListingBackups().complete(null, null);
    metrics.startListingBackups();

    // when
    metrics.close();

    // then
    assertThat(registry.getMeters()).isEmpty();
  }
}

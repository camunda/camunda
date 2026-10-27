/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.historydeletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HistoryDeletionConfigurationTest {

  @Test
  void shouldHaveDefaultDelayBetweenRuns() {
    final var config = new HistoryDeletionConfiguration();

    assertThat(config.getDelayBetweenRuns()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldSetDelayBetweenRuns() {
    final var config = new HistoryDeletionConfiguration();
    config.setDelayBetweenRuns(Duration.ofMillis(2500));

    assertThat(config.getDelayBetweenRuns()).isEqualTo(Duration.ofMillis(2500));
  }

  @Test
  void shouldHaveDefaultMaxDelayBetweenRuns() {
    final var config = new HistoryDeletionConfiguration();

    assertThat(config.getMaxDelayBetweenRuns()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  void shouldSetMaxDelayBetweenRuns() {
    final var config = new HistoryDeletionConfiguration();
    config.setMaxDelayBetweenRuns(Duration.ofMillis(5000));

    assertThat(config.getMaxDelayBetweenRuns()).isEqualTo(Duration.ofMillis(5000));
  }

  @Test
  void shouldHaveDefaultBatchSize() {
    final var config = new HistoryDeletionConfiguration();

    assertThat(config.getQueueBatchSize()).isEqualTo(100);
  }

  @Test
  void shouldSetBatchSize() {
    final var config = new HistoryDeletionConfiguration();
    config.setQueueBatchSize(500);

    assertThat(config.getQueueBatchSize()).isEqualTo(500);
  }

  @Test
  void shouldHaveDefaultDependentRowLimit() {
    final var config = new HistoryDeletionConfiguration();

    assertThat(config.getDependentRowLimit()).isEqualTo(10000);
  }

  @Test
  void shouldSetDependentRowLimit() {
    final var config = new HistoryDeletionConfiguration();
    config.setDependentRowLimit(5000);

    assertThat(config.getDependentRowLimit()).isEqualTo(5000);
  }
}

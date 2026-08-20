/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.common.historydeletion.HistoryDeletionConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class HistoryDeletionTest {

  @Test
  void shouldHaveDefaults() {
    final HistoryDeletion historyDeletion = new HistoryDeletion();

    assertThat(historyDeletion.getDelayBetweenRuns())
        .as("Default delayBetweenRuns should be 1 second")
        .isEqualTo(Duration.ofSeconds(1));
    assertThat(historyDeletion.getMaxDelayBetweenRuns())
        .as("Default maxDelayBetweenRuns should be 5 minutes")
        .isEqualTo(Duration.ofSeconds(15));
    assertThat(historyDeletion.getQueueBatchSize())
        .as("Default queueBatchSize should be 100")
        .isEqualTo(100);
    assertThat(historyDeletion.getDependentRowLimit())
        .as("Default dependentRowLimit should be 10000")
        .isEqualTo(10000);
  }

  @Test
  void shouldConvertToHistoryDeletionConfiguration() {
    final HistoryDeletion historyDeletion = new HistoryDeletion();

    // mutate values to ensure mapping works for non-defaults
    historyDeletion.setDelayBetweenRuns(Duration.ofSeconds(3));
    historyDeletion.setMaxDelayBetweenRuns(Duration.ofMinutes(2));
    historyDeletion.setQueueBatchSize(250);
    historyDeletion.setDependentRowLimit(5000);

    final HistoryDeletionConfiguration config = historyDeletion.toConfiguration();

    assertThat(config.getDelayBetweenRuns())
        .as("Converted delayBetweenRuns should match")
        .isEqualTo(historyDeletion.getDelayBetweenRuns());
    assertThat(config.getMaxDelayBetweenRuns())
        .as("Converted maxDelayBetweenRuns should match")
        .isEqualTo(historyDeletion.getMaxDelayBetweenRuns());
    assertThat(config.getQueueBatchSize())
        .as("Converted queueBatchSize should match")
        .isEqualTo(historyDeletion.getQueueBatchSize());
    assertThat(config.getDependentRowLimit())
        .as("Converted dependentRowLimit should match")
        .isEqualTo(historyDeletion.getDependentRowLimit());
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.history-deletion.delay-between-runs=PT2S",
        "camunda.data.history-deletion.max-delay-between-runs=PT10M",
        "camunda.data.history-deletion.queue-batch-size=200",
        "camunda.data.history-deletion.dependent-row-limit=5000"
      })
  class RDBMSExporterTest {
    @Test
    void shouldPopulateWithHistoryDeletion(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();

      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getHistoryDeletion().getDelayBetweenRuns())
          .isEqualTo(Duration.ofSeconds(2));
      assertThat(config.getHistoryDeletion().getMaxDelayBetweenRuns())
          .isEqualTo(Duration.ofMinutes(10));
      assertThat(config.getHistoryDeletion().getQueueBatchSize()).isEqualTo(200);
      assertThat(config.getHistoryDeletion().getDependentRowLimit()).isEqualTo(5000);
    }
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.history-deletion.delay-between-runs=PT3S",
        "camunda.data.history-deletion.max-delay-between-runs=PT15M",
        "camunda.data.history-deletion.queue-batch-size=150",
      })
  class CamundaExporterTest {
    @Test
    void shouldPopulateWithHistoryDeletion(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();

      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getHistoryDeletion().getDelayBetweenRuns())
          .isEqualTo(Duration.ofSeconds(3));
      assertThat(config.getHistoryDeletion().getMaxDelayBetweenRuns())
          .isEqualTo(Duration.ofMinutes(15));
      assertThat(config.getHistoryDeletion().getQueueBatchSize()).isEqualTo(150);
      assertThat(config.getHistoryDeletion().getDependentRowLimit()).isEqualTo(10000);
    }
  }
}

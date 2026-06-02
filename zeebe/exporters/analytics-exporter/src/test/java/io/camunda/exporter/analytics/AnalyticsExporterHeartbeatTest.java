/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static io.camunda.exporter.analytics.AnalyticsAttributes.EVENT_HEARTBEAT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.util.VersionUtil;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalyticsExporterHeartbeatTest {

  private InMemoryLogRecordExporter memoryExporter;
  private ExporterTestController controller;
  private AnalyticsExporter exporter;

  @BeforeEach
  void setUp() {
    memoryExporter = InMemoryLogRecordExporter.create();
    controller = new ExporterTestController();
    exporter = openExporter(new AnalyticsExporterConfig(), memoryExporter, controller);
    // Heartbeat is emitted during open(). The tests will use this emitted event.
  }

  @Test
  void shouldEmitHeartbeatOnOpen() {
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            log -> {
              final var attrs = log.getAttributes().asMap();
              assertThat(attrs)
                  .containsEntry(AnalyticsAttributes.EVENT_NAME, EVENT_HEARTBEAT)
                  .containsEntry(AnalyticsAttributes.BROKER_VERSION, VersionUtil.getVersion())
                  .containsEntry(
                      AnalyticsAttributes.EXPORTER_VERSION, AnalyticsExporterVersion.get())
                  .containsKey(AnalyticsAttributes.SCHEMA_VERSION);
              assertThat(attrs.get(AnalyticsAttributes.SCHEMA_VERSION)).asString().isNotBlank();
              assertThat(attrs)
                  .doesNotContainKey(AnalyticsAttributes.LOG_POSITION)
                  .doesNotContainKey(AnalyticsAttributes.EVENT_SEQUENCE_NUMBER);
            });
  }

  @Test
  void shouldScheduleHeartbeatAtConfiguredInterval() {
    final var heartbeatInterval = new AnalyticsExporterConfig().getHeartbeatInterval();
    assertThat(controller.getScheduledTasks())
        .anyMatch(task -> task.getDelay().equals(heartbeatInterval));
  }

  @Test
  void shouldEmitFollowupHeartbeatWhenScheduledTaskFires() {
    // when — advance time past the heartbeat interval
    controller.runScheduledTasks(new AnalyticsExporterConfig().getHeartbeatInterval());

    // then — open()'s heartbeat plus one scheduled fire
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .filteredOn(
            log -> EVENT_HEARTBEAT.equals(log.getAttributes().get(AnalyticsAttributes.EVENT_NAME)))
        .hasSize(2);
  }

  @Test
  void shouldRescheduleHeartbeatRepeatedly() {
    final var heartbeatInterval = new AnalyticsExporterConfig().getHeartbeatInterval();

    // when — advance two interval ticks
    controller.runScheduledTasks(heartbeatInterval);
    controller.runScheduledTasks(heartbeatInterval);

    // then — open() + 2 scheduled fires
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .filteredOn(
            log -> EVENT_HEARTBEAT.equals(log.getAttributes().get(AnalyticsAttributes.EVENT_NAME)))
        .hasSize(3);
  }

  @Test
  void shouldCancelHeartbeatTaskOnClose() {
    exporter.close();

    assertThat(controller.getScheduledTasks()).allMatch(task -> task.isCanceled());
  }

  @Test
  void shouldHonorCustomHeartbeatInterval() {
    final var customController = new ExporterTestController();
    openExporter(
        new AnalyticsExporterConfig().setHeartbeatInterval("PT1M"),
        InMemoryLogRecordExporter.create(),
        customController);

    assertThat(customController.getScheduledTasks())
        .anyMatch(task -> task.getDelay().equals(Duration.ofMinutes(1)));
  }

  private static AnalyticsExporter openExporter(
      final AnalyticsExporterConfig config,
      final InMemoryLogRecordExporter memoryExporter,
      final ExporterTestController controller) {
    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("analytics", config))
            .setClusterId("test-cluster")
            .setPartitionId(1)
            .setLicenseKey("test-license-key");
    final var exporter = new AnalyticsExporter(TestOtelSdkManager.inMemory(memoryExporter));
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }
}

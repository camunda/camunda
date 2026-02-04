/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ApplyRolloverPeriodJob;
import io.camunda.exporter.tasks.archiver.BatchOperationArchiverJob;
import io.camunda.exporter.tasks.archiver.JobBatchMetricsArchiverJob;
import io.camunda.exporter.tasks.archiver.ProcessInstanceArchiverJob;
import io.camunda.exporter.tasks.archiver.ProcessInstanceToBeArchivedCountJob;
import io.camunda.exporter.tasks.archiver.StandaloneDecisionArchiverJob;
import io.camunda.exporter.tasks.archiver.UsageMetricArchiverJob;
import io.camunda.exporter.tasks.archiver.UsageMetricTUArchiverJob;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateTask;
import io.camunda.exporter.tasks.historydeletion.HistoryDeletionJob;
import io.camunda.exporter.tasks.incident.IncidentUpdateTask;
import io.camunda.exporter.tasks.utils.TestExporterResourceProvider;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class BackgroundTaskManagerFactoryTest {

  private static final int PARTITION_ID = 1;
  private static final String EXPORTER_ID = "test-exporter";

  private BackgroundTaskManagerFactory factory;
  private final ExporterConfiguration config = new ExporterConfiguration();

  @BeforeEach
  void setUp() {
    factory =
        new BackgroundTaskManagerFactory(
            PARTITION_ID,
            EXPORTER_ID,
            config,
            new TestExporterResourceProvider("", true),
            new CamundaExporterMetrics(new SimpleMeterRegistry()),
            LoggerFactory.getLogger(BackgroundTaskManagerFactoryTest.class),
            mock(ExporterMetadata.class),
            new ObjectMapper(),
            mock(ExporterEntityCacheImpl.class));
  }

  @Test
  void shouldScheduleProcessInstanceArchiverTaskWhenConfigEnabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should contain ProcessInstancesArchiverJob when config is enabled")
        .anyMatch(task -> isProcessInstanceArchiverTask(task));
  }

  @Test
  void shouldNotScheduleProcessInstanceArchiverTaskWhenConfigDisabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(false);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should not contain ProcessInstancesArchiverJob when config is disabled")
        .noneMatch(task -> isProcessInstanceArchiverTask(task));
  }

  @Test
  void shouldScheduleProcessInstanceCountJobWhenConfigEnabledAndMetricsTrackingEnabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(true);
    config.getHistory().setTrackArchivalMetricsForProcessInstance(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should contain ProcessInstanceToBeArchivedCountJob when both configs are enabled")
        .anyMatch(task -> isProcessInstanceToBeArchivedCountTask(task));
  }

  @Test
  void shouldNotScheduleProcessInstanceCountJobWhenProcessInstanceConfigDisabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(false);
    config.getHistory().setTrackArchivalMetricsForProcessInstance(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should not contain ProcessInstanceToBeArchivedCountJob when PI config is disabled")
        .noneMatch(task -> isProcessInstanceToBeArchivedCountTask(task))
        .noneMatch(task -> isProcessInstanceArchiverTask(task));
  }

  @Test
  void shouldNotScheduleProcessInstanceCountJobWhenMetricsTrackingDisabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(true);
    config.getHistory().setTrackArchivalMetricsForProcessInstance(false);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as(
            "Should not contain ProcessInstanceToBeArchivedCountJob when metrics tracking is disabled")
        .noneMatch(task -> isProcessInstanceToBeArchivedCountTask(task));
  }

  @Test
  void shouldScheduleBothProcessInstanceTasksWhenAllConfigsEnabled() {
    // given
    config.getHistory().setProcessInstanceEnabled(true);
    config.getHistory().setTrackArchivalMetricsForProcessInstance(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should contain both PI archiver and count tasks when all configs are enabled")
        .anyMatch(task -> isProcessInstanceArchiverTask(task))
        .anyMatch(task -> isProcessInstanceToBeArchivedCountTask(task));
  }

  @Test
  void shouldAlwaysScheduleNonProcessInstanceTasks() {
    // given
    config.getHistory().setProcessInstanceEnabled(false);
    config.getHistory().getRetention().setEnabled(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should always schedule incident and usage metrics tasks regardless of PI config")
        .hasSize(9)
        .anyMatch(task -> isTaskOfType(task, IncidentUpdateTask.class))
        .anyMatch(task -> isTaskOfType(task, UsageMetricArchiverJob.class))
        .anyMatch(task -> isTaskOfType(task, UsageMetricTUArchiverJob.class))
        .anyMatch(task -> isTaskOfType(task, JobBatchMetricsArchiverJob.class))
        .anyMatch(task -> isTaskOfType(task, StandaloneDecisionArchiverJob.class))
        .anyMatch(task -> isTaskOfType(task, BatchOperationArchiverJob.class))
        .anyMatch(task -> isTaskOfType(task, BatchOperationUpdateTask.class))
        .anyMatch(task -> isTaskOfType(task, ApplyRolloverPeriodJob.class))
        .anyMatch(task -> isTaskOfType(task, HistoryDeletionJob.class));
  }

  @Test
  void shouldNotScheduleApplyRolloverPeriodJobWhenRetentionDisabled() {
    // given
    config.getHistory().getRetention().setEnabled(false);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should not schedule ApplyRolloverPeriodJob when retention is disabled")
        .hasSize(10)
        .noneMatch(task -> isTaskOfType(task, ApplyRolloverPeriodJob.class));
  }

  @Test
  void shouldScheduleApplyRolloverPeriodJobWhenRetentionEnabled() {
    // given
    config.getHistory().getRetention().setEnabled(true);

    // when
    final var taskManager = factory.build();

    // then
    final var tasks = getTasksFromManager(taskManager);
    assertThat(tasks)
        .as("Should schedule ApplyRolloverPeriodJob when retention is enabled")
        .hasSize(11)
        .anyMatch(task -> isTaskOfType(task, ApplyRolloverPeriodJob.class));
  }

  private List<RunnableTask> getTasksFromManager(final BackgroundTaskManager taskManager) {
    try {
      final var field = BackgroundTaskManager.class.getDeclaredField("tasks");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      final var tasks = (List<RunnableTask>) field.get(taskManager);
      return tasks;
    } catch (final Exception e) {
      throw new RuntimeException("Failed to access tasks field", e);
    }
  }

  private boolean isProcessInstanceArchiverTask(final RunnableTask task) {
    return isTaskOfType(task, ProcessInstanceArchiverJob.class);
  }

  private boolean isProcessInstanceToBeArchivedCountTask(final RunnableTask task) {
    return isTaskOfType(task, ProcessInstanceToBeArchivedCountJob.class);
  }

  private boolean isTaskOfType(final RunnableTask task, final Class<?> type) {
    if (task instanceof final ReschedulingTask reschedulingTask) {
      return getWrappedTask(reschedulingTask).getClass().equals(type);
    }
    return false;
  }

  private BackgroundTask getWrappedTask(final ReschedulingTask reschedulingTask) {
    try {
      final var field = ReschedulingTask.class.getDeclaredField("task");
      field.setAccessible(true);
      return (BackgroundTask) field.get(reschedulingTask);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to access wrapped task", e);
    }
  }
}

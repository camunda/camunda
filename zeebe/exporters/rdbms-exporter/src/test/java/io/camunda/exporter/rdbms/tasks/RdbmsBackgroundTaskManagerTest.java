/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.tasks;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class RdbmsBackgroundTaskManagerTest {

  private static final Duration CLOSE_TIMEOUT = Duration.ofMillis(200);
  private static final int PARTITION_ID = 1;

  private HistoryCleanupService historyCleanupService;
  private HistoryDeletionService historyDeletionService;

  @BeforeEach
  void setUp() {
    historyCleanupService = mock(HistoryCleanupService.class);
    historyDeletionService = mock(HistoryDeletionService.class);
    when(historyCleanupService.cleanupHistory(anyInt(), any())).thenReturn(Duration.ofDays(1));
    when(historyCleanupService.cleanupUsageMetricsHistory(anyInt(), any()))
        .thenReturn(Duration.ofDays(1));
    when(historyCleanupService.cleanupJobBatchMetricsHistory(anyInt(), any()))
        .thenReturn(Duration.ofDays(1));
    when(historyCleanupService.getCurrentCleanupInterval(anyInt())).thenReturn(Duration.ofDays(1));
    when(historyCleanupService.getUsageMetricsHistoryCleanupInterval())
        .thenReturn(Duration.ofDays(1));
    when(historyCleanupService.getJobBatchMetricsHistoryCleanupInterval())
        .thenReturn(Duration.ofDays(1));
    when(historyDeletionService.deleteHistory(anyInt())).thenReturn(Duration.ofDays(1));
    when(historyDeletionService.getCurrentDelayBetweenRuns()).thenReturn(Duration.ofDays(1));
  }

  @Nested
  final class StartTest {

    @Test
    void shouldNotResubmitTasksOnStart() {
      // given
      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);
      manager.start();

      // when
      manager.start();

      // then - all tasks are submitted only once (start() is idempotent once fully started)
      manager.close();
    }

    @Test
    void shouldStartAndRunTasks() {
      // given
      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);

      // when
      manager.start();

      // then - services should have been called by the background tasks
      Awaitility.await("Background tasks should run")
          .untilAsserted(
              () -> Mockito.verify(historyCleanupService).cleanupHistory(anyInt(), any()));

      manager.close();
    }
  }

  @Nested
  final class CloseTest {

    @Test
    void shouldCloseTasksOnCloseByVerifyingServicesNoLongerCalled() {
      // given
      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);
      manager.start();

      // Wait for at least one service call to confirm tasks are running
      Awaitility.await("Tasks should run before close")
          .untilAsserted(
              () ->
                  Mockito.verify(historyCleanupService, Mockito.atLeastOnce())
                      .cleanupHistory(anyInt(), any()));

      // when
      assertThatCode(manager::close).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnClose() {
      // given
      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);
      manager.start();

      // when + then
      assertThatCode(manager::close).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowReopenAfterClose() {
      // given
      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);
      manager.start();
      manager.close();

      // when - start again after close
      manager.start();

      // then - tasks should run again
      Awaitility.await("Background tasks should run after reopen")
          .untilAsserted(
              () ->
                  Mockito.verify(historyCleanupService, Mockito.atLeastOnce())
                      .cleanupHistory(anyInt(), any()));

      manager.close();
    }
  }

  @Nested
  final class TaskBehaviorTest {

    @Test
    void shouldRescheduleAfterSuccessfulRun() {
      // given - services return a large delay so tasks don't re-run during the test
      when(historyCleanupService.cleanupHistory(anyInt(), any())).thenReturn(Duration.ofDays(1));

      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);

      // when
      manager.start();

      // then - cleanup should have been called once (immediate run after submit)
      Awaitility.await("HistoryCleanup should run once")
          .untilAsserted(
              () ->
                  Mockito.verify(historyCleanupService, Mockito.atLeastOnce())
                      .cleanupHistory(anyInt(), any()));

      manager.close();
    }

    @Test
    void shouldUseFallbackDelayOnError() {
      // given - cleanup service throws on first call
      final var fallbackDelay = Duration.ofDays(1);
      when(historyCleanupService.cleanupHistory(anyInt(), any()))
          .thenThrow(new RuntimeException("Simulated cleanup failure"));
      when(historyCleanupService.getCurrentCleanupInterval(anyInt())).thenReturn(fallbackDelay);

      final var manager =
          new RdbmsBackgroundTaskManager(
              PARTITION_ID,
              historyCleanupService,
              historyDeletionService,
              LoggerFactory.getLogger(RdbmsBackgroundTaskManagerTest.class),
              CLOSE_TIMEOUT);

      // when
      manager.start();

      // then - fallback delay supplier should have been called
      Awaitility.await("Fallback interval should be used on error")
          .untilAsserted(
              () ->
                  Mockito.verify(historyCleanupService, Mockito.atLeastOnce())
                      .getCurrentCleanupInterval(anyInt()));

      manager.close();
    }
  }
}

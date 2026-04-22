/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.tasks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SingleExecutionTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SingleExecutionTaskTest.class);

  @AutoClose
  private static final ScheduledThreadPoolExecutor EXECUTOR =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  private final BackgroundTask backgroundTask = mock(BackgroundTask.class);

  @Test
  void shouldStopAfterFirstSuccessfulExecution() {
    // given
    when(backgroundTask.execute()).thenReturn(CompletableFuture.completedFuture(42));
    final var task = new SingleExecutionTask(backgroundTask, 10L, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    Mockito.verify(backgroundTask, Mockito.timeout(5_000).times(1)).close();
    Mockito.verify(EXECUTOR, Mockito.after(200).never())
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRetryOnErrorAndStopAfterSuccess() {
    // given
    when(backgroundTask.execute())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("failure")))
        .thenReturn(CompletableFuture.completedFuture(1));
    final var task = new SingleExecutionTask(backgroundTask, 10L, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    Mockito.verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    Mockito.verify(backgroundTask, Mockito.timeout(5_000).times(1)).close();
  }

  @Test
  void shouldRetryIfTaskReturnsNullResult() {
    // given
    when(backgroundTask.execute())
        .thenReturn(null)
        .thenReturn(CompletableFuture.completedFuture(1));
    final var task = new SingleExecutionTask(backgroundTask, 10L, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    Mockito.verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    Awaitility.await().untilAsserted(() -> Mockito.verify(backgroundTask, Mockito.times(1)).close());
  }
}


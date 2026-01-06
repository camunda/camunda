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
import static org.mockito.Mockito.when;

import io.camunda.exporter.tasks.archiver.ArchiverJob;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReschedulingTaskTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReschedulingTaskTest.class);

  @AutoClose
  private static final ScheduledThreadPoolExecutor EXECUTOR =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  private final ArchiverJob archiverJob = mock(ArchiverJob.class);

  @Test
  void shouldRescheduleTaskOnError() {
    // given
    when(archiverJob.execute()).thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    final var task = new ReschedulingTask(archiverJob, 1, 10, 1000, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRescheduleTaskOnErrorWithDelay() {
    // given
    when(archiverJob.execute()).thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    final var task = new ReschedulingTask(archiverJob, 1, 10, 1000, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 12L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldResetErrorDelayOnSuccessfulArchiving() {
    // given
    when(archiverJob.execute())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()))
        .thenReturn(CompletableFuture.completedFuture(1))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    final var task = new ReschedulingTask(archiverJob, 1, 10L, 1000, EXECUTOR, LOGGER);

    // when
    task.run();

    // then - scheduled after the minimum delay on the first error, again on success, and then
    // since we reset the error delay, again on the second error
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRescheduleEvenIfJobResultIsNull() {
    // given
    when(archiverJob.execute()).thenReturn(null);

    final var task = new ReschedulingTask(archiverJob, 1, 10L, 1000, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRescheduleTaskWithDelayOnPartialBatch() {
    // given
    when(archiverJob.execute()).thenReturn(CompletableFuture.completedFuture(1));

    final var task = new ReschedulingTask(archiverJob, 2, 10L, 1000, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    // FYI - the back off is exponential, but at low values it looks like it's always adding the
    // same value
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 12L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 14L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldRespectMaxDelayWhenRescheduleOnPartialBatch() {
    // given
    when(archiverJob.execute()).thenReturn(CompletableFuture.completedFuture(1));

    final var task = new ReschedulingTask(archiverJob, 2, 10L, 12L, EXECUTOR, LOGGER);

    // when
    task.run();

    // then
    // FYI - the back off is exponential, but at low values it looks like it's always adding the
    // same value
    final var inOrder = Mockito.inOrder(EXECUTOR);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 10L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 12L, TimeUnit.MILLISECONDS);
    inOrder
        .verify(EXECUTOR, Mockito.timeout(5_000).times(1))
        .schedule(task, 12L, TimeUnit.MILLISECONDS);
  }

  @Test
  void shouldNotRescheduleIfClosed() {
    // given
    final var runningCounter = new AtomicInteger();
    when(archiverJob.execute())
        .thenReturn(CompletableFuture.completedFuture(runningCounter.incrementAndGet()));

    final var task = new ReschedulingTask(archiverJob, 1, 100, 100, EXECUTOR, LOGGER);

    // when
    task.run();
    task.close();

    // then
    Awaitility.await("Until it's been rescheduled").until(() -> task.executionCount() == 1);
    assertThat(runningCounter.get()).isEqualTo(1);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.tasks.archiver.ArchiverJob;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

  @Test
  void shouldRescheduleTaskOnError() {
    // given
    final var task = new ReschedulingTask(new FailingJob(), 1, 10, 1000, EXECUTOR, LOGGER);

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
    final var task = new ReschedulingTask(new FailingJob(), 1, 10, 1000, EXECUTOR, LOGGER);

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
    final var job =
        new ArchiverJob() {
          private final AtomicInteger count = new AtomicInteger();

          @Override
          public CompletableFuture<Integer> archiveNextBatch() {
            final var runCount = count.getAndIncrement();
            if (runCount == 0) {
              return CompletableFuture.failedFuture(new RuntimeException("error"));
            }

            if (runCount == 2) {
              return CompletableFuture.failedFuture(new RuntimeException("error"));
            }

            return CompletableFuture.completedFuture(1);
          }
        };
    final var task = new ReschedulingTask(job, 1, 10L, 1000, EXECUTOR, LOGGER);

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
    final var job =
        new ArchiverJob() {
          @Override
          public CompletableFuture<Integer> archiveNextBatch() {
            return null;
          }
        };
    final var task = new ReschedulingTask(job, 1, 10L, 1000, EXECUTOR, LOGGER);

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
    final var job =
        new ArchiverJob() {
          @Override
          public CompletableFuture<Integer> archiveNextBatch() {
            return CompletableFuture.completedFuture(1);
          }
        };
    final var task = new ReschedulingTask(job, 2, 10L, 1000, EXECUTOR, LOGGER);

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
    final var job =
        new ArchiverJob() {
          @Override
          public CompletableFuture<Integer> archiveNextBatch() {
            return CompletableFuture.completedFuture(1);
          }
        };
    final var task = new ReschedulingTask(job, 2, 10L, 12L, EXECUTOR, LOGGER);

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
    final var runningCounter = new AtomicInteger();
    final var job =
        new ArchiverJob() {
          @Override
          public CompletionStage<Integer> archiveNextBatch() {
            return CompletableFuture.completedFuture(runningCounter.incrementAndGet());
          }
        };
    final var task = new ReschedulingTask(job, 1, 100, 100, EXECUTOR, LOGGER);
    task.run();
    task.close();
    Awaitility.await("Until it's been rescheduled").until(() -> task.executionCount() == 1);
    assertThat(runningCounter.get()).isEqualTo(1);
  }

  private static final class FailingJob implements ArchiverJob {
    @Override
    public CompletableFuture<Integer> archiveNextBatch() {
      return CompletableFuture.failedFuture(new RuntimeException("failure"));
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractImportSchedulerTest {

  private ZeebeImportScheduler scheduler;

  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.stopImportScheduling();
    }
  }

  @Test
  void shouldRunSlowMediatorWithoutBlockingFastMediator() throws Exception {
    // given
    final CountDownLatch fastMediatorRan = new CountDownLatch(1);
    final CountDownLatch releaseSlowMediator = new CountDownLatch(1);

    final ImportMediator slowMediator = mock(ImportMediator.class);
    final ImportMediator fastMediator = mock(ImportMediator.class);

    // lenient: whether these get invoked before the test ends is a timing race against the
    // background reschedule threads, not something this test asserts on.
    lenient().when(slowMediator.getBackoffTimeInMs()).thenReturn(0L);
    lenient().when(fastMediator.getBackoffTimeInMs()).thenReturn(0L);

    when(slowMediator.runImport())
        .thenAnswer(
            inv -> {
              releaseSlowMediator.await(5, TimeUnit.SECONDS);
              return CompletableFuture.completedFuture(null);
            });
    when(fastMediator.runImport())
        .thenAnswer(
            inv -> {
              fastMediatorRan.countDown();
              return CompletableFuture.completedFuture(null);
            });

    scheduler =
        new ZeebeImportScheduler(List.of(slowMediator, fastMediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then: fastMediator completes even though slowMediator is still blocked
    assertThat(fastMediatorRan.await(2, TimeUnit.SECONDS))
        .as("fast mediator should complete independently of the blocked slow mediator")
        .isTrue();

    releaseSlowMediator.countDown();
  }

  @Test
  void shouldRescheduleMediatorAutomaticallyAfterCompletion() throws Exception {
    // given
    final int expectedRuns = 3;
    final CountDownLatch runsCompleted = new CountDownLatch(expectedRuns);

    final ImportMediator mediator = mock(ImportMediator.class);
    when(mediator.getBackoffTimeInMs()).thenReturn(0L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              runsCompleted.countDown();
              return CompletableFuture.completedFuture(null);
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then: mediator is invoked repeatedly without any external trigger
    assertThat(runsCompleted.await(2, TimeUnit.SECONDS))
        .as("mediator should be rescheduled and run at least " + expectedRuns + " times")
        .isTrue();

    verify(mediator, atLeast(expectedRuns)).runImport();
  }

  @Test
  void shouldApplyMediatorBackoffBeforeRescheduling() throws Exception {
    // given
    final long backoffMs = 200L;
    final AtomicInteger runCount = new AtomicInteger(0);
    final AtomicInteger backoffCallCount = new AtomicInteger(0);
    final CountDownLatch firstRunComplete = new CountDownLatch(1);

    final ImportMediator mediator = mock(ImportMediator.class);
    // return backoff on the first reschedule decision, 0 on subsequent ones. Tracked via a
    // dedicated counter (rather than runCount) since getBackoffTimeInMs() is evaluated by the
    // scheduler right after runImport() has already incremented runCount for that run.
    when(mediator.getBackoffTimeInMs())
        .thenAnswer(inv -> backoffCallCount.getAndIncrement() == 0 ? backoffMs : 0L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              runCount.incrementAndGet();
              firstRunComplete.countDown();
              return CompletableFuture.completedFuture(null);
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();
    assertThat(firstRunComplete.await(2, TimeUnit.SECONDS)).isTrue();

    // then: immediately after the first run, the mediator should NOT have run again yet
    // (it's waiting for backoffMs)
    assertThat(runCount.get())
        .as("mediator should not have been rescheduled immediately after first run with backoff")
        .isEqualTo(1);

    // after backoff elapses, it runs again
    Thread.sleep(backoffMs + 100);
    assertThat(runCount.get())
        .as("mediator should have run again after backoff elapsed")
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldContinueRunningOtherMediatorsWhenOneFails() throws Exception {
    // given
    final CountDownLatch successMediatorRan = new CountDownLatch(1);

    final ImportMediator failingMediator = mock(ImportMediator.class);
    final ImportMediator successMediator = mock(ImportMediator.class);

    // small backoff so the failing mediator retries a few times without spinning in a tight,
    // zero-delay loop (and flooding the log) for the duration of the test
    when(failingMediator.getBackoffTimeInMs()).thenReturn(50L);
    when(successMediator.getBackoffTimeInMs()).thenReturn(0L);

    when(failingMediator.runImport()).thenThrow(new RuntimeException("simulated ES failure"));
    when(successMediator.runImport())
        .thenAnswer(
            inv -> {
              successMediatorRan.countDown();
              return CompletableFuture.completedFuture(null);
            });

    scheduler =
        new ZeebeImportScheduler(
            List.of(failingMediator, successMediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then: success mediator completes despite the other mediator throwing
    assertThat(successMediatorRan.await(2, TimeUnit.SECONDS))
        .as("success mediator should run even when another mediator throws")
        .isTrue();
  }

  @Test
  void shouldReportIsImportingWhileExecutorIsRunning() throws Exception {
    // given
    final CountDownLatch mediatorRan = new CountDownLatch(1);
    final ImportMediator mediator = mock(ImportMediator.class);
    lenient().when(mediator.getBackoffTimeInMs()).thenReturn(60_000L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              mediatorRan.countDown();
              return CompletableFuture.completedFuture(null);
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then
    assertThat(scheduler.isImporting()).isTrue();
    assertThat(mediatorRan.await(2, TimeUnit.SECONDS))
        .as("mediator should have run once while the scheduler is active")
        .isTrue();

    scheduler.stopImportScheduling();
    assertThat(scheduler.isImporting()).isFalse();
  }

  @Test
  void shouldHandleEmptyMediatorListWithoutThrowing() {
    // given
    scheduler = new ZeebeImportScheduler(List.of(), mock(ZeebeConfigDto.class));

    // when / then: no exception on start or stop
    scheduler.startImportScheduling();
    scheduler.stopImportScheduling();
  }
}

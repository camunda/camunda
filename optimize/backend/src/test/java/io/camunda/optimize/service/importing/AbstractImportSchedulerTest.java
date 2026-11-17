/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    final CountDownLatch slowMediatorStarted = new CountDownLatch(1);
    final AtomicBoolean slowMediatorCompleted = new AtomicBoolean(false);

    final ImportMediator slowMediator = readyMediator();
    final ImportMediator fastMediator = readyMediator();

    // lenient: whether these get invoked before the test ends is a timing race against the
    // background reschedule threads, not something this test asserts on.
    lenient().when(slowMediator.getBackoffTimeInMs()).thenReturn(0L);
    lenient().when(fastMediator.getBackoffTimeInMs()).thenReturn(0L);

    when(slowMediator.runImport())
        .thenAnswer(
            inv -> {
              slowMediatorStarted.countDown();
              releaseSlowMediator.await(5, TimeUnit.SECONDS);
              slowMediatorCompleted.set(true);
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

    // then: fastMediator completes while slowMediator is confirmed still running (not just
    // never scheduled), proving genuine concurrency rather than a lucky ordering
    assertThat(slowMediatorStarted.await(2, TimeUnit.SECONDS))
        .as("slow mediator should have started running on its own thread")
        .isTrue();
    assertThat(fastMediatorRan.await(2, TimeUnit.SECONDS))
        .as("fast mediator should complete independently of the blocked slow mediator")
        .isTrue();
    assertThat(slowMediatorCompleted.get())
        .as("slow mediator should still be blocked when the fast mediator has already completed")
        .isFalse();

    releaseSlowMediator.countDown();
  }

  @Test
  void shouldRescheduleMediatorAutomaticallyAfterCompletion() throws Exception {
    // given
    final int expectedRuns = 3;
    final CountDownLatch runsCompleted = new CountDownLatch(expectedRuns);

    final ImportMediator mediator = readyMediator();
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

    final ImportMediator mediator = readyMediator();
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

    // then: the mediator should not be rescheduled before backoff elapses. Checked with
    // Awaitility's during() (holds continuously for part of the backoff window) rather than a
    // single point-in-time read, since a stalled/descheduled test thread could otherwise observe
    // the count after the real reschedule already happened and flake under a loaded CI runner.
    // A short, fast poll loop (10ms) with real margin before the real 200ms backoff fires, since
    // Awaitility's own default poll interval/delay would otherwise eat most of a tight budget.
    await()
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(10))
        .during(Duration.ofMillis(50))
        .atMost(Duration.ofMillis(150))
        .untilAsserted(
            () ->
                assertThat(runCount.get())
                    .as(
                        "mediator should not have been rescheduled immediately after first run"
                            + " with backoff")
                    .isEqualTo(1));

    // after backoff elapses, it runs again. Polled via Awaitility rather than a fixed
    // Thread.sleep + buffer, so this doesn't flake under a slow/loaded CI runner.
    await()
        .atMost(Duration.ofMillis(backoffMs + 2000))
        .untilAsserted(
            () ->
                assertThat(runCount.get())
                    .as("mediator should have run again after backoff elapsed")
                    .isGreaterThanOrEqualTo(2));
  }

  @Test
  void shouldWaitUntilMediatorCanImportBeforeRunning() throws Exception {
    // given
    final CountDownLatch canImportChecked = new CountDownLatch(1);

    final ImportMediator mediator = mock(ImportMediator.class);
    when(mediator.canImport())
        .thenAnswer(
            inv -> {
              canImportChecked.countDown();
              return false;
            });
    when(mediator.getBackoffTimeInMs()).thenReturn(60_000L);

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then
    assertThat(canImportChecked.await(2, TimeUnit.SECONDS))
        .as("scheduler should check mediator readiness")
        .isTrue();
    await()
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(10))
        .during(Duration.ofMillis(50))
        .atMost(Duration.ofMillis(150))
        .untilAsserted(() -> verify(mediator, never()).runImport());
  }

  @Test
  void shouldRunMediatorAfterCanImportBecomesTrue() throws Exception {
    // given
    final CountDownLatch mediatorRan = new CountDownLatch(1);
    final AtomicInteger canImportChecks = new AtomicInteger(0);

    final ImportMediator mediator = mock(ImportMediator.class);
    when(mediator.canImport()).thenAnswer(inv -> canImportChecks.getAndIncrement() == 1);
    when(mediator.getBackoffTimeInMs()).thenReturn(50L, 60_000L);
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
    assertThat(mediatorRan.await(2, TimeUnit.SECONDS))
        .as("mediator should run after its backoff makes it ready")
        .isTrue();
  }

  @Test
  void shouldRescheduleMediatorWhenRunImportReturnsNullFuture() {
    // given
    final AtomicInteger runCount = new AtomicInteger(0);

    final ImportMediator mediator = mock(ImportMediator.class);
    when(mediator.canImport()).thenReturn(true);
    when(mediator.getBackoffTimeInMs()).thenReturn(0L, 60_000L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              if (runCount.incrementAndGet() == 1) {
                return null;
              }
              return CompletableFuture.completedFuture(null);
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(2));
  }

  @Test
  void shouldWaitForMediatorFutureCompletionBeforeRescheduling() {
    // given
    final AtomicInteger runCount = new AtomicInteger(0);
    final AtomicReference<CompletableFuture<Void>> firstRunFuture = new AtomicReference<>();

    final ImportMediator mediator = readyMediator();
    when(mediator.getBackoffTimeInMs()).thenReturn(0L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              final CompletableFuture<Void> importFuture = new CompletableFuture<>();
              firstRunFuture.compareAndSet(null, importFuture);
              runCount.incrementAndGet();
              return importFuture;
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(1));
    await()
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(10))
        .during(Duration.ofMillis(50))
        .atMost(Duration.ofMillis(150))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(1));

    firstRunFuture.get().complete(null);
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(2));
  }

  @Test
  void shouldRescheduleMediatorAfterFutureCompletesExceptionally() {
    // given
    final AtomicInteger runCount = new AtomicInteger(0);
    final AtomicReference<CompletableFuture<Void>> firstRunFuture = new AtomicReference<>();

    final ImportMediator mediator = readyMediator();
    when(mediator.getBackoffTimeInMs()).thenReturn(0L);
    when(mediator.runImport())
        .thenAnswer(
            inv -> {
              final CompletableFuture<Void> importFuture = new CompletableFuture<>();
              firstRunFuture.compareAndSet(null, importFuture);
              runCount.incrementAndGet();
              return importFuture;
            });

    scheduler = new ZeebeImportScheduler(List.of(mediator), mock(ZeebeConfigDto.class));

    // when
    scheduler.startImportScheduling();

    // then
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(1));
    firstRunFuture.get().completeExceptionally(new RuntimeException("simulated async failure"));
    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(runCount.get()).isEqualTo(2));
  }

  @Test
  void shouldContinueRunningOtherMediatorsWhenOneFails() throws Exception {
    // given
    final CountDownLatch successMediatorRan = new CountDownLatch(1);

    final ImportMediator failingMediator = readyMediator();
    final ImportMediator successMediator = readyMediator();

    // small backoff so the failing mediator retries a few times without spinning in a tight,
    // zero-delay loop (and flooding the log) for the duration of the test. lenient: the test only
    // waits on successMediator, so whether failingMediator's background thread has reached its
    // reschedule decision by the time the test ends is an unasserted timing race.
    lenient().when(failingMediator.getBackoffTimeInMs()).thenReturn(50L);
    lenient().when(successMediator.getBackoffTimeInMs()).thenReturn(0L);

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
    final ImportMediator mediator = readyMediator();
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

  private ImportMediator readyMediator() {
    final ImportMediator mediator = mock(ImportMediator.class);
    // lenient: stopImportScheduling() shutdownNow()s the executor, which cancels any
    // not-yet-started mediator task. On a CPU-starved runner, a mediator's task may never reach
    // its canImport() check before teardown — whether that happens is an unasserted timing race,
    // not something every test using this helper cares about.
    lenient().when(mediator.canImport()).thenReturn(true);
    return mediator;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.ExporterException;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingFlushTest {
  private static final long TIME0 = 789L;
  private static final long TIME1 = 890L;

  private final Runnable flush = mock(Runnable.class);
  private final InstantSource clock = mock(InstantSource.class);

  @BeforeEach
  void setup() {
    when(clock.millis()).thenReturn(TIME0, TIME1);
  }

  @Test
  void shouldWaitForCompletionOfSuccessfulRun() {
    // given
    final var pendingFlush = new PendingFlush(Runnable::run, clock, flush, 123L);

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush).run();
    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);
  }

  @Test
  void shouldNotUpdateFlushTimeIfNotFlushed() {
    // given
    final var pendingFlush =
        new PendingFlush(
            task -> {
              /* no-op */
            },
            clock,
            flush,
            123L);

    // then
    verify(flush, never()).run();
    assertThat(pendingFlush.maybeFlushTimeMillis()).isEmpty();
  }

  @Test
  void shouldWaitForCompletionOfAlreadyCompletedRun() {
    // given
    final var pendingFlush = new PendingFlush(Runnable::run, clock, flush, 123L);
    pendingFlush.waitForCompletion();

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush).run();
    // flush time should not have been updated by the second waitForCompletion call
    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);
  }

  @Test
  void shouldThrowExportExceptionWhenWaitingForFailedRun() {
    // given
    doThrow(new ExporterException("flush failed")).when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, clock, flush, 123L);

    // when + then
    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(ExporterException.class)
        .hasMessage("flush failed");
    verify(flush).run();

    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenWaitingForFailedRun() {
    // given
    doThrow(new RuntimeException("flush failed")).when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, clock, flush, 123L);

    // when + then
    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected exception during flush")
        .hasCauseInstanceOf(RuntimeException.class)
        .cause()
        .hasMessage("flush failed");
    verify(flush).run();

    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);
  }

  @Test
  public void shouldRetryFlushAfterFailure() {
    // given
    doThrow(new ExporterException("flush failed")).doNothing().when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, clock, flush, 123L);

    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(ExporterException.class)
        .hasMessage("flush failed");

    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush, times(2)).run();

    // retried so we should be on the next time for the flush
    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME1);
  }

  @Test
  public void shouldResetFlushTimeWhenRetryingFlush() {
    // given
    doThrow(new ExporterException("flush failed")).doNothing().when(flush).run();

    // use this to let us control exactly when the tasks run
    final var semaphore = new Semaphore(1);
    final Executor executor = mock(Executor.class);
    doAnswer(
            invocation -> {
              semaphore.acquire();
              final Runnable task = invocation.getArgument(0);
              task.run();
              return null;
            })
        .when(executor)
        .execute(any());

    final var pendingFlush = new PendingFlush(executor, clock, flush, 123L);

    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(ExporterException.class)
        .hasMessage("flush failed");

    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME0);

    // when

    // pendingFlush::waitForCompletion will block so we can confirm the time gets reset
    final var waitingForCompletion = CompletableFuture.runAsync(pendingFlush::waitForCompletion);

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(pendingFlush.maybeFlushTimeMillis()).isEmpty());

    // second flush not run yet
    verify(flush).run();

    // now let things continue
    semaphore.release();

    waitingForCompletion.join();

    verify(flush, times(2)).run();
    assertThat(pendingFlush.maybeFlushTimeMillis()).hasValue(TIME1);
  }
}

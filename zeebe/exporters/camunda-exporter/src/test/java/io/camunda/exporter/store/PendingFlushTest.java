/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.exporter.api.ExporterException;
import org.junit.jupiter.api.Test;

class PendingFlushTest {

  @Test
  void shouldWaitForCompletionOfSuccessfulRun() {
    // given
    final var flush = mock(Runnable.class);
    final var pendingFlush = new PendingFlush(Runnable::run, flush, 123L);

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush).run();
  }

  @Test
  void shouldWaitForCompletionOfAlreadyCompletedRun() {
    // given
    final var flush = mock(Runnable.class);
    final var pendingFlush = new PendingFlush(Runnable::run, flush, 123L);
    pendingFlush.waitForCompletion();

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush).run();
  }

  @Test
  void shouldThrowExportExceptionWhenWaitingForFailedRun() {
    // given
    final var flush = mock(Runnable.class);
    doThrow(new ExporterException("flush failed")).when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, flush, 123L);

    // when + then
    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(ExporterException.class)
        .hasMessage("flush failed");
    verify(flush).run();
  }

  @Test
  void shouldThrowIllegalStateExceptionWhenWaitingForFailedRun() {
    // given
    final var flush = mock(Runnable.class);
    doThrow(new RuntimeException("flush failed")).when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, flush, 123L);

    // when + then
    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected exception during flush")
        .hasCauseInstanceOf(RuntimeException.class)
        .cause()
        .hasMessage("flush failed");
    verify(flush).run();
  }

  @Test
  public void shouldRetryFlushAfterFailure() {
    // given
    final var flush = mock(Runnable.class);
    doThrow(new ExporterException("flush failed")).doNothing().when(flush).run();

    final var pendingFlush = new PendingFlush(Runnable::run, flush, 123L);

    assertThatThrownBy(pendingFlush::waitForCompletion)
        .isInstanceOf(ExporterException.class)
        .hasMessage("flush failed");

    // when
    pendingFlush.waitForCompletion();

    // then
    verify(flush, times(2)).run();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.camunda.search.exception.CamundaSearchException;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ExponentialBackoffRetryingQueryExecutorTest {

  final int maxRetries = 2;
  final Duration initialRetryDelay = Duration.ofMillis(50);
  final double backoffFactor = 2.0;

  @Test
  public void shouldReturnOnFirstTry() throws Exception {
    final var retryStrategy =
        new ExponentialBackoffRetryingQueryExecutor(maxRetries, initialRetryDelay, backoffFactor);

    final var callableResult = "result";
    final Callable<String> callable = mock(Callable.class);
    when(callable.call()).thenReturn(callableResult);

    final var result = retryStrategy.runRetryable(callable);

    verify(callable, times(1)).call();
    assertThat(result).isEqualTo(callableResult);
  }

  @Test
  public void shouldReturnOnFirstTryWithNoRetries() throws Exception {
    final var retryStrategy =
        new ExponentialBackoffRetryingQueryExecutor(0, initialRetryDelay, backoffFactor);

    final var callableResult = "result";
    final Callable<String> callable = mock(Callable.class);
    when(callable.call()).thenReturn(callableResult);

    final var result = retryStrategy.runRetryable(callable);

    verify(callable, times(1)).call();
    assertThat(result).isEqualTo(callableResult);
  }

  @Test
  public void shouldDoTwoRetries() throws Exception {
    final var retryStrategy =
        new ExponentialBackoffRetryingQueryExecutor(maxRetries, initialRetryDelay, backoffFactor);

    final var callableResult = "result";
    final Callable<String> callable = mock(Callable.class);
    when(callable.call()).thenThrow(new Exception("error 1")).thenReturn(callableResult);

    final var result = retryStrategy.runRetryable(callable);

    verify(callable, times(2)).call();
    assertThat(result).isEqualTo(callableResult);
  }

  @Test
  public void shouldThrowException() throws Exception {
    final var retryStrategy =
        new ExponentialBackoffRetryingQueryExecutor(maxRetries, initialRetryDelay, backoffFactor);

    final Callable<String> callable = mock(Callable.class);
    when(callable.call())
        .thenThrow(new Exception("error 1"))
        .thenThrow(new Exception("error 2"))
        .thenThrow(new Exception("error 3"));

    final long startTime = System.currentTimeMillis();
    assertThatThrownBy(() -> retryStrategy.runRetryable(callable)).cause().hasMessage("error 3");
    final long endTime = System.currentTimeMillis();

    verify(callable, times(3)).call();

    // The total delay should be at least the sum of the delays for 3 retries:
    // 50ms + 100ms = 150ms
    assertThat(endTime - startTime).isGreaterThan(150);
  }

  @Test
  public void shouldFailImmediatelyOnCamundaSearchExceptionWithReasonNotFound() throws Exception {
    final var retryStrategy =
        new ExponentialBackoffRetryingQueryExecutor(maxRetries, initialRetryDelay, backoffFactor);

    final Callable<String> callable = mock(Callable.class);
    when(callable.call())
        .thenThrow(new CamundaSearchException("error", CamundaSearchException.Reason.NOT_FOUND));

    assertThatThrownBy(() -> retryStrategy.runRetryable(callable)).cause().hasMessage("error");

    verify(callable, times(1)).call();
  }
}

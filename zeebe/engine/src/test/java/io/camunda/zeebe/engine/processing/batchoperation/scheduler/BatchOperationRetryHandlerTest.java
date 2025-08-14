/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializer.BatchOperationInitializationException;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializer.BatchOperationInitializationResult;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryResult;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryableOperation;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BatchOperationRetryHandlerTest {

  private static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(100);
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);
  private static final int MAX_RETRIES = 3;
  private static final int BACKOFF_FACTOR = 2;
  private static final long BATCH_OPERATION_KEY = 12345L;
  private static final int NUM_ATTEMPTS = 0;
  private BatchOperationRetryHandler retryHandler;
  private RetryableOperation operation;

  @BeforeEach
  void setUp() {
    retryHandler =
        new BatchOperationRetryHandler(
            INITIAL_RETRY_DELAY, MAX_RETRY_DELAY, MAX_RETRIES, BACKOFF_FACTOR);
    operation = mock(RetryableOperation.class);
  }

  @Test
  void shouldReturnSuccessWhenOperationSucceeds() {
    // given
    final var expectedResult =
        new BatchOperationInitializationResult(BATCH_OPERATION_KEY, "new-cursor");
    when(operation.execute()).thenReturn(expectedResult);

    // when
    final var result = retryHandler.executeWithRetry(operation, NUM_ATTEMPTS);

    // then
    assertThat(result).isInstanceOf(RetryResult.Success.class);
    final var success = (RetryResult.Success) result;
    assertThat(success.searchResultCursor()).isEqualTo("new-cursor");
  }

  @Test
  void shouldRetryWhenOperationFailsWithRetryableException() {
    // given
    final var retryableException =
        new CamundaSearchException("Temporary failure", Reason.CONNECTION_FAILED);
    final var initException =
        new BatchOperationInitializationException(retryableException, "test-end-cursor");
    when(operation.execute()).thenThrow(initException);

    // when
    final var result = retryHandler.executeWithRetry(operation, NUM_ATTEMPTS);

    // then
    assertThat(result).isInstanceOf(RetryResult.Retry.class);
    final var retry = (RetryResult.Retry) result;
    assertThat(retry.delay()).isEqualTo(INITIAL_RETRY_DELAY);
    assertThat(retry.endCursor()).isEqualTo("test-end-cursor");
    assertThat(retry.numAttempts()).isEqualTo(1);
  }

  @Test
  void shouldFailImmediatelyWhenMaxRetriesExceeded() {
    // given
    final var retryableException =
        new CamundaSearchException("Temporary failure", Reason.CONNECTION_FAILED);
    final var initException =
        new BatchOperationInitializationException(retryableException, "cursor");
    when(operation.execute()).thenThrow(initException);

    // when
    final var result = retryHandler.executeWithRetry(operation, MAX_RETRIES);

    // then
    assertThat(result).isInstanceOf(RetryResult.Failure.class);
    final var failure = (RetryResult.Failure) result;
    assertThat(failure.exception()).isEqualTo(initException);
  }

  @ParameterizedTest
  @EnumSource(
      value = Reason.class,
      names = {"NOT_FOUND", "NOT_UNIQUE", "SECONDARY_STORAGE_NOT_SET", "FORBIDDEN"})
  void shouldFailImmediatelyForNonRetryableReasons(final Reason reason) {
    // given
    final var nonRetryableException = new CamundaSearchException("Non-retryable", reason);
    final var initException =
        new BatchOperationInitializationException(nonRetryableException, "cursor");
    when(operation.execute()).thenThrow(initException);

    // when
    final var result = retryHandler.executeWithRetry(operation, NUM_ATTEMPTS);

    // then
    assertThat(result).isInstanceOf(RetryResult.Failure.class);
    final var failure = (RetryResult.Failure) result;
    assertThat(failure.exception()).isEqualTo(initException);
  }

  @Test
  void shouldRetryForNonCamundaSearchExceptions() {
    // given
    final var genericException = new RuntimeException("Generic error");
    final var initException = new BatchOperationInitializationException(genericException, "cursor");
    when(operation.execute()).thenThrow(initException);

    // when
    final var result = retryHandler.executeWithRetry(operation, NUM_ATTEMPTS);

    // then
    assertThat(result).isInstanceOf(RetryResult.Retry.class);
  }
}

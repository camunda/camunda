/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryPolicy.RetryDecision;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BatchOperationRetryPolicyTest {

  private static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(100);
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);
  private static final int MAX_RETRIES = 3;
  private static final int BACKOFF_FACTOR = 2;
  private static final int NUM_ATTEMPTS = 0;
  private static final String CURSOR = "test-cursor";

  private BatchOperationRetryPolicy retryPolicy;

  @BeforeEach
  void setUp() {
    retryPolicy =
        new BatchOperationRetryPolicy(
            INITIAL_RETRY_DELAY, MAX_RETRY_DELAY, MAX_RETRIES, BACKOFF_FACTOR);
  }

  @Test
  void shouldRetryWithInitialDelayOnFirstAttempt() {
    // given
    final var retryableException =
        new CamundaSearchException("Temporary failure", Reason.CONNECTION_FAILED);

    // when
    final var decision = retryPolicy.evaluate(CURSOR, retryableException, NUM_ATTEMPTS);

    // then
    assertThat(decision).isInstanceOf(RetryDecision.Retry.class);
    final var retry = (RetryDecision.Retry) decision;
    assertThat(retry.delay()).isEqualTo(INITIAL_RETRY_DELAY);
    assertThat(retry.cursor()).isEqualTo(CURSOR);
    assertThat(retry.numAttempts()).isEqualTo(1);
  }

  @Test
  void shouldRetryWithExponentialBackoff() {
    // given
    final var exception = new RuntimeException("Transient failure");

    // when - second attempt (after first retry)
    final var decision = retryPolicy.evaluate(CURSOR, exception, 1);

    // then - delay should be doubled (100ms * 2^1 = 200ms)
    assertThat(decision).isInstanceOf(RetryDecision.Retry.class);
    final var retry = (RetryDecision.Retry) decision;
    assertThat(retry.delay()).isEqualTo(Duration.ofMillis(200));
    assertThat(retry.numAttempts()).isEqualTo(2);
  }

  @Test
  void shouldCapDelayAtMaxRetryDelay() {
    // given - a policy where the delay exceeds max before retries are exhausted
    final var policyWithLowMaxDelay =
        new BatchOperationRetryPolicy(Duration.ofMillis(100), Duration.ofMillis(500), 20, 2);
    final var exception = new RuntimeException("Transient failure");
    // With initial 100ms and backoff factor 2, attempt 5 would give 100 * 2^5 = 3200ms
    // But max is 500ms

    // when
    final var decision = policyWithLowMaxDelay.evaluate(CURSOR, exception, 5);

    // then - delay should be capped at max
    assertThat(decision).isInstanceOf(RetryDecision.Retry.class);
    final var retry = (RetryDecision.Retry) decision;
    assertThat(retry.delay()).isEqualTo(Duration.ofMillis(500));
  }

  @Test
  void shouldHandleLargeAttemptNumbersWithoutOverflow() {
    // given - a policy that allows many retries to test overflow protection
    final var policyWithManyRetries =
        new BatchOperationRetryPolicy(Duration.ofMillis(100), Duration.ofSeconds(10), 100, 2);
    final var exception = new RuntimeException("Transient failure");

    // when - attempt number that would overflow with Math.pow(2, 50)
    final var decision = policyWithManyRetries.evaluate(CURSOR, exception, 50);

    // then - should safely cap at max delay, not overflow
    assertThat(decision).isInstanceOf(RetryDecision.Retry.class);
    final var retry = (RetryDecision.Retry) decision;
    assertThat(retry.delay()).isEqualTo(Duration.ofSeconds(10));
    assertThat(retry.delay().isNegative()).isFalse();
  }

  @Test
  void shouldFailWhenMaxRetriesExceeded() {
    // given
    final var retryableException =
        new CamundaSearchException("Temporary failure", Reason.CONNECTION_FAILED);

    // when
    final var decision = retryPolicy.evaluate(CURSOR, retryableException, MAX_RETRIES);

    // then
    assertThat(decision).isInstanceOf(RetryDecision.Fail.class);
    final var fail = (RetryDecision.Fail) decision;
    assertThat(fail.message()).contains("Temporary failure");
    assertThat(fail.errorType()).isEqualTo(BatchOperationErrorType.QUERY_FAILED);
  }

  @ParameterizedTest
  @EnumSource(
      value = Reason.class,
      names = {"NOT_FOUND", "NOT_UNIQUE", "SECONDARY_STORAGE_NOT_SET", "FORBIDDEN"})
  void shouldFailImmediatelyForNonRetryableReasons(final Reason reason) {
    // given
    final var nonRetryableException = new CamundaSearchException("Non-retryable", reason);

    // when
    final var decision = retryPolicy.evaluate(CURSOR, nonRetryableException, NUM_ATTEMPTS);

    // then
    assertThat(decision).isInstanceOf(RetryDecision.Fail.class);
    final var fail = (RetryDecision.Fail) decision;
    assertThat(fail.message()).contains("Non-retryable");
    assertThat(fail.errorType()).isEqualTo(BatchOperationErrorType.QUERY_FAILED);
  }

  @Test
  void shouldRetryForNonCamundaSearchExceptions() {
    // given
    final var genericException = new RuntimeException("Generic error");

    // when
    final var decision = retryPolicy.evaluate(CURSOR, genericException, NUM_ATTEMPTS);

    // then
    assertThat(decision).isInstanceOf(RetryDecision.Retry.class);
  }
}

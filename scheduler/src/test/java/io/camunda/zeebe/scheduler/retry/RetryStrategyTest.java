/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RetryStrategyTest {

  @RegisterExtension
  private final ControlledActorSchedulerExtension schedulerRule =
      new ControlledActorSchedulerExtension();

  private ActorFuture<Boolean> resultFuture;

  @ParameterizedTest
  @ValueSource(strings = {"endless", "recoverable", "abortable", "backoff"})
  void shouldRunUntilDone(final TestCase<?> test) {
    // given
    final var count = new AtomicInteger(0);
    schedulerRule.submitActor(test.actor);

    // when
    test.actor.run(
        () -> resultFuture = test.strategy.runWithRetry(() -> count.incrementAndGet() == 10));
    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture).succeedsWithin(Duration.ZERO).isEqualTo(true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"endless", "recoverable", "abortable", "backoff"})
  void shouldStopWhenAbortConditionReturnsTrue(final TestCase<?> test) {
    // given
    final AtomicInteger count = new AtomicInteger(0);
    schedulerRule.submitActor(test.actor);

    // when
    test.actor.run(
        () ->
            resultFuture =
                test.strategy.runWithRetry(() -> false, () -> count.incrementAndGet() == 10));
    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture).succeedsWithin(Duration.ZERO).isEqualTo(false);
  }

  @ParameterizedTest
  @ValueSource(strings = {"recoverable", "abortable"})
  void shouldAbortOnOtherException(final TestCase<?> test) {
    // given
    final RuntimeException failure = new RuntimeException("expected");
    schedulerRule.submitActor(test.actor);

    // when
    test.actor.run(
        () ->
            resultFuture =
                test.strategy.runWithRetry(
                    () -> {
                      throw failure;
                    }));
    schedulerRule.workUntilDone();

    // then
    assertThat(resultFuture)
        .failsWithin(Duration.ZERO)
        .withThrowableOfType(ExecutionException.class)
        .withCause(failure);
  }

  @ParameterizedTest
  @ValueSource(strings = {"endless", "recoverable", "abortable"})
  void shouldNotInterleaveRetry(final TestCase<?> test) {
    // given
    final AtomicReference<ActorFuture<Boolean>> firstFuture = new AtomicReference<>();
    final AtomicReference<ActorFuture<Boolean>> secondFuture = new AtomicReference<>();
    final AtomicInteger executionAttempt = new AtomicInteger(0);
    final AtomicInteger firstResult = new AtomicInteger();
    final AtomicInteger secondResult = new AtomicInteger();
    schedulerRule.submitActor(test.actor);

    // when
    final var retryCounts = 5;
    test.actor.run(
        () ->
            firstFuture.set(
                test.strategy.runWithRetry(
                    () -> {
                      firstResult.set(executionAttempt.getAndIncrement());
                      return executionAttempt.get() >= retryCounts;
                    })));
    test.actor.run(
        () ->
            secondFuture.set(
                test.strategy.runWithRetry(
                    () -> {
                      secondResult.set(executionAttempt.getAndIncrement());
                      return true;
                    })));
    schedulerRule.workUntilDone();

    // then
    assertThat(firstFuture.get()).isDone().isNotEqualTo(secondFuture.get());
    assertThat(secondFuture.get()).isDone();
    assertThat(firstResult).hasValue(retryCounts - 1);
    assertThat(secondResult).hasValue(retryCounts);
  }

  @ParameterizedTest
  @ValueSource(strings = {"endless", "recoverable", "abortable", "backoff"})
  void shouldYieldThreadOnRetry() {}

  private record TestCase<T extends RetryStrategy>(ControllableActor actor, T strategy) {

    // actually used by junit 5, see
    // https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests-argument-conversion-implicit
    @SuppressWarnings("unused")
    static TestCase<?> of(final String type) {
      return switch (type.toLowerCase()) {
        case "endless" -> TestCase.of(EndlessRetryStrategy::new);
        case "recoverable" -> TestCase.of(RecoverableRetryStrategy::new);
        case "abortable" -> TestCase.of(AbortableRetryStrategy::new);
        case "backoff" -> TestCase.of(actor -> new BackOffRetryStrategy(actor, Duration.ZERO));
        default -> throw new IllegalArgumentException(
            "Expected one of ['endless', 'recoverable', 'abortable', or 'backoff'], but got "
                + type);
      };
    }

    private static <T extends RetryStrategy> TestCase<T> of(
        final Function<ActorControl, T> provider) {
      final var actor = new ControllableActor();
      final var strategy = provider.apply(actor.getActor());
      return new TestCase<>(actor, strategy);
    }
  }

  private static final class ControllableActor extends Actor {
    public ActorControl getActor() {
      return actor;
    }
  }
}

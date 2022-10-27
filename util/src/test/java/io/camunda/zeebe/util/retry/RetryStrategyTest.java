/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RetryStrategyTest {

  @RegisterExtension
  private final ControlledActorSchedulerExtension schedulerRule =
      new ControlledActorSchedulerExtension();

  private ActorFuture<Boolean> resultFuture;

  @ParameterizedTest(name = "{0}")
  @MethodSource({
    "provideEndlessStrategy",
    "provideRecoverableStrategy",
    "provideAbortableStrategy",
    "provideBackOffStrategy"
  })
  void shouldRunUntilDone(final RetryStrategy strategy, final ControllableActor actor) {
    // given
    final var count = new AtomicInteger(0);
    schedulerRule.submitActor(actor);

    // when
    actor.run(() -> resultFuture = strategy.runWithRetry(() -> count.incrementAndGet() == 10));
    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture).succeedsWithin(Duration.ZERO).isEqualTo(true);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({
    "provideEndlessStrategy",
    "provideRecoverableStrategy",
    "provideAbortableStrategy",
    "provideBackOffStrategy"
  })
  void shouldStopWhenAbortConditionReturnsTrue(
      final RetryStrategy strategy, final ControllableActor actor) {
    // given
    final AtomicInteger count = new AtomicInteger(0);
    schedulerRule.submitActor(actor);

    // when
    actor.run(
        () ->
            resultFuture = strategy.runWithRetry(() -> false, () -> count.incrementAndGet() == 10));
    schedulerRule.workUntilDone();

    // then
    assertThat(count.get()).isEqualTo(10);
    assertThat(resultFuture).succeedsWithin(Duration.ZERO).isEqualTo(false);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({"provideRecoverableStrategy", "provideAbortableStrategy"})
  void shouldAbortOnOtherException(final RetryStrategy strategy, final ControllableActor actor) {
    // given
    final RuntimeException failure = new RuntimeException("expected");
    schedulerRule.submitActor(actor);

    // when
    actor.run(
        () ->
            resultFuture =
                strategy.runWithRetry(
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

  @ParameterizedTest(name = "{0}")
  @MethodSource({
    "provideEndlessStrategy",
    "provideRecoverableStrategy",
    "provideAbortableStrategy"
  })
  void shouldNotInterleaveRetry(final RetryStrategy strategy, final ControllableActor actor) {
    // given
    final AtomicReference<ActorFuture<Boolean>> firstFuture = new AtomicReference<>();
    final AtomicReference<ActorFuture<Boolean>> secondFuture = new AtomicReference<>();
    final AtomicInteger executionAttempt = new AtomicInteger(0);
    final AtomicInteger firstResult = new AtomicInteger();
    final AtomicInteger secondResult = new AtomicInteger();
    schedulerRule.submitActor(actor);

    // when
    final var retryCounts = 5;
    actor.run(
        () ->
            firstFuture.set(
                strategy.runWithRetry(
                    () -> {
                      firstResult.set(executionAttempt.getAndIncrement());
                      return executionAttempt.get() >= retryCounts;
                    })));
    actor.run(
        () ->
            secondFuture.set(
                strategy.runWithRetry(
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

  private static Stream<Arguments> provideRecoverableStrategy() {
    return Stream.of(TestCase.of(RecoverableRetryStrategy::new));
  }

  private static Stream<Arguments> provideAbortableStrategy() {
    return Stream.of(TestCase.of(AbortableRetryStrategy::new));
  }

  private static Stream<Arguments> provideEndlessStrategy() {
    return Stream.of(TestCase.of(EndlessRetryStrategy::new));
  }

  private static Stream<Arguments> provideBackOffStrategy() {
    return Stream.of(
        TestCase.of(
            // it's important to use a zero delay as otherwise workUntilDone will not wait for
            // all timers to be triggered
            actor -> new BackOffRetryStrategy(actor, Duration.ZERO)));
  }

  private record TestCase<T extends RetryStrategy>(ControllableActor actor, T strategy)
      implements Arguments {

    private static <T extends RetryStrategy> TestCase<T> of(
        final Function<ActorControl, T> provider) {
      final var actor = new ControllableActor();
      final var strategy = provider.apply(actor.getActor());
      return new TestCase<>(actor, strategy);
    }

    @Override
    public Object[] get() {
      return new Object[] {Named.of(strategy.getClass().getSimpleName(), strategy), actor};
    }
  }

  private static final class ControllableActor extends Actor {
    public ActorControl getActor() {
      return actor;
    }

    @Override
    public void close() {
      // do not wait for the close, as the scheduler is already closed by the time the
      // actor is closed
      closeAsync();
    }
  }
}

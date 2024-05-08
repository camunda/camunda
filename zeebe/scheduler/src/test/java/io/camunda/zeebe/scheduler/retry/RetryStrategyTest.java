/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RetryStrategyTest {

  /** Ensure we use a single thread to better control the scheduling in the tests. */
  @RegisterExtension
  private final ControlledActorSchedulerExtension schedulerRule =
      new ControlledActorSchedulerExtension(
          builder -> builder.setIoBoundActorThreadCount(0).setCpuBoundActorThreadCount(1));

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

  /**
   * Only the {@link RecoverableRetryStrategy} and {@link AbortableRetryStrategy} stop retrying when
   * an unrecoverable exception occurs; the others will always retry. We may want to extract this to
   * specific class tests?
   */
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

  /**
   * The {@link BackOffRetryStrategy} is excluded here because its usage of timers necessarily allow
   * interleaving calls. If we decide to fix it, then we should refactor the strategy and add it as
   * a test case here.
   */
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

  /**
   * The {@link BackOffRetryStrategy} is excluded here as it is already yielding implicitly by using
   * timers for scheduling.
   */
  @ParameterizedTest
  @ValueSource(strings = {"endless", "recoverable", "abortable"})
  void shouldYieldThreadOnRetry(final TestCase<?> test) {
    // given - all actors share the same thread, force interleaving of their execution to ensure the
    // retry strategy yields the thread in between retries
    final var barrier = new LinkedTransferQueue<Boolean>();
    final var future = new CompletableFuture<Void>();
    final var secondActor = new ControllableActor();
    schedulerRule.submitActor(test.actor);
    schedulerRule.submitActor(secondActor);

    // when
    test.strategy.runWithRetry(
        () -> {
          // capture the result before to ensure we're looping
          final boolean isDone = future.isDone();
          barrier.offer(true);
          return isDone; // false - will cause to retry; true will complete the retry strategy
        });
    // toggle the retry strategy to stop retrying, letting workUntilDone finish
    secondActor.run(
        () -> {
          // wait until the test actor ran at least once, guaranteeing it's currently looping
          // and retrying
          try {
            // always set a timeout - if this hits we know the other job wasn't execute before.
            barrier.poll(1, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            throw new RuntimeException(e);
          }
          future.complete(null);
        });
    // wrap workUntilDone in a timeout condition, as otherwise the test hangs forever there if the
    // actors are not yielding
    Awaitility.await(
            "workUntilDone should be finite if each actor yields the thread, used retry strategy "
                + test.strategy.getClass().getName())
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(schedulerRule::workUntilDone);

    // then
    assertThat(future)
        .as("future is completed iff second actor can run")
        .succeedsWithin(Duration.ofSeconds(2));
  }

  private record TestCase<T extends RetryStrategy>(ControllableActor actor, T strategy) {

    // used to generate test cases in conjunction with @ValueSource
    // https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests-argument-conversion-implicit
    @SuppressWarnings("unused")
    static TestCase<?> of(final String type) {
      return switch (type.toLowerCase()) {
        case "endless" -> TestCase.of(EndlessRetryStrategy::new);
        case "recoverable" -> TestCase.of(RecoverableRetryStrategy::new);
        case "abortable" -> TestCase.of(AbortableRetryStrategy::new);
        case "backoff" -> TestCase.of(actor -> new BackOffRetryStrategy(actor, Duration.ZERO));
        default ->
            throw new IllegalArgumentException(
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

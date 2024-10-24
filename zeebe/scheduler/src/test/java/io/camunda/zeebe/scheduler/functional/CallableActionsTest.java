/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.functional.CallableExecutionTest.CloseableActor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public final class CallableActionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldCompleteFutureOnException() throws Exception {
    // given
    final Exception expected = new Exception();
    final ExceptionActor actor = new ExceptionActor();
    schedulerRule.submitActor(actor);

    final Future<Void> future = actor.failWith(expected);
    schedulerRule.workUntilDone();

    // then/when
    assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCause(expected);

    assertThat(actor.invocations).hasValue(1); // should not resubmit actor job on failure
  }

  @Test
  public void shouldCompleteFutureExceptionallyWhenCalledAfterActorClosed() {
    // given
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);

    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> future = actor.doCall();
    schedulerRule.workUntilDone();

    // then
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  @Test
  public void shouldCompleteFutureExceptionallyWhenActorClosesAfterSubmission() {
    // given
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);

    actor.closeAsync();
    final ActorFuture<Void> future = actor.doCall();

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  @Test
  public void shouldCompleteFutureWhenCallableCompletes() {
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);
    final var latch = new CountDownLatch(1);
    final var future =
        actor.submitCallable(
            () -> {
              latch.await();
              return 1;
            });
    assertThat(future.isDone()).isFalse();
    latch.countDown();
    schedulerRule.workUntilDone();
    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void shouldCompleteFutureWithException() {
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);
    final var latch = new CountDownLatch(1);
    final var future =
        actor.submitCallable(
            () -> {
              latch.await();
              throw new Exception("Test exception");
            });
    assertThat(future.isDone()).isFalse();
    latch.countDown();
    schedulerRule.workUntilDone();
    assertThat(future.isCompletedExceptionally()).isTrue();
  }

  protected static class ExceptionActor extends Actor {
    protected final AtomicInteger invocations = new AtomicInteger(0);

    public Future<Void> failWith(final Exception e) {
      return actor.call(
          () -> {
            invocations.incrementAndGet();
            throw e;
          });
    }
  }

  class CloseableActor extends Actor {
    ActorFuture<Void> doCall() {
      return actor.call(() -> {});
    }
  }
}

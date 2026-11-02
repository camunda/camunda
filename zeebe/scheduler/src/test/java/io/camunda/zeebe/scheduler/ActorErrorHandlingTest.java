/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class ActorErrorHandlingTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Test
  public void shouldCatchThrowable() {
    // given
    final var throwable = new Throwable();
    final var actor = new ThrowingActor(throwable);
    scheduler.submitActor(actor);

    // when
    actor.throwError();
    scheduler.workUntilDone();

    // then
    assertThat(actor.recordedFailure).isEqualTo(throwable);
  }

  @Test
  public void shouldCatchException() {
    // given
    final var exception = new Exception();
    final var actor = new ThrowingActor(exception);
    scheduler.submitActor(actor);

    // when
    actor.throwError();
    scheduler.workUntilDone();

    // then
    assertThat(actor.recordedFailure).isEqualTo(exception);
  }

  @Test
  public void shouldCloseActorWhenFailureHandlingThrows() {
    // given
    final var actor =
        new ThrowingActor(new RuntimeException("expected")) {
          @Override
          protected void handleFailure(final Throwable failure) {
            throw new IllegalStateException("handler failed");
          }
        };
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    actor.throwError();
    scheduler.workUntilDone();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture.isDone()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void shouldCompleteCallFutureWhenFailureHandlingThrows(
      final boolean failureCleanupThrows) {
    // given
    final var callFailure = new RuntimeException("call failed");
    final var handlerFailure = new IllegalStateException("handler failed");
    final var actor =
        new ThrowingActor(callFailure) {
          @Override
          protected void handleFailure(final Throwable failure) {
            throw handlerFailure;
          }

          @Override
          public void onActorFailed() {
            if (failureCleanupThrows) {
              throw new IllegalStateException("cleanup failed");
            }
          }
        };
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    final var callFuture =
        actor.call(
            () -> {
              throw callFailure;
            });
    scheduler.workUntilDone();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(handlerFailure);
    assertThat(callFuture.isDone())
        .as("the failing call's future must complete even when failure handling throws")
        .isTrue();
    assertThat(callFuture.isCompletedExceptionally()).isTrue();
    assertThat(callFuture.getException()).isSameAs(callFailure);
  }

  @Test
  public void shouldKeepProcessingWhenCompletionCallbackThrows() {
    // given
    final var actor = new ThrowingActor(new RuntimeException("unused"));
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final var future = actor.call(() -> 1);
    future.onComplete(
        (result, error) -> {
          throw new IllegalStateException("callback failed");
        },
        Runnable::run);

    // when
    scheduler.workUntilDone();
    final var secondCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(secondCall.isDone()).isTrue();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();
    assertThat(closeFuture.isDone()).isTrue();
  }

  private static class ThrowingActor extends Actor {
    public Throwable throwable;
    public Throwable recordedFailure = null;

    public ThrowingActor(final Throwable throwable) {
      this.throwable = throwable;
    }

    public <T> io.camunda.zeebe.scheduler.future.ActorFuture<T> call(
        final java.util.concurrent.Callable<T> callable) {
      return actor.call(callable);
    }

    void throwError() {
      actor.run(() -> LangUtil.rethrowUnchecked(throwable));
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      recordedFailure = failure;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ActorErrorHandlingTest {

  @RegisterExtension
  final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @Test
  void shouldCatchThrowable() {
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
  void shouldCatchException() {
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
  void shouldNotReportCompletionCallbackFailureToActor() {
    // given
    final var actor = startActor(new RecordingActor());
    final var callbackRan = new AtomicBoolean();
    actor
        .call(() -> 1)
        .onComplete(
            (value, error) -> {
              callbackRan.set(true);
              throwingCallback(value, error);
            },
            Runnable::run);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(callbackRan).isTrue();
    assertThat(actor.failures).isEmpty();
  }

  @Test
  void shouldKeepProcessingJobsWhenCompletionCallbackThrows() {
    // given
    final var actor = startActor(new RecordingActor());
    actor.call(() -> 1).onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var nextCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(nextCall).succeedsWithin(Duration.ZERO).isEqualTo(2);
  }

  @Test
  void shouldCloseWhenCompletionCallbackThrows() {
    // given
    final var actor = startActor(new DefaultActor());
    actor.call(() -> 1).onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
  }

  @Test
  void shouldProcessJobsWhenStartCallbackThrows() {
    // given
    final var actor = new RecordingActor();
    scheduler
        .submitActor(actor)
        .onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var call = actor.call(() -> 1);
    scheduler.workUntilDone();

    // then
    assertThat(call).succeedsWithin(Duration.ZERO).isEqualTo(1);
  }

  private <T extends Actor> T startActor(final T actor) {
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    return actor;
  }

  private static void throwingCallback(final Object value, final Throwable error) {
    throw new RuntimeException("callback failure");
  }

  private static final class DefaultActor extends Actor {}

  private static final class RecordingActor extends Actor {
    private final List<Throwable> failures = new ArrayList<>();

    @Override
    protected void handleFailure(final Throwable failure) {
      failures.add(failure);
      actor.fail(failure);
    }
  }

  private static class ThrowingActor extends Actor {
    public Throwable throwable;
    public Throwable recordedFailure = null;

    public ThrowingActor(final Throwable throwable) {
      this.throwable = throwable;
    }

    private void throwError() {
      actor.run(() -> LangUtil.rethrowUnchecked(throwable));
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      recordedFailure = failure;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.agrona.LangUtil;
import org.junit.Rule;
import org.junit.Test;

public final class ActorErrorHandlingTest {

  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

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

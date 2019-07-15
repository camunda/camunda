/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class ActorRecyclingTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldPerformFullLifecycleAfterRecycle() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();
    actor.phases.clear();

    // when
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldReturnNonCompletedFutureAfterRecycle() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> actorStartedFuture = schedulerRule.submitActor(actor);
    assertThat(actorStartedFuture).isNotDone();
  }

  @Test
  public void shouldNotExecutePreviouslySubmittedJobs() {
    // given
    final Runnable action = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    actor.close();
    schedulerRule.workUntilDone();
    actor.control().run(action); // submit during close requested phase

    future.complete(null); // allow the actor to close
    schedulerRule.workUntilDone();

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    verifyZeroInteractions(action);
  }

  @Test
  @Ignore("TODO")
  public void shouldNotRecycleIfNotClosed() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    schedulerRule.submitActor(actor);
  }
}

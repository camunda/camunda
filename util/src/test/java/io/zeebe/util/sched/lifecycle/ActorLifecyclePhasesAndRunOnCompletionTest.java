/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSE_REQUESTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSING;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTING;
import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.function.BiConsumer;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ActorLifecyclePhasesAndRunOnCompletionTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldWaitForFutureInStarting() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            runOnCompletion();
          }
        };

    // when
    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));
  }

  @Test
  public void shouldContinueWhenFutureInStartingResolved() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            runOnCompletion(future);
          }
        };

    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    future.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
  }

  @Test
  public void shouldNotWaitOnFutureInCloseRequested() {
    // given
    final BiConsumer<Void, Throwable> callback = mock(BiConsumer.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            runOnCompletion(future, callback);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases)
        .isEqualTo(newArrayList(STARTING, STARTED, CLOSE_REQUESTED, CLOSING, CLOSED));
    verifyZeroInteractions(callback);
  }

  @Test
  public void shouldNotWaitOnFutureInClosed() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            super.onActorClosed();
            runOnCompletion();
          }
        };
    schedulerRule.submitActor(actor);
    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldWaitOnFutureSubmittedInCallback() {
    final CompletableActorFuture<Void> future1 = new CompletableActorFuture<>();
    final CompletableActorFuture<Void> future2 = new CompletableActorFuture<>();
    final CompletableActorFuture<Void> future3 = new CompletableActorFuture<>();

    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();

            actor.runOnCompletion(
                future1,
                (r1, t1) -> {
                  actor.runOnCompletion(
                      future2,
                      (r2, t2) -> {
                        future3.complete(null);
                      });
                });
          }
        };

    schedulerRule.submitActor(actor);

    // before completing first future
    schedulerRule.workUntilDone();
    assertThat(future3).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));

    // when completing first future
    future1.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));

    // when completing second future
    future2.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.util.function.BiConsumer;
import org.assertj.core.util.Lists;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public final class ActorLifecyclePhasesAndBlockPhaseTest {
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
            blockPhase();
          }
        };

    // when
    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isNotDone();
    assertThat(actor.phases).isEqualTo(Lists.newArrayList(ActorLifecyclePhase.STARTING));
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
            blockPhase(future);
          }
        };

    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    future.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isDone();
    assertThat(actor.phases)
        .isEqualTo(newArrayList(ActorLifecyclePhase.STARTING, ActorLifecyclePhase.STARTED));
  }

  @Test
  public void shouldWaitOnFutureInCloseRequested() {
    // given
    final BiConsumer<Void, Throwable> callback = mock(BiConsumer.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            blockPhase(future, callback);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isNotDone();
    assertThat(actor.phases)
        .isEqualTo(
            newArrayList(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED));
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldCompleteCloseRequestedWhenFutureCompleted() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    future.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(LifecycleRecordingActor.FULL_LIFECYCLE);
  }

  @Test
  public void shouldNotWaitOnFutureInClosed() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            super.onActorClosed();
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(LifecycleRecordingActor.FULL_LIFECYCLE);
  }

  @Test
  public void shouldNotWaitForNestedRunOnCompletion() {
    // given
    final CompletableActorFuture<Void> trigger = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorClosed();
            actor.runOnCompletionBlockingCurrentPhase(
                trigger,
                (r1, e1) -> {
                  actor.runOnCompletion(new CompletableActorFuture<>(), (r2, e2) -> {});
                });
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // when
    trigger.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
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

            actor.runOnCompletionBlockingCurrentPhase(
                future1,
                (r1, t1) -> {
                  actor.runOnCompletionBlockingCurrentPhase(
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
    assertThat(actor.phases).isEqualTo(Lists.newArrayList(ActorLifecyclePhase.STARTING));

    // when completing first future
    future1.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isNotDone();
    assertThat(actor.phases).isEqualTo(Lists.newArrayList(ActorLifecyclePhase.STARTING));

    // when completing second future
    future2.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isDone();
    assertThat(actor.phases)
        .isEqualTo(newArrayList(ActorLifecyclePhase.STARTING, ActorLifecyclePhase.STARTED));
  }
}

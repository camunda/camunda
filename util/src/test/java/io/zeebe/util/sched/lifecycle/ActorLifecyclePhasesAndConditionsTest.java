/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecyclePhasesAndConditionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteConditionalJobsInStartingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    final ActorCondition condition = conditionFuture.join();

    // when
    condition.signal();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldExecuteConditionalJobsInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    conditionFuture.join().signal();

    // then
    verify(runnable, times(0)).run();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldExecuteConditionalJobsInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    final ActorCondition condition = conditionFuture.join();

    // when
    condition.signal();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldNotExecuteConditionalJobsInCloseRequestedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final ActorCondition condition = conditionFuture.join();
    condition.signal();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldNotExecuteConditionalJobsInClosingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final ActorCondition condition = conditionFuture.join();
    condition.signal();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldNotExecuteConditionalJobsInClosedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<ActorCondition> conditionFuture = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            conditionFuture.complete(actor.onCondition("condition", runnable));
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final ActorCondition condition = conditionFuture.join();
    condition.signal();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }
}

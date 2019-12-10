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

import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecyclePhasesAndSubmitTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteSubmittedJobsInStartingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.submit(runnable);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldExecuteSubmittedJobsInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.submit(runnable);
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.workUntilDone();
    verify(runnable, times(0)).run();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldExecuteSubmittedJobsInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            actor.submit(runnable);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldNotExecuteSubmittedJobsInCloseRequestedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            actor.submit(runnable);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldNotExecuteSubmittedJobsInClosingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            actor.submit(runnable);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }

  @Test
  public void shouldNotExecuteSubmittedJobsInClosedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            actor.submit(runnable);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }
}

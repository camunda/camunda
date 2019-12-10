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
import java.util.concurrent.Callable;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ActorLifecyclePhasesAndCallablesTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteCallablesInStartingPhase() throws Exception {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(0)).call();
  }

  @Test
  public void shouldExecuteCallablesSubmittedInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();
    verify(callable, times(0)).call();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(callable, times(1)).call();
  }

  @Test
  public void shouldExecuteCallablesInStartedPhase() throws Exception {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(1)).call();
  }

  @Test
  public void shouldNotExecuteCallablesInCloseRequestedPhase() throws Exception {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(0)).call();
  }

  @Test
  public void shouldNotExecuteCallablesInClosingPhase() throws Exception {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(0)).call();
  }

  @Test
  public void shouldNotExecuteCallablesInClosedPhase() throws Exception {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            blockPhase();
          }
        };

    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(0)).call();
  }
}

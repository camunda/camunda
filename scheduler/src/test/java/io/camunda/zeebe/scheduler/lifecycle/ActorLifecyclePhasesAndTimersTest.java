/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.lifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public final class ActorLifecyclePhasesAndTimersTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteTimersInStartingPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.schedule(Duration.ofMillis(10), action);
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

  @Test
  public void shouldExecuteTimersSubmittedInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.schedule(Duration.ofMillis(10), action);
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();
    verify(action, times(0)).run();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(action, times(1)).run();
  }

  @Test
  public void shouldExecuteTimersInStartedPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(1)).run();
  }

  @Test
  public void shouldNotExecuteTimersInCloseRequestedPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase();
            actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }

  @Test
  public void shouldNotExecuteTimersInClosingPhase() {
    // given
    schedulerRule.getClock().setCurrentTime(100);
    final Runnable action = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            blockPhase();
            actor.schedule(Duration.ofMillis(10), action);
          }
        };
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    schedulerRule.getClock().addTime(Duration.ofMillis(10));
    schedulerRule.workUntilDone();

    // then
    verify(action, times(0)).run();
  }
}

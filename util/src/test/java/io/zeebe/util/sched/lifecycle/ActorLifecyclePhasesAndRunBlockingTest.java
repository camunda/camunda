/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ActorLifecyclePhasesAndRunBlockingTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteCompletionConsumerInStartingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.runBlocking(runnable, completionConsumer);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);
  }

  @Test
  public void shouldExecuteCompletionConsumerInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.runBlocking(runnable, completionConsumer);
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);

    // when
    schedulerRule.workUntilDone();
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(completionConsumer, times(1)).accept(eq(null));
  }

  @Test
  public void shouldExecuteCompletionConsumerInStartedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            actor.runBlocking(runnable, completionConsumer);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verify(completionConsumer, times(1)).accept(eq(null));
  }

  @Test
  public void shouldNotExecuteCompletionConsumerInCloseRequestedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            actor.runBlocking(runnable, completionConsumer);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);
  }

  @Test
  public void shouldNotExecuteCompletionConsumerInClosingPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            actor.runBlocking(runnable, completionConsumer);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);
  }

  @Test
  public void shouldNotExecuteCompletionConsumerInClosedPhase() throws Exception {
    // given
    final Runnable runnable = mock(Runnable.class);
    final Consumer<Throwable> completionConsumer = mock(Consumer.class);

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            actor.runBlocking(runnable, completionConsumer);
            blockPhase();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);
  }
}

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

import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecyclePhasesAndConsumeTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldNotExecuteConsumersInStartingPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.consume(queue, consumerRunnable(queue, runnable));
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
  public void shouldExecuteConsumersInStartingPhaseWhenInStartedPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            actor.consume(queue, consumerRunnable(queue, runnable));
            blockPhase(future);
          }
        };

    schedulerRule.submitActor(actor);

    // when then
    schedulerRule.workUntilDone();
    verify(runnable, times(0)).run();

    // when then
    future.complete(null);
    schedulerRule.workUntilDone();
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldExecuteConsumersInStartedPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            actor.consume(queue, consumerRunnable(queue, runnable));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
  }

  @Test
  public void shouldNotExecuteConsumersInCloseRequestedPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            actor.consume(queue, consumerRunnable(queue, runnable));
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
  public void shouldNotExecuteConsumersInClosingPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            actor.consume(queue, consumerRunnable(queue, runnable));
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
  public void shouldNotExecuteConsumersInClosedPhase() throws Exception {
    // given
    final ConcurrentQueueChannel<Object> queue =
        new ConcurrentQueueChannel<>(new ConcurrentLinkedQueue<>());
    queue.add(new Object());

    final Runnable runnable = mock(Runnable.class);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            actor.consume(queue, consumerRunnable(queue, runnable));
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

  private Runnable consumerRunnable(Queue<Object> queue, Runnable wrappedRunnable) {
    return () -> {
      queue.poll();
      wrappedRunnable.run();
    };
  }
}

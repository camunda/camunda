/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.functional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class BlockingActionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void testInvokeBlockingAction() throws InterruptedException {
    // given
    final Runnable blockingAction = mock(Runnable.class);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runBlocking(blockingAction);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(blockingAction, times(1)).run();
  }

  @Test
  public void testInvokeCallbackAfterBlockingAction() throws InterruptedException {
    // given
    final Runnable blockingAction = mock(Runnable.class);
    final Consumer<Throwable> whenDone = mock(Consumer.class);
    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runBlocking(blockingAction, whenDone);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(blockingAction, whenDone);
    inOrder.verify(blockingAction, times(1)).run();
    inOrder.verify(whenDone, times(1)).accept(eq(null));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testPassExceptionToCallback() throws InterruptedException {
    // given
    final Runnable blockingAction = mock(Runnable.class);
    final RuntimeException exception = new RuntimeException();
    doThrow(exception).when(blockingAction).run();

    final Consumer<Throwable> whenDone = mock(Consumer.class);

    final Actor actor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            actor.runBlocking(blockingAction, whenDone);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(blockingAction, whenDone);
    inOrder.verify(blockingAction, times(1)).run();
    inOrder.verify(whenDone, times(1)).accept(eq(exception));
    inOrder.verifyNoMoreInteractions();
  }
}

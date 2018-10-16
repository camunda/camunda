/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

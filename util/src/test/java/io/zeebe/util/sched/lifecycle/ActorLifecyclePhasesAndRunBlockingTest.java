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
    actor.close();
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
    actor.close();
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
    actor.close();
    schedulerRule.workUntilDone();
    schedulerRule.awaitBlockingTasksCompleted(1);
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(1)).run();
    verifyZeroInteractions(completionConsumer);
  }
}

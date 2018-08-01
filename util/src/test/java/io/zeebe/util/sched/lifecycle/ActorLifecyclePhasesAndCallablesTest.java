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
    actor.close();
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
    actor.close();
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
    actor.close();
    schedulerRule.workUntilDone();

    // when
    final Callable<Void> callable = mock(Callable.class);
    actor.control().call(callable);
    schedulerRule.workUntilDone();

    // then
    verify(callable, times(0)).call();
  }
}

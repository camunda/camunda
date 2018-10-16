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
    actor.close();
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
    actor.close();
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
    actor.close();
    schedulerRule.workUntilDone();

    // then
    verify(runnable, times(0)).run();
  }
}

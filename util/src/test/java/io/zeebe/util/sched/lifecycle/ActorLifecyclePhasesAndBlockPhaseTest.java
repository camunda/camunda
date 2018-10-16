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

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSE_REQUESTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTING;
import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.function.BiConsumer;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ActorLifecyclePhasesAndBlockPhaseTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldWaitForFutureInStarting() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            blockPhase();
          }
        };

    // when
    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));
  }

  @Test
  public void shouldContinueWhenFutureInStartingResolved() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            blockPhase(future);
          }
        };

    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    future.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(startFuture).isDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
  }

  @Test
  public void shouldWaitOnFutureInCloseRequested() {
    // given
    final BiConsumer<Void, Throwable> callback = mock(BiConsumer.class);

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            blockPhase(callback);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.close();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED, CLOSE_REQUESTED));
    verifyZeroInteractions(callback);
  }

  @Test
  public void shouldCompleteCloseRequestedWhenFutureCompleted() {
    // given
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.close();
    schedulerRule.workUntilDone();

    // when
    future.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldNotWaitOnFutureInClosed() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosed() {
            super.onActorClosed();
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);
    final ActorFuture<Void> closeFuture = actor.close();

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldNotWaitForNestedRunOnCompletion() {
    // given
    final CompletableActorFuture<Void> trigger = new CompletableActorFuture<>();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorClosed();
            actor.runOnCompletionBlockingCurrentPhase(
                trigger,
                (r1, e1) -> {
                  actor.runOnCompletion(new CompletableActorFuture<>(), (r2, e2) -> {});
                });
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    final ActorFuture<Void> closeFuture = actor.close();

    // when
    trigger.complete(null);
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
  }

  @Test
  public void shouldWaitOnFutureSubmittedInCallback() {
    final CompletableActorFuture<Void> future1 = new CompletableActorFuture<>();
    final CompletableActorFuture<Void> future2 = new CompletableActorFuture<>();
    final CompletableActorFuture<Void> future3 = new CompletableActorFuture<>();

    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();

            actor.runOnCompletionBlockingCurrentPhase(
                future1,
                (r1, t1) -> {
                  actor.runOnCompletionBlockingCurrentPhase(
                      future2,
                      (r2, t2) -> {
                        future3.complete(null);
                      });
                });
          }
        };

    schedulerRule.submitActor(actor);

    // before completing first future
    schedulerRule.workUntilDone();
    assertThat(future3).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));

    // when completing first future
    future1.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isNotDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));

    // when completing second future
    future2.complete(null);
    schedulerRule.workUntilDone();
    assertThat(future3).isDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
  }
}

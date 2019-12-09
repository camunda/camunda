/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class ActorRecyclingTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldPerformFullLifecycleAfterRecycle() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();
    actor.phases.clear();

    // when
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldReturnNonCompletedFutureAfterRecycle() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    actor.closeAsync();
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> actorStartedFuture = schedulerRule.submitActor(actor);
    assertThat(actorStartedFuture).isNotDone();
  }

  @Test
  public void shouldNotAllowMultipleActorSubmit() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase();
          }
        };
    schedulerRule.submitActor(actor);

    // when
    final ThrowingCallable throwsOnExecute = () -> schedulerRule.submitActor(actor);

    // then expect
    assertThatThrownBy(throwsOnExecute).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldAllowMultipleActorSubmitAfterClosed() throws Exception {
    // given
    final AtomicLong startedCount = new AtomicLong(0);
    final CountDownLatch latch = new CountDownLatch(1);
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            startedCount.incrementAndGet();
          }

          @Override
          public void onActorClosed() {
            latch.countDown();
          }
        };
    schedulerRule.submitActor(actor);
    actor.closeAsync();

    // when
    schedulerRule.workUntilDone();
    latch.await();
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startedCount).hasValue(2);
  }

  @Test
  public void shouldNotExecutePreviouslySubmittedJobs() throws Exception {
    // given
    final Runnable action = mock(Runnable.class);
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            blockPhase(future);
          }
        };
    schedulerRule.submitActor(actor);

    // when closed
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();
    actor.control().run(action); // submit during close requested phase

    future.complete(null); // allow the actor to close
    schedulerRule.workUntilDone();

    // then
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    // expect
    verifyZeroInteractions(action);
  }

  @Test
  @Ignore("TODO")
  public void shouldNotRecycleIfNotClosed() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    schedulerRule.submitActor(actor);
  }
}

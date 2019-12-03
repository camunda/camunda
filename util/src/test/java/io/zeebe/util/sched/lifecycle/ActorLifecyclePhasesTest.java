/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.lifecycle;

import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSE_REQUESTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.CLOSING;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTED;
import static io.zeebe.util.sched.ActorTask.ActorLifecyclePhase.STARTING;
import static io.zeebe.util.sched.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;

import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public class ActorLifecyclePhasesTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldStartActor() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();

    // when
    final ActorFuture<Void> startedFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startedFuture).isDone();
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED));
  }

  @Test
  public void shouldCloseActor() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldDoFullLifecycleIfClosedConcurrently() {
    // given
    final LifecycleRecordingActor actor = new LifecycleRecordingActor();
    schedulerRule.submitActor(actor);

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync(); // request close before doing work
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldCloseOnFailureWhileActorStarting() {
    // given
    final RuntimeException failure = new RuntimeException("foo");

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();

            throw failure;
          }
        };

    // when
    final ActorFuture<Void> startedFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startedFuture.isCompletedExceptionally()).isTrue();
    assertThat(startedFuture.getException()).isEqualTo(failure);
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));
  }

  @Test
  public void shouldCloseOnFailureWhileActorClosing() {
    // given
    final RuntimeException failure = new RuntimeException("foo");

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            super.onActorClosing();

            throw failure;
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isEqualTo(failure);
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED, CLOSE_REQUESTED, CLOSING));
  }

  @Test
  public void shouldPropagateFailureWhileActorStartingAndRun() {
    // given
    final RuntimeException failure = new RuntimeException("foo");

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();

            this.actor.run(
                () -> {
                  throw failure;
                });
          }
        };

    // when
    final ActorFuture<Void> startedFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startedFuture.isCompletedExceptionally()).isTrue();
    assertThat(startedFuture.getException()).isEqualTo(failure);
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING));
  }

  @Test
  public void shouldPropagateFailureWhileActorClosingAndRun() {
    // given
    final RuntimeException failure = new RuntimeException("foo");

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            super.onActorClosing();

            this.actor.run(
                () -> {
                  throw failure;
                });
          }
        };

    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isEqualTo(failure);
    assertThat(actor.phases).isEqualTo(newArrayList(STARTING, STARTED, CLOSE_REQUESTED, CLOSING));
  }

  @Test
  public void shouldDiscardJobsOnFailureWhileActorStarting() {
    // given
    final RuntimeException failure = new RuntimeException("foo");
    final AtomicBoolean isInvoked = new AtomicBoolean();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();

            this.actor.run(() -> isInvoked.set(true));

            throw failure;
          }
        };

    // when
    final ActorFuture<Void> startedFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(startedFuture.isCompletedExceptionally()).isTrue();
    assertThat(isInvoked).isFalse();
  }

  @Test
  public void shouldNotCloseOnFailureWhileActorStarted() {
    // given
    final AtomicInteger invocations = new AtomicInteger();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();

            this.actor.runUntilDone(
                () -> {
                  final int inv = invocations.getAndIncrement();

                  if (inv == 0) {
                    throw new RuntimeException("foo");
                  } else if (inv == 10) {
                    actor.done();
                  }
                });
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    TestUtil.waitUntil(() -> invocations.get() >= 10);

    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }
}

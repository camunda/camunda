/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.lifecycle;

import static io.camunda.zeebe.scheduler.lifecycle.LifecycleRecordingActor.FULL_LIFECYCLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerRule;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.util.Lists;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public final class ActorLifecyclePhasesTest {
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
    assertThat(actor.phases)
        .isEqualTo(newArrayList(ActorLifecyclePhase.STARTING, ActorLifecyclePhase.STARTED));
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
    assertThat(actor.phases).isEqualTo(Lists.newArrayList(ActorLifecyclePhase.STARTING));
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
    assertThat(actor.phases)
        .isEqualTo(
            newArrayList(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED,
                ActorLifecyclePhase.CLOSING));
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

            actor.run(
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
    assertThat(actor.phases).isEqualTo(Lists.newArrayList(ActorLifecyclePhase.STARTING));
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

            actor.run(
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
    assertThat(actor.phases)
        .isEqualTo(
            newArrayList(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED,
                ActorLifecyclePhase.CLOSING));
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

            actor.run(() -> isInvoked.set(true));

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
            actor.submit(this::triggerFailure);
          }

          @Override
          protected void handleFailure(final Throwable failure) {
            final int inv = invocations.getAndIncrement();
            if (inv < 10) {
              actor.submit(this::triggerFailure);
            }
          }

          private void triggerFailure() {
            throw new RuntimeException("fail");
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    Awaitility.await().until(() -> invocations.get() >= 10);

    actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases).isEqualTo(FULL_LIFECYCLE);
  }

  @Test
  public void shouldActorSpecificHandleException() {
    // given
    final AtomicInteger invocations = new AtomicInteger();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            actor.run(
                () -> {
                  throw new RuntimeException("foo");
                });
          }

          @Override
          public void handleFailure(final Throwable failure) {
            invocations.incrementAndGet();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations.get()).isEqualTo(1);
  }

  @Test
  public void shouldHandleFailureWhenExceptionOnFutureContinuation() {
    // given
    final AtomicInteger invocations = new AtomicInteger();
    final ActorFuture<Void> future = new CompletableActorFuture();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            actor.runOnCompletion(
                future,
                (v, t) -> {
                  throw new RuntimeException("foo");
                });
            actor.run(() -> future.complete(null));
          }

          @Override
          public void handleFailure(final Throwable failure) {
            invocations.incrementAndGet();
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations.get()).isEqualTo(1);
  }

  @Test
  public void shouldNotExecuteNextJobsOnFail() {
    // given
    final AtomicInteger invocations = new AtomicInteger();

    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            actor.submit(() -> actor.fail(new RuntimeException("foo")));
            actor.submit(invocations::incrementAndGet);
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();

    // then
    assertThat(invocations.get()).isEqualTo(0);
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.FAILED));
  }

  @Test
  public void shouldCompleteCloseFutureWhenFailingInStarted() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarted() {
            super.onActorStarted();
            actor.fail(new RuntimeException("foo"));
          }
        };

    // when - explicitly do not work after close as it should be completed already
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // then
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.FAILED));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteCloseFutureOnExceptionOnClosing() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            super.onActorClosing();
            throw new RuntimeException("foo");
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED,
                ActorLifecyclePhase.CLOSING));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteCloseFutureWhenFailingOnClosing() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorClosing() {
            super.onActorClosing();
            actor.fail(new RuntimeException("foo"));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED,
                ActorLifecyclePhase.CLOSING,
                ActorLifecyclePhase.FAILED));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteFuturesWhenFailingOnStarting() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            actor.fail(new RuntimeException("foo"));
          }
        };

    // when - explicitly do not work after close as it should be completed already
    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // then
    assertThat(actor.phases)
        .isEqualTo(List.of(ActorLifecyclePhase.STARTING, ActorLifecyclePhase.FAILED));
    assertThat(startFuture).failsWithin(Duration.ofSeconds(1));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteFuturesOnExceptionOnStarting() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorStarting() {
            super.onActorStarting();
            throw new RuntimeException("hello");
          }
        };

    // when - explicitly do not work after close as it should be completed already
    final ActorFuture<Void> startFuture = schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();

    // then
    assertThat(actor.phases).isEqualTo(List.of(ActorLifecyclePhase.STARTING));
    assertThat(startFuture).failsWithin(Duration.ofSeconds(1));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteCloseFutureWhenFailingOnCloseRequested() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            super.onActorCloseRequested();
            actor.fail(new RuntimeException("hello"));
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED,
                ActorLifecyclePhase.FAILED));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }

  @Test
  public void shouldCompleteCloseFutureWhenExceptionOnCloseRequested() {
    // given
    final LifecycleRecordingActor actor =
        new LifecycleRecordingActor() {
          @Override
          public void onActorCloseRequested() {
            super.onActorCloseRequested();
            throw new RuntimeException("hello");
          }
        };

    // when
    schedulerRule.submitActor(actor);
    schedulerRule.workUntilDone();
    final ActorFuture<Void> closeFuture = actor.closeAsync();
    schedulerRule.workUntilDone();

    // then
    assertThat(actor.phases)
        .isEqualTo(
            List.of(
                ActorLifecyclePhase.STARTING,
                ActorLifecyclePhase.STARTED,
                ActorLifecyclePhase.CLOSE_REQUESTED));
    assertThat(closeFuture).failsWithin(Duration.ofSeconds(1));
  }
}

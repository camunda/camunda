/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.ActorTask.TaskSchedulingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class ActorErrorHandlingTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @Test
  public void shouldCatchThrowable() {
    // given
    final var throwable = new Throwable();
    final var actor = new ThrowingActor(throwable);
    scheduler.submitActor(actor);

    // when
    actor.throwError();
    scheduler.workUntilDone();

    // then
    assertThat(actor.recordedFailure).isEqualTo(throwable);
  }

  @Test
  public void shouldCatchException() {
    // given
    final var exception = new Exception();
    final var actor = new ThrowingActor(exception);
    scheduler.submitActor(actor);

    // when
    actor.throwError();
    scheduler.workUntilDone();

    // then
    assertThat(actor.recordedFailure).isEqualTo(exception);
  }

  @Test
  public void shouldCloseActorWhenFailureHandlingThrows() {
    // given
    final var actor =
        new ThrowingActor(new RuntimeException("expected")) {
          @Override
          protected void handleFailure(final Throwable failure) {
            throw new IllegalStateException("handler failed");
          }
        };
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    actor.throwError();
    scheduler.workUntilDone();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture.isDone()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void shouldCompleteCallFutureWhenFailureHandlingThrows(
      final boolean failureCleanupThrows) {
    // given
    final var callFailure = new RuntimeException("call failed");
    final var handlerFailure = new IllegalStateException("handler failed");
    final var actor =
        new ThrowingActor(callFailure) {
          @Override
          protected void handleFailure(final Throwable failure) {
            throw handlerFailure;
          }

          @Override
          public void onActorFailed() {
            if (failureCleanupThrows) {
              throw new IllegalStateException("cleanup failed");
            }
          }
        };
    scheduler.submitActor(actor);
    scheduler.workUntilDone();

    // when
    final var callFuture =
        actor.call(
            () -> {
              throw callFailure;
            });
    scheduler.workUntilDone();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(handlerFailure);
    assertThat(callFuture.isDone())
        .as("the failing call's future must complete even when failure handling throws")
        .isTrue();
    assertThat(callFuture.isCompletedExceptionally()).isTrue();
    assertThat(callFuture.getException()).isSameAs(callFailure);
  }

  @Test
  public void shouldKeepProcessingWhenCompletionCallbackThrows() {
    // given
    final var actor = new ThrowingActor(new RuntimeException("unused"));
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    final var future = actor.call(() -> 1);
    future.onComplete(
        (result, error) -> {
          throw new IllegalStateException("callback failed");
        },
        Runnable::run);

    // when
    scheduler.workUntilDone();
    final var secondCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(secondCall.isDone()).isTrue();
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();
    assertThat(closeFuture.isDone()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRecoverInActorContextWhenSubscriptionPollingThrows(
      final boolean failureCallbackThrows) {
    // given
    final var pollFailure = new IllegalStateException("poll failed");
    final var subscription = mock(ActorSubscription.class);
    when(subscription.triggersInPhase(ActorLifecyclePhase.STARTED)).thenReturn(true);
    when(subscription.poll()).thenThrow(pollFailure);
    final var observedPhase = new AtomicReference<ActorLifecyclePhase>();
    final var failureCallbacks = new AtomicInteger();
    final var closeCallbacks = new AtomicInteger();
    final var task = new AtomicReference<ActorTask>();
    final var failingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            task.set(ActorThread.current().getCurrentTask());
            task.get().addSubscription(subscription);
            task.get()
                .closeFuture
                .onComplete(
                    (result, error) -> {
                      closeCallbacks.incrementAndGet();
                      if (failureCallbackThrows) {
                        throw new IllegalStateException("close callback failed");
                      }
                    },
                    Runnable::run);
          }

          @Override
          public void onActorFailed() {
            observedPhase.set(actor.getLifecyclePhase());
            failureCallbacks.incrementAndGet();
            if (failureCallbackThrows) {
              throw new IllegalStateException("failure callback failed");
            }
          }
        };
    final var healthyActor = new Actor() {};
    scheduler.submitActor(healthyActor);
    scheduler.workUntilDone();

    // when
    scheduler.submitActor(failingActor);
    scheduler.workUntilDone();
    final var closeFuture = failingActor.closeAsync();
    final var rejectedCall = failingActor.call(() -> 1);
    final var healthyCall = healthyActor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(observedPhase).hasValue(ActorLifecyclePhase.FAILED);
    assertThat(failureCallbacks).hasValue(1);
    assertThat(closeCallbacks).hasValue(1);
    assertThat(task.get().getState()).isEqualTo(TaskSchedulingState.NOT_SCHEDULED);
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(pollFailure);
    assertThat(rejectedCall.isCompletedExceptionally()).isTrue();
    assertThat(healthyCall.isDone()).isTrue();
    assertThat(healthyCall.join()).isEqualTo(2);
    verify(subscription, times(1)).cancel();
  }

  @Test
  void shouldFailInterruptedAndQueuedCallsDespiteCleanupFailures() {
    // given
    final var completionFailure = new IllegalStateException("subscription completion failed");
    final var subscription = mock(ActorSubscription.class);
    when(subscription.triggersInPhase(ActorLifecyclePhase.STARTED)).thenReturn(true);
    when(subscription.poll()).thenReturn(true);
    when(subscription.isRecurring()).thenReturn(true);
    doThrow(completionFailure).when(subscription).onJobCompleted();
    doThrow(new IllegalArgumentException("cancel failed")).when(subscription).cancel();
    final var calls = new ArrayList<ActorFuture<?>>();
    final var callbackCount = new AtomicInteger();
    final var failureCallbacks = new AtomicInteger();
    final var failingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            final var task = ActorThread.current().getCurrentTask();
            final var subscriptionJob = new ActorJob();
            subscriptionJob.onJobAddedToTask(task);
            subscriptionJob.setRunnable(
                () -> {
                  for (int i = 0; i < 2; i++) {
                    final var job = new ActorJob();
                    job.onJobAddedToTask(task);
                    final ActorFuture<?> future = job.setCallable(() -> 1);
                    future.onComplete(
                        (result, error) -> {
                          callbackCount.incrementAndGet();
                          throw new IllegalArgumentException("completion callback failed");
                        },
                        Runnable::run);
                    calls.add(future);
                    task.insertJob(job);
                  }
                  final var submittedJob = new ActorJob();
                  submittedJob.onJobAddedToTask(task);
                  calls.add(submittedJob.setCallable(() -> 2));
                  task.submit(submittedJob);
                });
            subscriptionJob.setSubscription(subscription);
            when(subscription.getJob()).thenReturn(subscriptionJob);
          }

          @Override
          public void onActorFailed() {
            failureCallbacks.incrementAndGet();
            throw new IllegalArgumentException("failure callback failed");
          }
        };

    // when
    scheduler.submitActor(failingActor);
    scheduler.workUntilDone();
    final var closeFuture = failingActor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(calls).hasSize(3).allMatch(ActorFuture::isCompletedExceptionally);
    assertThat(callbackCount).hasValue(2);
    assertThat(failureCallbacks).hasValue(1);
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(completionFailure);
    verify(subscription, times(1)).onJobCompleted();
    verify(subscription, times(1)).cancel();
  }

  @Test
  void shouldFinishClosingWhenSubscriptionCancellationThrows() {
    // given
    final var cancellationFailure = new IllegalArgumentException("cancel failed");
    final var metricsFailure = new IllegalArgumentException("metrics close failed");
    final var metrics = mock(ActorMetrics.ActorMetricsScoped.class);
    doThrow(metricsFailure).when(metrics).close();
    final var first = mock(ActorSubscription.class);
    final var second = mock(ActorSubscription.class);
    doThrow(cancellationFailure).when(first).cancel();
    final var failureCallbacks = new AtomicInteger();
    final var closingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            final var task = ActorThread.current().getCurrentTask();
            task.setActorMetrics(metrics);
            task.addSubscription(first);
            task.addSubscription(second);
          }

          @Override
          public void onActorFailed() {
            failureCallbacks.incrementAndGet();
          }
        };
    scheduler.submitActor(closingActor);
    scheduler.workUntilDone();

    // when
    final var closeFuture = closingActor.closeAsync();
    final var queuedCall = closingActor.call(() -> 1);
    scheduler.workUntilDone();
    final var laterCall = closingActor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(cancellationFailure);
    assertThat(cancellationFailure.getSuppressed()).contains(metricsFailure);
    assertThat(queuedCall.isCompletedExceptionally()).isTrue();
    assertThat(laterCall.isCompletedExceptionally()).isTrue();
    assertThat(failureCallbacks).hasValue(0);
    verify(first, times(1)).cancel();
    verify(second, times(1)).cancel();
    verify(metrics, times(1)).close();
  }

  @Test
  void shouldFinishRecoveryWhenStartupCompletionCallbackThrows() {
    // given
    final var startupFailure = new IllegalArgumentException("startup failed");
    final var callbackFailure = new IllegalStateException("startup completion callback failed");
    final var startingActor =
        new Actor() {
          @Override
          protected void onActorStarting() {
            throw startupFailure;
          }
        };
    final var startingFuture = scheduler.submitActor(startingActor);
    startingFuture.onComplete(
        (result, error) -> {
          throw callbackFailure;
        },
        Runnable::run);

    // when
    scheduler.workUntilDone();
    final var closeFuture = startingActor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(startingFuture.isCompletedExceptionally()).isTrue();
    assertThat(startingFuture.getException()).isSameAs(startupFailure);
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(callbackFailure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRecoverWhenPollingThrowsAfterPublishingWaiting(final boolean wakeupFirst) {
    // given
    final var pollFailure = new IllegalArgumentException("poll after WAITING failed");
    final var subscription = mock(ActorSubscription.class);
    when(subscription.triggersInPhase(ActorLifecyclePhase.STARTED)).thenReturn(true);
    final var task = new AtomicReference<ActorTask>();
    final var pollCount = new AtomicInteger();
    final var failureCallbacks = new AtomicInteger();
    when(subscription.poll())
        .thenAnswer(
            invocation -> {
              if (pollCount.incrementAndGet() == 2) {
                if (wakeupFirst) {
                  task.get().tryWakeup();
                }
                throw pollFailure;
              }
              return false;
            });
    final var waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            task.set(ActorThread.current().getCurrentTask());
            task.get().addSubscription(subscription);
          }

          @Override
          public void onActorFailed() {
            assertThat(actor.getLifecyclePhase()).isEqualTo(ActorLifecyclePhase.FAILED);
            failureCallbacks.incrementAndGet();
          }
        };

    // when
    scheduler.submitActor(waitingActor);
    scheduler.workUntilDone();
    final var closeFuture = waitingActor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(failureCallbacks).hasValue(1);
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(pollFailure);
    assertThat(task.get().getState()).isEqualTo(TaskSchedulingState.NOT_SCHEDULED);
  }

  @Test
  void shouldDeferRecoveryUntilConcurrentExecutionFinishes() throws Exception {
    // given
    final var pollFailure = new IllegalArgumentException("poll after WAITING failed");
    final var waitingPoll = new CompletableFuture<Void>();
    final var releasePoll = new CompletableFuture<Void>();
    final var executingCall = new CompletableFuture<Void>();
    final var releaseCall = new CompletableFuture<Void>();
    final var inCall = new AtomicBoolean();
    final var recoveredConcurrently = new AtomicBoolean();
    final var failureCallbacks = new AtomicInteger();
    final var subscription = mock(ActorSubscription.class);
    when(subscription.triggersInPhase(ActorLifecyclePhase.STARTED)).thenReturn(true);
    final var pollCount = new AtomicInteger();
    when(subscription.poll())
        .thenAnswer(
            invocation -> {
              if (pollCount.incrementAndGet() == 2) {
                waitingPoll.complete(null);
                releasePoll.get(10, TimeUnit.SECONDS);
                throw pollFailure;
              }
              return false;
            });
    final var task = new AtomicReference<ActorTask>();
    final var waitingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            task.set(ActorThread.current().getCurrentTask());
            task.get().addSubscription(subscription);
          }

          @Override
          public void onActorFailed() {
            recoveredConcurrently.set(inCall.get());
            assertThat(actor.getLifecyclePhase()).isEqualTo(ActorLifecyclePhase.FAILED);
            failureCallbacks.incrementAndGet();
          }
        };
    try (final var concurrentScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(2)
            .setIoBoundActorThreadCount(0)
            .build()) {
      concurrentScheduler.start();
      try {
        concurrentScheduler.submitActor(waitingActor);
        assertThat(waitingPoll).succeedsWithin(Duration.ofSeconds(10));

        // when
        final var call =
            waitingActor.call(
                () -> {
                  inCall.set(true);
                  executingCall.complete(null);
                  releaseCall.get(10, TimeUnit.SECONDS);
                  inCall.set(false);
                  return 1;
                });
        assertThat(executingCall).succeedsWithin(Duration.ofSeconds(10));
        releasePoll.complete(null);
        // The second worker is blocked in the call, so only the first can run this probe.
        final var probe = new Actor() {};
        assertThat(concurrentScheduler.submitActor(probe)).succeedsWithin(Duration.ofSeconds(10));
        releaseCall.complete(null);

        // then
        assertThat(call).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(1);
        assertThat(task.get().closeFuture)
            .failsWithin(Duration.ofSeconds(10))
            .withThrowableThat()
            .havingCause()
            .isSameAs(pollFailure);
        assertThat(recoveredConcurrently).isFalse();
        assertThat(failureCallbacks).hasValue(1);
        assertThat(probe.closeAsync()).succeedsWithin(Duration.ofSeconds(10));
      } finally {
        releasePoll.complete(null);
        releaseCall.complete(null);
      }
    }
  }

  @Test
  void shouldNotRepeatFailureCallbackWhenCleanupRequiresRecovery() {
    // given
    final var actorFailure = new IllegalArgumentException("actor failed");
    final var cancellationFailure = new IllegalStateException("cancel failed");
    final var subscription = mock(ActorSubscription.class);
    doThrow(cancellationFailure).when(subscription).cancel();
    final var failureCallbacks = new AtomicInteger();
    final var task = new AtomicReference<ActorTask>();
    final var failingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            task.set(ActorThread.current().getCurrentTask());
            task.get().addSubscription(subscription);
            actor.fail(actorFailure);
          }

          @Override
          public void onActorFailed() {
            assertThat(actor.getLifecyclePhase()).isEqualTo(ActorLifecyclePhase.FAILED);
            failureCallbacks.incrementAndGet();
          }
        };

    // when
    scheduler.submitActor(failingActor);
    scheduler.workUntilDone();
    final var rejectedCall = failingActor.call(() -> 1);
    scheduler.workUntilDone();

    // then
    assertThat(failureCallbacks).hasValue(1);
    assertThat(task.get().closeFuture.getException()).isSameAs(actorFailure);
    assertThat(task.get().getState()).isEqualTo(TaskSchedulingState.NOT_SCHEDULED);
    assertThat(rejectedCall.isCompletedExceptionally()).isTrue();
    verify(subscription, times(1)).cancel();
  }

  @Test
  void shouldRecoverWhenFailureHandlerRethrowsOriginalException() {
    // given
    final var failure = new IllegalArgumentException("call failed");
    final var failingActor =
        new Actor() {
          @Override
          protected void handleFailure(final Throwable error) {
            LangUtil.rethrowUnchecked(error);
          }
        };
    scheduler.submitActor(failingActor);
    scheduler.workUntilDone();

    // when
    final var call =
        failingActor.call(
            () -> {
              throw failure;
            });
    scheduler.workUntilDone();
    final var closeFuture = failingActor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(call.isCompletedExceptionally()).isTrue();
    assertThat(call.getException()).isSameAs(failure);
    assertThat(closeFuture.isCompletedExceptionally()).isTrue();
    assertThat(closeFuture.getException()).isSameAs(failure);
  }

  @Test
  void shouldNotifyFailureWhenCloseCompletionInterruptsExplicitFailure() {
    // given
    final var failure = new IllegalArgumentException("call failed");
    final var failureCallbacks = new AtomicInteger();
    final var failingActor =
        new Actor() {
          @Override
          protected void onActorStarted() {
            ActorThread.current()
                .getCurrentTask()
                .closeFuture
                .onComplete(
                    (result, error) -> {
                      throw new IllegalStateException("close callback failed");
                    },
                    Runnable::run);
          }

          @Override
          protected void handleFailure(final Throwable error) {
            actor.fail(error);
          }

          @Override
          public void onActorFailed() {
            assertThat(actor.getLifecyclePhase()).isEqualTo(ActorLifecyclePhase.FAILED);
            failureCallbacks.incrementAndGet();
          }
        };
    scheduler.submitActor(failingActor);
    scheduler.workUntilDone();

    // when
    final var call =
        failingActor.call(
            () -> {
              throw failure;
            });
    scheduler.workUntilDone();
    final var rejectedCall = failingActor.call(() -> 1);
    scheduler.workUntilDone();

    // then
    assertThat(failureCallbacks).hasValue(1);
    assertThat(call.isCompletedExceptionally()).isTrue();
    assertThat(call.getException()).isSameAs(failure);
    assertThat(rejectedCall.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldRecoverWhenSchedulingMetricsThrow() throws Exception {
    // given
    final var metricsFailure = new IllegalArgumentException("latency recording failed");
    final var metrics = mock(ActorMetrics.class);
    when(metrics.isEnabled()).thenReturn(true);
    doThrow(metricsFailure).when(metrics).observeJobSchedulingLatency(anyLong(), any());
    final var failureCallbacks = new AtomicInteger();
    final var failingActor =
        new Actor() {
          @Override
          public void onActorFailed() {
            assertThat(actor.getLifecyclePhase()).isEqualTo(ActorLifecyclePhase.FAILED);
            failureCallbacks.incrementAndGet();
          }
        };
    try (final var metricsScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setActorThreadFactory(
                (name, id, group, taskScheduler, clock, timers, ignoredMetrics, idleStrategy) ->
                    new ActorThread(
                        name, id, group, taskScheduler, clock, timers, metrics, idleStrategy))
            .build()) {
      metricsScheduler.start();

      // when
      final var startingFuture = metricsScheduler.submitActor(failingActor);

      // then
      assertThat(startingFuture)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .havingCause()
          .isSameAs(metricsFailure);
      assertThat(failingActor.closeAsync())
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableThat()
          .havingCause()
          .isSameAs(metricsFailure);
      assertThat(failureCallbacks).hasValue(1);
      verify(metrics, times(1)).observeJobSchedulingLatency(anyLong(), any());
    }
  }

  private static class ThrowingActor extends Actor {
    public Throwable throwable;
    public Throwable recordedFailure = null;

    public ThrowingActor(final Throwable throwable) {
      this.throwable = throwable;
    }

    public <T> io.camunda.zeebe.scheduler.future.ActorFuture<T> call(
        final java.util.concurrent.Callable<T> callable) {
      return actor.call(callable);
    }

    void throwError() {
      actor.run(() -> LangUtil.rethrowUnchecked(throwable));
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      recordedFailure = failure;
    }
  }
}

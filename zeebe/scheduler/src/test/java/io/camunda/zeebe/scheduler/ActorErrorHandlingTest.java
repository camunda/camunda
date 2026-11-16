/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.ActorMetrics.ActorMetricsScoped;
import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.util.CloseableSilently;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ActorErrorHandlingTest {

  @RegisterExtension
  final ControlledActorSchedulerExtension scheduler = new ControlledActorSchedulerExtension();

  @Test
  void shouldCatchThrowable() {
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
  void shouldCatchException() {
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
  void shouldNotReportCompletionCallbackFailureToActor() {
    // given
    final var actor = startActor(new RecordingActor());
    final var callbackRan = new AtomicBoolean();
    actor
        .call(() -> 1)
        .onComplete(
            (value, error) -> {
              callbackRan.set(true);
              throwingCallback(value, error);
            },
            Runnable::run);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(callbackRan).isTrue();
    assertThat(actor.failures).isEmpty();
  }

  @Test
  void shouldKeepProcessingJobsWhenCompletionCallbackThrows() {
    // given
    final var actor = startActor(new RecordingActor());
    actor.call(() -> 1).onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var nextCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(nextCall).succeedsWithin(Duration.ZERO).isEqualTo(2);
  }

  @Test
  void shouldCloseWhenCompletionCallbackThrows() {
    // given
    final var actor = startActor(new DefaultActor());
    actor.call(() -> 1).onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
  }

  @Test
  void shouldProcessJobsWhenStartCallbackThrows() {
    // given
    final var actor = new RecordingActor();
    scheduler
        .submitActor(actor)
        .onComplete(ActorErrorHandlingTest::throwingCallback, Runnable::run);
    scheduler.workUntilDone();

    // when
    final var call = actor.call(() -> 1);
    scheduler.workUntilDone();

    // then
    assertThat(call).succeedsWithin(Duration.ZERO).isEqualTo(1);
  }

  @Test
  void shouldCancelRemainingSubscriptionsWhenOneFailsToCancel() {
    // given
    final var actor = startActor(new DefaultActor());
    final var subscription = new CancelRecordingSubscription();
    actor.run(
        () -> {
          final var task = ActorThread.current().getCurrentJob().getTask();
          task.addSubscription(new CancelFailingSubscription());
          task.addSubscription(subscription);
        });
    scheduler.workUntilDone();

    // when
    actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void shouldCloseWhenSubscriptionCancelThrows() {
    // given
    final var actor = startActor(new DefaultActor());
    actor.run(
        () ->
            ActorThread.current()
                .getCurrentJob()
                .getTask()
                .addSubscription(new CancelFailingSubscription()));
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture).succeedsWithin(Duration.ZERO);
  }

  @Test
  void shouldFailCallsWhenHandleFailureThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // when
    final var nextCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(nextCall).isDone();
    assertThat(nextCall.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldCloseWhenHandleFailureThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
  }

  @Test
  void shouldRunJobsScheduledOnFailureWhenHandleFailureThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    final var cleanedUp = new AtomicBoolean();
    actor.onFailed = () -> actor.run(() -> cleanedUp.set(true));

    // when
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // then
    assertThat(cleanedUp).isTrue();
  }

  @Test
  void shouldFailCallsWhenHandleFailureFailsActorAndThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    actor.failBeforeThrowing = true;
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // when
    final var nextCall = actor.call(() -> 2);
    scheduler.workUntilDone();

    // then
    assertThat(nextCall).isDone();
    assertThat(nextCall.isCompletedExceptionally()).isTrue();
  }

  @Test
  void shouldFailCallWithItsFailureWhenHandleFailureThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    final var failure = new RuntimeException("call failure");

    // when
    final var call =
        actor.call(
            () -> {
              throw failure;
            });
    scheduler.workUntilDone();

    // then
    assertThat(call).failsWithin(Duration.ZERO).withThrowableThat().havingCause().isSameAs(failure);
  }

  @Test
  void shouldLetActorUseItsControlOnFailureWhenHandleFailureThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    final var cleanedUp = new AtomicBoolean();
    actor.onFailed =
        () ->
            actor.runOnCompletion(
                CompletableActorFuture.completed(), (value, error) -> cleanedUp.set(true));

    // when
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // then
    assertThat(cleanedUp).isTrue();
  }

  @Test
  void shouldRunJobsScheduledOnFailureWhenHandleFailureFailsActorAndThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    final var cleanedUp = new AtomicBoolean();
    actor.failBeforeThrowing = true;
    actor.onFailed = () -> actor.run(() -> cleanedUp.set(true));

    // when
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // then
    assertThat(cleanedUp).isTrue();
  }

  @Test
  void shouldRunCleanUpOnCompletionWhenHandleFailureFailsActorAndThrows() {
    // given
    final var actor = startActor(new FailingHandlerActor());
    final var released = new CompletableActorFuture<Void>();
    final var cleanedUp = new AtomicBoolean();
    actor.failBeforeThrowing = true;
    actor.onFailed = () -> actor.runOnCompletion(released, (value, error) -> cleanedUp.set(true));
    actor.run(ActorErrorHandlingTest::throwingJob);
    scheduler.workUntilDone();

    // when
    released.complete(null);
    scheduler.workUntilDone();

    // then
    assertThat(cleanedUp).isTrue();
  }

  @Test
  void shouldFailPendingJobWhenSubscriptionCompletionThrows() {
    // given
    final var actor = startActor(new DefaultActor());
    final var failure = new IllegalStateException("completion failure");
    final var result = new CompletableActorFuture<Void>();
    actor.run(
        () -> {
          final var task = ActorThread.current().getCurrentJob().getTask();
          final var pendingJob = new ActorJob();
          pendingJob.setRunnable(() -> {});
          pendingJob.setResultFuture(result);
          pendingJob.onJobAddedToTask(task);
          new CompletionFailingSubscription(task, pendingJob, failure);
        });

    // when
    scheduler.workUntilDone();

    // then
    assertThat(result)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingCause()
        .isSameAs(failure);
  }

  @Test
  void shouldReleaseSubscriptionsWhenSubscriptionPollKeepsThrowing() {
    // given
    final var actor = startActor(new DefaultActor());
    final var subscription = new PollFailingSubscription();

    // when
    actor.run(() -> ActorThread.current().getCurrentJob().getTask().addSubscription(subscription));
    scheduler.workUntilDone();

    // then
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void shouldCompleteCloseWhenSubscriptionThrowsAfterActorClosed() {
    // given
    final var actor = startActor(new DefaultActor());
    final var subscription = new ClosedPhaseFailingSubscription();
    actor.run(() -> ActorThread.current().getCurrentJob().getTask().addSubscription(subscription));
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture).isDone();
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void shouldRunJobsWhenObservingSchedulingLatencyThrows() throws Exception {
    // given
    final var registry = new SchedulingLatencyFailingRegistry();
    try (final var actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(0)
            .setMeterRegistry(registry)
            .build()) {
      actorScheduler.start();
      final var actor = new DefaultActor();

      // when
      actorScheduler.submitActor(actor);
      final var result = actor.call(() -> 1);

      // then
      assertThat(result).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(1);
    } finally {
      registry.close();
    }
  }

  @Test
  void shouldCompleteCloseWhenReleasingTaskThrows() {
    // given
    final var actor = startActor(new DefaultActor());
    actor.run(
        () ->
            ActorThread.current()
                .getCurrentJob()
                .getTask()
                .setActorMetrics(new CloseFailingMetrics()));
    scheduler.workUntilDone();

    // when
    final var closeFuture = actor.closeAsync();
    scheduler.workUntilDone();

    // then
    assertThat(closeFuture)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .havingCause()
        .withMessage("metrics failure");
  }

  private <T extends Actor> T startActor(final T actor) {
    scheduler.submitActor(actor);
    scheduler.workUntilDone();
    return actor;
  }

  private static void throwingCallback(final Object value, final Throwable error) {
    throw new RuntimeException("callback failure");
  }

  private static void throwingJob() {
    throw new RuntimeException("job failure");
  }

  private static final class DefaultActor extends Actor {}

  private static final class FailingHandlerActor extends Actor {
    private Runnable onFailed = () -> {};
    private boolean failBeforeThrowing;

    @Override
    protected void handleFailure(final Throwable failure) {
      if (failBeforeThrowing) {
        actor.fail(failure);
      }
      throw new IllegalStateException("handler failure", failure);
    }

    @Override
    public void onActorFailed() {
      onFailed.run();
    }
  }

  private static final class RecordingActor extends Actor {
    private final List<Throwable> failures = new ArrayList<>();

    @Override
    protected void handleFailure(final Throwable failure) {
      failures.add(failure);
      actor.fail(failure);
    }
  }

  private static final class CancelFailingSubscription implements ActorSubscription {
    @Override
    public boolean poll() {
      return false;
    }

    @Override
    public ActorJob getJob() {
      return null;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }

    @Override
    public void cancel() {
      throw new IllegalStateException("cancel failure");
    }
  }

  private static final class CancelRecordingSubscription implements ActorSubscription {
    private boolean cancelled;

    @Override
    public boolean poll() {
      return false;
    }

    @Override
    public ActorJob getJob() {
      return null;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class CompletionFailingSubscription implements ActorSubscription {
    private final ActorJob job = new ActorJob();
    private final RuntimeException failure;
    private boolean triggered;

    private CompletionFailingSubscription(
        final ActorTask task, final ActorJob nextJob, final RuntimeException failure) {
      this.failure = failure;
      job.setRunnable(() -> task.insertJob(nextJob));
      job.onJobAddedToTask(task);
      job.setSubscription(this);
    }

    @Override
    public boolean poll() {
      final var wasTriggered = triggered;
      triggered = true;
      return !wasTriggered;
    }

    @Override
    public ActorJob getJob() {
      return job;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }

    @Override
    public void onJobCompleted() {
      throw failure;
    }
  }

  private static final class PollFailingSubscription implements ActorSubscription {
    private boolean cancelled;

    @Override
    public boolean triggersInPhase(final ActorLifecyclePhase phase) {
      return true;
    }

    @Override
    public boolean poll() {
      throw new IllegalStateException("poll failure");
    }

    @Override
    public ActorJob getJob() {
      return null;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class ClosedPhaseFailingSubscription implements ActorSubscription {
    private boolean cancelled;

    @Override
    public boolean triggersInPhase(final ActorLifecyclePhase phase) {
      if (phase == ActorLifecyclePhase.CLOSED) {
        throw new IllegalStateException("phase check failure");
      }
      return false;
    }

    @Override
    public boolean poll() {
      return false;
    }

    @Override
    public ActorJob getJob() {
      return null;
    }

    @Override
    public boolean isRecurring() {
      return false;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class CloseFailingMetrics implements ActorMetricsScoped {
    @Override
    public void close() {
      throw new IllegalStateException("metrics failure");
    }

    @Override
    public void countExecution() {}

    @Override
    public void updateJobQueueLength(final int length) {}

    @Override
    public CloseableSilently startExecutionTimer() {
      return ActorMetricsScoped.NOOP.startExecutionTimer();
    }

    @Override
    public boolean isEnabled() {
      return false;
    }
  }

  private static final class SchedulingLatencyFailingRegistry extends SimpleMeterRegistry {
    @Override
    protected Timer newTimer(
        final Meter.Id id,
        final DistributionStatisticConfig config,
        final PauseDetector pauseDetector) {
      if (!id.getName().equals(ActorMetricsDoc.SCHEDULING_LATENCY.getName())) {
        return super.newTimer(id, config, pauseDetector);
      }
      return new CumulativeTimer(id, clock, config, pauseDetector, getBaseTimeUnit(), false) {
        @Override
        protected void recordNonNegative(final long amount, final TimeUnit unit) {
          throw new IllegalStateException("metrics failure");
        }
      };
    }
  }

  private static class ThrowingActor extends Actor {
    public Throwable throwable;
    public Throwable recordedFailure = null;

    public ThrowingActor(final Throwable throwable) {
      this.throwable = throwable;
    }

    private void throwError() {
      actor.run(() -> LangUtil.rethrowUnchecked(throwable));
    }

    @Override
    protected void handleFailure(final Throwable failure) {
      recordedFailure = failure;
    }
  }
}

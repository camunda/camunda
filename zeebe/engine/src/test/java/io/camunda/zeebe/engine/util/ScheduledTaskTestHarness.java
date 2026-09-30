/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.NoopScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.impl.ProcessingScheduleServiceImpl;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.metrics.ScheduledTaskMetrics;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Runs the scheduled tasks of a {@link StreamProcessorLifecycleAware} on a real {@link
 * ProcessingScheduleServiceImpl}, with a {@link FailingLogStreamWriter} in place of the log stream.
 * This allows testing how a scheduler and the scheduling service behave together when writes fail,
 * without a stream processor or a log.
 *
 * <p>Time is controlled by the given actor scheduler extension, which the test must register as a
 * JUnit extension itself. Use {@link #advanceTime(Duration)} to move it forward and to run the
 * tasks that became due.
 */
public final class ScheduledTaskTestHarness {

  private final ControlledActorSchedulerExtension actorScheduler;
  private final StreamProcessorLifecycleAware scheduler;
  private final ProcessingScheduleServiceImpl scheduleService;
  private final FailingLogStreamWriter writer = new FailingLogStreamWriter();

  /**
   * @param actorScheduler controls the time and runs the actors, must be registered by the test
   * @param scheduler the scheduler under test, which is given the lifecycle callbacks by this
   *     harness
   */
  public ScheduledTaskTestHarness(
      final ControlledActorSchedulerExtension actorScheduler,
      final StreamProcessorLifecycleAware scheduler) {
    this.actorScheduler = actorScheduler;
    this.scheduler = scheduler;
    scheduleService =
        new ProcessingScheduleServiceImpl(
            () -> Phase.PROCESSING,
            () -> false,
            () -> writer,
            new NoopScheduledCommandCache(),
            actorScheduler.getClock(),
            Duration.ofSeconds(1),
            ScheduledTaskMetrics.noop());
    actorScheduler.submitActor(new ScheduleServiceActor(scheduleService));
    actorScheduler.workUntilDone();
  }

  public FailingLogStreamWriter writer() {
    return writer;
  }

  /**
   * Signals the recovery of the stream processor to the scheduler under test, like the stream
   * processor does before it starts processing, and runs everything that this schedules.
   */
  public void recover() {
    scheduler.onRecovered(new HarnessContext(new SyncOnlyScheduleService(scheduleService)));
    actorScheduler.workUntilDone();
  }

  /** Moves the clock forward and runs everything that became due, until nothing is left to do. */
  public void advanceTime(final Duration duration) {
    actorScheduler.updateClock(duration);
    actorScheduler.workUntilDone();
  }

  /** A writer that accepts all writes, unless it is told to fail them. */
  public static final class FailingLogStreamWriter implements LogStreamWriter {
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<Intent> writtenIntents = new CopyOnWriteArrayList<>();
    private final AtomicReference<IntFunction<@Nullable WriteFailure>> failure =
        new AtomicReference<>(attempt -> null);

    /** Fails every write with the given failure until {@link #stopFailing()} is called. */
    public void failWith(final @NonNull WriteFailure writeFailure) {
      failure.set(attempt -> writeFailure);
    }

    /** Accepts every write from now on. */
    public void stopFailing() {
      failure.set(attempt -> null);
    }

    /**
     * Fails every write with the given failure while the condition holds, and accepts it otherwise.
     *
     * @param condition tested with the number of this attempt, counting from 1 and including the
     *     failed attempts
     * @param writeFailure the failure to fail the write with
     */
    public void failWhile(final IntPredicate condition, final @NonNull WriteFailure writeFailure) {
      failure.set(attempt -> condition.test(attempt) ? writeFailure : null);
    }

    /** The number of writes that were tried, including the failed ones. */
    public int attempts() {
      return attempts.get();
    }

    /** The intents of the accepted entries, in the order they were written. */
    public List<Intent> writtenIntents() {
      return writtenIntents;
    }

    @Override
    public @NonNull Either<WriteFailure, Long> tryWrite(
        final @NonNull WriteContext context,
        final @NonNull List<LogAppendEntry> appendEntries,
        final long sourcePosition) {
      final var writeFailure = failure.get().apply(attempts.incrementAndGet());
      if (writeFailure != null) {
        return Either.left(writeFailure);
      }
      appendEntries.forEach(entry -> writtenIntents.add(entry.recordMetadata().getIntent()));
      return Either.right((long) writtenIntents.size());
    }
  }

  private record HarnessContext(ProcessingScheduleService scheduleService)
      implements ReadonlyStreamProcessorContext {

    @Override
    public @NonNull ProcessingScheduleService getScheduleService() {
      return scheduleService;
    }

    @Override
    public int getPartitionId() {
      return 1;
    }

    @Override
    public @NonNull StreamClock getClock() {
      throw new UnsupportedOperationException("The harness does not provide a stream clock");
    }
  }

  /**
   * Exposes the real scheduling service as a {@link ProcessingScheduleService}. The async variants
   * need an async actor, which the harness does not provide, so they fail loudly.
   */
  private record SyncOnlyScheduleService(ProcessingScheduleServiceImpl delegate)
      implements ProcessingScheduleService {

    @Override
    public @NonNull ScheduledTask runDelayed(
        final @NonNull Duration delay, final @NonNull Runnable task) {
      return delegate.runDelayed(delay, task);
    }

    @Override
    public @NonNull ScheduledTask runDelayed(
        final @NonNull Duration delay, final @NonNull Task task) {
      return delegate.runDelayed(delay, task);
    }

    @Override
    public @NonNull ScheduledTask runAt(final long timestamp, final @NonNull Task task) {
      return delegate.runAt(timestamp, task);
    }

    @Override
    public @NonNull ScheduledTask runAt(final long timestamp, final @NonNull Runnable task) {
      return delegate.runAt(timestamp, task);
    }

    @Override
    public void runAtFixedRate(final @NonNull Duration delay, final @NonNull Runnable task) {
      delegate.runAtFixedRate(delay, task);
    }

    @Override
    public void runAtFixedRate(final @NonNull Duration delay, final @NonNull Task task) {
      delegate.runAtFixedRate(delay, task);
    }

    @Override
    public void runAtFixedRateAsync(final @NonNull Duration delay, final @NonNull Task task) {
      throw unsupportedAsync();
    }

    @Override
    public @NonNull ScheduledTask runDelayedAsync(
        final @NonNull Duration delay, final @NonNull Task task) {
      throw unsupportedAsync();
    }

    @Override
    public @NonNull ScheduledTask runAtAsync(final long timestamp, final @NonNull Task task) {
      throw unsupportedAsync();
    }

    @Override
    public void runAtFixedRateAsync(
        final @NonNull Duration delay,
        final @NonNull Task task,
        final @NonNull AsyncTaskGroup taskGroup) {
      throw unsupportedAsync();
    }

    @Override
    public @NonNull ScheduledTask runDelayedAsync(
        final @NonNull Duration delay,
        final @NonNull Task task,
        final @NonNull AsyncTaskGroup taskGroup) {
      throw unsupportedAsync();
    }

    @Override
    public @NonNull ScheduledTask runAtAsync(
        final long timestamp, final @NonNull Task task, final @NonNull AsyncTaskGroup taskGroup) {
      throw unsupportedAsync();
    }

    private static UnsupportedOperationException unsupportedAsync() {
      return new UnsupportedOperationException(
          "The harness only supports synchronous scheduling, e.g. disable the async variant of"
              + " the scheduler under test");
    }
  }

  private static final class ScheduleServiceActor extends Actor {
    private final ProcessingScheduleServiceImpl scheduleService;

    private ScheduleServiceActor(final ProcessingScheduleServiceImpl scheduleService) {
      this.scheduleService = scheduleService;
    }

    @Override
    protected void onActorStarting() {
      actor.runOnCompletionBlockingCurrentPhase(
          scheduleService.open(actor),
          (v, t) -> {
            if (t != null) {
              actor.fail(t);
            }
          });
    }
  }
}

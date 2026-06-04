/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public final class ActorScheduler implements AutoCloseable, ActorSchedulingService {
  private final AtomicReference<SchedulerState> state = new AtomicReference<>();
  private final ActorSchedulerBuilder builder;
  private final ActorMetrics metrics;
  // Non-final: rebuilt on restart because the executor's Java threads are single-use and cannot be
  // restarted after closeAsync() (EXPERIMENTAL — CRaC restart support).
  private ActorExecutor actorTaskExecutor;

  public ActorScheduler(final ActorSchedulerBuilder builder) {
    state.set(SchedulerState.NEW);
    this.builder = builder;
    actorTaskExecutor = builder.getActorExecutor();
    metrics = builder.getActorMetrics();
  }

  /**
   * Submits a non-blocking, CPU-bound actor.
   *
   * @param actor the actor to submit
   */
  @Override
  public ActorFuture<Void> submitActor(final Actor actor) {
    return submitActor(actor, SchedulingHints.cpuBound());
  }

  /**
   * Submits an actor providing hints to the scheduler about how to best schedule the actor. Actors
   * must always be non-blocking. On top of that, the scheduler distinguishes
   *
   * <ul>
   *   <li>CPU-bound actors: actors which perform no or very little blocking I/O. It is possible to
   *       specify a priority.
   *   <li>I/O-bound actors: actors where the runtime is dominated by performing <strong>blocking
   *       I/O</strong> (usually filesystem writes). It is possible to specify the I/O device used
   *       by the actor.
   * </ul>
   *
   * Scheduling hints can be created using the {@link SchedulingHints} class.
   *
   * @param actor the actor to submit
   * @param schedulingHints additional scheduling hint
   */
  @Override
  public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints schedulingHints) {
    checkRunningState();

    final ActorTask task = actor.actor.task;
    task.setActorMetrics(metrics.scoped(actor.getName()));

    return switch (schedulingHints) {
      case CPU_BOUND -> actorTaskExecutor.submitCpuBound(task);
      case IO_BOUND -> actorTaskExecutor.submitIoBoundTask(task);
    };
  }

  private void checkRunningState() {
    if (state.get() != SchedulerState.RUNNING) {
      throw new IllegalStateException("Actor scheduler is not running");
    }
  }

  /**
   * @return true if the scheduler is currently running (started and not stopped).
   */
  public boolean isRunning() {
    return state.get() == SchedulerState.RUNNING;
  }

  public void start() {
    if (state.compareAndSet(SchedulerState.NEW, SchedulerState.RUNNING)) {
      actorTaskExecutor.start();
    } else if (state.compareAndSet(SchedulerState.TERMINATED, SchedulerState.RUNNING)) {
      // EXPERIMENTAL (CRaC restart): the previous executor's Java threads are dead and cannot be
      // restarted, so rebuild the executor + thread groups from the retained builder config.
      actorTaskExecutor = builder.rebuildActorExecutor();
      actorTaskExecutor.start();
    } else {
      throw new IllegalStateException("Cannot start scheduler already started.");
    }
  }

  public Future<Void> stop() {
    if (state.compareAndSet(SchedulerState.RUNNING, SchedulerState.TERMINATING)) {

      return actorTaskExecutor.closeAsync().thenRun(() -> state.set(SchedulerState.TERMINATED));
    } else {
      throw new IllegalStateException("Cannot stop scheduler not running");
    }
  }

  /**
   * Convenience implementation for blocking stop which can be used with try-with-resources and
   * other constructs.
   */
  @Override
  public void close() throws Exception {
    stop().get(10, TimeUnit.SECONDS);
  }

  public static ActorSchedulerBuilder newActorScheduler() {
    return new ActorSchedulerBuilder();
  }

  public static class ActorSchedulerBuilder {

    /**
     * @see BackoffIdleStrategy#DEFAULT_MAX_SPINS
     */
    public static final long DEFAULT_MAX_SPINS = 10;

    /**
     * @see BackoffIdleStrategy#DEFAULT_MAX_YIELDS
     */
    public static final long DEFAULT_MAX_YIELDS = 5;

    /**
     * @see BackoffIdleStrategy#DEFAULT_MIN_PARK_PERIOD_NS
     */
    public static final long DEFAULT_MIN_PARK_PERIOD_NS = 1000;

    /** 20 ms instead of {@link BackoffIdleStrategy#DEFAULT_MAX_PARK_PERIOD_NS}. */
    public static final long DEFAULT_MAX_PARK_PERIOD_NS = 20_000_000;

    private String schedulerName = "";
    private ActorClock actorClock;
    private int cpuBoundThreadsCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    private ActorThreadGroup cpuBoundActorGroup;
    private int ioBoundThreadsCount = 2;
    private ActorThreadGroup ioBoundActorGroup;
    private ActorThreadFactory actorThreadFactory;
    private ActorExecutor actorExecutor;
    private ActorTimerQueue actorTimerQueue;
    private final boolean enableMetrics = false;
    private Supplier<IdleStrategy> idleStrategySupplier =
        ActorSchedulerBuilder::defaultIdleStrategySupplier;
    private ActorMetrics actorMetrics = ActorMetrics.disabled();

    public static IdleStrategy defaultIdleStrategySupplier() {
      return new BackoffIdleStrategy(
          DEFAULT_MAX_SPINS,
          DEFAULT_MAX_YIELDS,
          DEFAULT_MIN_PARK_PERIOD_NS,
          DEFAULT_MAX_PARK_PERIOD_NS);
    }

    public String getSchedulerName() {
      return schedulerName;
    }

    public ActorSchedulerBuilder setSchedulerName(final String schedulerName) {
      this.schedulerName = schedulerName;
      return this;
    }

    public ActorClock getActorClock() {
      return actorClock;
    }

    public ActorSchedulerBuilder setActorClock(final ActorClock actorClock) {
      this.actorClock = actorClock;
      return this;
    }

    public Supplier<IdleStrategy> getIdleStrategySupplier() {
      return idleStrategySupplier;
    }

    public ActorSchedulerBuilder setIdleStrategySupplier(
        final Supplier<IdleStrategy> idleStrategySupplier) {
      this.idleStrategySupplier = Objects.requireNonNull(idleStrategySupplier);
      return this;
    }

    public ActorTimerQueue getActorTimerQueue() {
      return actorTimerQueue;
    }

    public ActorSchedulerBuilder setActorTimerQueue(final ActorTimerQueue actorTimerQueue) {
      this.actorTimerQueue = actorTimerQueue;
      return this;
    }

    public int getCpuBoundActorThreadCount() {
      return cpuBoundThreadsCount;
    }

    public ActorSchedulerBuilder setCpuBoundActorThreadCount(final int actorThreadCount) {
      cpuBoundThreadsCount = actorThreadCount;
      return this;
    }

    public int getIoBoundActorThreadCount() {
      return ioBoundThreadsCount;
    }

    public ActorSchedulerBuilder setIoBoundActorThreadCount(final int ioBoundActorsThreadCount) {
      ioBoundThreadsCount = ioBoundActorsThreadCount;
      return this;
    }

    public ActorThreadFactory getActorThreadFactory() {
      return actorThreadFactory;
    }

    public ActorSchedulerBuilder setActorThreadFactory(
        final ActorThreadFactory actorThreadFactory) {
      this.actorThreadFactory = actorThreadFactory;
      return this;
    }

    public ActorExecutor getActorExecutor() {
      return actorExecutor;
    }

    public ActorThreadGroup getCpuBoundActorThreads() {
      return cpuBoundActorGroup;
    }

    public ActorThreadGroup getIoBoundActorThreads() {
      return ioBoundActorGroup;
    }

    ActorMetrics getActorMetrics() {
      return actorMetrics;
    }

    public ActorSchedulerBuilder setMeterRegistry(final MeterRegistry meterRegistry) {
      actorMetrics = ActorMetrics.ofNullable(meterRegistry);
      return this;
    }

    private void initActorThreadFactory() {
      if (actorThreadFactory == null) {
        actorThreadFactory = new DefaultActorThreadFactory();
      }
    }

    private void initIoBoundActorThreadGroup() {
      if (ioBoundActorGroup == null) {
        ioBoundActorGroup = new IoThreadGroup(this);
      }
    }

    private void initCpuBoundActorThreadGroup() {
      if (cpuBoundActorGroup == null) {
        cpuBoundActorGroup = new CpuThreadGroup(this);
      }
    }

    private void initActorExecutor() {
      if (actorExecutor == null) {
        actorExecutor = new ActorExecutor(this);
      }
    }

    /**
     * EXPERIMENTAL (CRaC restart): discards the previously built thread groups + executor and
     * builds a fresh {@link ActorExecutor} with new threads from the same configuration. Required
     * because the executor's Java threads are single-use and cannot be restarted after {@link
     * ActorExecutor#closeAsync()}.
     */
    ActorExecutor rebuildActorExecutor() {
      cpuBoundActorGroup = null;
      ioBoundActorGroup = null;
      actorExecutor = null;
      initCpuBoundActorThreadGroup();
      initIoBoundActorThreadGroup();
      initActorExecutor();
      return actorExecutor;
    }

    public ActorScheduler build() {
      initActorThreadFactory();
      initCpuBoundActorThreadGroup();
      initIoBoundActorThreadGroup();
      initActorExecutor();
      return new ActorScheduler(this);
    }
  }

  public static final class DefaultActorThreadFactory implements ActorThreadFactory {
    @Override
    public ActorThread newThread(
        final String name,
        final int id,
        final ActorThreadGroup threadGroup,
        final TaskScheduler taskScheduler,
        final ActorClock clock,
        final ActorTimerQueue timerQueue,
        final ActorMetrics actorMetrics,
        final IdleStrategy idleStrategy) {
      return new ActorThread(
          name, id, threadGroup, taskScheduler, clock, timerQueue, actorMetrics, idleStrategy);
    }
  }

  public interface ActorThreadFactory {
    ActorThread newThread(
        final String name,
        final int id,
        final ActorThreadGroup threadGroup,
        final TaskScheduler taskScheduler,
        final ActorClock clock,
        final ActorTimerQueue timerQueue,
        final ActorMetrics actorMetrics,
        final IdleStrategy idleStrategy);
  }

  private enum SchedulerState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // scheduler is not reusable
  }
}

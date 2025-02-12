/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.util.Loggers;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.slf4j.Logger;
import org.slf4j.MDC;

public class ActorThread extends Thread implements Consumer<Runnable> {
  private static final Logger LOG = Loggers.ACTOR_LOGGER;
  private static final FatalErrorHandler FATAL_ERROR_HANDLER = FatalErrorHandler.withLogger(LOG);
  private static final VarHandle STATE_HANDLE;

  static {
    try {
      STATE_HANDLE =
          MethodHandles.lookup().findVarHandle(ActorThread.class, "state", ActorThreadState.class);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public final ManyToManyConcurrentArrayQueue<Runnable> submittedCallbacks =
      new ManyToManyConcurrentArrayQueue<>(1024 * 24);
  protected final ActorTimerQueue timerJobQueue;
  protected ActorTaskRunnerIdleStrategy idleStrategy;
  ActorTask currentTask;
  private final ActorMetrics actorMetrics;
  private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();
  private final ActorClock clock;
  private final int threadId;
  private final TaskScheduler taskScheduler;
  private final BoundedArrayQueue<ActorJob> jobs = new BoundedArrayQueue<>(2048);
  private final ActorThreadGroup actorThreadGroup;
  private volatile ActorThreadState state;

  public ActorThread(
      final String name,
      final int id,
      final ActorThreadGroup threadGroup,
      final TaskScheduler taskScheduler,
      final ActorClock clock,
      final ActorTimerQueue timerQueue,
      final ActorMetrics actorMetrics) {
    this(
        name,
        id,
        threadGroup,
        taskScheduler,
        clock,
        timerQueue,
        actorMetrics,
        ActorSchedulerBuilder.defaultIdleStrategySupplier());
  }

  public ActorThread(
      final String name,
      final int id,
      final ActorThreadGroup threadGroup,
      final TaskScheduler taskScheduler,
      final ActorClock clock,
      final ActorTimerQueue timerQueue,
      final ActorMetrics actorMetrics,
      final IdleStrategy idleStrategy) {
    this.actorMetrics = actorMetrics;
    setName(name);
    state = ActorThreadState.NEW;
    threadId = id;
    this.clock = clock != null ? clock : new DefaultActorClock();
    timerJobQueue = timerQueue != null ? timerQueue : new ActorTimerQueue(this.clock);
    actorThreadGroup = threadGroup;
    this.taskScheduler = taskScheduler;
    this.idleStrategy = new ActorTaskRunnerIdleStrategy(idleStrategy);
  }

  private void doWork() {
    submittedCallbacks.drain(this);

    if (clock.update()) {
      timerJobQueue.processExpiredTimers(clock);
    }

    currentTask = taskScheduler.getNextTask();

    if (currentTask != null) {
      final var metrics = currentTask.getActorMetrics();
      try (final var timer = metrics != null ? metrics.startExecutionTimer() : null) {
        executeCurrentTask();
      } finally {
        if (metrics != null) {
          metrics.updateJobQueueLength(currentTask.estimateQueueLength());
          metrics.countExecution();
        }
      }
    } else {
      idleStrategy.onIdle();
    }
  }

  private void executeCurrentTask() {
    final var properties = currentTask.getActor().getContext();
    boolean resubmit = false;

    for (final var property : properties.entrySet()) {
      MDC.put(property.getKey(), property.getValue());
    }

    idleStrategy.onTaskExecuted();

    try {
      resubmit = currentTask.execute(this);
    } catch (final Throwable e) {
      FATAL_ERROR_HANDLER.handleError(e);
      LOG.error("Unexpected error occurred in task {}", currentTask, e);
    } finally {
      clock.update();
      properties.keySet().forEach(MDC::remove);
    }

    if (resubmit) {
      currentTask.resubmit();
    }
  }

  public void hintWorkAvailable() {
    idleStrategy.hintWorkAvailable();
  }

  /** Must be called from this thread, schedules a job to be run later. */
  public void scheduleTimer(final TimerSubscription timer) {
    timerJobQueue.schedule(timer, clock);
  }

  /** Must be called from this thread, remove a scheduled job. */
  public void removeTimer(final TimerSubscription timer) {
    timerJobQueue.remove(timer);
  }

  /**
   * Returns the current {@link ActorThread} or null if the current thread is not an {@link
   * ActorThread}.
   *
   * @return the current {@link ActorThread} or null
   */
  public static ActorThread current() {
    /*
     * Yes, we could work with a thread-local. Except thread locals are slow as f***
     * since they are kept in a map datastructure on the current thread.
     * This implementation takes advantage of the fact that ActorTaskRunner extends Thread
     * itself. If we can cast down, the current thread is the current ActorTaskRunner.
     */
    return Thread.currentThread() instanceof ActorThread
        ? (ActorThread) Thread.currentThread()
        : null;
  }

  public static ActorThread ensureCalledFromActorThread(final String methodName) {
    final ActorThread thread = ActorThread.current();

    if (thread == null) {
      throw new UnsupportedOperationException(
          "Incorrect usage of actor. " + methodName + ": must be called from actor thread");
    }

    return thread;
  }

  public static boolean isCalledFromActorThread() {
    final ActorThread thread = ActorThread.current();
    return thread != null;
  }

  public ActorJob newJob() {
    ActorJob job = jobs.poll();

    if (job == null) {
      job = new ActorJob();
    }

    return job;
  }

  void recycleJob(final ActorJob j) {
    j.reset();
    jobs.offer(j);
  }

  public int getRunnerId() {
    return threadId;
  }

  @Override
  public synchronized void start() {
    if (STATE_HANDLE.compareAndSet(this, ActorThreadState.NEW, ActorThreadState.RUNNING)) {
      super.start();
    } else {
      throw new IllegalStateException("Cannot start runner, not in state 'NEW'.");
    }
  }

  @Override
  public void run() {
    idleStrategy.init();
    MDC.put("actor-scheduler", actorThreadGroup.getSchedulerName());

    while (state == ActorThreadState.RUNNING) {
      try {
        doWork();
      } catch (final Exception e) {
        LOG.error("Unexpected error occurred while in the actor thread {}", getName(), e);
      }
    }

    state = ActorThreadState.TERMINATED;

    terminationFuture.complete(null);
  }

  public CompletableFuture<Void> close() {
    if (STATE_HANDLE.compareAndSet(this, ActorThreadState.RUNNING, ActorThreadState.TERMINATING)) {
      return terminationFuture;
    } else {
      throw new IllegalStateException("Cannot stop runner, not in state 'RUNNING'.");
    }
  }

  public ActorJob getCurrentJob() {
    final ActorTask task = getCurrentTask();

    if (task != null) {
      return task.currentJob;
    }

    return null;
  }

  public ActorTask getCurrentTask() {
    return currentTask;
  }

  public ActorClock getClock() {
    return clock;
  }

  public ActorThreadGroup getActorThreadGroup() {
    return actorThreadGroup;
  }

  @Override
  public void accept(final Runnable t) {
    t.run();
  }

  public ActorMetrics getActorMetrics() {
    return actorMetrics;
  }

  public enum ActorThreadState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // runner is not reusable
  }

  protected class ActorTaskRunnerIdleStrategy {
    private final IdleStrategy idleStrategy;
    private boolean isIdle;

    protected ActorTaskRunnerIdleStrategy(final IdleStrategy idleStrategy) {
      this.idleStrategy = idleStrategy;
    }

    void init() {
      isIdle = true;
    }

    public void hintWorkAvailable() {
      LockSupport.unpark(ActorThread.this);
    }

    protected void onIdle() {
      if (!isIdle) {
        clock.update();
        isIdle = true;
      }

      idleStrategy.idle();
    }

    protected void onTaskExecuted() {
      idleStrategy.reset();
      isIdle = false;
    }
  }
}

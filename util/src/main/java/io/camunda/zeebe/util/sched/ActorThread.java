/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched;

import io.zeebe.util.BoundedArrayQueue;
import io.zeebe.util.Loggers;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.clock.DefaultActorClock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.slf4j.Logger;
import org.slf4j.MDC;
import sun.misc.Unsafe;

public class ActorThread extends Thread implements Consumer<Runnable> {
  static final Unsafe UNSAFE = UnsafeAccess.UNSAFE;
  private static final long STATE_OFFSET;
  private static final Logger LOG = Loggers.ACTOR_LOGGER;

  static {
    try {
      STATE_OFFSET = UNSAFE.objectFieldOffset(ActorThread.class.getDeclaredField("state"));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public final ManyToManyConcurrentArrayQueue<Runnable> submittedCallbacks =
      new ManyToManyConcurrentArrayQueue<>(1024 * 24);
  protected final ActorTimerQueue timerJobQueue;
  protected ActorTaskRunnerIdleStrategy idleStrategy = new ActorTaskRunnerIdleStrategy();
  ActorTask currentTask;
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
      final ActorTimerQueue timerQueue) {
    setName(name);
    state = ActorThreadState.NEW;
    threadId = id;
    this.clock = clock != null ? clock : new DefaultActorClock();
    timerJobQueue = timerQueue != null ? timerQueue : new ActorTimerQueue(this.clock);
    actorThreadGroup = threadGroup;
    this.taskScheduler = taskScheduler;
  }

  private void doWork() {
    submittedCallbacks.drain(this);

    if (clock.update()) {
      timerJobQueue.processExpiredTimers(clock);
    }

    currentTask = taskScheduler.getNextTask(clock);

    if (currentTask != null) {
      try {
        executeCurrentTask();
      } finally {
        taskScheduler.onTaskReleased(currentTask);
      }
    } else {
      idleStrategy.onIdle();
    }
  }

  private void executeCurrentTask() {
    MDC.put("actor-name", currentTask.getName());
    idleStrategy.onTaskExecuted();

    boolean resubmit = false;

    try {
      resubmit = currentTask.execute(this);
    } catch (final Exception e) {
      // TODO: check interrupt state?
      // TODO: Handle Exception
      LOG.error("Unexpected error occurred in task {}", currentTask, e);

      // TODO: resubmit on exception?
      //                resubmit = true;
    } finally {
      MDC.remove("actor-name");

      clock.update();
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
    if (UNSAFE.compareAndSwapObject(
        this, STATE_OFFSET, ActorThreadState.NEW, ActorThreadState.RUNNING)) {
      super.start();
    } else {
      throw new IllegalStateException("Cannot start runner, not in state 'NEW'.");
    }
  }

  @Override
  public void run() {
    idleStrategy.init();

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
    if (UNSAFE.compareAndSwapObject(
        this, STATE_OFFSET, ActorThreadState.RUNNING, ActorThreadState.TERMINATING)) {
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

  public enum ActorThreadState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // runner is not reusable
  }

  protected class ActorTaskRunnerIdleStrategy {
    final BackoffIdleStrategy backoff =
        new BackoffIdleStrategy(100, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));
    boolean isIdle;

    long idleTimeStart;
    long busyTimeStart;

    void init() {
      isIdle = true;
      idleTimeStart = System.nanoTime();
    }

    public void hintWorkAvailable() {
      LockSupport.unpark(ActorThread.this);
    }

    protected void onIdle() {
      if (!isIdle) {
        clock.update();
        idleTimeStart = clock.getNanoTime();
        isIdle = true;
      }

      backoff.idle();
    }

    protected void onTaskExecuted() {
      backoff.reset();

      if (isIdle) {
        busyTimeStart = clock.getNanoTime();
        isIdle = false;
      }
    }
  }
}

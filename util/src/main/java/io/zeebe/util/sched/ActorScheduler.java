/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ActorScheduler {
  private final AtomicReference<SchedulerState> state = new AtomicReference<>();
  private final ActorExecutor actorTaskExecutor;

  public ActorScheduler(ActorSchedulerBuilder builder) {
    state.set(SchedulerState.NEW);
    actorTaskExecutor = builder.getActorExecutor();
  }

  /**
   * Submits an non-blocking, CPU-bound actor.
   *
   * @param actor the actor to submit
   */
  public ActorFuture<Void> submitActor(Actor actor) {
    return actorTaskExecutor.submitCpuBound(actor.actor.task);
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
  public ActorFuture<Void> submitActor(Actor actor, int schedulingHints) {
    final ActorTask task = actor.actor.task;

    final ActorFuture<Void> startingFuture;
    if (SchedulingHints.isCpuBound(schedulingHints)) {
      task.setPriority(SchedulingHints.getPriority(schedulingHints));
      startingFuture = actorTaskExecutor.submitCpuBound(task);
    } else {
      startingFuture = actorTaskExecutor.submitIoBoundTask(task);
    }
    return startingFuture;
  }

  public void start() {
    if (state.compareAndSet(SchedulerState.NEW, SchedulerState.RUNNING)) {
      actorTaskExecutor.start();
    } else {
      throw new IllegalStateException("Cannot start scheduler already started.");
    }
  }

  public Future<Void> stop() {
    if (state.compareAndSet(SchedulerState.RUNNING, SchedulerState.TERMINATING)) {

      return actorTaskExecutor
          .closeAsync()
          .thenRun(
              () -> {
                state.set(SchedulerState.TERMINATED);
              });
    } else {
      throw new IllegalStateException("Cannot stop scheduler not running");
    }
  }

  public void setBlockingTasksShutdownTime(Duration shutdownTime) {
    actorTaskExecutor.setBlockingTasksShutdownTime(shutdownTime);
  }

  public static ActorSchedulerBuilder newActorScheduler() {
    return new ActorSchedulerBuilder();
  }

  public static ActorScheduler newDefaultActorScheduler() {
    return new ActorSchedulerBuilder().build();
  }

  public static class ActorSchedulerBuilder {
    private final double[] priorityQuotas = new double[] {0.60, 0.30, 0.10};
    private String schedulerName = "";
    private ActorClock actorClock;
    private int cpuBoundThreadsCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    private ActorThreadGroup cpuBoundActorGroup;
    private int ioBoundThreadsCount = 2;
    private ActorThreadGroup ioBoundActorGroup;

    private ActorThreadFactory actorThreadFactory;
    private ThreadPoolExecutor blockingTasksRunner;
    private Duration blockingTasksShutdownTime = Duration.ofSeconds(15);
    private ActorExecutor actorExecutor;

    private ActorTimerQueue actorTimerQueue;

    public String getSchedulerName() {
      return schedulerName;
    }

    public ActorSchedulerBuilder setSchedulerName(String schedulerName) {
      this.schedulerName = schedulerName;
      return this;
    }

    public ActorClock getActorClock() {
      return actorClock;
    }

    public ActorSchedulerBuilder setActorClock(ActorClock actorClock) {
      this.actorClock = actorClock;
      return this;
    }

    public ActorTimerQueue getActorTimerQueue() {
      return actorTimerQueue;
    }

    public ActorSchedulerBuilder setActorTimerQueue(ActorTimerQueue actorTimerQueue) {
      this.actorTimerQueue = actorTimerQueue;
      return this;
    }

    public int getCpuBoundActorThreadCount() {
      return cpuBoundThreadsCount;
    }

    public ActorSchedulerBuilder setCpuBoundActorThreadCount(int actorThreadCount) {
      this.cpuBoundThreadsCount = actorThreadCount;
      return this;
    }

    public int getIoBoundActorThreadCount() {
      return ioBoundThreadsCount;
    }

    public ActorSchedulerBuilder setIoBoundActorThreadCount(int ioBoundActorsThreadCount) {
      this.ioBoundThreadsCount = ioBoundActorsThreadCount;
      return this;
    }

    public double[] getPriorityQuotas() {
      return Arrays.copyOf(priorityQuotas, priorityQuotas.length);
    }

    public ActorThreadFactory getActorThreadFactory() {
      return actorThreadFactory;
    }

    public ActorSchedulerBuilder setActorThreadFactory(ActorThreadFactory actorThreadFactory) {
      this.actorThreadFactory = actorThreadFactory;
      return this;
    }

    public ThreadPoolExecutor getBlockingTasksRunner() {
      return blockingTasksRunner;
    }

    public Duration getBlockingTasksShutdownTime() {
      return blockingTasksShutdownTime;
    }

    public ActorSchedulerBuilder setBlockingTasksShutdownTime(Duration blockingTasksShutdownTime) {
      this.blockingTasksShutdownTime = blockingTasksShutdownTime;
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

    private void initActorThreadFactory() {
      if (actorThreadFactory == null) {
        actorThreadFactory = new DefaultActorThreadFactory();
      }
    }

    private void initBlockingTaskRunner() {
      if (blockingTasksRunner == null) {
        blockingTasksRunner =
            new ThreadPoolExecutor(
                1,
                Integer.MAX_VALUE,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new BlockingTasksThreadFactory(schedulerName));
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

    public ActorScheduler build() {
      initActorThreadFactory();
      initBlockingTaskRunner();
      initCpuBoundActorThreadGroup();
      initIoBoundActorThreadGroup();
      initActorExecutor();
      return new ActorScheduler(this);
    }
  }

  public static class DefaultActorThreadFactory implements ActorThreadFactory {
    @Override
    public ActorThread newThread(
        String name,
        int id,
        ActorThreadGroup threadGroup,
        TaskScheduler taskScheduler,
        ActorClock clock,
        ActorTimerQueue timerQueue) {
      return new ActorThread(name, id, threadGroup, taskScheduler, clock, timerQueue);
    }
  }

  public static class BlockingTasksThreadFactory implements ThreadFactory {
    final AtomicLong idGenerator = new AtomicLong();
    private final String schedulerName;

    public BlockingTasksThreadFactory(String schedulerName) {
      this.schedulerName = schedulerName;
    }

    @Override
    public Thread newThread(Runnable r) {
      final Thread thread = new Thread(r);
      thread.setName(
          "zb-blocking-task-runner-" + idGenerator.incrementAndGet() + "-" + schedulerName);
      return thread;
    }
  }

  public interface ActorThreadFactory {
    ActorThread newThread(
        String name,
        int id,
        ActorThreadGroup threadGroup,
        TaskScheduler taskScheduler,
        ActorClock clock,
        ActorTimerQueue timerQueue);
  }

  private enum SchedulerState {
    NEW,
    RUNNING,
    TERMINATING,
    TERMINATED // scheduler is not reusable
  }
}

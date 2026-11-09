/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.ActorTask.ActorLifecyclePhase;
import io.camunda.zeebe.scheduler.ActorTask.TaskSchedulingState;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.slf4j.MDC;

/**
 * SPIKE: runs one actor on its own virtual thread, so that the JDK's virtual-thread scheduler (a
 * work-stealing {@link java.util.concurrent.ForkJoinPool}) replaces {@link WorkStealingGroup}.
 *
 * <p>The actor's {@link ActorTask} state machine is unchanged: whenever the platform scheduler
 * would append the task to a queue, this runner is woken up instead. Per-thread state (timers,
 * clock, job pool) lives in an {@link ActorThread} object that is never started and serves as this
 * actor's context; {@link ActorThread#current()} returns it on the virtual thread.
 *
 * <p>Enabled by default on the spike branch; {@code -Dzeebe.actor.virtualThreads=false} restores
 * the platform scheduler.
 */
final class VirtualActorRunner implements Runnable {
  static final boolean ENABLED =
      Boolean.parseBoolean(System.getProperty("zeebe.actor.virtualThreads", "true"));
  // optional busy-wait before parking, in nanoseconds (0 = park immediately)
  private static final long SPIN_NANOS = Long.getLong("zeebe.actor.virtualSpinNanos", 0L);

  private final ActorTask task;
  private final ActorThread context;
  private final ActorClock clock;
  private final ActorTimerQueue timers;
  private final String schedulerName;
  private final Thread thread;
  private volatile boolean pending;
  private long nextDeadline;

  VirtualActorRunner(
      final ActorTask task,
      final ActorThreadGroup group,
      final ActorClock sharedClock,
      final ActorMetrics metrics) {
    this.task = task;
    clock = sharedClock != null ? sharedClock : new DefaultActorClock();
    context =
        new ActorThread(
            "vactor-" + task.getName(),
            -1,
            group,
            null,
            clock,
            null,
            metrics,
            NoOpIdleStrategy.INSTANCE);
    context.currentTask = task;
    timers = context.timerJobQueue;
    schedulerName = group.getSchedulerName();
    thread = Thread.ofVirtual().name("zb-vactor-" + task.getName()).unstarted(this);
  }

  void start() {
    thread.start();
  }

  /** Called wherever the platform scheduler would append the task to a queue. */
  void wakeup() {
    pending = true;
    LockSupport.unpark(thread);
  }

  @Override
  public void run() {
    ActorThread.VIRTUAL_CONTEXT.set(context);
    MDC.put("actor-scheduler", schedulerName);
    try {
      while (task.virtualRunner == this) {
        context.submittedCallbacks.drain(context);
        clock.update();
        timers.processExpiredTimers(clock);

        if (pending) {
          pending = false;
          if (context.executeOnVirtualThread()) {
            // same as a platform thread re-enqueueing the task: let other virtual threads run
            task.resubmit();
            Thread.yield();
          } else if (isFinished()) {
            return;
          }
        } else {
          park();
        }
      }
    } finally {
      ActorThread.VIRTUAL_CONTEXT.remove();
    }
  }

  private boolean isFinished() {
    final ActorLifecyclePhase phase = task.getLifecyclePhase();
    return (phase == ActorLifecyclePhase.CLOSED || phase == ActorLifecyclePhase.FAILED)
        && task.getState() == TaskSchedulingState.NOT_SCHEDULED;
  }

  private void park() {
    if (SPIN_NANOS > 0) {
      final long end = System.nanoTime() + SPIN_NANOS;
      while (System.nanoTime() < end) {
        if (pending) {
          return;
        }
        Thread.onSpinWait();
      }
    }

    if (timers.timerCount() == 0) {
      LockSupport.park(this);
      return;
    }

    nextDeadline = Long.MAX_VALUE;
    timers.forEach((deadline, timerId) -> nextDeadline = Math.min(nextDeadline, deadline));
    final long waitMillis = nextDeadline - clock.getTimeMillis();
    if (waitMillis > 0) {
      LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(waitMillis));
    }
  }
}

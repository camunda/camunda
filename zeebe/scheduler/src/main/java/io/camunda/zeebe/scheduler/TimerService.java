/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.util.Loggers;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * SPIKE: one timer thread per scheduler, replacing the timer wheel of every actor thread.
 *
 * <p>Expiring a timer only wakes the actor up ({@link TimerSubscription#onTimerExpired}); the
 * timer's job then runs on whichever worker picks the actor. Timers therefore no longer depend on
 * the worker that scheduled them being awake or unblocked, and workers do not need to wake up to
 * poll timers. Cancellation is lazy: a cancelled subscription ignores its expiry.
 */
final class TimerService implements Runnable {
  private final ConcurrentLinkedQueue<Entry> submitted = new ConcurrentLinkedQueue<>();
  private final PriorityQueue<Entry> timers = new PriorityQueue<>();
  private final ActorClock clock;
  private final Thread thread;
  private volatile long nextWakeupMillis = Long.MAX_VALUE;
  private volatile boolean running = true;
  private long sequence;

  TimerService(final ActorClock clock, final String schedulerName) {
    this.clock = clock != null ? clock : new DefaultActorClock();
    thread = Thread.ofPlatform().daemon().name(schedulerName + "-timers").unstarted(this);
  }

  void start() {
    thread.start();
  }

  void stop() {
    running = false;
    LockSupport.unpark(thread);
  }

  /** Thread-safe; called from actor jobs. */
  void schedule(final TimerSubscription timer, final ActorClock callerClock) {
    final long deadline = timer.getDeadline(callerClock);
    submitted.add(new Entry(deadline, timer));
    if (deadline < nextWakeupMillis) {
      LockSupport.unpark(thread);
    }
  }

  @Override
  public void run() {
    while (running) {
      try {
        Entry entry;
        while ((entry = submitted.poll()) != null) {
          entry.seq = sequence++;
          timers.add(entry);
        }

        clock.update();
        final long now = clock.getTimeMillis();
        while ((entry = timers.peek()) != null && entry.deadline <= now) {
          timers.poll();
          entry.timer.onTimerExpired(TimeUnit.MILLISECONDS, now);
        }

        final long next = entry == null ? Long.MAX_VALUE : entry.deadline;
        nextWakeupMillis = next;
        // a timer submitted after draining, but before nextWakeupMillis was published, did not
        // unpark us: look again before parking
        if (!submitted.isEmpty()) {
          continue;
        }
        if (next == Long.MAX_VALUE) {
          LockSupport.park(this);
        } else {
          LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(Math.max(1, next - now)));
        }
      } catch (final Throwable t) {
        Loggers.ACTOR_LOGGER.error("Unexpected error in actor timer thread", t);
      }
    }
  }

  private static final class Entry implements Comparable<Entry> {
    final long deadline;
    final TimerSubscription timer;
    long seq;

    Entry(final long deadline, final TimerSubscription timer) {
      this.deadline = deadline;
      this.timer = timer;
    }

    @Override
    public int compareTo(final Entry o) {
      final int c = Long.compare(deadline, o.deadline);
      return c != 0 ? c : Long.compare(seq, o.seq);
    }
  }
}

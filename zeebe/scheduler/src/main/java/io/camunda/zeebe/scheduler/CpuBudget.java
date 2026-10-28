/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Caps the CPU time that the threads of one scheduler can use. A sampler thread reads the CPU time
 * of every thread in each period and charges it to the budget, then grants the budget of the next
 * period. While the balance is not positive, the actor threads of the scheduler do not take new
 * tasks.
 *
 * <p>A task that is already running is not interrupted, so the balance can go below zero. This debt
 * is carried to the next periods. Unused budget is not carried: the balance never exceeds one
 * period.
 */
public final class CpuBudget implements AutoCloseable {
  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

  private final long periodNanos;
  private final long grantNanos;
  private volatile long balanceNanos;
  private final AtomicLong chargedNanos = new AtomicLong();
  private volatile long usedNanos;
  private volatile long throttledPeriods;
  private volatile boolean running = true;
  private Thread sampler;
  // thread -> CPU time at the last sample; a terminated thread is dropped at the next sample
  private final Map<Thread, Long> lastCpuNanos = new ConcurrentHashMap<>();
  private final ReentrantLock lock = new ReentrantLock();
  // signalled when the budget is refilled, and when the waiting threads must stop
  private final Condition refilled = lock.newCondition();

  /**
   * @param cores CPU time that the threads can use in each period, in cores. May be fractional.
   * @param period how often the sampler charges the used CPU time and grants a new budget
   */
  public CpuBudget(final double cores, final Duration period) {
    periodNanos = period.toNanos();
    grantNanos = (long) (cores * periodNanos);
    balanceNanos = grantNanos;
  }

  /** Returns true if the threads of the scheduler must not take a new task. */
  public boolean isExhausted() {
    return balanceNanos <= 0;
  }

  /**
   * Blocks the calling thread until the budget is not exhausted, the budget is closed, or {@code
   * stop} is true after {@link #wakeWaiters()}. Returns at once if the budget is not exhausted.
   */
  public void awaitRefill(final BooleanSupplier stop) throws InterruptedException {
    lock.lock();
    try {
      while (balanceNanos <= 0 && running && !stop.getAsBoolean()) {
        refilled.await();
      }
    } finally {
      lock.unlock();
    }
  }

  /** Makes the threads in {@link #awaitRefill} check their stop condition. */
  public void wakeWaiters() {
    lock.lock();
    try {
      refilled.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Charges CPU time that was used outside of the sampled threads, for example by a task on a
   * thread that is not an actor thread. Can be called from any thread.
   */
  public void charge(final long cpuNanos) {
    chargedNanos.addAndGet(cpuNanos);
  }

  /**
   * Starts sampling the CPU time of the given threads. These are the threads that stop when the
   * budget is exhausted.
   */
  public void start(final List<? extends Thread> threads) {
    threads.forEach(this::register);
    sampler = new Thread(this::sample, "cpu-budget-sampler");
    sampler.setDaemon(true);
    sampler.start();
  }

  /**
   * Charges the CPU time of the thread to the budget until the thread ends. The thread is never
   * stopped, so the charge only reduces what the actor threads get. Can be called from any thread,
   * also before the thread starts and after {@link #start(List)}.
   */
  public void register(final Thread thread) {
    // -1 if the thread is not alive yet
    lastCpuNanos.put(thread, Math.max(0, THREADS.getThreadCpuTime(thread.threadId())));
  }

  private void sample() {
    while (running) {
      LockSupport.parkNanos(periodNanos);

      long periodUsedNanos = 0;
      for (final var thread : lastCpuNanos.keySet()) {
        // -1 if the thread is not alive (yet or anymore)
        final long cpuNanos = THREADS.getThreadCpuTime(thread.threadId());
        if (cpuNanos >= 0) {
          periodUsedNanos += cpuNanos - lastCpuNanos.put(thread, cpuNanos);
        } else if (thread.getState() == Thread.State.TERMINATED) {
          lastCpuNanos.remove(thread);
        }
      }

      periodUsedNanos += chargedNanos.getAndSet(0);
      usedNanos += periodUsedNanos;
      final long next = Math.min(balanceNanos - periodUsedNanos + grantNanos, grantNanos);
      if (next <= 0) {
        throttledPeriods++;
      }
      final boolean wasExhausted = balanceNanos <= 0;
      balanceNanos = next;
      if (wasExhausted && next > 0) {
        lock.lock();
        try {
          refilled.signalAll();
        } finally {
          lock.unlock();
        }
      }
    }
  }

  /** CPU time of all threads charged so far. */
  public long usedNanos() {
    return usedNanos;
  }

  /** Count of periods that ended with an exhausted budget. */
  public long throttledPeriods() {
    return throttledPeriods;
  }

  @Override
  public void close() {
    running = false;
    if (sampler != null) {
      LockSupport.unpark(sampler);
    }
    lock.lock();
    try {
      refilled.signalAll();
    } finally {
      lock.unlock();
    }
  }
}

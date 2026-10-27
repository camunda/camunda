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
import java.util.concurrent.locks.LockSupport;

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
  private volatile long usedNanos;
  private volatile long throttledPeriods;
  private volatile boolean running = true;
  private Thread sampler;

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
  boolean isExhausted() {
    return balanceNanos <= 0;
  }

  /** Starts sampling the CPU time of the given threads. */
  public void start(final List<? extends Thread> threads) {
    sampler = new Thread(() -> sample(threads), "cpu-budget-sampler");
    sampler.setDaemon(true);
    sampler.start();
  }

  private void sample(final List<? extends Thread> threads) {
    final var lastCpuNanos = new long[threads.size()];
    while (running) {
      LockSupport.parkNanos(periodNanos);

      long periodUsedNanos = 0;
      for (int i = 0; i < lastCpuNanos.length; i++) {
        // -1 if the thread is not alive (yet or anymore)
        final long cpuNanos = THREADS.getThreadCpuTime(threads.get(i).threadId());
        if (cpuNanos >= 0) {
          periodUsedNanos += cpuNanos - lastCpuNanos[i];
          lastCpuNanos[i] = cpuNanos;
        }
      }

      usedNanos += periodUsedNanos;
      final long next = Math.min(balanceNanos - periodUsedNanos + grantNanos, grantNanos);
      if (next <= 0) {
        throttledPeriods++;
      }
      balanceNanos = next;
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
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.CpuBudget;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * A thread pool whose tasks count for a {@link CpuBudget}. A task waits before it starts while the
 * budget is exhausted, and its CPU time is charged to the budget when it ends.
 *
 * <p>The CPU time of a task is only charged when the task ends. A task that runs for a long time is
 * not stopped by the budget. Cancelling its future interrupts it, as usual.
 */
final class BudgetedThreadPoolExecutor extends ThreadPoolExecutor {
  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
  private static final long WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

  private final CpuBudget budget;
  private final ThreadLocal<Long> startCpuNanos = new ThreadLocal<>();

  BudgetedThreadPoolExecutor(
      final int threads,
      final long keepAliveSeconds,
      final BlockingQueue<Runnable> queue,
      final ThreadFactory threadFactory,
      final CpuBudget budget) {
    super(threads, threads, keepAliveSeconds, TimeUnit.SECONDS, queue, threadFactory);
    this.budget = budget;
  }

  @Override
  protected void beforeExecute(final Thread thread, final Runnable task) {
    // a cancelled task, for example one that timed out in the queue, must not wait
    while (budget.isExhausted() && !isCancelled(task) && !isShutdown()) {
      LockSupport.parkNanos(WAIT_NANOS);
    }
    startCpuNanos.set(THREADS.getCurrentThreadCpuTime());
  }

  @Override
  protected void afterExecute(final Runnable task, final Throwable error) {
    budget.charge(THREADS.getCurrentThreadCpuTime() - startCpuNanos.get());
  }

  private static boolean isCancelled(final Runnable task) {
    return task instanceof final Future<?> future && future.isCancelled();
  }
}

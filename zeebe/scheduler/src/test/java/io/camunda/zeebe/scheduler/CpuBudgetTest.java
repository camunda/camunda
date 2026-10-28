/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class CpuBudgetTest {

  private static final Duration PERIOD = Duration.ofMillis(20);
  private static final long MEASURE_MILLIS = 3_000;

  private final List<ActorScheduler> schedulers = new ArrayList<>();
  private final List<CpuBudget> budgets = new ArrayList<>();
  private final List<Thread> outsideThreads = new ArrayList<>();

  @AfterEach
  void tearDown() throws Exception {
    budgets.forEach(CpuBudget::close);
    outsideThreads.forEach(Thread::interrupt);
    for (final var scheduler : schedulers) {
      scheduler.close();
    }
  }

  @Test
  void shouldLimitCpuTimeOfSaturatedPoolToBudget() throws Exception {
    // given - a pool of 2 threads, both kept busy, with a budget of half a core
    final var scheduler = startSaturatedPool(0.5, 2);

    // when
    final long usedNanos = measureThreadCpuNanos(scheduler.getThreads());

    // then
    final double cores = (double) usedNanos / Duration.ofMillis(MEASURE_MILLIS).toNanos();
    System.out.printf("RESULT cap cores=%.3f%n", cores);
    assertThat(cores).isBetween(0.4, 0.6);
  }

  @Test
  void shouldNotLimitPoolWithoutBudget() throws Exception {
    // given - the same pool without a budget
    final var scheduler = newScheduler(null, 2);
    saturate(scheduler, 2);
    final var threads = scheduler.getThreads();

    // when
    final long usedNanos = measureThreadCpuNanos(threads);

    // then
    final double cores = (double) usedNanos / Duration.ofMillis(MEASURE_MILLIS).toNanos();
    assertThat(cores).isGreaterThan(1.5);
  }

  @Test
  void shouldDivideCpuTimeBetweenPoolsByBudget() throws Exception {
    // given - two saturated pools with the budgets of tenants with the weights 2 and 1
    final var heavy = startSaturatedPool(1.0, 2).getThreads();
    final var light = startSaturatedPool(0.5, 2).getThreads();

    // when
    final long heavyBefore = cpuNanos(heavy);
    final long lightBefore = cpuNanos(light);
    Thread.sleep(MEASURE_MILLIS);
    final long heavyUsed = cpuNanos(heavy) - heavyBefore;
    final long lightUsed = cpuNanos(light) - lightBefore;
    final double ratio = (double) heavyUsed / lightUsed;
    System.out.printf(
        "RESULT heavy=%d ms light=%d ms ratio=%.3f%n",
        heavyUsed / 1_000_000, lightUsed / 1_000_000, ratio);

    // then
    assertThat(ratio).isBetween(1.9, 2.1);
  }

  @Test
  void shouldChargeRegisteredThreadWithoutStoppingIt() throws Exception {
    // given - a saturated pool with a budget of half a core, and a thread that is not part of the
    // pool and uses about a quarter of a core
    final var budget = new CpuBudget(0.5, PERIOD);
    budgets.add(budget);
    final var scheduler = newScheduler(budget, 2);
    budget.start(scheduler.getThreads());
    saturate(scheduler, 2);
    final var outside = startQuarterCoreThread();
    budget.register(outside);

    // when
    final long poolBefore = cpuNanos(scheduler.getThreads());
    final long outsideBefore = threadCpuNanos(outside);
    Thread.sleep(MEASURE_MILLIS);
    final long window = Duration.ofMillis(MEASURE_MILLIS).toNanos();
    final double poolCores = (double) (cpuNanos(scheduler.getThreads()) - poolBefore) / window;
    final double outsideCores = (double) (threadCpuNanos(outside) - outsideBefore) / window;
    System.out.printf("RESULT pool=%.3f outside=%.3f%n", poolCores, outsideCores);

    // then - the thread keeps its CPU time and the pool gets what is left of the budget
    assertThat(outsideCores).isBetween(0.15, 0.35);
    assertThat(poolCores + outsideCores).isBetween(0.4, 0.6);
  }

  /**
   * Documents a known limitation: an exhausted budget also stops actors that are not part of the
   * data path, such as the ones that handle role changes or health checks.
   */
  @Test
  void shouldDelayUnrelatedActorWhileBudgetIsExhausted() throws Exception {
    // given - a budget of 20% of a core, overshot by a busy actor on the only thread
    final var period = Duration.ofMillis(100);
    final var budget = new CpuBudget(0.2, period);
    budgets.add(budget);
    final var scheduler = newScheduler(budget, 1);
    budget.start(scheduler.getThreads());
    saturate(scheduler, 1);
    final var other = new Actor() {};
    scheduler.submitActor(other).join();

    // when
    long maxLatencyNanos = 0;
    for (int i = 0; i < 30; i++) {
      final long start = System.nanoTime();
      other.actor.call(() -> {}).join();
      maxLatencyNanos = Math.max(maxLatencyNanos, System.nanoTime() - start);
      Thread.sleep(37);
    }

    // then
    assertThat(Duration.ofNanos(maxLatencyNanos)).isGreaterThan(period);
  }

  private ActorScheduler startSaturatedPool(final double cores, final int threads) {
    final var budget = new CpuBudget(cores, PERIOD);
    budgets.add(budget);
    final var scheduler = newScheduler(budget, threads);
    budget.start(scheduler.getThreads());
    saturate(scheduler, threads);
    return scheduler;
  }

  private static long cpuNanos(final List<ActorThread> threads) {
    final var bean = java.lang.management.ManagementFactory.getThreadMXBean();
    return threads.stream().mapToLong(t -> bean.getThreadCpuTime(t.threadId())).sum();
  }

  private static long threadCpuNanos(final Thread thread) {
    return java.lang.management.ManagementFactory.getThreadMXBean()
        .getThreadCpuTime(thread.threadId());
  }

  /** Spins for 5 ms of each 20 ms period. */
  private Thread startQuarterCoreThread() {
    final var thread =
        new Thread(
            () -> {
              while (!Thread.currentThread().isInterrupted()) {
                final long end = System.nanoTime() + 5_000_000;
                while (System.nanoTime() < end) {
                  Thread.onSpinWait();
                }
                java.util.concurrent.locks.LockSupport.parkNanos(15_000_000);
              }
            });
    thread.setDaemon(true);
    thread.start();
    outsideThreads.add(thread);
    return thread;
  }

  private static long measureThreadCpuNanos(final List<ActorThread> threads)
      throws InterruptedException {
    final long before = cpuNanos(threads);
    Thread.sleep(MEASURE_MILLIS);
    return cpuNanos(threads) - before;
  }

  private ActorScheduler newScheduler(final CpuBudget budget, final int threads) {
    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(threads)
            .setIoBoundActorThreadCount(1)
            .setCpuBudget(budget)
            .build();
    scheduler.start();
    schedulers.add(scheduler);
    return scheduler;
  }

  private static void saturate(final ActorScheduler scheduler, final int actors) {
    for (int i = 0; i < actors; i++) {
      scheduler.submitActor(new BusyActor()).join();
    }
  }

  /** Burns CPU in 1 ms jobs, so that the thread can take another task between the jobs. */
  private static final class BusyActor extends Actor {
    @Override
    protected void onActorStarted() {
      burn();
    }

    private void burn() {
      final long end = System.nanoTime() + 1_000_000;
      while (System.nanoTime() < end) {
        Thread.onSpinWait();
      }
      actor.submit(this::burn);
    }
  }
}

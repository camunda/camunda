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

  @AfterEach
  void tearDown() throws Exception {
    budgets.forEach(CpuBudget::close);
    for (final var scheduler : schedulers) {
      scheduler.close();
    }
  }

  @Test
  void shouldLimitCpuTimeOfSaturatedPoolToBudget() throws Exception {
    // given - a pool of 2 threads, both kept busy, with a budget of half a core
    final var budget = startSaturatedPool(0.5, 2);

    // when
    final long usedNanos = measureUsedNanos(budget);

    // then
    final double cores = (double) usedNanos / Duration.ofMillis(MEASURE_MILLIS).toNanos();
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
    final var heavy = startSaturatedPool(1.0, 2);
    final var light = startSaturatedPool(0.5, 2);

    // when
    final long heavyBefore = heavy.usedNanos();
    final long lightBefore = light.usedNanos();
    Thread.sleep(MEASURE_MILLIS);
    final double ratio =
        (double) (heavy.usedNanos() - heavyBefore) / (light.usedNanos() - lightBefore);

    // then
    assertThat(ratio).isBetween(1.7, 2.3);
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

  private CpuBudget startSaturatedPool(final double cores, final int threads) {
    final var budget = new CpuBudget(cores, PERIOD);
    budgets.add(budget);
    final var scheduler = newScheduler(budget, threads);
    budget.start(scheduler.getThreads());
    saturate(scheduler, threads);
    return budget;
  }

  private long measureUsedNanos(final CpuBudget budget) throws InterruptedException {
    final long before = budget.usedNanos();
    Thread.sleep(MEASURE_MILLIS);
    return budget.usedNanos() - before;
  }

  private static long measureThreadCpuNanos(final List<ActorThread> threads)
      throws InterruptedException {
    final var bean = java.lang.management.ManagementFactory.getThreadMXBean();
    final long before = threads.stream().mapToLong(t -> bean.getThreadCpuTime(t.threadId())).sum();
    Thread.sleep(MEASURE_MILLIS);
    return threads.stream().mapToLong(t -> bean.getThreadCpuTime(t.threadId())).sum() - before;
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

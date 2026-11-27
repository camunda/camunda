/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.cpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ThreadGroupCpuSamplerTest {

  private static final String GROUP_ID = "group-a";
  private static final Duration PERIOD = Duration.ofMillis(100);
  private static final Duration MAX_REPAY_TIME = Duration.ofSeconds(1);

  private final Map<Long, Long> fakeCpuNanos = new ConcurrentHashMap<>();
  private final CountDownLatch release = new CountDownLatch(1);
  private final List<Thread> threads = new ArrayList<>();
  private long nowNanos;
  private boolean failReads;
  private final ThreadGroupCpuSampler sampler =
      new ThreadGroupCpuSampler(PERIOD, MAX_REPAY_TIME, this::fakeCpuNanosOf, 64, () -> nowNanos);

  @AfterEach
  void tearDown() throws InterruptedException {
    sampler.close();
    release.countDown();
    for (final var thread : threads) {
      thread.join(10_000);
    }
  }

  @Test
  void shouldChargeRealCpuTimeOfAllThreadsOfGroup() throws Exception {
    try (final var realSampler = new ThreadGroupCpuSampler(PERIOD, MAX_REPAY_TIME)) {
      // given
      final var group = new ThreadGroup(GROUP_ID);
      final var burn = Duration.ofMillis(200);
      final var burned = new CountDownLatch(2);
      realSampler.register(GROUP_ID, group, 1);
      for (int i = 0; i < 2; i++) {
        startThread(
            group,
            () -> {
              final var bean = ManagementFactory.getThreadMXBean();
              final var target = bean.getCurrentThreadCpuTime() + burn.toNanos();
              while (bean.getCurrentThreadCpuTime() < target) {
                Thread.onSpinWait();
              }
              burned.countDown();
              awaitRelease();
            });
      }
      assertThat(burned.await(30, TimeUnit.SECONDS)).isTrue();

      // when
      realSampler.sample();

      // then
      final var expected = burn.multipliedBy(2);
      assertThat(realSampler.usage(GROUP_ID).orElseThrow().usedNanos())
          .isBetween(expected.toNanos(), expected.plusMillis(100).toNanos());
    }
  }

  @Test
  void shouldChargeAllThreadsWhenTheThreadBufferMustGrow() {
    // given
    try (final var tiny =
        new ThreadGroupCpuSampler(
            PERIOD, MAX_REPAY_TIME, this::fakeCpuNanosOf, 1, () -> nowNanos)) {
      final var group = new ThreadGroup(GROUP_ID);
      final var first = startParkedThread(group);
      final var second = startParkedThread(group);
      tiny.register(GROUP_ID, group, 1);

      // when
      setCpu(first, millis(10));
      setCpu(second, millis(20));
      nowNanos += PERIOD.toNanos();
      tiny.sample();

      // then
      assertThat(tiny.usage(GROUP_ID).orElseThrow().usedNanos()).isEqualTo(millis(30));
    }
  }

  @Test
  void shouldMeasureFromRegistrationThenChargeOnlyNewCpuTime() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var busy = startParkedThread(group);
    final var idle = startParkedThread(group);
    setCpu(busy, millis(500));
    nowNanos = millis(50);
    sampler.register(GROUP_ID, group, 1);
    assertThat(sampler.usage(GROUP_ID)).isEmpty();

    // when
    setCpu(busy, millis(520));
    sampleAfter(PERIOD.dividedBy(2));
    final var first = sampler.usage(GROUP_ID).orElseThrow();

    final var newcomer = startParkedThread(group);
    setCpu(idle, millis(5));
    setCpu(newcomer, millis(40));
    sampleAfter(PERIOD);
    final var second = sampler.usage(GROUP_ID).orElseThrow();

    // then
    assertThat(first).isEqualTo(new CpuUsage(millis(20), millis(50), 0));
    assertThat(second.usedNanos()).isEqualTo(millis(45));
  }

  @Test
  void shouldChargeEachThreadToTheRegisteredGroupItBelongsTo() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var otherGroup = new ThreadGroup("group-b");
    final var inSubgroup = startParkedThread(new ThreadGroup(group, "subgroup"));
    final var inOtherGroup = startParkedThread(otherGroup);
    final var outside = startParkedThread(null);
    sampler.register(GROUP_ID, group, 1);
    sampler.register("group-b", otherGroup, 1);

    // when
    setCpu(inSubgroup, millis(10));
    setCpu(inOtherGroup, millis(20));
    setCpu(outside, millis(100));
    sampleAfter(PERIOD);

    // then
    assertThat(sampler.usage(GROUP_ID).orElseThrow().usedNanos()).isEqualTo(millis(10));
    assertThat(sampler.usage("group-b").orElseThrow().usedNanos()).isEqualTo(millis(20));
  }

  @Test
  void shouldChargeAThreadOnlyFromItsFirstReadAfterAFailedOne() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var readable = startParkedThread(group);
    final var unreadable = startParkedThread(group);
    sampler.register(GROUP_ID, group, 1);

    // when - e.g. thread CPU time measurement is disabled, then enabled again
    setCpu(readable, millis(10));
    setCpu(unreadable, -1);
    sampleAfter(PERIOD);
    final var whileUnreadable = sampler.usage(GROUP_ID).orElseThrow();
    setCpu(unreadable, millis(500));
    sampleAfter(PERIOD);
    final var firstRead = sampler.usage(GROUP_ID).orElseThrow();
    setCpu(unreadable, millis(530));
    sampleAfter(PERIOD);
    final var secondRead = sampler.usage(GROUP_ID).orElseThrow();

    // then
    assertThat(whileUnreadable.usedNanos()).isEqualTo(millis(10));
    assertThat(firstRead.usedNanos()).isZero();
    assertThat(secondRead.usedNanos()).isEqualTo(millis(30));
  }

  @Test
  void shouldCarryDebtButNotBankUnusedBudget() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    sampler.register(GROUP_ID, group, 1);
    sampleAfter(PERIOD);
    final var afterIdle = sampler.usage(GROUP_ID).orElseThrow();

    // when
    setCpu(thread, millis(150));
    sampleAfter(PERIOD);
    final var afterOvershoot = sampler.usage(GROUP_ID).orElseThrow();
    setCpu(thread, millis(250));
    sampleAfter(PERIOD);
    final var afterExactBudget = sampler.usage(GROUP_ID).orElseThrow();
    setCpu(thread, millis(280));
    sampleAfter(PERIOD);
    final var afterUndershoot = sampler.usage(GROUP_ID).orElseThrow();

    // then
    assertThat(afterIdle.debtNanos()).isZero();
    assertThat(afterOvershoot.debtNanos()).isEqualTo(millis(50));
    assertThat(afterExactBudget.debtNanos()).isEqualTo(millis(50));
    assertThat(afterUndershoot.debtNanos()).isZero();
  }

  @Test
  void shouldCapDebtAtWhatTheGroupRepaysWithinTheMaxRepayTime() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    sampler.register(GROUP_ID, group, 0.5);

    // when
    setCpu(thread, millis(5_000));
    sampleAfter(PERIOD);
    final var afterOvershoot = sampler.usage(GROUP_ID).orElseThrow();
    sampleAfter(MAX_REPAY_TIME);
    final var afterMaxRepayTime = sampler.usage(GROUP_ID).orElseThrow();

    // then - 1 s of repay time at a quota of 0.5 CPUs
    assertThat(afterOvershoot.debtNanos()).isEqualTo(millis(500));
    assertThat(afterMaxRepayTime.debtNanos()).isZero();
  }

  @Test
  void shouldApplyFractionalQuotaToAllElapsedTimeWhenSampleIsLate() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    sampler.register(GROUP_ID, group, 1.5);
    sampleAfter(PERIOD);

    // when
    setCpu(thread, millis(250));
    sampleAfter(PERIOD.multipliedBy(3));

    // then
    assertThat(sampler.usage(GROUP_ID).orElseThrow())
        .isEqualTo(new CpuUsage(millis(250), millis(450), 0));
  }

  @Test
  void shouldApplyANewQuotaWithoutResettingCpuTimeOrDebt() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    sampler.register(GROUP_ID, group, 1);
    setCpu(thread, millis(150));
    sampleAfter(PERIOD);

    // when
    setCpu(thread, millis(200));
    sampler.register(GROUP_ID, group, 2);
    setCpu(thread, millis(320));
    sampleAfter(PERIOD);

    // then - 50 ms debt + 170 ms used - 200 ms budget
    assertThat(sampler.usage(GROUP_ID).orElseThrow())
        .isEqualTo(new CpuUsage(millis(170), millis(200), millis(20)));
  }

  @Test
  void shouldRegisterFromScratchWhenTheBaselineReadFails() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    setCpu(thread, millis(500));
    failReads = true;
    assertThatThrownBy(() -> sampler.register(GROUP_ID, group, 1))
        .isInstanceOf(IllegalStateException.class);

    // when
    failReads = false;
    sampler.register(GROUP_ID, group, 1);
    setCpu(thread, millis(530));
    sampleAfter(PERIOD);

    // then
    assertThat(sampler.usage(GROUP_ID).orElseThrow())
        .isEqualTo(new CpuUsage(millis(30), millis(100), 0));
  }

  @Test
  void shouldRegisterAGroupAgainAfterUnregisteringIt() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var thread = startParkedThread(group);
    sampler.register(GROUP_ID, group, 1);
    setCpu(thread, millis(150));
    sampleAfter(PERIOD);

    // when
    sampler.unregister(GROUP_ID);
    final var afterUnregister = sampler.usage(GROUP_ID);
    sampler.register(GROUP_ID, group, 1);
    setCpu(thread, millis(160));
    sampleAfter(PERIOD);

    // then
    assertThat(afterUnregister).isEmpty();
    assertThat(sampler.usage(GROUP_ID).orElseThrow())
        .isEqualTo(new CpuUsage(millis(10), millis(100), 0));
  }

  @Test
  void shouldRejectRegisteringAnIdOrAGroupTwice() {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    sampler.register(GROUP_ID, group, 1);

    // when - then
    assertThatThrownBy(() -> sampler.register(GROUP_ID, new ThreadGroup("group-b"), 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sampler.register("group-b", group, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
  void shouldRejectAnInvalidQuota(final double quota) {
    // given
    final var group = new ThreadGroup(GROUP_ID);

    // when - then
    assertThatThrownBy(() -> sampler.register(GROUP_ID, group, quota))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the quota must be a positive number of CPUs");
  }

  @Test
  void shouldRejectCpuTimeSourceWhenMeasurementIsDisabled() {
    // given
    final var bean = mock(ThreadMXBean.class);
    when(bean.isThreadCpuTimeSupported()).thenReturn(true);
    when(bean.isThreadCpuTimeEnabled()).thenReturn(false);

    // when - then
    assertThatThrownBy(() -> ThreadGroupCpuSampler.cpuTimeSourceOf(bean))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNotChargeSamplingThreadToGroupOfCaller() throws Exception {
    // given
    final var group = new ThreadGroup(GROUP_ID);
    final var reads = new AtomicInteger();
    // every matched thread appears to use CPU between two reads
    try (final var started =
        startedSampler(
            ids -> {
              final var cpuNanos = new long[ids.length];
              Arrays.fill(cpuNanos, reads.incrementAndGet());
              return cpuNanos;
            })) {
      final var starter = startThread(group, started::start);
      assertThat(starter.join(Duration.ofSeconds(10))).isTrue();

      // when
      started.register(GROUP_ID, group, 1);

      // then
      Awaitility.await()
          .untilAsserted(
              () ->
                  assertThat(started.usage(GROUP_ID))
                      .hasValueSatisfying(usage -> assertThat(usage.usedNanos()).isZero()));
    }
  }

  @Test
  void shouldKeepSamplingAfterFailingSample() {
    // given
    final var calls = new AtomicInteger();
    try (final var flaky =
        startedSampler(
            ids -> {
              // the first call reads the baseline at registration
              final int call = calls.incrementAndGet();
              if (call == 2) {
                throw new IllegalStateException("expected failure");
              }
              final var cpuNanos = new long[ids.length];
              Arrays.fill(cpuNanos, millis(10) * call);
              return cpuNanos;
            })) {
      final var group = new ThreadGroup(GROUP_ID);
      startParkedThread(group);
      flaky.register(GROUP_ID, group, 1);

      // when
      flaky.start();

      // then
      Awaitility.await()
          .untilAsserted(
              () ->
                  assertThat(flaky.usage(GROUP_ID))
                      .hasValueSatisfying(
                          usage -> assertThat(usage.usedNanos()).isEqualTo(millis(10))));
    }
  }

  private static ThreadGroupCpuSampler startedSampler(final UnaryOperator<long[]> source) {
    return new ThreadGroupCpuSampler(
        Duration.ofMillis(10), MAX_REPAY_TIME, source, 64, System::nanoTime);
  }

  private void sampleAfter(final Duration elapsed) {
    nowNanos += elapsed.toNanos();
    sampler.sample();
  }

  private long[] fakeCpuNanosOf(final long[] ids) {
    if (failReads) {
      throw new IllegalStateException("expected failure");
    }
    final var cpuNanos = new long[ids.length];
    for (int i = 0; i < ids.length; i++) {
      cpuNanos[i] = fakeCpuNanos.getOrDefault(ids[i], 0L);
    }
    return cpuNanos;
  }

  private void setCpu(final Thread thread, final long nanos) {
    fakeCpuNanos.put(thread.threadId(), nanos);
  }

  private static long millis(final long millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }

  private Thread startParkedThread(final ThreadGroup group) {
    return startThread(group, this::awaitRelease);
  }

  private Thread startThread(final ThreadGroup group, final Runnable runnable) {
    final var thread = new Thread(group, runnable);
    threads.add(thread);
    thread.start();
    return thread;
  }

  private void awaitRelease() {
    try {
      release.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

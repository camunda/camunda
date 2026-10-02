/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CpuBudgetTest {

  private static final long INTERVAL = CpuBudget.SAMPLE_INTERVAL.toNanos();

  private double load;
  private double throttled = -1;
  private long backlog = -1;
  private int samples;

  @Test
  void shouldAllowOneMoreInstancePerIntervalWithinBudget() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 3, 0);

    // when
    final var limits = limitsOverIntervals(budget, 4);

    // then
    assertThat(limits).containsExactly(2, 3, 3, 3);
  }

  @Test
  void shouldHalveUntilPausedWhileOverBudget() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 7);

    // when
    load = 0.9;
    final var limits = limitsOverIntervals(budget, 4, 8);

    // then
    assertThat(limits).containsExactly(4, 2, 1, 0);
    assertThat(budget.backOffs()).isEqualTo(4);
  }

  @Test
  void shouldGrowSlowlyAfterBackingOff() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 7);
    load = 0.9;
    limitsOverIntervals(budget, 1, 8);

    // when
    load = 0.5;
    final var limits = limitsOverIntervals(budget, 2 * CpuBudget.REGROWTH_SAMPLES, 9);

    // then
    assertThat(limits).containsExactly(4, 4, 4, 4, 5, 5, 5, 5, 5, 6);
  }

  @Test
  void shouldGrowQuicklyBackToHalfTheLimitBeforeAPause() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 7);
    load = 0.9;
    limitsOverIntervals(budget, 4, 8);

    // when
    load = 0.5;
    final var limits = limitsOverIntervals(budget, 4 + CpuBudget.REGROWTH_SAMPLES, 12);

    // then
    assertThat(limits).containsExactly(1, 2, 3, 4, 4, 4, 4, 4, 5);
  }

  @Test
  void shouldSampleOncePerInterval() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);

    // when
    for (long now = 0; now < 3 * INTERVAL; now += INTERVAL / 10) {
      budget.inFlightLimit(now);
    }

    // then
    assertThat(samples).isEqualTo(2);
  }

  @Test
  void shouldKeepTheLimitWhileTheLoadIsUnknown() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 2);

    // when
    load = -1;
    final var limits = limitsOverIntervals(budget, 3, 3);

    // then
    assertThat(limits).containsExactly(3, 3, 3);
  }

  @Test
  void shouldHalveWhileTheContainerIsThrottledWithinTheLoadBudget() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 7);

    // when
    throttled = 0.1;
    final var limits = limitsOverIntervals(budget, 2, 8);

    // then
    assertThat(limits).containsExactly(4, 2);
  }

  @Test
  void shouldGrowWhileNotThrottledEvenIfTheLoadIsUnknown() {
    // given
    load = -1;
    throttled = 0;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);

    // when
    final var limits = limitsOverIntervals(budget, 3);

    // then
    assertThat(limits).containsExactly(2, 3, 4);
  }

  @Test
  void shouldHalveWhileAPartitionHasAProcessingBacklog() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, this::throttled, this::backlog, 0.7, 250, 32, 0);
    limitsOverIntervals(budget, 7);

    // when
    backlog = 900;
    final var limits = limitsOverIntervals(budget, 2, 8);

    // then
    assertThat(limits).containsExactly(4, 2);
  }

  private long backlog() {
    return backlog;
  }

  private double throttled() {
    return throttled;
  }

  private double sample() {
    samples++;
    return load;
  }

  private List<Integer> limitsOverIntervals(final CpuBudget budget, final int count) {
    return limitsOverIntervals(budget, count, 1);
  }

  private List<Integer> limitsOverIntervals(
      final CpuBudget budget, final int count, final int firstInterval) {
    final var limits = new ArrayList<Integer>();
    for (int i = 0; i < count; i++) {
      limits.add(budget.inFlightLimit((firstInterval + i) * INTERVAL));
    }
    return limits;
  }
}

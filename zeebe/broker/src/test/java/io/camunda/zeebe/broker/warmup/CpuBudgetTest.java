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
  private int samples;

  @Test
  void shouldAllowOneMoreInstancePerIntervalWithinBudget() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, 0.7, 3, 0);

    // when
    final var limits = limitsOverIntervals(budget, 4);

    // then
    assertThat(limits).containsExactly(2, 3, 3, 3);
  }

  @Test
  void shouldHalveUntilPausedWhileOverBudget() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, 0.7, 32, 0);
    limitsOverIntervals(budget, 7);

    // when
    load = 0.9;
    final var limits = limitsOverIntervals(budget, 4, 8);

    // then
    assertThat(limits).containsExactly(4, 2, 1, 0);
    assertThat(budget.backOffs()).isEqualTo(4);
  }

  @Test
  void shouldSampleOncePerInterval() {
    // given
    load = 0.5;
    final var budget = new CpuBudget(this::sample, 0.7, 32, 0);

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
    final var budget = new CpuBudget(this::sample, 0.7, 32, 0);
    limitsOverIntervals(budget, 2);

    // when
    load = -1;
    final var limits = limitsOverIntervals(budget, 3, 3);

    // then
    assertThat(limits).containsExactly(3, 3, 3);
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

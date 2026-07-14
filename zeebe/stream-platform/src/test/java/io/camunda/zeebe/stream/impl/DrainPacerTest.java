/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The pacing math of a sliced drain: wait exactly while the progress fraction runs ahead of
 * elapsed-over-budget, never wait when behind, never wait past the budget, never wait once
 * expedited. Time is passed in explicitly, so no clocks and no sleeps.
 */
final class DrainPacerTest {

  private static final long START = 1_000_000;
  private static final long BUDGET = 100;

  @Test
  void shouldNotWaitWhileBehindSchedule() {
    // given a drain 20% done at 30% of its budget
    final DrainPacer pacer = new DrainPacer(BUDGET, START);

    // when / then -- behind schedule means no wait
    assertThat(pacer.delayNanos(0.2, START + 30)).isZero();
  }

  @Test
  void shouldWaitUntilElapsedCatchesUpWithProgress() {
    // given a drain 50% done at 10% of its budget
    final DrainPacer pacer = new DrainPacer(BUDGET, START);

    // when / then -- resume exactly when elapsed/budget reaches the progress fraction
    assertThat(pacer.delayNanos(0.5, START + 10)).isEqualTo(40);
  }

  @Test
  void shouldNeverWaitPastTheBudget() {
    // given
    final DrainPacer pacer = new DrainPacer(BUDGET, START);

    // when / then -- a fully progressed drain waits at most to the budget's end
    assertThat(pacer.delayNanos(1.0, START + 10)).isEqualTo(90);
    // and never waits once the budget elapsed, whatever the progress says
    assertThat(pacer.delayNanos(1.0, START + BUDGET)).isZero();
    assertThat(pacer.delayNanos(0.1, START + BUDGET + 20)).isZero();
  }

  @Test
  void shouldNotWaitAfterExpedite() {
    // given a drain that would wait
    final DrainPacer pacer = new DrainPacer(BUDGET, START);
    assertThat(pacer.delayNanos(1.0, START + 10)).isPositive();

    // when the owner shortens the deadline mid-round (over capacity while in flight)
    pacer.expedite();

    // then the drain runs flat-out from the next check on
    assertThat(pacer.delayNanos(1.0, START + 10)).isZero();
    assertThat(pacer.delayNanos(0.01, START + 1)).isZero();
  }

  @Test
  void shouldTreatZeroBudgetAsFlatOut() {
    // given
    final DrainPacer pacer = new DrainPacer(0, START);

    // when / then
    assertThat(pacer.delayNanos(1.0, START)).isZero();
    assertThat(pacer.delayNanos(0.5, START + 5)).isZero();
  }

  @Test
  void shouldRejectNegativeBudget() {
    // when / then
    assertThatThrownBy(() -> new DrainPacer(-1, START))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.lake.serving.tools.ScreenAlgorithm.ShiftCorrelation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenAlgorithmTest {

  @Test
  void shouldFindPerfectCorrelationAtZeroShiftForAnIdenticalSeries() {
    // given -- both series step at the same index (the StepScenarioFixtures shape)
    final List<Double> target = stepSeries(20);
    final List<Double> candidate = stepSeries(20);

    // when
    final ShiftCorrelation result = ScreenAlgorithm.bestShift(target, candidate);

    // then
    assertThat(result.shiftSlots()).isZero();
    assertThat(result.correlation()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void shouldFindTheShiftWhenACandidateLagsTheTarget() {
    // given -- target steps at index 20; candidate steps 2 slots later, at index 22
    final List<Double> target = stepSeries(20);
    final List<Double> candidate = stepSeries(22);

    // when
    final ShiftCorrelation result = ScreenAlgorithm.bestShift(target, candidate);

    // then -- shift = 2: candidate[i + 2] lines up with target[i]'s own move
    assertThat(result.shiftSlots()).isEqualTo(2);
    assertThat(result.correlation()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void shouldReportZeroCorrelationForAnUncorrelatedSeries() {
    // given -- target steps; candidate is flat (no deltas at all, undefined correlation -> 0)
    final List<Double> target = stepSeries(20);
    final List<Double> flat = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      flat.add(42.0);
    }

    // when
    final ShiftCorrelation result = ScreenAlgorithm.bestShift(target, flat);

    // then
    assertThat(result.correlation()).isZero();
  }

  private static List<Double> stepSeries(final int stepIndex) {
    final List<Double> values = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      values.add(i < stepIndex ? 1000.0 : 3000.0);
    }
    return values;
  }
}

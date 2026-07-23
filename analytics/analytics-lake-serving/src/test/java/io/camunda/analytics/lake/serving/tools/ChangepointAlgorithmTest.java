/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.tools.ChangepointAlgorithm.Outcome;
import io.camunda.analytics.lake.serving.tools.ChangepointAlgorithm.Shape;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangepointAlgorithmTest {

  @Test
  void shouldDetectACleanStepChange() {
    // given -- 40 points, a clean step from 1000 to 3000 at index 20 (see StepScenarioFixtures)
    final List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      points.add(new SeriesPoint("t" + i, i < 20 ? 1000.0 : 3000.0));
    }

    // when
    final Outcome outcome = ChangepointAlgorithm.detect(points);

    // then
    assertThat(outcome.shape()).isEqualTo(Shape.STEP);
    assertThat(outcome.at()).isEqualTo("t20");
    assertThat(outcome.before()).isEqualTo(1000.0);
    assertThat(outcome.after()).isEqualTo(3000.0);
    assertThat(outcome.confidence()).isGreaterThan(0.5).isLessThanOrEqualTo(1.0);
  }

  @Test
  void shouldReportNoneForAFlatSeries() {
    // given
    final List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      points.add(new SeriesPoint("t" + i, 42.0));
    }

    // when
    final Outcome outcome = ChangepointAlgorithm.detect(points);

    // then
    assertThat(outcome.shape()).isEqualTo(Shape.NONE);
    assertThat(outcome.at()).isNull();
    assertThat(outcome.confidence()).isEqualTo(0.0);
  }

  @Test
  void shouldReportDriftForAGradualRamp() {
    // given -- a steady ramp: window=5 makes every candidate split's gap the constant 5*slope
    // (500), while no single consecutive-point delta (100) reaches 60% of that gap -- concentrated
    // in no single step, spread evenly instead.
    final List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      points.add(new SeriesPoint("t" + i, 1000.0 + i * 100.0));
    }

    // when
    final Outcome outcome = ChangepointAlgorithm.detect(points);

    // then
    assertThat(outcome.shape()).isEqualTo(Shape.DRIFT);
  }

  @Test
  void shouldTreatNullPointsAsGapsAndSkipThem() {
    // given -- same step scenario with a handful of nulls interspersed
    final List<SeriesPoint> points = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      points.add(new SeriesPoint("t" + i, i == 5 || i == 25 ? null : (i < 20 ? 1000.0 : 3000.0)));
    }

    // when
    final Outcome outcome = ChangepointAlgorithm.detect(points);

    // then -- still detects the step over the dense (non-null) points
    assertThat(outcome.shape()).isEqualTo(Shape.STEP);
    assertThat(outcome.before()).isEqualTo(1000.0);
    assertThat(outcome.after()).isEqualTo(3000.0);
  }
}

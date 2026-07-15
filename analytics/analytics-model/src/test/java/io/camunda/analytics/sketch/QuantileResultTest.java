/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.junit.jupiter.api.Test;

/**
 * The server-side sketch-backed reads {@link QuantileResult} exposes on top of its declared ranks
 * ({@link #quantile}, {@link #shareAbove}, {@link #outlierStats}) — the enabling primitive for the
 * outlier-analysis feature — plus the guarantee that the sketch never rides the JSON.
 */
final class QuantileResultTest {

  private final QuantileAggregateFunction<Double> quantile =
      new QuantileAggregateFunction<>(Double::doubleValue);

  @Test
  void shouldAnswerAnArbitraryRankFromTheSketch() {
    // given a sketch-backed result over 1..100, declared only at the default ranks
    final QuantileResult result = quantile.getResult(fold(1, 100));

    // when an undeclared rank (0.25) is asked directly
    // then the sketch answers it anyway, unlike valueAt which only knows declared ranks
    assertThat(result.quantile(0.25)).isCloseTo(25.0, within(3.0));
    assertThat(result.valueAt(0.25)).isNaN();
  }

  @Test
  void shouldFallBackToTheDeclaredRankWhenTheSketchIsAbsent() {
    // given a result whose sketch did not survive (e.g. a hypothetical JSON round-trip), but whose
    // declared ranks/values still carry the 0.5 estimate
    final QuantileResult result =
        new QuantileResult(100L, 1.0, 100.0, new double[] {0.5}, new double[] {50.0}, null);

    // then the declared rank still answers via valueAt, while an undeclared rank is NaN
    assertThat(result.quantile(0.5)).isEqualTo(50.0);
    assertThat(result.quantile(0.25)).isNaN();
  }

  @Test
  void shouldComputeShareAboveAValueFromTheSketch() {
    // given a sketch-backed result over 1..100
    final QuantileResult result = quantile.getResult(fold(1, 100));

    // when the share above ~90 is asked
    // then roughly 10% of the observations sit above it
    assertThat(result.shareAbove(90.0)).isCloseTo(0.10, within(0.05));
  }

  @Test
  void shouldReturnNaNShareAboveWithoutASketch() {
    // given a result without a sketch (empty(), the only production path that omits one)
    final QuantileResult result = quantile.getResult(quantile.createAccumulator());

    // then shareAbove degrades to NaN rather than throwing
    assertThat(result.shareAbove(10.0)).isNaN();
  }

  @Test
  void shouldReturnNullOutlierStatsForAnEmptySketch() {
    // given the empty accumulator's result (no sketch attached)
    final QuantileResult result = quantile.getResult(quantile.createAccumulator());

    // then outlierStats degrades to null rather than throwing or dividing by zero
    assertThat(result.outlierStats()).isNull();
  }

  @Test
  void shouldComputeOutlierStatsFromASkewedDistribution() {
    // given a tight cluster of 100 observations (10/11/12 repeating) plus 5 far outliers at 1000
    KllDoublesSketch acc = quantile.createAccumulator();
    for (int i = 0; i < 100; i++) {
      acc = quantile.add(10.0 + (i % 3), acc);
    }
    for (int i = 0; i < 5; i++) {
      acc = quantile.add(1_000.0, acc);
    }
    final QuantileResult result = quantile.getResult(acc);

    // when the boxplot stats are computed
    final OutlierStats stats = result.outlierStats();

    // then the fence sits well below the outliers, which get counted above it
    assertThat(stats).isNotNull();
    assertThat(stats.n()).isEqualTo(105L);
    assertThat(stats.fence()).isLessThan(1_000.0);
    assertThat(stats.share()).isCloseTo(5.0 / 105.0, within(0.02));
    assertThat(stats.count()).isGreaterThan(0L);
  }

  @Test
  void shouldSerializeToJsonWithoutTheSketchField() throws Exception {
    // given a sketch-backed result
    final QuantileResult result = quantile.getResult(fold(1, 10));

    // when serialized the way the dashboard controller serializes report-row measures
    final String json = new ObjectMapper().writeValueAsString(result);

    // then the sketch never appears in the JSON even though the record carries it
    assertThat(json).doesNotContain("sketch");
    assertThat(json).contains("\"count\"");
  }

  private KllDoublesSketch fold(final int from, final int to) {
    KllDoublesSketch acc = quantile.createAccumulator();
    for (int i = from; i <= to; i++) {
      acc = quantile.add((double) i, acc);
    }
    return acc;
  }
}

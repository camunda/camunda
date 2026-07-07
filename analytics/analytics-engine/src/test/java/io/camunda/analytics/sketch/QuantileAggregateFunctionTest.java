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

import org.apache.datasketches.kll.KllDoublesSketch;
import org.junit.jupiter.api.Test;

final class QuantileAggregateFunctionTest {

  private final QuantileAggregateFunction<Double> quantile =
      new QuantileAggregateFunction<>(Double::doubleValue);
  private final KllDoublesSketchValue codec = new KllDoublesSketchValue();

  @Test
  void shouldComputeMedianMinMaxAndCount() {
    // given — the values 1..100 folded into one accumulator
    KllDoublesSketch acc = quantile.createAccumulator();
    for (int i = 1; i <= 100; i++) {
      acc = quantile.add((double) i, acc);
    }

    // when
    final QuantileResult result = quantile.getResult(acc);

    // then — count/min/max are exact, the median is close to 50
    assertThat(result.count()).isEqualTo(100L);
    assertThat(result.min()).isEqualTo(1.0);
    assertThat(result.max()).isEqualTo(100.0);
    assertThat(result.valueAt(0.5)).isCloseTo(50.0, within(3.0));
    assertThat(result.valueAt(0.9)).isCloseTo(90.0, within(3.0));
    assertThat(result.valueAt(0.99)).isCloseTo(99.0, within(3.0));
  }

  @Test
  void shouldMergePartialsIntoTheSameDistribution() {
    // given — the low and high halves folded separately
    KllDoublesSketch low = quantile.createAccumulator();
    KllDoublesSketch high = quantile.createAccumulator();
    for (int i = 1; i <= 50; i++) {
      low = quantile.add((double) i, low);
    }
    for (int i = 51; i <= 100; i++) {
      high = quantile.add((double) i, high);
    }

    // when the partials merge
    final QuantileResult result = quantile.getResult(quantile.merge(low, high));

    // then the merged distribution spans both halves
    assertThat(result.count()).isEqualTo(100L);
    assertThat(result.min()).isEqualTo(1.0);
    assertThat(result.max()).isEqualTo(100.0);
    assertThat(result.valueAt(0.5)).isCloseTo(50.0, within(3.0));
  }

  @Test
  void shouldRoundTripThroughRecordValue() {
    // given
    KllDoublesSketch acc = quantile.createAccumulator();
    for (int i = 1; i <= 100; i++) {
      acc = quantile.add((double) i, acc);
    }

    // when the accumulator is serialized and read back
    final KllDoublesSketch restored = codec.fromBytes(codec.toBytes(acc));

    // then the restored sketch yields the same estimates
    final QuantileResult before = quantile.getResult(acc);
    final QuantileResult after = quantile.getResult(restored);
    assertThat(after.count()).isEqualTo(before.count());
    assertThat(after.valueAt(0.9)).isEqualTo(before.valueAt(0.9));
  }

  @Test
  void shouldMergeIntoTheTargetInPlaceMatchingThePureMerge() {
    // given the low and high halves folded separately, and the pure merge as the reference
    final KllDoublesSketch target = fold(1, 50);
    final KllDoublesSketch delta = fold(51, 100);
    final QuantileResult pure = quantile.getResult(quantile.merge(target, delta));

    // when the delta is folded in place
    final KllDoublesSketch merged = quantile.mergeInto(target, delta);

    // then the target itself carries the merged distribution, identical to the pure merge
    assertThat(merged).isSameAs(target);
    final QuantileResult inPlace = quantile.getResult(merged);
    assertThat(inPlace.count()).isEqualTo(pure.count());
    assertThat(inPlace.min()).isEqualTo(pure.min());
    assertThat(inPlace.max()).isEqualTo(pure.max());
    assertThat(inPlace.valueAt(0.5)).isEqualTo(pure.valueAt(0.5));
    assertThat(inPlace.valueAt(0.99)).isEqualTo(pure.valueAt(0.99));
  }

  @Test
  void shouldMergeAWrappedDeltaLikeAHeapifiedDelta() {
    // given a delta serialized through the codec, and two identical targets
    final byte[] deltaBytes = codec.toBytes(fold(51, 100));
    final KllDoublesSketch heapTarget = fold(1, 50);
    final KllDoublesSketch wrapTarget = fold(1, 50);

    // when one target merges the heap decode and the other the zero-copy merge-only view
    quantile.mergeInto(heapTarget, codec.fromBytes(deltaBytes));
    quantile.mergeInto(wrapTarget, new KllDoublesSketchValue().fromBytesForMerge(deltaBytes));

    // then both targets agree on every estimate
    final QuantileResult viaHeap = quantile.getResult(heapTarget);
    final QuantileResult viaWrap = quantile.getResult(wrapTarget);
    assertThat(viaWrap.count()).isEqualTo(viaHeap.count());
    assertThat(viaWrap.min()).isEqualTo(viaHeap.min());
    assertThat(viaWrap.max()).isEqualTo(viaHeap.max());
    assertThat(viaWrap.valueAt(0.5)).isEqualTo(viaHeap.valueAt(0.5));
    assertThat(viaWrap.valueAt(0.99)).isEqualTo(viaHeap.valueAt(0.99));
  }

  private KllDoublesSketch fold(final int from, final int to) {
    KllDoublesSketch acc = quantile.createAccumulator();
    for (int i = from; i <= to; i++) {
      acc = quantile.add((double) i, acc);
    }
    return acc;
  }

  @Test
  void shouldReportEmptyWhenNoObservations() {
    // when
    final QuantileResult result = quantile.getResult(quantile.createAccumulator());

    // then
    assertThat(result.count()).isZero();
    assertThat(result.valueAt(0.5)).isNaN();
  }
}

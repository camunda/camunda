/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

final class ExecutionTimeSummaryTest {

  private static final double[] RANKS = {0.5, 0.9};

  private static ExecutionTimeSummary fold(final long... durations) {
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);
    ExecutionTimeSummary acc = aggregate.createAccumulator();
    for (final long duration : durations) {
      acc = aggregate.add(duration, acc);
    }
    return acc;
  }

  @Test
  void shouldServeCountTotalMinMaxAndPercentilesFromOneFold() {
    // given one composite fold of 1..100
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);
    ExecutionTimeSummary acc = aggregate.createAccumulator();
    for (final long v : (Iterable<Long>) () -> LongStream.rangeClosed(1, 100).iterator()) {
      acc = aggregate.add(v, acc);
    }

    // when
    final ExecutionTimeSummaryResult result = aggregate.getResult(acc);

    // then the whole family comes from the single accumulator
    assertThat(result.count()).isEqualTo(100L);
    assertThat(result.averageMs()).isEqualTo(50.5);
    assertThat(result.minMs()).isEqualTo(1L);
    assertThat(result.maxMs()).isEqualTo(100L);
    assertThat(result.quantilesMs()[0]).isBetween(45.0, 55.0); // ~p50
    assertThat(result.quantilesMs()[1]).isBetween(85.0, 95.0); // ~p90
  }

  @Test
  void shouldMergeCommutativelyAcrossPartials() {
    // given two partials folded separately
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);
    final ExecutionTimeSummary a = fold(10L, 20L);
    final ExecutionTimeSummary b = fold(30L, 40L, 50L);

    // when merged
    final ExecutionTimeSummaryResult merged = aggregate.getResult(aggregate.merge(a, b));

    // then stats combine
    assertThat(merged.count()).isEqualTo(5L);
    assertThat(merged.minMs()).isEqualTo(10L);
    assertThat(merged.maxMs()).isEqualTo(50L);
    assertThat(merged.averageMs()).isEqualTo(30.0);
  }

  @Test
  void shouldRoundTripThroughCodec() {
    // given
    final ExecutionTimeSummaryValue codec = new ExecutionTimeSummaryValue();
    final ExecutionTimeSummary acc = fold(10L, 20L, 30L, 40L);

    // when
    final ExecutionTimeSummary decoded = codec.fromBytes(codec.toBytes(acc));

    // then exact stats survive and the sketch stays equivalent
    assertThat(decoded.count()).isEqualTo(4L);
    assertThat(decoded.totalMs()).isEqualTo(100L);
    assertThat(decoded.minMs()).isEqualTo(10L);
    assertThat(decoded.maxMs()).isEqualTo(40L);
    assertThat(decoded.sketch().getQuantile(0.5)).isEqualTo(acc.sketch().getQuantile(0.5));
  }

  @Test
  void shouldMergeIntoTheTargetInPlaceMatchingThePureMerge() {
    // given two partials, and the pure merge as the reference
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);
    final ExecutionTimeSummary target = fold(10L, 20L);
    final ExecutionTimeSummary delta = fold(30L, 40L, 50L);
    final ExecutionTimeSummaryResult pure = aggregate.getResult(aggregate.merge(target, delta));

    // when the delta is folded in place
    final ExecutionTimeSummary merged = aggregate.mergeInto(target, delta);

    // then the target itself carries the combined family, identical to the pure merge
    assertThat(merged).isSameAs(target);
    final ExecutionTimeSummaryResult inPlace = aggregate.getResult(merged);
    assertThat(inPlace.count()).isEqualTo(pure.count());
    assertThat(inPlace.averageMs()).isEqualTo(pure.averageMs());
    assertThat(inPlace.minMs()).isEqualTo(pure.minMs());
    assertThat(inPlace.maxMs()).isEqualTo(pure.maxMs());
    assertThat(inPlace.quantilesMs()).containsExactly(pure.quantilesMs());
  }

  @Test
  void shouldMergeAWrappedDeltaLikeAHeapifiedDelta() {
    // given a delta serialized through the codec, and two identical targets
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);
    final ExecutionTimeSummaryValue codec = new ExecutionTimeSummaryValue();
    final byte[] deltaBytes = codec.toBytes(fold(30L, 40L, 50L));
    final ExecutionTimeSummary heapTarget = fold(10L, 20L);
    final ExecutionTimeSummary wrapTarget = fold(10L, 20L);

    // when one target merges the heap decode and the other the zero-copy merge-only view
    aggregate.mergeInto(heapTarget, codec.fromBytes(deltaBytes));
    aggregate.mergeInto(wrapTarget, new ExecutionTimeSummaryValue().fromBytesForMerge(deltaBytes));

    // then both targets agree on the exact stats and the sketch estimates
    final ExecutionTimeSummaryResult viaHeap = aggregate.getResult(heapTarget);
    final ExecutionTimeSummaryResult viaWrap = aggregate.getResult(wrapTarget);
    assertThat(viaWrap.count()).isEqualTo(viaHeap.count());
    assertThat(viaWrap.averageMs()).isEqualTo(viaHeap.averageMs());
    assertThat(viaWrap.minMs()).isEqualTo(viaHeap.minMs());
    assertThat(viaWrap.maxMs()).isEqualTo(viaHeap.maxMs());
    assertThat(viaWrap.quantilesMs()).containsExactly(viaHeap.quantilesMs());
  }

  @Test
  void shouldReturnEmptyResultForNoObservations() {
    // given
    final ExecutionTimeSummaryAggregateFunction<Long> aggregate =
        new ExecutionTimeSummaryAggregateFunction<>(Long::longValue, RANKS);

    // when
    final ExecutionTimeSummaryResult result = aggregate.getResult(aggregate.createAccumulator());

    // then
    assertThat(result.count()).isZero();
    assertThat(result.averageMs()).isZero();
    assertThat(result.quantilesMs()).containsExactly(Double.NaN, Double.NaN);
  }
}

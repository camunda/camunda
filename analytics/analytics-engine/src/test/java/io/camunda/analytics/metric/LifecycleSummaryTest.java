/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.MeasureRef;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LifecycleSummaryTest {

  private static final double[] RANKS = {0.5};

  private final LifecycleSummaryAggregateFunction aggregate =
      new LifecycleSummaryAggregateFunction(new MeasureRef("durationMs"), RANKS);

  private static Fact event(final Transition transition, final Long durationMs) {
    final Fact.Builder builder = Fact.builder(FactType.PROCESS_INSTANCE).transition(transition);
    if (durationMs != null) {
      builder.field("durationMs", durationMs);
    }
    return builder.build();
  }

  private LifecycleSummary fold(final List<Fact> facts) {
    LifecycleSummary acc = aggregate.createAccumulator();
    for (final Fact fact : facts) {
      acc = aggregate.add(fact, acc);
    }
    return acc;
  }

  @Test
  void shouldCountTransitionsAndSummariseDurations() {
    // given two activations, two completions and a termination
    final LifecycleSummary acc =
        fold(
            List.of(
                event(Transition.ACTIVATED, null),
                event(Transition.ACTIVATED, null),
                event(Transition.COMPLETED, 100L),
                event(Transition.COMPLETED, 300L),
                event(Transition.TERMINATED, 200L)));

    // when
    final LifecycleSummaryResult result = aggregate.getResult(acc);

    // then per-transition counts and the duration family come from one accumulator
    assertThat(result.activated()).isEqualTo(2L);
    assertThat(result.completed()).isEqualTo(2L);
    assertThat(result.terminated()).isEqualTo(1L);
    assertThat(result.duration().count()).isEqualTo(3L); // only ended events carry a duration
    assertThat(result.duration().averageMs()).isEqualTo(200.0);
    assertThat(result.duration().minMs()).isEqualTo(100L);
    assertThat(result.duration().maxMs()).isEqualTo(300L);
  }

  @Test
  void shouldMergeAcrossPartials() {
    // given two partials
    final LifecycleSummary a =
        fold(List.of(event(Transition.ACTIVATED, null), event(Transition.COMPLETED, 100L)));
    final LifecycleSummary b =
        fold(List.of(event(Transition.COMPLETED, 300L), event(Transition.TERMINATED, 200L)));

    // when
    final LifecycleSummaryResult merged = aggregate.getResult(aggregate.merge(a, b));

    // then
    assertThat(merged.activated()).isEqualTo(1L);
    assertThat(merged.completed()).isEqualTo(2L);
    assertThat(merged.terminated()).isEqualTo(1L);
    assertThat(merged.duration().count()).isEqualTo(3L);
    assertThat(merged.duration().averageMs()).isEqualTo(200.0);
  }

  @Test
  void shouldRoundTripThroughCodec() {
    // given
    final LifecycleSummaryValue codec = new LifecycleSummaryValue();
    final LifecycleSummary acc =
        fold(
            List.of(
                event(Transition.ACTIVATED, null),
                event(Transition.COMPLETED, 100L),
                event(Transition.COMPLETED, 300L)));

    // when
    final LifecycleSummary decoded = codec.fromBytes(codec.toBytes(acc));

    // then counts and duration stats survive, and the sketch stays equivalent
    assertThat(decoded.activated()).isEqualTo(1L);
    assertThat(decoded.completed()).isEqualTo(2L);
    assertThat(decoded.terminated()).isZero();
    assertThat(decoded.duration().count()).isEqualTo(2L);
    assertThat(decoded.duration().totalMs()).isEqualTo(400L);
    assertThat(decoded.duration().sketch().getQuantile(0.5))
        .isEqualTo(acc.duration().sketch().getQuantile(0.5));
  }

  @Test
  void shouldMergeIntoTheTargetInPlaceMatchingThePureMerge() {
    // given two partials, and the pure merge as the reference
    final LifecycleSummary target =
        fold(List.of(event(Transition.ACTIVATED, null), event(Transition.COMPLETED, 100L)));
    final LifecycleSummary delta =
        fold(List.of(event(Transition.COMPLETED, 300L), event(Transition.TERMINATED, 200L)));
    final LifecycleSummaryResult pure = aggregate.getResult(aggregate.merge(target, delta));

    // when the delta is folded in place
    final LifecycleSummary merged = aggregate.mergeInto(target, delta);

    // then the target itself carries the combined summary, identical to the pure merge
    assertThat(merged).isSameAs(target);
    final LifecycleSummaryResult inPlace = aggregate.getResult(merged);
    assertThat(inPlace.activated()).isEqualTo(pure.activated());
    assertThat(inPlace.completed()).isEqualTo(pure.completed());
    assertThat(inPlace.terminated()).isEqualTo(pure.terminated());
    assertThat(inPlace.duration().count()).isEqualTo(pure.duration().count());
    assertThat(inPlace.duration().averageMs()).isEqualTo(pure.duration().averageMs());
    assertThat(inPlace.duration().quantilesMs()).containsExactly(pure.duration().quantilesMs());
  }

  @Test
  void shouldMergeAWrappedDeltaLikeAHeapifiedDelta() {
    // given a delta serialized through the codec, and two identical targets
    final LifecycleSummaryValue codec = new LifecycleSummaryValue();
    final byte[] deltaBytes =
        codec.toBytes(
            fold(List.of(event(Transition.COMPLETED, 300L), event(Transition.TERMINATED, 200L))));
    final LifecycleSummary heapTarget =
        fold(List.of(event(Transition.ACTIVATED, null), event(Transition.COMPLETED, 100L)));
    final LifecycleSummary wrapTarget =
        fold(List.of(event(Transition.ACTIVATED, null), event(Transition.COMPLETED, 100L)));

    // when one target merges the heap decode and the other the zero-copy merge-only view
    aggregate.mergeInto(heapTarget, codec.fromBytes(deltaBytes));
    aggregate.mergeInto(wrapTarget, new LifecycleSummaryValue().fromBytesForMerge(deltaBytes));

    // then both targets agree on the counts and the duration family
    final LifecycleSummaryResult viaHeap = aggregate.getResult(heapTarget);
    final LifecycleSummaryResult viaWrap = aggregate.getResult(wrapTarget);
    assertThat(viaWrap.activated()).isEqualTo(viaHeap.activated());
    assertThat(viaWrap.completed()).isEqualTo(viaHeap.completed());
    assertThat(viaWrap.terminated()).isEqualTo(viaHeap.terminated());
    assertThat(viaWrap.duration().count()).isEqualTo(viaHeap.duration().count());
    assertThat(viaWrap.duration().averageMs()).isEqualTo(viaHeap.duration().averageMs());
    assertThat(viaWrap.duration().quantilesMs()).containsExactly(viaHeap.duration().quantilesMs());
  }
}

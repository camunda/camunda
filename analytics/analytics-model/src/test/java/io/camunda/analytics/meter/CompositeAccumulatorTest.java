/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.metric.ExecutionTimeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The composite accumulator is the cube-level value of ADR 0009: one slot per meter, folded,
 * merged, encoded and decoded together. These tests pin the slot-wise contract and the wire
 * framing's tolerance rules (fewer slots than declared = identity), which the parked
 * meter-evolution work builds on.
 */
final class CompositeAccumulatorTest {

  private final MeterCatalog catalog = MeterCatalog.withDefaults();
  private final List<BoundMeter<?, ?>> bounds =
      List.of(
          catalog.bind(Meter.of("count", MeterCatalog.COUNT)),
          catalog.bind(Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs")));

  private static Fact fact(final long durationMs) {
    return Fact.builder(FactType.PROCESS_INSTANCE).field("durationMs", durationMs).build();
  }

  @Test
  void shouldFoldEveryFactIntoEverySlot() {
    // given
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);

    // when two facts fold into the composite
    Object[] acc = aggregate.createAccumulator();
    acc = aggregate.add(fact(100L), acc);
    acc = aggregate.add(fact(300L), acc);

    // then both slots saw both facts
    final Object[] results = aggregate.getResult(acc);
    assertThat(results[0]).isEqualTo(2L);
    assertThat(((ExecutionTimeResult) results[1]).count()).isEqualTo(2L);
    assertThat(((ExecutionTimeResult) results[1]).maxMs()).isEqualTo(300L);
  }

  @Test
  void shouldRoundTripThroughTheCodecAndMergeSlotWise() {
    // given two composites folded independently (two "segments")
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(bounds);
    final Object[] a = aggregate.add(fact(100L), aggregate.createAccumulator());
    final Object[] b = aggregate.add(fact(300L), aggregate.createAccumulator());

    // when both round-trip the wire form and merge into a running total (the Stage-2 path)
    final Object[] total = aggregate.createAccumulator();
    aggregate.mergeInto(total, codec.fromBytesForMerge(codec.toBytes(a)));
    aggregate.mergeInto(total, codec.fromBytesForMerge(codec.toBytes(b)));

    // then the merged slots equal a direct fold of both facts
    final Object[] results = aggregate.getResult(total);
    assertThat(results[0]).isEqualTo(2L);
    assertThat(((ExecutionTimeResult) results[1]).minMs()).isEqualTo(100L);
    assertThat(((ExecutionTimeResult) results[1]).maxMs()).isEqualTo(300L);
  }

  @Test
  void shouldTreatMissingTrailingSlotsAsIdentityOnMerge() {
    // given a payload encoded with FEWER slots than declared (an older layout: count only)
    final List<BoundMeter<?, ?>> countOnly = List.of(bounds.get(0));
    final CompositeAggregateFunction countAggregate = new CompositeAggregateFunction(countOnly);
    final byte[] shortPayload =
        new CompositeAccumulatorValue(countOnly)
            .toBytes(countAggregate.add(fact(100L), countAggregate.createAccumulator()));

    // when decoded and merged under the two-slot declaration
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(bounds);
    final Object[] total = aggregate.add(fact(300L), aggregate.createAccumulator());
    aggregate.mergeInto(total, codec.fromBytesForMerge(shortPayload));

    // then the present slot merged and the absent slot was identity (unchanged)
    final Object[] results = aggregate.getResult(total);
    assertThat(results[0]).isEqualTo(2L);
    assertThat(((ExecutionTimeResult) results[1]).count()).isEqualTo(1L);
  }

  @Test
  void shouldExposeAlignedSlotBytesForTheServingWriters() {
    // given an encoded composite
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(bounds);
    final byte[] composite =
        codec.toBytes(aggregate.add(fact(100L), aggregate.createAccumulator()));

    // when sliced for the writers
    final List<byte[]> slots = CompositeAccumulatorValue.slotBytes(composite, bounds.size());

    // then each slot decodes independently with its own meter codec
    assertThat(slots).hasSize(2);
    assertThat(bounds.get(0).accumulatorCodec().fromBytes(slots.get(0))).isEqualTo(1L);

    // and a shorter payload pads missing slots with null
    final List<byte[]> padded = CompositeAccumulatorValue.slotBytes(composite, 3);
    assertThat(padded).hasSize(3);
    assertThat(padded.get(2)).isNull();
  }

  @Test
  void shouldNeverAdoptADeltaSlotIntoTheTarget() {
    // given a delta decoded through the merge-only path (slots may be read-only views)
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(bounds);
    final Object[] folded = aggregate.add(fact(100L), aggregate.createAccumulator());
    final Object[] delta = codec.fromBytesForMerge(codec.toBytes(folded));

    // when merged into a target whose slots are null (older layout / pure merge with a null side)
    final Object[] target = new Object[bounds.size()];
    final Object[] merged = aggregate.mergeInto(target, delta);
    final Object[] pureMerged = aggregate.merge(new Object[bounds.size()], delta);

    // then no mutable slot of any result is the delta's own object — views are folded, never
    // adopted. (Slot 0 is a boxed Long whose small values intern, so identity is asserted on the
    // mutable execution-time accumulator slot; slot 0 is checked by value.)
    assertThat(merged[1]).isNotSameAs(delta[1]);
    assertThat(pureMerged[1]).isNotSameAs(delta[1]);
    assertThat(merged[0]).isEqualTo(1L);
    assertThat(pureMerged[0]).isEqualTo(1L);
  }
}

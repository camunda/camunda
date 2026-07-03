/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.aggregation.CubeMeterAggregation;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.aggregate.Segments;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CubeMeterAggregationTest {

  private static final DimensionSchema GRAIN =
      DimensionSchema.of(new DimensionColumn("region", DimensionType.STRING));

  private record Sealed(DimensionKey key, long segment, long delta) {}

  private final List<Sealed> emitted = new ArrayList<>();

  private CubeMeterAggregation gate(final long activationOnPartition0) {
    final DatasetRegistry registry = new DatasetRegistry();
    final RegisteredDataset dataset =
        registry.admit(
            DatasetDeclaration.builder("d", FactType.PROCESS_INSTANCE)
                .dimension("region", DimensionType.STRING)
                .meter(Meter.of("n", MeterCatalog.COUNT))
                .window(60_000L)
                .build(),
            Map.of(0, activationOnPartition0));
    final SegmentSealingAggregation<Fact, DimensionKey, Long> delegate =
        new SegmentSealingAggregation<Fact, DimensionKey, Long>(
            new SumAggregateFunction<Fact>(f -> ((Number) f.get("v")).longValue()),
            new DimensionKeySelector(GRAIN),
            new SourceCoordinate<Fact>() {
              @Override
              public int partition(final Fact fact) {
                return fact.sourcePartition();
              }

              @Override
              public long position(final Fact fact) {
                return fact.sourcePosition();
              }
            },
            Fact::eventTime,
            TumblingWindows.of(1_000_000L),
            Segments.ofStride(10L),
            (cell, partition, segment, delta) ->
                emitted.add(new Sealed(cell.key(), segment, delta)));
    return new CubeMeterAggregation(
        FactType.PROCESS_INSTANCE,
        dataset,
        List.of(FilterPredicate.equals("bpmnProcessId", "invoice")),
        delegate);
  }

  private static Fact fact(
      final FactType type,
      final long position,
      final String region,
      final String bpmnProcessId,
      final long value) {
    return Fact.builder(type)
        .source(0, position)
        .eventTime(100L)
        .field("region", region)
        .field("bpmnProcessId", bpmnProcessId)
        .field("v", value)
        .build();
  }

  @Test
  void shouldFoldOnlyMatchingFactsOfTheCube() {
    // given a cube gate (activation at position 100)
    final CubeMeterAggregation aggregation = gate(100L);

    // when a matching fact, a wrong-type fact and a filtered-out fact land in segment 10
    aggregation.accept(fact(FactType.PROCESS_INSTANCE, 100L, "EU", "invoice", 5L));
    aggregation.accept(fact(FactType.ELEMENT, 101L, "EU", "invoice", 99L)); // wrong fact type
    aggregation.accept(fact(FactType.PROCESS_INSTANCE, 102L, "EU", "other", 99L)); // filtered out
    // and a matching fact in segment 11 seals segment 10
    aggregation.accept(fact(FactType.PROCESS_INSTANCE, 110L, "EU", "invoice", 3L));

    // then only the matching fact contributed to the sealed segment
    assertThat(emitted).containsExactly(new Sealed(DimensionKey.of(GRAIN, "EU"), 10L, 5L));
  }

  @Test
  void shouldExcludeFactsBeforeActivation() {
    // given activation at position 100
    final CubeMeterAggregation aggregation = gate(100L);

    // when a matching fact arrives before activation, then one at activation seals the earlier
    // segment
    aggregation.accept(
        fact(FactType.PROCESS_INSTANCE, 90L, "EU", "invoice", 7L)); // before activation
    aggregation.accept(fact(FactType.PROCESS_INSTANCE, 100L, "EU", "invoice", 5L));

    // then the pre-activation segment sealed nothing (the fact was gated out, forward-only)
    assertThat(emitted).isEmpty();
  }
}

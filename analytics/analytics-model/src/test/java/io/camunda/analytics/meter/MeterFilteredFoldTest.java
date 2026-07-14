/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.metric.ExecutionTimeResult;
import io.camunda.analytics.metric.RatioResult;
import io.camunda.analytics.sketch.QuantileResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Per-meter filters (SQL {@code FILTER}-clause semantics): a slot folds a fact only when the
 * meter's predicate conjunction admits it, while unfiltered slots of the same composite still see
 * every fact. Filtering is a fold-time concern only — merge behaviour and wire shape are the
 * subject of {@link CompositeAccumulatorTest} and unchanged by filters.
 */
final class MeterFilteredFoldTest {

  private final MeterCatalog catalog = MeterCatalog.withDefaults();

  private static Fact completed(final long durationMs) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(Transition.COMPLETED)
        .field("durationMs", durationMs)
        .build();
  }

  private static Fact activated() {
    return Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.ACTIVATED).build();
  }

  @Test
  void shouldFoldOnlyMatchingFactsIntoAFilteredSlot() {
    // given a composite of an unfiltered count and a count filtered to completions
    final List<BoundMeter<?, ?>> bounds =
        List.of(
            catalog.bind(Meter.of("all", MeterCatalog.COUNT)),
            catalog.bind(
                Meter.of("completed", MeterCatalog.COUNT)
                    .filtered(
                        FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name()))));
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);

    // when matching, non-matching and transition-less facts fold
    Object[] acc = aggregate.createAccumulator();
    acc = aggregate.add(completed(100L), acc);
    acc = aggregate.add(activated(), acc);
    acc = aggregate.add(Fact.builder(FactType.PROCESS_INSTANCE).build(), acc);

    // then the unfiltered slot saw all three and the filtered slot only the completion
    final Object[] results = aggregate.getResult(acc);
    assertThat(results[0]).isEqualTo(3L);
    assertThat(results[1]).isEqualTo(1L);
  }

  @Test
  void shouldRequireEveryPredicateOfAConjunction() {
    // given a count filtered on both the transition and a duration bound
    final List<BoundMeter<?, ?>> bounds =
        List.of(
            catalog.bind(
                Meter.of("slow_completed", MeterCatalog.COUNT)
                    .filtered(
                        FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name()),
                        FilterPredicate.greaterThan("durationMs", "200"))));
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);

    // when a fact matches only one of the two predicates, and one matches both
    Object[] acc = aggregate.createAccumulator();
    acc = aggregate.add(completed(100L), acc); // completed, but fast
    acc = aggregate.add(activated(), acc); // neither
    acc = aggregate.add(completed(300L), acc); // both

    // then only the fact satisfying the whole conjunction folded
    assertThat(aggregate.getResult(acc)[0]).isEqualTo(1L);
  }

  @Test
  void shouldImplicitlySkipAbsentMeasureFactsOnNumericMeters() {
    // given a numeric-measure meter with NO declared filters, and one declaring the NOT_NULL
    // explicitly — the catalog binds numeric kinds with the implicit measure-presence filter
    final List<BoundMeter<?, ?>> bounds =
        List.of(
            catalog.bind(Meter.of("implicit", MeterCatalog.PERCENTILE, "durationMs")),
            catalog.bind(
                Meter.of("explicit", MeterCatalog.PERCENTILE, "durationMs")
                    .filtered(FilterPredicate.notNull("durationMs"))));
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);

    // when facts WITHOUT the measured field fold alongside real observations
    Object[] acc = aggregate.createAccumulator();
    acc = aggregate.add(completed(1_000L), acc);
    acc = aggregate.add(completed(2_000L), acc);
    acc = aggregate.add(activated(), acc); // no durationMs — never a phantom 0 observation
    acc = aggregate.add(activated(), acc);

    // then both slots saw only the true observations (SQL semantics: SUM/percentile ignore NULLs)
    final Object[] results = aggregate.getResult(acc);
    for (final Object result : results) {
      final QuantileResult quantile = (QuantileResult) result;
      assertThat(quantile.count()).isEqualTo(2L);
      assertThat(quantile.min()).isEqualTo(1_000.0);
      assertThat(quantile.max()).isEqualTo(2_000.0);
      assertThat(quantile.valueAt(0.5)).isBetween(1_000.0, 2_000.0);
    }
  }

  @Test
  void shouldKeepRatioCountingFactsWithoutTheMeasuredFlag() {
    // given a no-incident-style ratio over a boolean flag — RATIO is deliberately excluded from
    // the implicit presence filter: an absent flag reading 0 IS the information (no incident),
    // and the denominator must count every fact
    final List<BoundMeter<?, ?>> bounds =
        List.of(
            catalog.bind(
                new Meter(
                    "no_incident",
                    MeterCatalog.RATIO,
                    "hadIncident",
                    Map.of("op", "eq", "threshold", "0"))));
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);

    // when two flag-less facts fold alongside one incident
    Object[] acc = aggregate.createAccumulator();
    acc = aggregate.add(activated(), acc); // no hadIncident field — compliant
    acc = aggregate.add(activated(), acc);
    acc =
        aggregate.add(
            Fact.builder(FactType.PROCESS_INSTANCE).field("hadIncident", 1L).build(), acc);

    // then the flag-less facts stayed in both the numerator and the denominator
    final RatioResult ratio = (RatioResult) aggregate.getResult(acc)[0];
    assertThat(ratio.matched()).isEqualTo(2L);
    assertThat(ratio.total()).isEqualTo(3L);
  }

  @Test
  void shouldKeepFilteredSlotsMergeCompatibleWithUnfilteredDeltas() {
    // given two segments folded under the same filtered declaration
    final List<BoundMeter<?, ?>> bounds =
        List.of(
            catalog.bind(
                Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs")
                    .filtered(FilterPredicate.notNull("durationMs"))));
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    final CompositeAccumulatorValue codec = new CompositeAccumulatorValue(bounds);
    Object[] a = aggregate.createAccumulator();
    a = aggregate.add(completed(100L), a);
    a = aggregate.add(activated(), a); // filtered out at fold time
    final Object[] b = aggregate.add(completed(300L), aggregate.createAccumulator());

    // when both round-trip the wire form and merge (the Stage-2 path — no filter runs here)
    final Object[] total = aggregate.createAccumulator();
    aggregate.mergeInto(total, codec.fromBytesForMerge(codec.toBytes(a)));
    aggregate.mergeInto(total, codec.fromBytesForMerge(codec.toBytes(b)));

    // then the merged slot equals a direct filtered fold of all facts
    final ExecutionTimeResult result = (ExecutionTimeResult) aggregate.getResult(total)[0];
    assertThat(result.count()).isEqualTo(2L);
    assertThat(result.minMs()).isEqualTo(100L);
    assertThat(result.maxMs()).isEqualTo(300L);
  }
}

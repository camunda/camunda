/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.analytics.fact.ProcessInstanceExecutionTimeFactRow;
import io.camunda.analytics.metric.ExecutionTimeAccumulator;
import io.camunda.analytics.metric.ExecutionTimeAggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the generic dimension/meter core reproduces the hand-wired region execution-time metric:
 * folding the same facts through the declared {@link DimensionSchema} + {@link
 * DimensionKeySelector} + the {@code execution_time} meter from the {@link MeterCatalog} yields the
 * same per-cell accumulators as the production {@code RegionKey} + {@code
 * ExecutionTimeAggregateFunction} path — cell for cell. The old code's {@code "<none>"} region
 * sentinel corresponds to the generic unknown/{@code null} bucket.
 */
final class RegionMetricEquivalenceTest {

  private static final String NO_REGION = "<none>";
  private static final long REGION_CUBE_ID = 1L;

  private static final DimensionSchema REGION_GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("bpmnProcessId", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT),
          new DimensionColumn("tenantId", DimensionType.STRING));

  /** Grouping key of the production region metric (mirrors {@code Metrics}' RegionKey lambda). */
  private record RegionKey(
      String region,
      String bpmnProcessId,
      long processDefinitionKey,
      int version,
      String tenantId) {}

  private static ProcessInstanceExecutionTimeFact fact(
      final long defKey, final int version, final long durationMs, final String region) {
    final Map<String, String> variables = new HashMap<>();
    if (region != null) {
      variables.put("region", region);
    }
    return new ProcessInstanceExecutionTimeFact(
        1L,
        defKey,
        "invoice",
        version,
        "t1",
        0L,
        durationMs,
        durationMs,
        true,
        false,
        0,
        0L,
        variables);
  }

  @Test
  void shouldReproduceProductionRegionCellsCellForCell() {
    // given a spread of facts across regions, definitions and one missing-region fact
    final List<ProcessInstanceExecutionTimeFact> facts =
        List.of(
            fact(100L, 1, 10L, "EU"),
            fact(100L, 1, 30L, "EU"),
            fact(100L, 1, 50L, "US"),
            fact(100L, 1, 70L, null), // no region → unknown bucket
            fact(200L, 2, 25L, "EU"));

    // when folded through the production (hand-wired) path
    final Map<List<Object>, ExecutionTimeAccumulator> expected = foldProduction(facts);

    // and through the generic declared path (schema + selector + catalog meter)
    final Map<List<Object>, ExecutionTimeAccumulator> actual = foldGeneric(facts);

    // then the two produce identical cells
    assertThat(actual).isEqualTo(expected);
    // sanity: the EU/invoice/v1 cell aggregated its two facts
    assertThat(actual.get(List.of("EU", "invoice", 100L, 1, "t1")))
        .isEqualTo(new ExecutionTimeAccumulator(2L, 40L, 10L, 30L));
  }

  /** Production path: RegionKey (with "<none>" default) + ExecutionTimeAggregateFunction. */
  private static Map<List<Object>, ExecutionTimeAccumulator> foldProduction(
      final List<ProcessInstanceExecutionTimeFact> facts) {
    final ExecutionTimeAggregateFunction<ProcessInstanceExecutionTimeFact> aggregate =
        new ExecutionTimeAggregateFunction<>(ProcessInstanceExecutionTimeFact::durationMs);
    final Map<RegionKey, ExecutionTimeAccumulator> cells = new HashMap<>();
    for (final ProcessInstanceExecutionTimeFact fact : facts) {
      final RegionKey key =
          new RegionKey(
              fact.variables().getOrDefault("region", NO_REGION),
              fact.bpmnProcessId(),
              fact.processDefinitionKey(),
              fact.version(),
              fact.tenantId());
      cells.merge(key, aggregate.add(fact, aggregate.createAccumulator()), aggregate::merge);
    }
    final Map<List<Object>, ExecutionTimeAccumulator> normalized = new HashMap<>();
    cells.forEach(
        (key, acc) ->
            normalized.put(
                tuple(
                    NO_REGION.equals(key.region()) ? null : key.region(),
                    key.bpmnProcessId(),
                    key.processDefinitionKey(),
                    key.version(),
                    key.tenantId()),
                acc));
    return normalized;
  }

  /** Generic path: declared schema + DimensionKeySelector + the execution_time meter. */
  private static Map<List<Object>, ExecutionTimeAccumulator> foldGeneric(
      final List<ProcessInstanceExecutionTimeFact> facts) {
    final DimensionKeySelector selector = new DimensionKeySelector(REGION_GRAIN);
    final MeterCatalog catalog = MeterCatalog.withDefaults();
    // declaring the cube's meter allocates its stable aggId (ties the registry in)
    final MeterRegistry registry = new MeterRegistry(new InMemoryMeterIdStore());
    registry.aggIdFor(REGION_CUBE_ID, "duration");
    @SuppressWarnings("unchecked")
    final AggregateFunction<FactRow, ExecutionTimeAccumulator, ?> aggregate =
        (AggregateFunction<FactRow, ExecutionTimeAccumulator, ?>)
            catalog
                .bind(Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs"))
                .aggregate();

    final Map<DimensionKey, ExecutionTimeAccumulator> cells = new HashMap<>();
    for (final ProcessInstanceExecutionTimeFact fact : facts) {
      final var row = new ProcessInstanceExecutionTimeFactRow(fact);
      final DimensionKey key = selector.getKey(row);
      cells.merge(key, aggregate.add(row, aggregate.createAccumulator()), aggregate::merge);
    }
    final Map<List<Object>, ExecutionTimeAccumulator> normalized = new HashMap<>();
    cells.forEach((key, acc) -> normalized.put(new ArrayList<>(key.values()), acc));
    return normalized;
  }

  private static List<Object> tuple(final Object... values) {
    final List<Object> tuple = new ArrayList<>(values.length);
    for (final Object value : values) {
      tuple.add(value);
    }
    return tuple;
  }
}

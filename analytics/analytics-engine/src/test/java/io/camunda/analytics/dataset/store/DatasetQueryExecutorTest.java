/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DatasetQueryExecutorTest {

  private static final long MINUTE = 60_000L;

  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  @Test
  void shouldRollUpAllWindowsIntoOneBucketWhenGranularitySpansTheRange() {
    // given three cells: orders has 3 then 2 across two windows; ship has 4
    final FakeClient client = new FakeClient(cells());
    final DatasetQueryExecutor executor =
        new DatasetQueryExecutor(new DatasetQueryPlanner(), client);

    // when grouped by process over the whole range in one bucket
    final ReportResult result =
        executor.execute(
            new ReportQuery(
                List.of("bpmnProcessId"), 0L, 2 * MINUTE, 2 * MINUTE, List.of(), List.of("count")),
            dataset);

    // then windows merge: orders = 3 + 2 = 5, ship = 4 — one bucket each
    assertThat(result.rows())
        .extracting(r -> r.dimensions().get("bpmnProcessId"), r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("orders", 5L),
            org.assertj.core.groups.Tuple.tuple("ship", 4L));
  }

  @Test
  void shouldKeepPerWindowSeriesWhenGranularityEqualsTheTier() {
    // given
    final DatasetQueryExecutor executor =
        new DatasetQueryExecutor(new DatasetQueryPlanner(), new FakeClient(cells()));

    // when grouped by process at minute granularity
    final ReportResult result =
        executor.execute(
            new ReportQuery(
                List.of("bpmnProcessId"), 0L, 2 * MINUTE, MINUTE, List.of(), List.of("count")),
            dataset);

    // then each window is its own row
    assertThat(result.rows())
        .extracting(
            r -> r.dimensions().get("bpmnProcessId"),
            ReportRow::windowStart,
            r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("orders", 0L, 3L),
            org.assertj.core.groups.Tuple.tuple("orders", MINUTE, 2L),
            org.assertj.core.groups.Tuple.tuple("ship", 0L, 4L));
  }

  private List<Cell> cells() {
    return List.of(
        new Cell(key("orders"), 0L, Map.of("count", count(3L))),
        new Cell(key("orders"), MINUTE, Map.of("count", count(2L))),
        new Cell(key("ship"), 0L, Map.of("count", count(4L))));
  }

  private DimensionKey key(final String process) {
    return DimensionKey.of(dataset.grain(), process);
  }

  @SuppressWarnings("unchecked")
  private byte[] count(final long value) {
    final CompiledMeter meter = dataset.meters().get(0);
    return ((BoundMeter<Object, Object>) meter.bound()).accumulatorCodec().toBytes(value);
  }

  /** Returns the fixed cells for every fetch (filter/range pushdown is not under test here). */
  private static final class FakeClient implements DatasetQueryClient {
    private final List<Cell> cells;

    FakeClient(final List<Cell> cells) {
      this.cells = new ArrayList<>(cells);
    }

    @Override
    public List<Cell> fetch(final DatasetFetch fetch) {
      return cells;
    }

    @Override
    public void close() {}
  }
}

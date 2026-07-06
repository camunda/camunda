/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.store.AggregatedFetch;
import io.camunda.analytics.dataset.store.AggregatedRow;
import io.camunda.analytics.dataset.store.Cell;
import io.camunda.analytics.dataset.store.DatasetFetch;
import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.ReadStrategy;
import io.camunda.analytics.dataset.store.TableFetch;
import io.camunda.analytics.dataset.store.TableRow;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import io.camunda.analytics.sketch.QuantileResult;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class DatasetQueryExecutorTest {

  private static final long MINUTE = 60_000L;

  // A mixed cube: an additive count (pushed down) and a percentile sketch (streamed + app-merged).
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-mixed", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .meter(Meter.of("p95", MeterCatalog.PERCENTILE, "durationMs"))
                  .window(MINUTE)
                  .build());

  @Test
  void shouldUnionPushedDownAdditiveWithStreamedSketch() {
    // given the store returns the count rows pushed down, and streams the percentile cells
    final FakeClient client = new FakeClient();
    final DatasetQueryExecutor executor =
        new DatasetQueryExecutor(new DatasetQueryPlanner(), client);

    // when a mixed report rolls two minute windows into one 2-minute bucket, grouped by process
    final ReportResult result =
        executor.execute(
            new ReportQuery(
                List.of("bpmnProcessId"),
                0L,
                2 * MINUTE,
                2 * MINUTE,
                List.of(),
                List.of("count", "p95")),
            dataset);

    // then count comes finalized from the pushdown and p95 from the streamed+merged sketch
    assertThat(client.pushedStrategy).isEqualTo(ReadStrategy.PUSH_DOWN);
    assertThat(result.rows())
        .hasSize(2)
        .anySatisfy(
            row -> {
              assertThat(row.dimensions().get("bpmnProcessId")).isEqualTo("orders");
              assertThat(row.measures().get("count")).isEqualTo(5L);
              assertThat(((QuantileResult) row.measures().get("p95")).count()).isEqualTo(3L);
            })
        .anySatisfy(
            row -> {
              assertThat(row.dimensions().get("bpmnProcessId")).isEqualTo("ship");
              assertThat(row.measures().get("count")).isEqualTo(4L);
              assertThat(((QuantileResult) row.measures().get("p95")).count()).isEqualTo(1L);
            });
  }

  @Test
  void shouldChooseDirectWhenGranularityEqualsTierAndGroupByIsFullGrain() {
    // given a per-window read grouped by the whole grain
    final QueryPlan plan =
        new DatasetQueryPlanner()
            .plan(
                new ReportQuery(
                    List.of("bpmnProcessId"), 0L, MINUTE, MINUTE, List.of(), List.of("count")),
                dataset);

    // then the additive meter is read DIRECT (1:1 with cells), never streamed
    assertThat(plan.streamFetches()).isEmpty();
    assertThat(plan.aggregatedFetches())
        .singleElement()
        .satisfies(fetch -> assertThat(fetch.strategy()).isEqualTo(ReadStrategy.DIRECT));
  }

  private byte[] p95(final long... durationsMs) {
    final CompiledMeter meter = meter("p95");
    @SuppressWarnings("unchecked")
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) meter.bound();
    final AggregateFunction<FactRow, Object, Object> aggregate = bound.aggregate();
    Object accumulator = aggregate.createAccumulator();
    for (final long duration : durationsMs) {
      accumulator =
          aggregate.add(
              Fact.builder(FactType.PROCESS_INSTANCE).field("durationMs", duration).build(),
              accumulator);
    }
    return bound.accumulatorCodec().toBytes(accumulator);
  }

  private CompiledMeter meter(final String name) {
    for (final CompiledMeter meter : dataset.meters()) {
      if (meter.meterName().equals(name)) {
        return meter;
      }
    }
    throw new IllegalArgumentException("no meter " + name);
  }

  private DimensionKey key(final String process) {
    return DimensionKey.of(dataset.grain(), process);
  }

  /** Serves count as pushed-down finalized rows and streams the p95 cells for the sketch merge. */
  private final class FakeClient implements DatasetQueryClient {
    private ReadStrategy pushedStrategy;

    @Override
    public List<Cell> fetch(final DatasetFetch fetch) {
      return List.of();
    }

    @Override
    public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
      pushedStrategy = fetch.strategy();
      return List.of(
          new AggregatedRow(List.of("orders"), 0L, Map.of("count", 5L)),
          new AggregatedRow(List.of("ship"), 0L, Map.of("count", 4L)));
    }

    @Override
    public void streamCells(final DatasetFetch fetch, final Consumer<Cell> sink) {
      sink.accept(new Cell(key("orders"), 0L, Map.of("p95", p95(100L, 200L))));
      sink.accept(new Cell(key("orders"), MINUTE, Map.of("p95", p95(300L))));
      sink.accept(new Cell(key("ship"), 0L, Map.of("p95", p95(400L))));
    }

    @Override
    public List<TableRow> fetchRows(final TableFetch fetch) {
      return List.of();
    }

    @Override
    public void close() {}
  }
}

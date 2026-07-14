/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.metric.ExecutionTimeResult;
import io.camunda.analytics.metric.RatioResult;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.ReadStrategy;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Layer B: the additive {@code PUSH_DOWN} / sketch {@code DIRECT} reads on the RDBMS backend. */
final class RdbmsPushdownTest {

  private static final long MINUTE = 60_000L;

  private final JdbcDataSource dataSource = h2();
  private final RdbmsDatasetStore store = new RdbmsDatasetStore(dataSource);

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  private RdbmsDatasetQueryClient client() {
    return (RdbmsDatasetQueryClient) store.queryClient();
  }

  @Test
  void shouldPushDownCountRollupAsGroupBySum() {
    // given per-window counts across two minute windows for two processes
    final CompiledDataset cube =
        cube(builder("pi-count").meter(Meter.of("count", MeterCatalog.COUNT)));
    store.schemaManager().ensure(cube);
    write(cube, "count", key(cube, "orders"), 0L, counts(3));
    write(cube, "count", key(cube, "orders"), MINUTE, counts(2));
    write(cube, "count", key(cube, "ship"), 0L, counts(4));
    store.writer().flush();

    // when the store rolls the two windows up into one 2-minute bucket, grouped by process
    final List<AggregatedRow> rows =
        client()
            .fetchAggregated(
                new AggregatedFetch(
                    cube,
                    MINUTE,
                    0L,
                    2 * MINUTE,
                    List.of(),
                    List.of("count"),
                    List.of("bpmnProcessId"),
                    2 * MINUTE,
                    ReadStrategy.PUSH_DOWN));

    // then the DB summed the windows: orders = 5, ship = 4, both in bucket 0
    assertThat(rows)
        .extracting(
            r -> r.groupValues().get(0), AggregatedRow::bucket, r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 0L, 5L), Tuple.tuple("ship", 0L, 4L));
  }

  @Test
  void shouldBucketNegativeWindowStartsLikeFloorMod() {
    // given a pre-1970 window [-1m, 0) and an epoch window [0, 1m)
    final CompiledDataset cube =
        cube(builder("pi-neg").meter(Meter.of("count", MeterCatalog.COUNT)));
    store.schemaManager().ensure(cube);
    write(cube, "count", key(cube, "orders"), -MINUTE, counts(3));
    write(cube, "count", key(cube, "orders"), 0L, counts(2));
    store.writer().flush();

    // when rolled up at a 2-minute granularity spanning both
    final List<AggregatedRow> rows =
        client()
            .fetchAggregated(
                new AggregatedFetch(
                    cube,
                    MINUTE,
                    -2 * MINUTE,
                    2 * MINUTE,
                    List.of(),
                    List.of("count"),
                    List.of("bpmnProcessId"),
                    2 * MINUTE,
                    ReadStrategy.PUSH_DOWN));

    // then the negative window floors to bucket -2m (like Math.floorMod in the executor and the
    // document backend's histogram) — SQL's sign-following MOD would have put it in bucket 0
    assertThat(rows)
        .extracting(
            r -> r.groupValues().get(0), AggregatedRow::bucket, r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(
            Tuple.tuple("orders", -2 * MINUTE, 3L), Tuple.tuple("orders", 0L, 2L));
  }

  @Test
  void shouldPushDownExecutionTimeRollupWithSumMinMax() {
    // given two windows of durations for one process
    final CompiledDataset cube =
        cube(builder("pi-et").meter(Meter.of("et", MeterCatalog.EXECUTION_TIME, "durationMs")));
    store.schemaManager().ensure(cube);
    write(cube, "et", key(cube, "orders"), 0L, durations(100L, 300L));
    write(cube, "et", key(cube, "orders"), MINUTE, durations(50L, 200L));
    store.writer().flush();

    // when rolled up across both windows
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        2 * MINUTE,
                        List.of(),
                        List.of("et"),
                        List.of("bpmnProcessId"),
                        2 * MINUTE,
                        ReadStrategy.PUSH_DOWN)));

    // then count/total SUM, min MIN, max MAX — average derived on recompose
    assertThat(row.measures().get("et"))
        .isEqualTo(new ExecutionTimeResult(4L, (100.0 + 300.0 + 50.0 + 200.0) / 4.0, 50L, 300L));
  }

  @Test
  void shouldPushDownRatioRollupAsMatchedOverTotal() {
    // given four completed instances, three within a 300s SLA
    final CompiledDataset cube =
        cube(
            builder("pi-sla")
                .meter(
                    new Meter(
                        "sla",
                        MeterCatalog.RATIO,
                        "durationMs",
                        Map.of("op", "LE", "threshold", "300000"))));
    store.schemaManager().ensure(cube);
    write(cube, "sla", key(cube, "orders"), 0L, completed(100_000L, 200_000L, 300_000L, 400_000L));
    store.writer().flush();

    // when read at the tier granularity
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        MINUTE,
                        List.of(),
                        List.of("sla"),
                        List.of("bpmnProcessId"),
                        MINUTE,
                        ReadStrategy.PUSH_DOWN)));

    // then matched=3 of total=4 (ratio 0.75)
    assertThat(row.measures().get("sla")).isEqualTo(new RatioResult(3L, 4L, 0.75));
  }

  @Test
  void shouldDirectReadASketchDenormalizedValue() {
    // given a distinct sketch of three process ids for a tenant, in one window
    final CompiledDataset cube =
        new DatasetCompiler(
                MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
            .compile(
                9L,
                DatasetDeclaration.builder("pi-distinct", FactType.PROCESS_INSTANCE)
                    .dimension("tenantId", DimensionType.STRING)
                    .meter(Meter.of("distinct", MeterCatalog.DISTINCT, "bpmnProcessId"))
                    .window(MINUTE)
                    .build());
    store.schemaManager().ensure(cube);
    final List<Fact> facts = new ArrayList<>();
    for (final String process : List.of("order", "payment", "shipping")) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).field("bpmnProcessId", process).build());
    }
    write(cube, "distinct", DimensionKey.of(cube.grain(), "<default>"), 0L, facts);
    store.writer().flush();

    // when a matching-granularity DIRECT read fetches the denormalized _value column
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        MINUTE,
                        List.of(FilterPredicate.equals("tenantId", "<default>")),
                        List.of("distinct"),
                        List.of("tenantId"),
                        MINUTE,
                        ReadStrategy.DIRECT)));

    // then the finalized scalar is the distinct estimate (3), with no blob on the wire
    assertThat(row.groupValues().get(0)).isEqualTo("<default>");
    assertThat((Double) row.measures().get("distinct")).isEqualTo(3.0);
  }

  @Test
  void shouldDirectReadAQuantileHeadlineAsItsFirstDeclaredRank() {
    // given five durations in one window for a percentile meter (default ranks — p50 first)
    final CompiledDataset cube =
        cube(builder("pi-p").meter(Meter.of("p", MeterCatalog.PERCENTILE, "durationMs")));
    store.schemaManager().ensure(cube);
    write(cube, "p", key(cube, "orders"), 0L, durations(100L, 200L, 300L, 400L, 500L));
    store.writer().flush();

    // when a matching-granularity DIRECT read fetches the denormalized _value column
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        MINUTE,
                        List.of(),
                        List.of("p"),
                        List.of("bpmnProcessId"),
                        MINUTE,
                        ReadStrategy.DIRECT)));

    // then the headline is the first declared rank's value (the median, 300) — a percentile the
    // meter was declared for, not the observation count
    assertThat((Double) row.measures().get("p")).isEqualTo(300.0);
  }

  @Test
  void shouldStoreANullQuantileHeadlineWhenTheMeterHasNoObservations() {
    // given a composite written before the percentile meter was declared (the evolution seam):
    // its slot is absent, so the writer stores the meter's empty accumulator
    final CompiledDataset countOnly =
        cube(builder("pi-p-empty").meter(Meter.of("count", MeterCatalog.COUNT)));
    final CompiledDataset cube =
        cube(
            builder("pi-p-empty")
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .meter(Meter.of("p", MeterCatalog.PERCENTILE, "durationMs")));
    store.schemaManager().ensure(cube);
    store
        .writer()
        .upsertCell(
            cube,
            key(cube, "orders"),
            0L,
            cube.finestTier().windowMs(),
            fold(countOnly, counts(2)),
            WriteVersion.SEED);
    store.writer().flush();

    // when the quantile's denormalized _value is read DIRECT
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        MINUTE,
                        List.of(),
                        List.of("p"),
                        List.of("bpmnProcessId"),
                        MINUTE,
                        ReadStrategy.DIRECT)));

    // then "no observations" reads as null — never 0, which is a legitimate percentile
    assertThat(row.measures().get("p")).isNull();
  }

  @Test
  void shouldPushDownMinMaxRollupAcrossWindows() {
    // given two windows of durations for one process, on a cube with standalone min + max meters
    final CompiledDataset cube =
        cube(
            builder("pi-extrema")
                .meter(Meter.of("fastest", MeterCatalog.MIN, "durationMs"))
                .meter(Meter.of("slowest", MeterCatalog.MAX, "durationMs")));
    store.schemaManager().ensure(cube);
    write(cube, "fastest", key(cube, "orders"), 0L, durations(100L, 300L));
    write(cube, "fastest", key(cube, "orders"), MINUTE, durations(50L, 200L));
    store.writer().flush();

    // when rolled up across both windows
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        2 * MINUTE,
                        List.of(),
                        List.of("fastest", "slowest"),
                        List.of("bpmnProcessId"),
                        2 * MINUTE,
                        ReadStrategy.PUSH_DOWN)));

    // then MIN and MAX push down as native column aggregates across the windows
    assertThat(row.measures().get("fastest")).isEqualTo(50L);
    assertThat(row.measures().get("slowest")).isEqualTo(300L);
  }

  @Test
  void shouldPushDownStdDevRollupAcrossWindows() {
    // given the classic population example split across two windows: {2,4,4,4} and {5,5,7,9}
    final CompiledDataset cube =
        cube(builder("pi-spread").meter(Meter.of("spread", MeterCatalog.STDDEV, "durationMs")));
    store.schemaManager().ensure(cube);
    write(cube, "spread", key(cube, "orders"), 0L, durations(2L, 4L, 4L, 4L));
    write(cube, "spread", key(cube, "orders"), MINUTE, durations(5L, 5L, 7L, 9L));
    store.writer().flush();

    // when rolled up across both windows (SUMmed count/sum/sumSq moments, recomposed on read)
    final AggregatedRow row =
        single(
            client()
                .fetchAggregated(
                    new AggregatedFetch(
                        cube,
                        MINUTE,
                        0L,
                        2 * MINUTE,
                        List.of(),
                        List.of("spread"),
                        List.of("bpmnProcessId"),
                        2 * MINUTE,
                        ReadStrategy.PUSH_DOWN)));

    // then the population standard deviation over all 8 observations is exact: mean 5, variance 4
    assertThat(row.measures().get("spread")).isEqualTo(2.0);
  }

  private static DatasetDeclaration.Builder builder(final String name) {
    return DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
        .dimension("bpmnProcessId", DimensionType.STRING);
  }

  private static CompiledDataset cube(final DatasetDeclaration.Builder builder) {
    return new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
        .compile(1L, builder.window(MINUTE).build());
  }

  private DimensionKey key(final CompiledDataset cube, final String process) {
    return DimensionKey.of(cube.grain(), process);
  }

  private void write(
      final CompiledDataset cube,
      final String meterName,
      final DimensionKey key,
      final long windowStart,
      final List<Fact> facts) {
    store
        .writer()
        .upsertCell(
            cube,
            key,
            windowStart,
            cube.finestTier().windowMs(),
            fold(cube, facts),
            WriteVersion.SEED);
  }

  /** Folds the facts into the cube's composite accumulator — every meter slot at once. */
  private static byte[] fold(final CompiledDataset cube, final List<Fact> facts) {
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(cube.meterBounds());
    Object[] accumulator = aggregate.createAccumulator();
    for (final Fact fact : facts) {
      accumulator = aggregate.add(fact, accumulator);
    }
    return new CompositeAccumulatorValue(cube.meterBounds()).toBytes(accumulator);
  }

  private static List<Fact> counts(final int n) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).build());
    }
    return facts;
  }

  private static List<Fact> durations(final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durationsMs) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).field("durationMs", duration).build());
    }
    return facts;
  }

  private static List<Fact> completed(final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durationsMs) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .build());
    }
    return facts;
  }

  private static AggregatedRow single(final List<AggregatedRow> rows) {
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }
}

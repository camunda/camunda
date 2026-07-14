/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.ReadStrategy;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/**
 * The serving-store contract on a real document backend — the same behaviors the RDBMS store pins
 * in {@code RdbmsWriteFenceTest}, {@code RdbmsSnapshotStoreTest}, and {@code RdbmsPushdownTest}:
 * the external-version write fence (stale writes never regress a row, equal-or-newer applies), the
 * periodic-snapshot baseline/range reads (including null-dimension keys), and the pushed-down /
 * direct aggregated reads (including dimension values containing the legacy key-join delimiter,
 * numeric group-by dimensions, and null-dimension groups). Subclasses provide the concrete
 * Elasticsearch / OpenSearch store.
 */
abstract class AbstractDocumentDatasetStoreIT {

  private static final long MINUTE = 60_000L;

  abstract DocumentDatasetStore store();

  /** Makes every prior write visible to search (the stores refresh on an interval otherwise). */
  abstract void refresh();

  private static DatasetCompiler compiler() {
    return new DatasetCompiler(
        MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));
  }

  @Test
  void shouldFenceAStaleWriteAndAcceptEqualAndNewerOnes() {
    // given a cell written by the current owner at (epoch 5, offset 100)
    final CompiledDataset cube =
        compiler()
            .compile(
                101L,
                DatasetDeclaration.builder("fence-count", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(MINUTE)
                    .build());
    store().schemaManager().ensure(cube);
    final DocumentDatasetWriter writer = (DocumentDatasetWriter) store().writer();
    final DimensionKey key = DimensionKey.of(cube.grain(), "orders");
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(10)), new WriteVersion(5, 100));
    refresh();
    assertThat(servedCount(cube)).isEqualTo(10L);

    // when a fenced zombie writes a stale total — older epoch, even at a higher offset
    // (offsets are not comparable across owners; the epoch decides)
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(3)), new WriteVersion(4, 999));
    refresh();

    // then the row is untouched and the rejection was counted, not errored
    assertThat(servedCount(cube)).isEqualTo(10L);
    assertThat(writer.fencedWrites()).isEqualTo(1);

    // when a deterministic replay re-writes the identical cut (equal version)
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(10)), new WriteVersion(5, 100));
    refresh();

    // then it applied idempotently (no new fenced write)
    assertThat(servedCount(cube)).isEqualTo(10L);
    assertThat(writer.fencedWrites()).isEqualTo(1);

    // when the same owner's next cut writes a newer total
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(12)), new WriteVersion(5, 200));
    refresh();

    // then it superseded the old value
    assertThat(servedCount(cube)).isEqualTo(12L);

    // and a later owner (higher epoch, lower offset — a fresh recovery) supersedes again
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(11)), new WriteVersion(6, 50));
    refresh();
    assertThat(servedCount(cube)).isEqualTo(11L);
    assertThat(writer.fencedWrites()).isEqualTo(1);
  }

  @Test
  void shouldPushDownCountRollupAsGroupBySum() {
    // given per-window counts across two minute windows for two processes
    final CompiledDataset cube = countCube(105L, "pd-count");
    store().schemaManager().ensure(cube);
    writeCell(cube, "orders", 0L, counts(3));
    writeCell(cube, "orders", MINUTE, counts(2));
    writeCell(cube, "ship", 0L, counts(4));
    refresh();

    // when the store rolls the two windows up into one 2-minute bucket, grouped by process
    final List<AggregatedRow> rows =
        store()
            .queryClient()
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

    // then the store summed the windows: orders = 5, ship = 4, both in bucket 0
    assertThat(rows)
        .extracting(
            r -> r.groupValues().get(0), AggregatedRow::bucket, r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 0L, 5L), Tuple.tuple("ship", 0L, 4L));
  }

  @Test
  void shouldGroupByDimensionValuesContainingTheLegacyKeyDelimiter() {
    // given dimension values that contain the joined-key delimiter "__"
    final CompiledDataset cube = countCube(106L, "pd-delim");
    store().schemaManager().ensure(cube);
    writeCell(cube, "or__ders", 0L, counts(3));
    writeCell(cube, "a__b__c", 0L, counts(2));
    refresh();

    // when grouped by that dimension
    final List<AggregatedRow> rows = groupedCounts(cube, "bpmnProcessId");

    // then the values round-trip verbatim — no misalignment, no cross-column reassignment
    assertThat(rows)
        .extracting(r -> r.groupValues().get(0), r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("or__ders", 3L), Tuple.tuple("a__b__c", 2L));
  }

  @Test
  void shouldGroupByNumericDimensions() {
    // given a numeric (LONG) grain dimension
    final CompiledDataset cube =
        compiler()
            .compile(
                107L,
                DatasetDeclaration.builder("pd-version", FactType.PROCESS_INSTANCE)
                    .dimension("version", DimensionType.LONG)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(MINUTE)
                    .build());
    store().schemaManager().ensure(cube);
    store()
        .writer()
        .upsertCell(
            cube,
            DimensionKey.of(cube.grain(), 3L),
            0L,
            MINUTE,
            fold(cube, counts(5)),
            WriteVersion.SEED);
    refresh();

    // when grouped by the numeric dimension
    final List<AggregatedRow> rows = groupedCounts(cube, "version");

    // then the group value comes back as the declared Long, not a crash or a string
    assertThat(rows)
        .extracting(r -> r.groupValues().get(0), r -> r.measures().get("count"))
        .containsExactly(Tuple.tuple(3L, 5L));
  }

  @Test
  void shouldReturnTheNullDimensionGroup() {
    // given cells for a named process and for the null-dimension key
    final CompiledDataset cube = countCube(108L, "pd-null");
    store().schemaManager().ensure(cube);
    writeCell(cube, "orders", 0L, counts(3));
    store()
        .writer()
        .upsertCell(
            cube,
            DimensionKey.of(cube.grain(), Collections.singletonList(null)),
            0L,
            MINUTE,
            fold(cube, counts(2)),
            WriteVersion.SEED);
    refresh();

    // when grouped by the dimension
    final List<AggregatedRow> rows = groupedCounts(cube, "bpmnProcessId");

    // then the null group is present (as the RDBMS GROUP BY returns the NULL group) — not dropped
    assertThat(rows)
        .extracting(r -> r.groupValues().get(0), r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 3L), Tuple.tuple(null, 2L));
  }

  @Test
  void shouldDirectReadASketchScalarAndKeepAnEmptyOneAbsent() {
    // given one cell with observed durations and one cell written before the percentile meter
    // was declared (its slot is absent, so the writer stores the empty accumulator + null scalar)
    final CompiledDataset countOnly =
        compiler()
            .compile(
                109L,
                DatasetDeclaration.builder("pd-direct", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(MINUTE)
                    .build());
    final CompiledDataset cube =
        compiler()
            .compile(
                109L,
                DatasetDeclaration.builder("pd-direct", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .meter(Meter.of("p", MeterCatalog.PERCENTILE, "durationMs"))
                    .window(MINUTE)
                    .build());
    store().schemaManager().ensure(cube);
    writeCell(cube, "orders", 0L, durations(100L, 200L, 300L, 400L, 500L));
    store()
        .writer()
        .upsertCell(
            cube,
            DimensionKey.of(cube.grain(), "ship"),
            0L,
            MINUTE,
            fold(countOnly, counts(2)),
            WriteVersion.SEED);
    refresh();

    // when read DIRECT at the tier granularity
    final List<AggregatedRow> rows =
        store()
            .queryClient()
            .fetchAggregated(
                new AggregatedFetch(
                    cube,
                    MINUTE,
                    0L,
                    MINUTE,
                    List.of(),
                    List.of("count", "p"),
                    List.of("bpmnProcessId"),
                    MINUTE,
                    ReadStrategy.DIRECT));

    // then the observed cell serves its first declared rank (the median, 300) and the empty one
    // keeps the measure absent — "no observations" is never 0
    assertThat(rows)
        .extracting(
            r -> r.groupValues().get(0), r -> r.measures().get("count"), r -> r.measures().get("p"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 5L, 300.0), Tuple.tuple("ship", 2L, null));
  }

  private List<AggregatedRow> groupedCounts(final CompiledDataset cube, final String dim) {
    return store()
        .queryClient()
        .fetchAggregated(
            new AggregatedFetch(
                cube,
                MINUTE,
                0L,
                MINUTE,
                List.of(),
                List.of("count"),
                List.of(dim),
                MINUTE,
                ReadStrategy.PUSH_DOWN));
  }

  private long servedCount(final CompiledDataset cube) {
    final List<AggregatedRow> rows = groupedCounts(cube, "bpmnProcessId");
    assertThat(rows).hasSize(1);
    return (Long) rows.get(0).measures().get("count");
  }

  private CompiledDataset countCube(final long cubeId, final String name) {
    return compiler()
        .compile(
            cubeId,
            DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(MINUTE)
                .build());
  }

  private void writeCell(
      final CompiledDataset cube,
      final String process,
      final long windowStart,
      final List<Fact> facts) {
    store()
        .writer()
        .upsertCell(
            cube,
            DimensionKey.of(cube.grain(), process),
            windowStart,
            MINUTE,
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
}

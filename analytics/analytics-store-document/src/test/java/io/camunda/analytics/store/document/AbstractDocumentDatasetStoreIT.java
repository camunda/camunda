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
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.FilterPredicate;
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
import io.camunda.analytics.serving.spi.SnapshotPoint;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
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

  /** One batching writer per test — staged writes only reach the store at {@link #refresh()}. */
  private DocumentDatasetWriter writer;

  abstract DocumentDatasetStore store();

  /** Refreshes the store's indices so already-flushed writes become visible to search. */
  abstract void refreshIndices();

  @BeforeEach
  void openWriter() {
    writer = (DocumentDatasetWriter) store().writer();
  }

  /** Flushes the writer's staged batch and makes every write visible to search. */
  final void refresh() {
    writer.flush();
    refreshIndices();
  }

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
  void shouldServeSnapshotBaselineAndRangeReads() {
    // given a sparse snapshot history for two keys: "order" changes at 1m and 5m; "claim" at 2m
    final CompiledDataset cube = snapshotCube(102L);
    store().schemaManager().ensure(cube);
    writeSnapshot(cube, "order", MINUTE, 3);
    writeSnapshot(cube, "order", 5 * MINUTE, 7);
    writeSnapshot(cube, "claim", 2 * MINUTE, 1);
    refresh();

    // when the baseline is fetched between order's two change points
    final List<SnapshotPoint> baseline = store().queryClient().snapshotBaseline(cube, 3 * MINUTE);

    // then each key contributes its newest row at-or-before that time — however far back
    assertThat(baseline)
        .extracting(
            p -> p.keyValues().get(0), SnapshotPoint::sampleTime, p -> p.measures().get("active"))
        .containsExactlyInAnyOrder(
            Tuple.tuple("order", MINUTE, 3L), Tuple.tuple("claim", 2 * MINUTE, 1L));

    // when the range after 1m up to 5m is fetched
    final List<SnapshotPoint> range = store().queryClient().snapshotRange(cube, MINUTE, 5 * MINUTE);

    // then only the change points inside the range appear, ordered by key then time
    assertThat(range)
        .extracting(
            p -> p.keyValues().get(0), SnapshotPoint::sampleTime, p -> p.measures().get("active"))
        .containsExactly(
            Tuple.tuple("claim", 2 * MINUTE, 1L), Tuple.tuple("order", 5 * MINUTE, 7L));
  }

  @Test
  void shouldFenceAStaleSnapshotWrite() {
    // given a snapshot row written at (epoch 1, offset = sample time)
    final CompiledDataset cube = snapshotCube(103L);
    store().schemaManager().ensure(cube);
    writeSnapshot(cube, "order", MINUTE, 3);
    refresh();

    // when a fenced zombie re-writes an older absolute for the existing sample
    writer.upsertSnapshotRow(
        cube,
        DimensionKey.of(cube.grain(), "order"),
        MINUTE,
        levelAbsolute(cube, 999),
        new WriteVersion(0, 5));
    refresh();

    // then the row is untouched and the rejection was counted
    final List<SnapshotPoint> baseline = store().queryClient().snapshotBaseline(cube, MINUTE);
    assertThat(baseline).extracting(p -> p.measures().get("active")).containsExactly(3L);
    assertThat(writer.fencedWrites()).isEqualTo(1);
  }

  @Test
  void shouldIncludeNullDimensionKeysInSnapshotReads() {
    // given snapshot rows for a named key and for the null-dimension key
    final CompiledDataset cube = snapshotCube(104L);
    store().schemaManager().ensure(cube);
    writeSnapshot(cube, "order", MINUTE, 2);
    writeSnapshot(cube, null, MINUTE, 4);
    writeSnapshot(cube, null, 2 * MINUTE, 5);
    refresh();

    // when the baseline is fetched after both keys' newest points
    final List<SnapshotPoint> baseline = store().queryClient().snapshotBaseline(cube, 3 * MINUTE);

    // then the null-dimension key is a first-class key with its own newest row
    assertThat(baseline)
        .extracting(
            p -> p.keyValues().get(0), SnapshotPoint::sampleTime, p -> p.measures().get("active"))
        .containsExactlyInAnyOrder(
            Tuple.tuple("order", MINUTE, 2L), Tuple.tuple(null, 2 * MINUTE, 5L));

    // and the range keeps it as one contiguous run
    final List<SnapshotPoint> range = store().queryClient().snapshotRange(cube, 0L, 3 * MINUTE);
    assertThat(range)
        .extracting(p -> p.keyValues().get(0), SnapshotPoint::sampleTime)
        .containsExactly(
            Tuple.tuple(null, MINUTE), Tuple.tuple(null, 2 * MINUTE), Tuple.tuple("order", MINUTE));
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
    writer.upsertCell(
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
    writer.upsertCell(
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
    writer.upsertCell(
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

  @Test
  void shouldUpsertAndDeleteTableRows() {
    // given a projected table with two rows
    final CompiledTable table =
        compiler()
            .compileTable(
                113L,
                DatasetDeclaration.builder("pd-open-rows", FactType.PROCESS_INSTANCE)
                    .filterEquals("transition", "ACTIVATED")
                    .asTable("processInstanceKey")
                    .evictWhen(FilterPredicate.notEquals("transition", "ACTIVATED"))
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .dimension("startTime", DimensionType.LONG)
                    .build());
    store().schemaManager().ensureTable(table);
    writer.upsertRow(table, "1001", List.of("order", 1_000L), new WriteVersion(1, 1));
    writer.upsertRow(table, "1002", List.of("ship", 2_000L), new WriteVersion(1, 2));
    refresh();
    assertThat(fetchRows(table)).hasSize(2);

    // when one row is evicted (and the eviction replays — deleting an absent doc is a no-op)
    writer.deleteRow(table, "1001", new WriteVersion(1, 3));
    refresh();
    writer.deleteRow(table, "1001", new WriteVersion(1, 3));
    refresh();

    // then only the other row remains
    assertThat(fetchRows(table))
        .singleElement()
        .satisfies(row -> assertThat(row.values().get("bpmnProcessId")).isEqualTo("ship"));

    // and a staged upsert-then-evict of one key in a single flush nets to no row (deletes run
    // after the bulked upserts, and the staged ops of one key collapse onto the newest)
    writer.upsertRow(table, "1003", List.of("claim", 3_000L), new WriteVersion(1, 4));
    writer.deleteRow(table, "1003", new WriteVersion(1, 5));
    refresh();
    assertThat(fetchRows(table)).hasSize(1);
  }

  private List<TableRow> fetchRows(final CompiledTable table) {
    return store().queryClient().fetchRows(new TableFetch(table, List.of(), 100));
  }

  @Test
  void shouldChunkALargeFlushIntoMultipleBulkRequests() {
    // given more staged cells than one bulk request may carry
    final CompiledDataset cube = countCube(110L, "pd-chunk");
    store().schemaManager().ensure(cube);
    final int cells = DocumentDatasetWriter.MAX_BULK_ITEMS + 50;
    for (int w = 0; w < cells; w++) {
      writeCell(cube, "orders", w * MINUTE, counts(1));
    }

    // when they are flushed at one batch boundary
    refresh();

    // then every cell landed — the flush was split into a full chunk plus the remainder
    final List<AggregatedRow> rows =
        store()
            .queryClient()
            .fetchAggregated(
                new AggregatedFetch(
                    cube,
                    MINUTE,
                    0L,
                    cells * MINUTE,
                    List.of(),
                    List.of("count"),
                    List.of("bpmnProcessId"),
                    cells * MINUTE,
                    ReadStrategy.PUSH_DOWN));
    assertThat(rows)
        .extracting(r -> r.groupValues().get(0), r -> r.measures().get("count"))
        .containsExactly(Tuple.tuple("orders", (long) cells));
    assertThat(writer.fencedWrites()).isZero();
  }

  @Test
  void shouldApplySiblingWritesWhenOneItemOfTheFlushIsFenced() {
    // given a cell owned at (epoch 5, offset 100)
    final CompiledDataset cube = countCube(111L, "pd-mixed");
    store().schemaManager().ensure(cube);
    writer.upsertCell(
        cube,
        DimensionKey.of(cube.grain(), "orders"),
        0L,
        MINUTE,
        fold(cube, counts(10)),
        new WriteVersion(5, 100));
    refresh();

    // when one flush carries a fenced zombie's stale overwrite next to a fresh sibling write
    writer.upsertCell(
        cube,
        DimensionKey.of(cube.grain(), "orders"),
        0L,
        MINUTE,
        fold(cube, counts(3)),
        new WriteVersion(4, 999));
    writer.upsertCell(
        cube,
        DimensionKey.of(cube.grain(), "ship"),
        0L,
        MINUTE,
        fold(cube, counts(4)),
        new WriteVersion(5, 100));
    refresh();

    // then the stale item was rejected and counted while its sibling applied
    assertThat(groupedCounts(cube, "bpmnProcessId"))
        .extracting(r -> r.groupValues().get(0), r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 10L), Tuple.tuple("ship", 4L));
    assertThat(writer.fencedWrites()).isEqualTo(1);
  }

  @Test
  void shouldKeepTheNewestWriteWhenTheSameCellIsUpsertedTwiceInOneFlush() {
    // given the same cell upserted twice between two batch boundaries
    final CompiledDataset cube = countCube(112L, "pd-lww");
    store().schemaManager().ensure(cube);
    final DimensionKey key = DimensionKey.of(cube.grain(), "orders");
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(3)), new WriteVersion(5, 100));
    writer.upsertCell(cube, key, 0L, MINUTE, fold(cube, counts(7)), new WriteVersion(5, 200));

    // when the batch is flushed
    refresh();

    // then the newest write won and nothing was fenced (the older write never conflicts)
    assertThat(servedCount(cube)).isEqualTo(7L);
    assertThat(writer.fencedWrites()).isZero();
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

  private CompiledDataset snapshotCube(final long cubeId) {
    return compiler()
        .compile(
            cubeId,
            DatasetDeclaration.builder("active-instances-" + cubeId, FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(Meter.of("active", MeterCatalog.LEVEL, "delta"))
                .window(MINUTE)
                .snapshots(MINUTE)
                .build());
  }

  private void writeCell(
      final CompiledDataset cube,
      final String process,
      final long windowStart,
      final List<Fact> facts) {
    writer.upsertCell(
        cube,
        DimensionKey.of(cube.grain(), process),
        windowStart,
        MINUTE,
        fold(cube, facts),
        WriteVersion.SEED);
  }

  private void writeSnapshot(
      final CompiledDataset cube, final String process, final long sampleTime, final int level) {
    writer.upsertSnapshotRow(
        cube,
        DimensionKey.of(cube.grain(), Collections.singletonList(process)),
        sampleTime,
        levelAbsolute(cube, level),
        new WriteVersion(1, sampleTime));
  }

  /** The cumulative absolute a snapshot row stores, folded as one signed delta. */
  private static byte[] levelAbsolute(final CompiledDataset cube, final long level) {
    return fold(
        cube, List.of(Fact.builder(FactType.PROCESS_INSTANCE).field("delta", level).build()));
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

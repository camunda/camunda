/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.query.SnapshotQueryExecutor.SnapshotQuery;
import io.camunda.analytics.query.SnapshotQueryExecutor.SnapshotSeriesPoint;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.SnapshotPoint;
import io.camunda.analytics.serving.spi.TableFetch;
import io.camunda.analytics.serving.spi.TableRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The carry-forward walk (ADR 0010): the baseline is the opening balance, sparse in-range points
 * step the series, silent stretches carry the last value, a baseline-only key yields a flat line,
 * and a key with no point at-or-before a bucket is absent from it.
 */
final class SnapshotQueryExecutorTest {

  private static final long MINUTE = 60_000L;

  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("active-instances", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("active", MeterCatalog.LEVEL, "delta"))
                  .window(MINUTE)
                  .snapshots(MINUTE)
                  .build());

  @Test
  void shouldCarryTheBaselineForwardAndStepOnEachChangePoint() {
    // given a baseline before the range and one change point inside it
    final DatasetQueryClient client =
        new StubClient(List.of(point("order", 0L, 2L)), List.of(point("order", 3 * MINUTE, 5L)));

    // when a 5-bucket series is materialised over (0, 5m]
    final List<SnapshotSeriesPoint> series =
        new SnapshotQueryExecutor(client)
            .execute(new SnapshotQuery(0L, 5 * MINUTE, MINUTE), dataset);

    // then the value carries forward from the baseline and steps at the change point
    assertThat(series)
        .extracting(SnapshotSeriesPoint::time, p -> p.measures().get("active"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(MINUTE, 2L),
            org.assertj.core.groups.Tuple.tuple(2 * MINUTE, 2L),
            org.assertj.core.groups.Tuple.tuple(3 * MINUTE, 5L),
            org.assertj.core.groups.Tuple.tuple(4 * MINUTE, 5L),
            org.assertj.core.groups.Tuple.tuple(5 * MINUTE, 5L));
  }

  @Test
  void shouldOmitAKeyUntilItsFirstPointAndFlatlineABaselineOnlyKey() {
    // given one key that only appears mid-range and one that only has a baseline
    final DatasetQueryClient client =
        new StubClient(List.of(point("steady", 0L, 9L)), List.of(point("late", 2 * MINUTE, 1L)));

    // when
    final List<SnapshotSeriesPoint> series =
        new SnapshotQueryExecutor(client)
            .execute(new SnapshotQuery(0L, 3 * MINUTE, MINUTE), dataset);

    // then "steady" is a flat line over every bucket; "late" starts existing at its first point
    assertThat(series)
        .extracting(
            p -> p.keyValues().get(0), SnapshotSeriesPoint::time, p -> p.measures().get("active"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("steady", MINUTE, 9L),
            org.assertj.core.groups.Tuple.tuple("steady", 2 * MINUTE, 9L),
            org.assertj.core.groups.Tuple.tuple("steady", 3 * MINUTE, 9L),
            org.assertj.core.groups.Tuple.tuple("late", 2 * MINUTE, 1L),
            org.assertj.core.groups.Tuple.tuple("late", 3 * MINUTE, 1L));
  }

  @Test
  void shouldTrimTheRangeToTheLastFullBucket() {
    // given a range that is not a granularity multiple (4.5 minutes at 1-minute buckets)
    final RecordingClient client =
        new RecordingClient(List.of(point("order", 0L, 2L)), List.of(point("order", MINUTE, 3L)));

    // when the series is materialised over (0, 4.5m]
    final List<SnapshotSeriesPoint> series =
        new SnapshotQueryExecutor(client)
            .execute(new SnapshotQuery(0L, 4 * MINUTE + MINUTE / 2, MINUTE), dataset);

    // then only full buckets are emitted (all on the grid, none past 4m) and the range fetch was
    // trimmed to the last grid boundary — rows past it could never have been emitted
    assertThat(series)
        .extracting(SnapshotSeriesPoint::time)
        .containsExactly(MINUTE, 2 * MINUTE, 3 * MINUTE, 4 * MINUTE);
    assertThat(client.rangeToMs).isEqualTo(4 * MINUTE);
  }

  @Test
  void shouldReturnNothingForARangeShorterThanOneBucket() {
    // given a non-empty range that contains no grid boundary
    final RecordingClient client = new RecordingClient(List.of(point("order", 0L, 2L)), List.of());

    // when
    final List<SnapshotSeriesPoint> series =
        new SnapshotQueryExecutor(client)
            .execute(new SnapshotQuery(0L, MINUTE / 2, MINUTE), dataset);

    // then there is no grid point to emit (and no fetch was issued at all)
    assertThat(series).isEmpty();
    assertThat(client.rangeToMs).isNull();
  }

  @Test
  void shouldRejectAQueryWithoutSnapshotsOrOffGrid() {
    final DatasetQueryClient client = new StubClient(List.of(), List.of());
    final SnapshotQueryExecutor executor = new SnapshotQueryExecutor(client);

    final CompiledDataset plain =
        new DatasetCompiler(
                MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
            .compile(
                2L,
                DatasetDeclaration.builder("plain", FactType.PROCESS_INSTANCE)
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .meter(Meter.of("count", MeterCatalog.COUNT))
                    .window(MINUTE)
                    .build());
    assertThatThrownBy(() -> executor.execute(new SnapshotQuery(0L, 5 * MINUTE, MINUTE), plain))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declares no snapshots");

    assertThatThrownBy(() -> executor.execute(new SnapshotQuery(0L, 5 * MINUTE, 90_000L), dataset))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multiple of the cube's sample interval");
  }

  private static SnapshotPoint point(final String key, final long time, final long active) {
    return new SnapshotPoint(List.of(key), time, Map.of("active", active));
  }

  /** A stub that additionally records the upper bound of the range fetch (null = not fetched). */
  private static final class RecordingClient extends ClientBase {
    private Long rangeToMs;

    private RecordingClient(final List<SnapshotPoint> baseline, final List<SnapshotPoint> range) {
      super(baseline, range);
    }

    @Override
    public List<SnapshotPoint> snapshotRange(
        final CompiledDataset dataset, final long fromMs, final long toMs) {
      rangeToMs = toMs;
      return super.snapshotRange(dataset, fromMs, toMs);
    }
  }

  /** A stub client serving canned snapshot reads; every other read is out of scope here. */
  private static final class StubClient extends ClientBase {
    private StubClient(final List<SnapshotPoint> baseline, final List<SnapshotPoint> range) {
      super(baseline, range);
    }
  }

  private static class ClientBase implements DatasetQueryClient {

    private final List<SnapshotPoint> baseline;
    private final List<SnapshotPoint> range;

    private ClientBase(final List<SnapshotPoint> baseline, final List<SnapshotPoint> range) {
      this.baseline = baseline;
      this.range = range;
    }

    @Override
    public List<SnapshotPoint> snapshotBaseline(final CompiledDataset dataset, final long atMs) {
      return baseline;
    }

    @Override
    public List<SnapshotPoint> snapshotRange(
        final CompiledDataset dataset, final long fromMs, final long toMs) {
      return range;
    }

    @Override
    public List<Cell> fetch(final DatasetFetch fetch) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AggregatedRow> fetchAggregated(final AggregatedFetch fetch) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<TableRow> fetchRows(final TableFetch fetch) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {}
  }
}

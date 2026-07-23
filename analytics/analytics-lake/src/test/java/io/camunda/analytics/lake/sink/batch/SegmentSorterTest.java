/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SegmentSorterTest {

  // Matches SegmentSorter's own day-bucketing divisor: family-day source columns carry epoch
  // microseconds (see TableSchema.Column#logicalType()), not milliseconds.
  private static final long MICROS_PER_DAY = 86_400_000_000L;

  // columns: ts (family day source, not a sort key), entityId (primary sort key),
  // kind (secondary sort key, STRING_DICT, compared by code), count (nullable, not sort key),
  // payload (nullable BINARY, not sort key)
  private static TableSchema schema() {
    return new TableSchema(
        "sorter_test",
        List.of(
            new TableSchema.Column("ts", ColumnType.LONG, 1, false, -1, true),
            new TableSchema.Column("entity_id", ColumnType.LONG, 2, false, 0, false),
            new TableSchema.Column("kind", ColumnType.STRING_DICT, 3, false, 1, false),
            new TableSchema.Column("count", ColumnType.INT, 4, true, -1, false),
            new TableSchema.Column("payload", ColumnType.BINARY, 5, true, -1, false)));
  }

  private record Row(long ts, long entityId, String kind, Integer count, byte[] payload) {}

  /** Appends every row into a single segment via the real ring + appender, sharing one interner. */
  private static Segment fillSegment(final List<Row> rows, final Interner interner) {
    final Segment[] segments =
        SegmentFactory.createSegments(
            schema(), 2, rows.size(), new int[] {0, 0, 0, 0, 16}, interner);
    final ColumnarSegmentRing ring = new ColumnarSegmentRing(segments, new NoopGate());
    final RowAppender appender = new SegmentRowAppender(ring);
    for (final Row row : rows) {
      assertThat(appender.begin()).isTrue();
      appender.putLong(0, row.ts());
      appender.putLong(1, row.entityId());
      appender.putDict(2, row.kind());
      if (row.count() == null) {
        appender.putNull(3);
      } else {
        appender.putInt(3, row.count());
      }
      if (row.payload() == null) {
        appender.putNull(4);
      } else {
        appender.putBinary(4, row.payload(), 0, row.payload().length);
      }
      appender.endRow();
    }
    return ring.filling();
  }

  /**
   * {@code entityId} is set to the row index, i.e. unique across the whole batch: the sorter is not
   * required to be stable (it is a plain quicksort), so any test that compares its output
   * index-for-index against a reference {@link java.util.List#sort} must avoid sort-key ties —
   * otherwise the two algorithms are free to (and will) order tied rows differently, which is not a
   * bug in either.
   */
  private static List<Row> randomRows(final Random random, final int count, final int dayCount) {
    final String[] kinds = {"alpha", "beta", "gamma", "delta"};
    final List<Row> rows = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final long day = random.nextInt(dayCount);
      final long ts = day * MICROS_PER_DAY + random.nextInt(1000);
      final long entityId = i;
      final String kind = kinds[random.nextInt(kinds.length)];
      final Integer count2 = random.nextBoolean() ? random.nextInt(1000) : null;
      final byte[] payload =
          random.nextBoolean() ? ("p" + i).getBytes(StandardCharsets.UTF_8) : null;
      rows.add(new Row(ts, entityId, kind, count2, payload));
    }
    return rows;
  }

  @Test
  void shouldSortRandomRowsMatchingAReferenceSort() {
    // given a batch of random rows and the same shared interner used to build the reference order
    final Random random = new Random(42);
    final Interner interner = new Interner();
    final List<Row> rows = randomRows(random, 500, 4);
    final Segment segment = fillSegment(rows, interner);

    // when
    final SegmentSorter sorter =
        new SegmentSorter(schema(), rows.size(), new int[] {0, 0, 0, 0, 16}, interner);
    final SortedRun run = sorter.sort(segment);

    // then: build the expected order with plain Java sorting of row tuples, using the SAME
    // interner (idempotent) to resolve dict codes exactly as the sorter does
    final List<Row> expected = new ArrayList<>(rows);
    expected.sort(
        Comparator.<Row>comparingLong(r -> Math.floorDiv(r.ts(), MICROS_PER_DAY))
            .thenComparingLong(Row::entityId)
            .thenComparingInt(r -> interner.intern(r.kind())));

    assertThat(run.size()).isEqualTo(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      final Row expectedRow = expected.get(i);
      assertThat(run.longAt(1, i)).as("entityId at %d", i).isEqualTo(expectedRow.entityId());
      assertThat(run.stringAt(2, i)).as("kind at %d", i).isEqualTo(expectedRow.kind());
      if (expectedRow.count() == null) {
        assertThat(run.isNullAt(3, i)).as("count null at %d", i).isTrue();
      } else {
        assertThat(run.isNullAt(3, i)).isFalse();
        assertThat(run.intAt(3, i)).isEqualTo(expectedRow.count());
      }
      if (expectedRow.payload() == null) {
        assertThat(run.isNullAt(4, i)).as("payload null at %d", i).isTrue();
      } else {
        assertThat(run.isNullAt(4, i)).isFalse();
        final byte[] dst = new byte[run.binaryLength(4, i)];
        run.copyBinaryTo(4, i, dst, 0);
        assertThat(dst).isEqualTo(expectedRow.payload());
      }
    }
  }

  @Test
  void shouldComputeDayRangesForThreeInterleavedDays() {
    // given 5 rows per day for 3 distinct days, appended in interleaved (not day-grouped) order
    final Interner interner = new Interner();
    final List<Row> rows = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      for (int day = 0; day < 3; day++) {
        rows.add(new Row(day * MICROS_PER_DAY + i, day * 100L + i, "kind", null, null));
      }
    }
    final Segment segment = fillSegment(rows, interner);

    // when
    final SegmentSorter sorter =
        new SegmentSorter(schema(), rows.size(), new int[] {0, 0, 0, 0, 16}, interner);
    final SortedRun run = sorter.sort(segment);

    // then: exactly 3 contiguous, ascending, non-overlapping ranges of 5 rows each
    final List<SortedRun.DayRange> ranges = run.dayRanges();
    assertThat(ranges).hasSize(3);
    assertThat(ranges.get(0).epochDay()).isEqualTo(0L);
    assertThat(ranges.get(1).epochDay()).isEqualTo(1L);
    assertThat(ranges.get(2).epochDay()).isEqualTo(2L);
    int expectedFrom = 0;
    for (final SortedRun.DayRange range : ranges) {
      assertThat(range.fromIndex()).isEqualTo(expectedFrom);
      assertThat(range.toIndex() - range.fromIndex()).isEqualTo(5);
      expectedFrom = range.toIndex();
    }
    assertThat(expectedFrom).isEqualTo(rows.size());
  }

  @Test
  void shouldBeDeterministicAcrossTwoRuns() {
    // given the same row content appended into two independent segments, sharing one interner so
    // dict codes line up identically between the two runs
    final Random random = new Random(7);
    final Interner interner = new Interner();
    final List<Row> rows = randomRows(random, 300, 3);
    final Segment segmentA = fillSegment(rows, interner);
    final Segment segmentB = fillSegment(rows, interner);

    // when sorted by two separate SegmentSorter instances
    final SortedRun runA =
        new SegmentSorter(schema(), rows.size(), new int[] {0, 0, 0, 0, 16}, interner)
            .sort(segmentA);
    final long[] entityOrderA = extractEntityIds(runA);

    final SortedRun runB =
        new SegmentSorter(schema(), rows.size(), new int[] {0, 0, 0, 0, 16}, interner)
            .sort(segmentB);
    final long[] entityOrderB = extractEntityIds(runB);

    // then the resulting order is identical
    assertThat(entityOrderB).isEqualTo(entityOrderA);
    assertThat(runB.dayRanges()).isEqualTo(runA.dayRanges());
  }

  @Test
  void shouldProduceCorrectResultsAcrossConsecutiveSortsReusingScratch() {
    // given one SegmentSorter instance reused for two different segments (as the flush thread
    // would across successive seals)
    final Random random = new Random(99);
    final Interner interner = new Interner();
    final List<Row> firstRows = randomRows(random, 200, 3);
    final List<Row> secondRows = randomRows(random, 80, 3); // smaller, to probe stale-scratch leaks

    final int[] binaryAvgBytes = {0, 0, 0, 0, 16};
    final SegmentSorter sorter = new SegmentSorter(schema(), 200, binaryAvgBytes, interner);

    final Segment firstSegment = fillSegment(firstRows, interner);
    sorter.sort(firstSegment);
    // the first result must be fully consumed before the next sort() call invalidates it
    final long[] firstEntityOrder = extractEntityIds(sorter);
    assertThat(firstEntityOrder).hasSize(firstRows.size());
    assertThat(sorter.dayRanges().get(sorter.dayRanges().size() - 1).toIndex())
        .isEqualTo(firstRows.size());

    // when: sort a second, smaller segment with the same reused sorter (scratch reuse)
    final Segment secondSegment = fillSegment(secondRows, interner);
    final SortedRun secondRun = sorter.sort(secondSegment);

    // then the second result is correct on its own terms, unaffected by the first sort's leftovers
    assertThat(secondRun.size()).isEqualTo(secondRows.size());
    final List<Row> expectedSecond = new ArrayList<>(secondRows);
    expectedSecond.sort(
        Comparator.<Row>comparingLong(r -> Math.floorDiv(r.ts(), MICROS_PER_DAY))
            .thenComparingLong(Row::entityId)
            .thenComparingInt(r -> interner.intern(r.kind())));
    for (int i = 0; i < expectedSecond.size(); i++) {
      assertThat(secondRun.longAt(1, i)).isEqualTo(expectedSecond.get(i).entityId());
    }
    assertThat(secondRun.dayRanges().get(secondRun.dayRanges().size() - 1).toIndex())
        .isEqualTo(secondRows.size());
  }

  @Test
  void shouldRejectADoubleColumnDeclaredAsASortKey() {
    // given a schema whose sort key names a DOUBLE column -- profile-shaped measures never sort on
    // values (see ColumnType.DOUBLE's own javadoc)
    final TableSchema badSchema =
        new TableSchema(
            "bad_sort_key",
            List.of(
                new TableSchema.Column("ts", ColumnType.LONG, 1, false, -1, true),
                new TableSchema.Column("value", ColumnType.DOUBLE, 2, false, 0, false)));

    // when / then: rejected eagerly at construction, not lazily on first compare
    assertThatThrownBy(() -> new SegmentSorter(badSchema, 8, new int[] {0, 0}, new Interner()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("value")
        .hasMessageContaining("DOUBLE");
  }

  private static long[] extractEntityIds(final SortedRun run) {
    final long[] result = new long[run.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = run.longAt(1, i);
    }
    return result;
  }

  private static final class NoopGate implements BackpressureGate {
    @Override
    public void pause() {}

    @Override
    public void resume() {}
  }
}

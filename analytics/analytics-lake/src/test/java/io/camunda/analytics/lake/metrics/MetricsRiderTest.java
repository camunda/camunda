/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Metrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MetricsRiderTest {

  private static final long MINUTE_MICROS = 60_000_000L;
  private static final long SLOT_A = 1_784_700_000_000_000L / MINUTE_MICROS * MINUTE_MICROS;
  private static final long SLOT_B = SLOT_A + MINUTE_MICROS;

  private static final TableSchema RAW =
      new TableSchema(
          "activities",
          List.of(
              new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, 0, false),
              new TableSchema.Column("element_id", ColumnType.STRING_DICT, 2, false, 1, false),
              new TableSchema.Column(
                  "started_at",
                  ColumnType.LONG,
                  3,
                  false,
                  2,
                  true,
                  TableSchema.LogicalType.TIMESTAMPTZ),
              new TableSchema.Column(
                  "ended_at",
                  ColumnType.LONG,
                  4,
                  true,
                  -1,
                  false,
                  TableSchema.LogicalType.TIMESTAMPTZ),
              new TableSchema.Column("duration_ms", ColumnType.LONG, 5, true, -1, false)));

  private CompiledEntityMetrics compiled;
  private RecordingEncoderFactory encoders;
  private MetricsRider rider;

  @BeforeEach
  void setUp() {
    compiled =
        EntityMetrics.declare("activities", RAW)
            .dims("process_id", "element_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    encoders = new RecordingEncoderFactory();
    rider = new MetricsRider(compiled, encoders, 128);
  }

  @Test
  void shouldFoldGroupsAcrossSegmentsOfOneWindowAndDrainOnce() {
    // given -- two sealed segments of the same flush window touching the same group
    rider.onSealed(
        run(
            row("order", "approve", SLOT_A + 5, 4200L),
            row("order", "approve", SLOT_A + 6, 4300L),
            row("order", "ship", SLOT_A + 7, 100L)));
    rider.onSealed(run(row("order", "approve", SLOT_A + 8, 5000L)));

    // when
    final Map<String, List<DataFileResult>> derived = rider.onWindowClose();

    // then -- one metrics row per group, counts accumulated ACROSS segments
    assertThat(derived).containsOnlyKeys("activities_metrics", "activities_hist");
    final List<Map<String, Object>> metrics = encoders.rows("activities_metrics");
    assertThat(metrics).hasSize(2);
    final Map<String, Object> approve = rowFor(metrics, "approve");
    assertThat(approve)
        .containsEntry("window_start", SLOT_A)
        .containsEntry("process_id", "order")
        .containsEntry("duration_ms_cnt", 3L)
        .containsEntry("duration_ms_sum", 13_500L)
        .containsEntry("duration_ms_min", 4200L)
        .containsEntry("duration_ms_max", 5000L);

    // and -- hist rows carry denormalized bins whose counts sum to the group's cnt
    final List<Map<String, Object>> hist = encoders.rows("activities_hist");
    final long approveBinTotal =
        hist.stream()
            .filter(r -> "approve".equals(r.get("element_id")))
            .mapToLong(r -> (Long) r.get("cnt"))
            .sum();
    assertThat(approveBinTotal).isEqualTo(3L);
    assertThat(hist)
        .allSatisfy(
            r -> {
              assertThat(r.get("measure")).isEqualTo("duration_ms");
              assertThat(r.get("scheme")).isEqualTo("exp2ll-3");
              assertThat((Long) r.get("bin_lo")).isLessThan((Long) r.get("bin_hi"));
            });
  }

  @Test
  void shouldAssignRowsToTheirEventTimeSlots() {
    // given -- same group key, different minutes
    rider.onSealed(
        run(row("order", "approve", SLOT_A + 1, 100L), row("order", "approve", SLOT_B + 1, 200L)));

    // when
    rider.onWindowClose();

    // then -- two separate slot rows, never merged at write time
    final List<Map<String, Object>> metrics = encoders.rows("activities_metrics");
    assertThat(metrics).hasSize(2);
    assertThat(metrics)
        .extracting(r -> r.get("window_start"))
        .containsExactlyInAnyOrder(SLOT_A, SLOT_B);
  }

  @Test
  void shouldSkipRowsWithoutEventTimeAndNullMeasuresPerMeasure() {
    // given -- one row with no ended_at (open), one with null duration
    rider.onSealed(
        run(
            row("order", "approve", null, 999L), // no slot -> skipped entirely
            row("order", "approve", SLOT_A + 1, null), // group exists, measure skipped
            row("order", "approve", SLOT_A + 2, 300L)));

    // when
    rider.onWindowClose();

    // then
    final List<Map<String, Object>> metrics = encoders.rows("activities_metrics");
    assertThat(metrics).hasSize(1);
    assertThat(metrics.get(0))
        .containsEntry("duration_ms_cnt", 1L)
        .containsEntry("duration_ms_sum", 300L);
  }

  @Test
  void shouldStartTheNextWindowEmpty() {
    // given
    rider.onSealed(run(row("order", "approve", SLOT_A + 1, 100L)));
    rider.onWindowClose();
    encoders.reset();

    // when -- a second window with one different row
    rider.onSealed(run(row("order", "ship", SLOT_B + 1, 50L)));
    final Map<String, List<DataFileResult>> derived = rider.onWindowClose();

    // then -- nothing from the first window leaks into the second
    assertThat(derived).isNotEmpty();
    final List<Map<String, Object>> metrics = encoders.rows("activities_metrics");
    assertThat(metrics).hasSize(1);
    assertThat(metrics.get(0))
        .containsEntry("element_id", "ship")
        .containsEntry("duration_ms_cnt", 1L);
  }

  @Test
  void shouldReturnEmptyForAnUntouchedWindow() {
    assertThat(rider.onWindowClose()).isEmpty();
  }

  @Test
  void shouldSurviveManyGroupsBeyondOneDrainSegment() {
    // given -- more groups than the drain segment capacity (128)
    final List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      rows.add(row("proc-" + i, "task", SLOT_A + i, (long) i + 1));
    }
    rider.onSealed(run(rows.toArray(Object[][]::new)));

    // when
    rider.onWindowClose();

    // then -- mid-drain segment flushes preserved every group
    assertThat(encoders.rows("activities_metrics")).hasSize(500);
  }

  // ------------------------------------------------------------------
  // fixtures
  // ------------------------------------------------------------------

  private static Object[] row(
      final String process, final String element, final Long endedMicros, final Long durationMs) {
    return new Object[] {process, element, SLOT_A - MINUTE_MICROS, endedMicros, durationMs};
  }

  private static Map<String, Object> rowFor(
      final List<Map<String, Object>> rows, final String elementId) {
    return rows.stream()
        .filter(r -> elementId.equals(r.get("element_id")))
        .findFirst()
        .orElseThrow();
  }

  /** Minimal in-memory {@link SortedRun} over object rows (schema = {@link #RAW}). */
  private static SortedRun run(final Object[]... rows) {
    return new SortedRun() {
      @Override
      public TableSchema schema() {
        return RAW;
      }

      @Override
      public int size() {
        return rows.length;
      }

      @Override
      public boolean isNullAt(final int column, final int i) {
        return rows[i][column] == null;
      }

      @Override
      public long longAt(final int column, final int i) {
        return (Long) rows[i][column];
      }

      @Override
      public int intAt(final int column, final int i) {
        return (Integer) rows[i][column];
      }

      @Override
      public String stringAt(final int column, final int i) {
        return (String) rows[i][column];
      }

      @Override
      public int binaryLength(final int column, final int i) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int copyBinaryTo(final int column, final int i, final byte[] dst, final int offset) {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<DayRange> dayRanges() {
        throw new UnsupportedOperationException("the rider never reads day ranges");
      }
    };
  }

  /** Captures every appended row per table instead of writing Parquet. */
  private static final class RecordingEncoderFactory implements BatchEncoder.Factory {

    private final Map<String, List<Map<String, Object>>> rowsByTable = new HashMap<>();

    List<Map<String, Object>> rows(final String table) {
      return rowsByTable.getOrDefault(table, List.of());
    }

    void reset() {
      rowsByTable.clear();
    }

    @Override
    public BatchEncoder newFile(final TableSchema schema, final long epochDay) {
      final List<Map<String, Object>> sink =
          rowsByTable.computeIfAbsent(schema.table(), t -> new ArrayList<>());
      return new BatchEncoder() {
        private long rowCount;

        @Override
        public void append(final SortedRun run, final int fromIndex, final int toIndex) {
          for (int i = fromIndex; i < toIndex; i++) {
            final Map<String, Object> row = new HashMap<>();
            for (int c = 0; c < schema.columns().size(); c++) {
              final TableSchema.Column column = schema.columns().get(c);
              if (run.isNullAt(c, i)) {
                row.put(column.name(), null);
              } else if (column.type() == ColumnType.STRING_DICT) {
                row.put(column.name(), run.stringAt(c, i));
              } else if (column.type() == ColumnType.INT) {
                row.put(column.name(), run.intAt(c, i));
              } else {
                row.put(column.name(), run.longAt(c, i));
              }
            }
            sink.add(row);
            rowCount++;
          }
        }

        @Override
        public DataFileResult finish() {
          return new DataFileResult(
              schema.table(),
              "mem://" + schema.table() + "/" + epochDay,
              rowCount,
              rowCount * 64,
              new Metrics(rowCount, null, null, null, null),
              epochDay);
        }

        @Override
        public void abort() {}
      };
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * {@link PollFedRider}'s own alignment contract: the swap ({@link
 * io.camunda.analytics.lake.sink.SealRider#onPollBoundary}/{@link
 * io.camunda.analytics.lake.sink.SealRider#rollbackPollBoundary}) freezes exactly the records
 * folded before it, never the ones after — the load-bearing property {@link SinkPipeline} relies on
 * to keep a record-fed rider's increments aligned with the descriptor offset range they were folded
 * under (see the class's own javadoc).
 */
final class PollFedRiderTest {

  private static final long MINUTE_MICROS = 60_000_000L;
  private static final long SLOT_A = 1_784_700_000_000_000L / MINUTE_MICROS * MINUTE_MICROS;

  private static final int PROCESS_ID = 0;
  private static final int FLOW_ID = 1;

  private static TableSchema virtualFlowSchema() {
    return new TableSchema(
        "flows_test",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column("flow_id", ColumnType.STRING_DICT, 2, false, -1, false),
            new TableSchema.Column(
                "taken_at",
                ColumnType.LONG,
                3,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ)));
  }

  private CompiledEntityMetrics compiled;
  private RecordingEncoderFactory encoders;
  private PollFedRider rider;

  @BeforeEach
  void setUp() {
    compiled =
        EntityMetrics.declare("flows_test", virtualFlowSchema())
            .dims("process_id", "flow_id")
            .window(Duration.ofMinutes(1), "taken_at")
            .count()
            .build();
    encoders = new RecordingEncoderFactory();
    rider = new PollFedRider(compiled, encoders, 128);
  }

  @Test
  void shouldFreezeOnlyRecordsFoldedBeforeTheBoundary() {
    // given: two records folded, then the host pipeline seals a boundary (swap)
    fold("order", "flow-a", SLOT_A + 5);
    fold("order", "flow-a", SLOT_A + 6);
    rider.onPollBoundary();
    // and: a third record folds AFTER the boundary, into the fresh active window
    fold("order", "flow-a", SLOT_A + 7);

    // when: the frozen window (only) is drained
    final Map<String, List<DataFileResult>> derived = rider.onWindowClose();

    // then: exactly the two pre-boundary folds are counted -- the third is not in this drain
    assertThat(derived).containsOnlyKeys("flows_test_metrics");
    final List<Map<String, Object>> rows = encoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 2L).containsEntry("flow_id", "flow-a");
  }

  @Test
  void shouldDrainTheAfterBoundaryRecordInTheNextWindowIndependently() {
    // given: the exact scenario above, first window already drained
    fold("order", "flow-a", SLOT_A + 5);
    rider.onPollBoundary();
    fold("order", "flow-a", SLOT_A + 7);
    rider.onWindowClose();
    encoders.reset();

    // when: a second boundary freezes the (previously after-boundary) record
    rider.onPollBoundary();
    final Map<String, List<DataFileResult>> derived = rider.onWindowClose();

    // then: nothing from the first window leaks into the second
    assertThat(derived).isNotEmpty();
    final List<Map<String, Object>> rows = encoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 1L);
  }

  @Test
  void shouldRollbackASpeculativeSwapAsIfItNeverHappened() {
    // given: one record folded, then a speculative swap that gets rolled back (ring was full)
    fold("order", "flow-a", SLOT_A + 1);
    rider.onPollBoundary();
    rider.rollbackPollBoundary();

    // when: another record folds -- it must land back in the SAME (restored) active window
    fold("order", "flow-a", SLOT_A + 2);
    rider.onPollBoundary();
    rider.onWindowClose();

    // then: both folds ended up in the one real boundary's frozen window, not split across two
    final List<Map<String, Object>> rows = encoders.rows("flows_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 2L);
  }

  @Test
  void shouldReturnEmptyWhenNoBoundaryHasEverSwappedAnything() {
    fold("order", "flow-a", SLOT_A + 1);
    // no onPollBoundary() call at all -- nothing has ever been frozen
    assertThat(rider.onWindowClose()).isEmpty();
  }

  @Test
  void shouldReportPendingDataOnlyAfterANonEmptySwap() {
    assertThat(rider.hasPendingPollFedData()).isFalse();

    // an empty swap (a boundary with nothing folded since the last one) is not pending data --
    // drained immediately so the queue does not carry it into the next check
    rider.onPollBoundary();
    assertThat(rider.hasPendingPollFedData()).isFalse();
    rider.onWindowClose();

    fold("order", "flow-a", SLOT_A + 1);
    rider.onPollBoundary(); // swaps a non-empty window
    assertThat(rider.hasPendingPollFedData()).isTrue();

    rider.onWindowClose();
    assertThat(rider.hasPendingPollFedData()).isFalse(); // drained, queue empty again
  }

  @Test
  void shouldSupportANullableDictDimAsAnHonestNullRatherThanDroppingTheRow() {
    // given a declaration with a nullable dim (mirrors flows' source/target element ids)
    final TableSchema schema =
        new TableSchema(
            "flows_nullable_test",
            List.of(
                new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column(
                    "source_element_id", ColumnType.STRING_DICT, 2, true, -1, false),
                new TableSchema.Column(
                    "taken_at",
                    ColumnType.LONG,
                    3,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    final CompiledEntityMetrics nullableCompiled =
        EntityMetrics.declare("flows_nullable_test", schema)
            .dims("process_id", "source_element_id")
            .window(Duration.ofMinutes(1), "taken_at")
            .count()
            .build();
    final RecordingEncoderFactory nullableEncoders = new RecordingEncoderFactory();
    final PollFedRider nullableRider = new PollFedRider(nullableCompiled, nullableEncoders, 128);

    // when: the source is unresolved (null) for this record
    nullableRider.putDict(0, "order").putDict(1, null);
    nullableRider.fold(SLOT_A + 1);
    nullableRider.onPollBoundary();
    nullableRider.onWindowClose();

    // then: the group is counted (not dropped), with an honest NULL for the unresolved dim
    final List<Map<String, Object>> rows = nullableEncoders.rows("flows_nullable_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 1L);
    assertThat(rows.get(0)).containsKey("source_element_id");
    assertThat(rows.get(0).get("source_element_id")).isNull();
  }

  @Test
  void shouldRejectADeclarationWithoutCount() {
    final TableSchema schema =
        new TableSchema(
            "flows_uncounted_test",
            List.of(
                new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column("some_measure", ColumnType.LONG, 2, false, -1, false),
                new TableSchema.Column(
                    "taken_at",
                    ColumnType.LONG,
                    3,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    final CompiledEntityMetrics uncounted =
        EntityMetrics.declare("flows_uncounted_test", schema)
            .dims("process_id")
            .window(Duration.ofMinutes(1), "taken_at")
            .measure("some_measure", Algebras.scalarStats()) // a measure, but no count()
            .build();
    assertThatThrownBy(() -> new PollFedRider(uncounted, encoders, 128))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("count()");
  }

  private void fold(final String processId, final String flowId, final long eventMicros) {
    rider.putDict(PROCESS_ID, processId).putDict(FLOW_ID, flowId);
    rider.fold(eventMicros);
  }

  /**
   * Captures every appended row per table instead of writing Parquet — mirrors MetricsRiderTest.
   */
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

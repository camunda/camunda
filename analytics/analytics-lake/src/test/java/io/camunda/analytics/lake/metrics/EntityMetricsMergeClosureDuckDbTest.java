/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static io.camunda.analytics.lake.metrics.EntityMetricsFixtures.userTasksRawSchema;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebra;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The load-bearing test: verifies merge closure through the <b>generated</b> SQL text, executed by
 * a real DuckDB connection — not a hand-simulated merge in Java. Two datasets (A, B) are folded
 * separately in Java, drained into two DuckDB tables per partials shape, merged via {@link
 * CompiledEntityMetrics#mergeMetricsSql}/{@link CompiledEntityMetrics#mergeHistSql} (which also
 * coarsens the window from minute to hour), finalized via {@link
 * CompiledEntityMetrics#finalizeMetricsSql}/{@link CompiledEntityMetrics#finalizeHistSql}, and
 * compared against folding {@code A ∪ B} directly in Java: scalar results must be exactly equal
 * (merging exact partials is itself exact — no estimation is involved until finalize), and
 * percentile estimates must fall within the histogram scheme's relative error bound of the exact
 * percentile of the combined sample.
 */
class EntityMetricsMergeClosureDuckDbTest {

  private static final long MINUTE_MICROS = Duration.ofMinutes(1).toNanos() / 1000;
  private static final long HOUR_MICROS = Duration.ofHours(1).toNanos() / 1000;
  private static final int SCALE = 3;
  private static final double ERROR_BOUND =
      Math.pow(2, -SCALE) + 0.02; // + midpoint-reporting slack

  private record Group(String processId, String elementId, long minuteWindowStart) {}

  @Test
  void shouldReproduceADirectFoldOfTheUnionThroughGeneratedMergeAndFinalizeSql() throws Exception {
    // given an entity with dims (process_id, element_id), a 1-minute window, and one measure
    // folded through both scalar stats and a scale-3 histogram
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .window(Duration.ofMinutes(1))
            .measure("work_time_ms", Algebras.scalarStats(), Algebras.expHistogram(SCALE))
            .build();

    // and three groups: group1 gets data from BOTH A (minute 0) and B (minute 1, same hour) so
    // coarsening must combine them; group2 gets data only from A; group3 gets data only from B, in
    // a DIFFERENT hour than group1, so it must stay separate after coarsening
    final Group group1MinuteA = new Group("proc1", "task1", 0L);
    final Group group1MinuteB = new Group("proc1", "task1", MINUTE_MICROS); // same hour as above
    final Group group2 = new Group("proc1", "task2", 0L);
    final Group group3 = new Group("proc2", "task1", 25 * HOUR_MICROS); // a different hour

    final Random random = new Random(2026);
    final Map<Group, List<Long>> datasetA =
        Map.of(
            group1MinuteA, randomValues(random, 400),
            group2, randomValues(random, 250));
    final Map<Group, List<Long>> datasetB =
        Map.of(
            group1MinuteB, randomValues(random, 350),
            group3, randomValues(random, 500));

    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:")) {
      createTable(duckdb, compiled.metricsSchema());
      createTable(duckdb, compiled.histSchema());
      renameTable(duckdb, compiled.metricsSchema().table(), "metrics_a");
      renameTable(duckdb, compiled.histSchema().table(), "hist_a");
      createTable(duckdb, compiled.metricsSchema());
      createTable(duckdb, compiled.histSchema());
      renameTable(duckdb, compiled.metricsSchema().table(), "metrics_b");
      renameTable(duckdb, compiled.histSchema().table(), "hist_b");

      foldAndInsert(duckdb, datasetA, "metrics_a", "hist_a");
      foldAndInsert(duckdb, datasetB, "metrics_b", "hist_b");

      // when: merge (coarsening minute -> hour) then finalize, all through generated SQL
      final String metricsUnion = "(SELECT * FROM metrics_a UNION ALL SELECT * FROM metrics_b)";
      final String mergeMetricsSql = compiled.mergeMetricsSql(metricsUnion, HOUR_MICROS);
      execute(duckdb, "CREATE TABLE metrics_merged AS " + mergeMetricsSql);
      final String finalizeMetricsSql = compiled.finalizeMetricsSql("metrics_merged");

      final String histUnion = "(SELECT * FROM hist_a UNION ALL SELECT * FROM hist_b)";
      final String mergeHistSql = compiled.mergeHistSql(histUnion, HOUR_MICROS);
      execute(duckdb, "CREATE TABLE hist_merged AS " + mergeHistSql);
      final String finalizeHistSql = compiled.finalizeHistSql("hist_merged");

      // then: scalar answers exactly match folding A ∪ B directly per (dims, hour) group
      assertScalarMatchesDirectFold(
          duckdb, finalizeMetricsSql, group1MinuteA, group1MinuteB, datasetA, datasetB);
      assertScalarMatchesDirectFoldSingleSource(duckdb, finalizeMetricsSql, group2, datasetA);
      assertScalarMatchesDirectFoldSingleSource(duckdb, finalizeMetricsSql, group3, datasetB);

      // and: percentile estimates are within the scheme's error bound of the exact percentile
      assertPercentilesWithinErrorBound(
          duckdb,
          finalizeHistSql,
          group1MinuteA,
          combinedValues(datasetA, group1MinuteA, datasetB, group1MinuteB));
      assertPercentilesWithinErrorBound(duckdb, finalizeHistSql, group2, datasetA.get(group2));
      assertPercentilesWithinErrorBound(duckdb, finalizeHistSql, group3, datasetB.get(group3));
    }
  }

  private static List<Long> combinedValues(
      final Map<Group, List<Long>> a,
      final Group ga,
      final Map<Group, List<Long>> b,
      final Group gb) {
    final List<Long> combined = new ArrayList<>(a.get(ga));
    combined.addAll(b.get(gb));
    return combined;
  }

  private void assertScalarMatchesDirectFold(
      final Connection duckdb,
      final String finalizeSql,
      final Group groupA,
      final Group groupB,
      final Map<Group, List<Long>> datasetA,
      final Map<Group, List<Long>> datasetB)
      throws SQLException {
    final List<Long> combined = combinedValues(datasetA, groupA, datasetB, groupB);
    assertScalarRow(duckdb, finalizeSql, groupA.processId(), groupA.elementId(), combined);
  }

  private void assertScalarMatchesDirectFoldSingleSource(
      final Connection duckdb,
      final String finalizeSql,
      final Group group,
      final Map<Group, List<Long>> dataset)
      throws SQLException {
    assertScalarRow(duckdb, finalizeSql, group.processId(), group.elementId(), dataset.get(group));
  }

  private void assertScalarRow(
      final Connection duckdb,
      final String finalizeSql,
      final String processId,
      final String elementId,
      final List<Long> values)
      throws SQLException {
    final long expectedCnt = values.size();
    final long expectedSum = values.stream().mapToLong(Long::longValue).sum();
    final long expectedMin = values.stream().mapToLong(Long::longValue).min().orElseThrow();
    final long expectedMax = values.stream().mapToLong(Long::longValue).max().orElseThrow();
    final double expectedAvg = (double) expectedSum / expectedCnt;

    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT * FROM ("
                    + finalizeSql
                    + ") WHERE process_id = '"
                    + processId
                    + "' AND element_id = '"
                    + elementId
                    + "'")) {
      assertThat(rs.next())
          .withFailMessage("no finalized row for %s/%s", processId, elementId)
          .isTrue();
      assertThat(rs.getLong("work_time_ms_cnt")).isEqualTo(expectedCnt);
      assertThat(rs.getLong("work_time_ms_sum")).isEqualTo(expectedSum);
      assertThat(rs.getLong("work_time_ms_min")).isEqualTo(expectedMin);
      assertThat(rs.getLong("work_time_ms_max")).isEqualTo(expectedMax);
      assertThat(rs.getDouble("work_time_ms_avg")).isEqualTo(expectedAvg);
      assertThat(rs.next())
          .withFailMessage("more than one merged row for %s/%s", processId, elementId)
          .isFalse();
    }
  }

  private void assertPercentilesWithinErrorBound(
      final Connection duckdb,
      final String finalizeHistSql,
      final Group group,
      final List<Long> values)
      throws SQLException {
    final List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);

    // finalizeHistSql's one bind parameter is the quantile itself (see its javadoc); wrap it in a
    // PreparedStatement per quantile and additionally filter down to this one group by dims
    for (final double quantile : List.of(0.5, 0.9, 0.99)) {
      try (PreparedStatement statement =
          duckdb.prepareStatement(
              "SELECT * FROM ("
                  + finalizeHistSql
                  + ") WHERE process_id = ? AND element_id = ? AND measure = ? AND scheme = ?")) {
        statement.setDouble(1, quantile);
        statement.setString(2, group.processId());
        statement.setString(3, group.elementId());
        statement.setString(4, "work_time_ms");
        statement.setString(5, "exp2ll-" + SCALE);
        try (ResultSet rs = statement.executeQuery()) {
          assertThat(rs.next()).withFailMessage("no percentile row for %s", group).isTrue();
          final double estimate = rs.getDouble("percentile");
          final double exact = exactPercentile(sorted, quantile);
          final double relativeError = Math.abs(estimate - exact) / Math.max(exact, 1.0);
          assertThat(relativeError)
              .withFailMessage(
                  "p%s for %s: estimate %s vs exact %s, relative error %s exceeds bound %s",
                  (int) (quantile * 100), group, estimate, exact, relativeError, ERROR_BOUND)
              .isLessThanOrEqualTo(ERROR_BOUND);
        }
      }
    }
  }

  private static double exactPercentile(final List<Long> sorted, final double quantile) {
    final int index = (int) Math.min(sorted.size() - 1, Math.floor(quantile * sorted.size()));
    return sorted.get(index);
  }

  private static List<Long> randomValues(final Random random, final int count) {
    final List<Long> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add((long) Math.max(1, Math.exp(random.nextGaussian() * 1.1 + 6.0)));
    }
    return values;
  }

  /** Folds every group's values in Java through fresh accumulators and inserts the drained rows. */
  private void foldAndInsert(
      final Connection duckdb,
      final Map<Group, List<Long>> dataset,
      final String metricsTable,
      final String histTable)
      throws SQLException {
    for (final Map.Entry<Group, List<Long>> entry : dataset.entrySet()) {
      final Group group = entry.getKey();
      final Algebra scalar = Algebras.scalarStats();
      final Algebra.Accumulator scalarAcc = scalar.create();
      final Algebra histogram = Algebras.expHistogram(SCALE);
      final Algebra.Accumulator histAcc = histogram.create();
      for (final long value : entry.getValue()) {
        scalarAcc.add(value);
        histAcc.add(value);
      }

      final long[] scalarRow = new long[4];
      scalarAcc.drain(collectSingleRow(scalarRow));
      try (PreparedStatement insert =
          duckdb.prepareStatement(
              "INSERT INTO "
                  + metricsTable
                  + " (window_start, process_id, element_id, work_time_ms_cnt, work_time_ms_sum,"
                  + " work_time_ms_min, work_time_ms_max) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
        insert.setLong(1, group.minuteWindowStart());
        insert.setString(2, group.processId());
        insert.setString(3, group.elementId());
        insert.setLong(4, scalarRow[0]);
        insert.setLong(5, scalarRow[1]);
        insert.setLong(6, scalarRow[2]);
        insert.setLong(7, scalarRow[3]);
        insert.executeUpdate();
      }

      final List<long[]> histRows = new ArrayList<>();
      histAcc.drain(collectMultiRow(histRows));
      try (PreparedStatement insert =
          duckdb.prepareStatement(
              "INSERT INTO "
                  + histTable
                  + " (window_start, process_id, element_id, measure, scheme, bin_lo, bin_hi, cnt)"
                  + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
        for (final long[] row : histRows) {
          insert.setLong(1, group.minuteWindowStart());
          insert.setString(2, group.processId());
          insert.setString(3, group.elementId());
          insert.setString(4, "work_time_ms");
          insert.setString(5, histogram.scheme());
          insert.setLong(6, row[0]);
          insert.setLong(7, row[1]);
          insert.setLong(8, row[2]);
          insert.executeUpdate();
        }
      }
    }
  }

  private static Algebra.RowWriter collectSingleRow(final long[] out) {
    return new Algebra.RowWriter() {
      @Override
      public void beginRow() {}

      @Override
      public void writeLong(final int columnIndex, final long value) {
        out[columnIndex] = value;
      }

      @Override
      public void endRow() {}
    };
  }

  private static Algebra.RowWriter collectMultiRow(final List<long[]> out) {
    return new Algebra.RowWriter() {
      private final long[] current = new long[3];

      @Override
      public void beginRow() {}

      @Override
      public void writeLong(final int columnIndex, final long value) {
        current[columnIndex] = value;
      }

      @Override
      public void endRow() {
        out.add(current.clone());
      }
    };
  }

  private static void createTable(final Connection duckdb, final TableSchema schema)
      throws SQLException {
    final StringBuilder ddl =
        new StringBuilder("CREATE TABLE ").append(schema.table()).append(" (");
    for (int i = 0; i < schema.columns().size(); i++) {
      if (i > 0) {
        ddl.append(", ");
      }
      final TableSchema.Column column = schema.columns().get(i);
      ddl.append(column.name()).append(' ').append(ddlType(column.type()));
    }
    ddl.append(')');
    execute(duckdb, ddl.toString());
  }

  private static String ddlType(final ColumnType type) {
    return switch (type) {
      case LONG -> "BIGINT";
      case INT -> "INTEGER";
      case STRING_DICT -> "VARCHAR";
      case BINARY -> "BLOB";
      case DOUBLE -> "DOUBLE";
    };
  }

  private static void renameTable(final Connection duckdb, final String from, final String to)
      throws SQLException {
    execute(duckdb, "ALTER TABLE " + from + " RENAME TO " + to);
  }

  private static void execute(final Connection duckdb, final String sql) throws SQLException {
    try (Statement statement = duckdb.createStatement()) {
      statement.execute(sql);
    }
  }
}

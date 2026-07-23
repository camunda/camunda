/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

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
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The double-valued twin of {@code EntityMetricsMergeClosureDuckDbTest}: same "fold A, fold B,
 * merge/coarsen/finalize through GENERATED SQL, compare against fold(A ∪ B)" shape, exercising
 * {@link Algebras#doubleScalarStats()} and {@link Algebras#signedDoubleExpHistogram(int)} together
 * on one measure (the same pairing {@code variable_profiles} uses), including non-finite values
 * (verifying {@code nonfinite_cnt} survives the merge exactly) and negative values (exercising the
 * signed histogram's sign-mirrored bins, which {@code EntityMetricsMergeClosureDuckDbTest}'s
 * non-negative-long domain never touches).
 */
class DoubleAlgebrasMergeClosureDuckDbTest {

  private static final long MINUTE_MICROS = Duration.ofMinutes(1).toNanos() / 1000;
  private static final long HOUR_MICROS = Duration.ofHours(1).toNanos() / 1000;
  private static final int SCALE = 3;
  private static final double ERROR_BOUND = Math.pow(2, -SCALE) + 0.02;

  private static TableSchema virtualSchema() {
    return new TableSchema(
        "variables",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, -1, false),
            new TableSchema.Column("var_name", ColumnType.STRING_DICT, 2, false, -1, false),
            new TableSchema.Column(
                "completed_at", ColumnType.LONG, 3, false, -1, true, TableSchema.LogicalType.NONE),
            new TableSchema.Column("value", ColumnType.DOUBLE, 4, true, -1, false)));
  }

  private record Group(String processId, String varName, long minuteWindowStart) {}

  @Test
  void shouldReproduceADirectFoldOfTheUnionThroughGeneratedMergeAndFinalizeSql() throws Exception {
    // given an entity with dims (process_id, var_name), a 1-minute window, and one double measure
    // folded through both scalar stats and a scale-3 signed histogram -- the exact pairing
    // variable_profiles uses
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("variables", virtualSchema())
            .dims("process_id", "var_name")
            .window(Duration.ofMinutes(1))
            .measure(
                "value", Algebras.doubleScalarStats(), Algebras.signedDoubleExpHistogram(SCALE))
            .build();

    // and three groups, mirroring the long-valued test's own layout, but with a mix of positive,
    // negative and non-finite values
    final Group group1MinuteA = new Group("proc1", "amount", 0L);
    final Group group1MinuteB = new Group("proc1", "amount", MINUTE_MICROS); // same hour
    final Group group2 = new Group("proc1", "score", 0L);
    final Group group3 = new Group("proc2", "amount", 25 * HOUR_MICROS); // a different hour

    // Each group's values are single-signed (mirrors the long-valued test's own single-domain
    // property: values safely away from zero, so the percentile crossover never lands in a
    // low-density region near the sign boundary) -- group1/group3 exercise the positive side,
    // group2 the negative side, all three also mixed with a sprinkling of non-finite values.
    final Random random = new Random(2026);
    final Map<Group, List<Double>> datasetA =
        Map.of(
            group1MinuteA, randomValuesWithNonFinite(random, 400, 1),
            group2, randomValuesWithNonFinite(random, 250, -1));
    final Map<Group, List<Double>> datasetB =
        Map.of(
            group1MinuteB, randomValuesWithNonFinite(random, 350, 1),
            group3, randomValuesWithNonFinite(random, 500, 1));

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

      // then: scalar answers (incl. nonfinite_cnt) exactly match folding A ∪ B directly
      assertScalarMatchesDirectFold(
          duckdb, finalizeMetricsSql, group1MinuteA, group1MinuteB, datasetA, datasetB);
      assertScalarMatchesDirectFoldSingleSource(duckdb, finalizeMetricsSql, group2, datasetA);
      assertScalarMatchesDirectFoldSingleSource(duckdb, finalizeMetricsSql, group3, datasetB);

      // and: percentile estimates (over the finite values only) are within the scheme's bound
      assertPercentilesWithinErrorBound(
          duckdb,
          finalizeHistSql,
          group1MinuteA,
          combinedValues(datasetA, group1MinuteA, datasetB, group1MinuteB));
      assertPercentilesWithinErrorBound(duckdb, finalizeHistSql, group2, datasetA.get(group2));
      assertPercentilesWithinErrorBound(duckdb, finalizeHistSql, group3, datasetB.get(group3));
    }
  }

  private static List<Double> combinedValues(
      final Map<Group, List<Double>> a,
      final Group ga,
      final Map<Group, List<Double>> b,
      final Group gb) {
    final List<Double> combined = new ArrayList<>(a.get(ga));
    combined.addAll(b.get(gb));
    return combined;
  }

  private void assertScalarMatchesDirectFold(
      final Connection duckdb,
      final String finalizeSql,
      final Group groupA,
      final Group groupB,
      final Map<Group, List<Double>> datasetA,
      final Map<Group, List<Double>> datasetB)
      throws SQLException {
    assertScalarRow(
        duckdb,
        finalizeSql,
        groupA.processId(),
        groupA.varName(),
        combinedValues(datasetA, groupA, datasetB, groupB));
  }

  private void assertScalarMatchesDirectFoldSingleSource(
      final Connection duckdb,
      final String finalizeSql,
      final Group group,
      final Map<Group, List<Double>> dataset)
      throws SQLException {
    assertScalarRow(duckdb, finalizeSql, group.processId(), group.varName(), dataset.get(group));
  }

  private void assertScalarRow(
      final Connection duckdb,
      final String finalizeSql,
      final String processId,
      final String varName,
      final List<Double> values)
      throws SQLException {
    final List<Double> finiteValues = values.stream().filter(Double::isFinite).toList();
    final long expectedNonfiniteCnt = values.size() - finiteValues.size();
    final long expectedCnt = finiteValues.size();
    final double expectedSum = finiteValues.stream().mapToDouble(Double::doubleValue).sum();
    final double expectedMin =
        finiteValues.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
    final double expectedMax =
        finiteValues.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    final double expectedAvg = expectedSum / expectedCnt;

    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT * FROM ("
                    + finalizeSql
                    + ") WHERE process_id = '"
                    + processId
                    + "' AND var_name = '"
                    + varName
                    + "'")) {
      assertThat(rs.next())
          .withFailMessage("no finalized row for %s/%s", processId, varName)
          .isTrue();
      // cnt/min/max/nonfinite_cnt are exact operations (SUM of integers, MIN, MAX) -- bit-for-bit
      // equality is the right bar. sum/avg go through floating-point addition, which is not
      // associative: DuckDB's SUM(sum) over the merged partials need not accumulate in the exact
      // same order Java's sequential fold above does, so a tiny relative tolerance (well below the
      // double-scalar-stats histogram's own error bound) is the correct comparison, not exact
      // equality.
      assertThat(rs.getLong("value_cnt")).isEqualTo(expectedCnt);
      assertThat(rs.getDouble("value_sum"))
          .isCloseTo(expectedSum, Offset.offset(Math.abs(expectedSum) * 1e-9));
      assertThat(rs.getDouble("value_min")).isEqualTo(expectedMin);
      assertThat(rs.getDouble("value_max")).isEqualTo(expectedMax);
      assertThat(rs.getDouble("value_avg"))
          .isCloseTo(expectedAvg, Offset.offset(Math.abs(expectedAvg) * 1e-9));
      assertThat(rs.getLong("value_nonfinite_cnt")).isEqualTo(expectedNonfiniteCnt);
      assertThat(rs.next())
          .withFailMessage("more than one merged row for %s/%s", processId, varName)
          .isFalse();
    }
  }

  private void assertPercentilesWithinErrorBound(
      final Connection duckdb,
      final String finalizeHistSql,
      final Group group,
      final List<Double> values)
      throws SQLException {
    final List<Double> sorted = new ArrayList<>(values.stream().filter(Double::isFinite).toList());
    Collections.sort(sorted);

    for (final double quantile : List.of(0.5, 0.9, 0.99)) {
      try (PreparedStatement statement =
          duckdb.prepareStatement(
              "SELECT * FROM ("
                  + finalizeHistSql
                  + ") WHERE process_id = ? AND var_name = ? AND measure = ? AND scheme = ?")) {
        statement.setDouble(1, quantile);
        statement.setString(2, group.processId());
        statement.setString(3, group.varName());
        statement.setString(4, "value");
        statement.setString(5, "dexp2ll-" + SCALE);
        try (ResultSet rs = statement.executeQuery()) {
          assertThat(rs.next()).withFailMessage("no percentile row for %s", group).isTrue();
          final double estimate = rs.getDouble("percentile");
          final double exact = exactPercentile(sorted, quantile);
          final double relativeError = Math.abs(estimate - exact) / Math.max(Math.abs(exact), 1.0);
          assertThat(relativeError)
              .withFailMessage(
                  "p%s for %s: estimate %s vs exact %s, relative error %s exceeds bound %s",
                  (int) (quantile * 100), group, estimate, exact, relativeError, ERROR_BOUND)
              .isLessThanOrEqualTo(ERROR_BOUND);
        }
      }
    }
  }

  private static double exactPercentile(final List<Double> sorted, final double quantile) {
    final int index = (int) Math.min(sorted.size() - 1, Math.floor(quantile * sorted.size()));
    return sorted.get(index);
  }

  /**
   * A single-signed ({@code sign} is {@code +1} or {@code -1}) sample of finite magnitudes plus a
   * sprinkling of non-finite values — see the call site for why single-signed per group.
   */
  private static List<Double> randomValuesWithNonFinite(
      final Random random, final int count, final int sign) {
    final List<Double> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      if (i % 47 == 0) {
        values.add(random.nextBoolean() ? Double.POSITIVE_INFINITY : Double.NaN);
      } else if (i % 53 == 0) {
        values.add(Double.NEGATIVE_INFINITY);
      } else {
        final double magnitude = Math.max(0.01, Math.exp(random.nextGaussian() * 1.1 + 6.0));
        values.add(sign * magnitude);
      }
    }
    return values;
  }

  /** Folds every group's values in Java through fresh accumulators and inserts the drained rows. */
  private void foldAndInsert(
      final Connection duckdb,
      final Map<Group, List<Double>> dataset,
      final String metricsTable,
      final String histTable)
      throws SQLException {
    for (final Map.Entry<Group, List<Double>> entry : dataset.entrySet()) {
      final Group group = entry.getKey();
      final Algebra scalar = Algebras.doubleScalarStats();
      final Algebra.Accumulator scalarAcc = scalar.create();
      final Algebra histogram = Algebras.signedDoubleExpHistogram(SCALE);
      final Algebra.Accumulator histAcc = histogram.create();
      for (final double value : entry.getValue()) {
        scalarAcc.addDouble(value);
        histAcc.addDouble(value);
      }

      final Object[] scalarRow = new Object[5];
      scalarAcc.drain(collectSingleRow(scalarRow));
      try (PreparedStatement insert =
          duckdb.prepareStatement(
              "INSERT INTO "
                  + metricsTable
                  + " (window_start, process_id, var_name, value_cnt, value_sum, value_min,"
                  + " value_max, value_nonfinite_cnt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
        insert.setLong(1, group.minuteWindowStart());
        insert.setString(2, group.processId());
        insert.setString(3, group.varName());
        insert.setLong(4, (Long) scalarRow[0]);
        insert.setDouble(5, (Double) scalarRow[1]);
        if (scalarRow[2] == null) {
          insert.setNull(6, java.sql.Types.DOUBLE);
        } else {
          insert.setDouble(6, (Double) scalarRow[2]);
        }
        if (scalarRow[3] == null) {
          insert.setNull(7, java.sql.Types.DOUBLE);
        } else {
          insert.setDouble(7, (Double) scalarRow[3]);
        }
        insert.setLong(8, (Long) scalarRow[4]);
        insert.executeUpdate();
      }

      final List<Object[]> histRows = new ArrayList<>();
      histAcc.drain(collectMultiRow(histRows));
      try (PreparedStatement insert =
          duckdb.prepareStatement(
              "INSERT INTO "
                  + histTable
                  + " (window_start, process_id, var_name, measure, scheme, bin_lo, bin_hi, cnt)"
                  + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
        for (final Object[] row : histRows) {
          insert.setLong(1, group.minuteWindowStart());
          insert.setString(2, group.processId());
          insert.setString(3, group.varName());
          insert.setString(4, "value");
          insert.setString(5, histogram.scheme());
          insert.setDouble(6, (Double) row[0]);
          insert.setDouble(7, (Double) row[1]);
          insert.setLong(8, (Long) row[2]);
          insert.executeUpdate();
        }
      }
    }
  }

  private static Algebra.RowWriter collectSingleRow(final Object[] out) {
    return new Algebra.RowWriter() {
      @Override
      public void beginRow() {}

      @Override
      public void writeLong(final int columnIndex, final long value) {
        out[columnIndex] = value;
      }

      @Override
      public void writeDouble(final int columnIndex, final double value) {
        out[columnIndex] = value;
      }

      @Override
      public void writeNull(final int columnIndex) {
        out[columnIndex] = null;
      }

      @Override
      public void endRow() {}
    };
  }

  private static Algebra.RowWriter collectMultiRow(final List<Object[]> out) {
    return new Algebra.RowWriter() {
      private final Object[] current = new Object[3];

      @Override
      public void beginRow() {}

      @Override
      public void writeDouble(final int columnIndex, final double value) {
        current[columnIndex] = value;
      }

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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.model.ActivityRow;
import io.camunda.analytics.lake.model.InstanceRow;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.io.CloseableIterable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds one synthetic instance with a known path -- including a repeated element (rework) and a
 * pair of consecutive activities with a negative gap (parallel-branch shape) -- through {@link
 * IcebergLakeWriter}, drives the gold-table recompute via {@link LakeCompactor#compactIfNeeded()}
 * (the same entry point the app's poll loop uses), and asserts on the derived {@code transitions}/
 * {@code instance_kpis}/{@code element_bits} row content, idempotence across two passes, and that
 * {@link IcebergLakeWriter#committedOffset(int)} is unaffected by any of it (see {@link
 * GoldTables}'s class javadoc for why that has to hold).
 *
 * <h2>The synthetic instance</h2>
 *
 * <p>Instance {@code 1000} (process {@code gold-test-process}, definition key {@code 100}, tenant
 * {@code <default>}), owning activities ordered by {@code (start_ms, element_key)}:
 *
 * <pre>
 *   A (key 1): start   0, end 100
 *   B (key 2): start 150, end 250
 *   A (key 3): start 200, end 300   -- rework: A runs a second time
 *   C (key 4): start 350, end 450
 * </pre>
 *
 * <p>Instance {@code start_ms} is {@code -20}, so the start edge's gap is {@code 20}. The B-&gt;A
 * pair's gap is {@code 200 - 250 = -50} -- negative, the "parallel branch" case the brief calls out
 * -- and must be excluded from {@code instance_kpis.total_wait_ms} while still appearing,
 * unmodified, as {@code transitions.min_gap_ms}/{@code max_gap_ms}/{@code total_gap_ms} for the
 * {@code (B, A)} edge.
 */
class GoldTablesRecomputeTest {

  private static final int PARTITION = 0;
  private static final long INSTANCE_KEY = 1000L;
  private static final long PROCESS_DEFINITION_KEY = 100L;
  private static final String PROCESS_ID = "gold-test-process";
  private static final long INSTANCE_START_MS = -20L;

  @Test
  void shouldDeriveGoldTablesCorrectlyAndIdempotentlyWithoutDisturbingCommittedOffset(
      @TempDir final Path tempDir) throws SQLException {
    // given a writer holding one finished instance and its four activities (rework on element A,
    // one negative consecutive gap between B and the reworked A), flushed as a single batch
    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "test-topic",
            "test-group",
            tempDir.resolve("warehouse"),
            tempDir.resolve("state"),
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    try {
      writer.append(instanceRow());
      writer.append(activityRow("A", 1L, 0L, 100L));
      writer.append(activityRow("B", 2L, 150L, 250L));
      writer.append(activityRow("A", 3L, 200L, 300L));
      writer.append(activityRow("C", 4L, 350L, 450L));
      writer.flush(PARTITION, 0L);

      final long committedOffsetBefore = writer.committedOffset(PARTITION);
      assertThat(committedOffsetBefore).isEqualTo(0L);

      // when the compaction pass runs (which now also drives the gold-table recompute)
      final LakeCompactor compactor = new LakeCompactor(writer);
      final LakeCompactor.CompactionReport firstReport = compactor.compactIfNeeded();

      // then the gold-table step ran and reports the expected row counts
      assertThat(firstReport.goldTables().recomputed()).isTrue();
      assertThat(firstReport.goldTables().transitionsRows()).isEqualTo(5L);
      assertThat(firstReport.goldTables().instanceKpisRows()).isEqualTo(1L);
      assertThat(firstReport.goldTables().elementBitsRows()).isEqualTo(3L);

      // and committedOffset() -- the raw tables' sole durability signal -- is untouched by any of
      // this: gold tables are derived caches, never truth (HARD CONSTRAINT 1)
      assertThat(writer.committedOffset(PARTITION)).isEqualTo(committedOffsetBefore);

      // and the transitions table has exactly the five expected directly-follows edges: three
      // consecutive pairs (including the rework's B->A edge with its negative gap, kept raw) plus
      // the synthetic __START__/__END__ edges
      final List<List<Object>> transitions =
          readRows(writer, goldTable(writer, "transitions"), "from_element, to_element");
      assertThat(transitions).hasSize(5);
      assertRow(transitions.get(0), "A", "B", 1L, 50L, 50L, 50L);
      assertRow(transitions.get(1), "A", "C", 1L, 50L, 50L, 50L);
      assertRow(transitions.get(2), "B", "A", 1L, -50L, -50L, -50L);
      assertRow(transitions.get(3), "C", "__END__", 1L, 0L, 0L, 0L);
      assertRow(transitions.get(4), "__START__", "A", 1L, 20L, 20L, 20L);
      for (final List<Object> row : transitions) {
        assertThat(row.get(0)).isEqualTo(PROCESS_ID); // process_id
        assertThat(row.get(1)).isEqualTo(PROCESS_DEFINITION_KEY); // process_definition_key
        assertThat(row.get(2)).isEqualTo("<default>"); // tenant_id
        assertThat(row.get(3)).asString().matches("\\d{4}-\\d{2}-\\d{2}"); // day
      }

      // and element_bits assigns 0-indexed bits alphabetically per process_definition_key
      final List<List<Object>> elementBits =
          readRows(writer, goldTable(writer, "element_bits"), "element_id");
      assertThat(elementBits).hasSize(3);
      assertThat(elementBits.get(0)).containsExactly(PROCESS_DEFINITION_KEY, "A", 0);
      assertThat(elementBits.get(1)).containsExactly(PROCESS_DEFINITION_KEY, "B", 1);
      assertThat(elementBits.get(2)).containsExactly(PROCESS_DEFINITION_KEY, "C", 2);

      // and instance_kpis reflects activity_count=4, rework_count=1 (only A ran twice),
      // total_wait_ms=100 (the two positive consecutive gaps, 50+50 -- the -50 gap is excluded),
      // and elements_seen = bit(A)|bit(B)|bit(C) = 0b111 = 7, consistent with element_bits above
      final List<List<Object>> instanceKpis =
          readRows(writer, goldTable(writer, "instance_kpis"), "instance_key");
      assertThat(instanceKpis).hasSize(1);
      final List<Object> kpiRow = instanceKpis.get(0);
      assertThat(kpiRow.get(0)).isEqualTo(INSTANCE_KEY); // instance_key
      assertThat(kpiRow.get(1)).isEqualTo(PROCESS_ID); // process_id
      assertThat(kpiRow.get(2)).isEqualTo(PROCESS_DEFINITION_KEY); // process_definition_key
      assertThat(kpiRow.get(3)).isEqualTo("<default>"); // tenant_id
      assertThat(kpiRow.get(5)).isEqualTo(4L); // activity_count
      assertThat(kpiRow.get(6)).isEqualTo(3L); // distinct_element_count
      assertThat(kpiRow.get(7)).isEqualTo(1L); // rework_count
      assertThat(kpiRow.get(8)).isEqualTo(100L); // total_wait_ms (excludes the negative gap)
      assertThat(kpiRow.get(9)).isEqualTo(7L); // elements_seen bitmask

      // when the compaction (and gold recompute) pass runs again with no new source data
      final LakeCompactor.CompactionReport secondReport = compactor.compactIfNeeded();

      // then the recompute is idempotent: identical row counts and identical row content
      assertThat(secondReport.goldTables().recomputed()).isTrue();
      assertThat(secondReport.goldTables().transitionsRows()).isEqualTo(5L);
      assertThat(secondReport.goldTables().instanceKpisRows()).isEqualTo(1L);
      assertThat(secondReport.goldTables().elementBitsRows()).isEqualTo(3L);
      assertThat(readRows(writer, goldTable(writer, "transitions"), "from_element, to_element"))
          .isEqualTo(transitions);
      assertThat(readRows(writer, goldTable(writer, "element_bits"), "element_id"))
          .isEqualTo(elementBits);
      assertThat(readRows(writer, goldTable(writer, "instance_kpis"), "instance_key"))
          .isEqualTo(instanceKpis);

      // and committedOffset() is still untouched after the second pass too
      assertThat(writer.committedOffset(PARTITION)).isEqualTo(committedOffsetBefore);
    } finally {
      writer.close();
    }
  }

  private static void assertRow(
      final List<Object> row,
      final String fromElement,
      final String toElement,
      final long n,
      final long totalGapMs,
      final long minGapMs,
      final long maxGapMs) {
    assertThat(row.get(4)).as("from_element").isEqualTo(fromElement);
    assertThat(row.get(5)).as("to_element").isEqualTo(toElement);
    assertThat(row.get(6)).as("n").isEqualTo(n);
    assertThat(row.get(7)).as("total_gap_ms").isEqualTo(totalGapMs);
    assertThat(row.get(8)).as("min_gap_ms").isEqualTo(minGapMs);
    assertThat(row.get(9)).as("max_gap_ms").isEqualTo(maxGapMs);
  }

  private static InstanceRow instanceRow() {
    return new InstanceRow(
        INSTANCE_KEY,
        PROCESS_DEFINITION_KEY,
        PROCESS_ID,
        1,
        "<default>",
        "COMPLETED",
        INSTANCE_START_MS,
        450L,
        470L,
        "{}");
  }

  private static ActivityRow activityRow(
      final String elementId, final long elementKey, final long startMs, final long endMs) {
    return new ActivityRow(
        INSTANCE_KEY,
        PROCESS_ID,
        1,
        "<default>",
        elementId,
        "SERVICE_TASK",
        elementKey,
        "COMPLETED",
        startMs,
        endMs,
        endMs - startMs,
        INSTANCE_START_MS);
  }

  /**
   * Loads a gold table fresh from the writer's own catalog -- read-only, after a recompute pass.
   */
  private static Table goldTable(final IcebergLakeWriter writer, final String name) {
    return writer.catalog().loadTable(TableIdentifier.of(Namespace.of("lake"), name));
  }

  /**
   * Reads every column of every row currently live in {@code table}, ordered by {@code orderBy}.
   */
  private static List<List<Object>> readRows(
      final IcebergLakeWriter writer, final Table table, final String orderBy) throws SQLException {
    table.refresh();
    final List<String> locations = new ArrayList<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        final String location = task.file().location();
        if (!locations.contains(location)) {
          locations.add(location);
        }
      }
    } catch (final IOException e) {
      throw new SQLException("Failed to plan data files for " + table.name(), e);
    }
    if (locations.isEmpty()) {
      return List.of();
    }
    final String fileList =
        locations.stream()
            .map(location -> "'" + LocalFileIO.toFilesystemPath(location) + "'")
            .collect(Collectors.joining(", "));
    final Connection duckdb = writer.duckdbConnection();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT * FROM read_parquet([" + fileList + "]) ORDER BY " + orderBy)) {
      final ResultSetMetaData meta = rs.getMetaData();
      final int columnCount = meta.getColumnCount();
      final List<List<Object>> rows = new ArrayList<>();
      while (rs.next()) {
        final List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
          row.add(rs.getObject(i));
        }
        rows.add(row);
      }
      return rows;
    }
  }
}

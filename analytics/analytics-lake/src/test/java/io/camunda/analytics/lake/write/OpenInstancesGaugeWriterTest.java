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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link IcebergLakeWriter#openInstancesGaugeTable()}'s schema/partitioning and that
 * {@link OpenInstancesGaugeWriter#writeBatch} lands a committed, queryable batch — the real-writer
 * counterpart to {@link OpenInstancesGaugeSamplerTest}'s pure batching-logic coverage.
 */
final class OpenInstancesGaugeWriterTest {

  @Test
  void shouldCreateTablePartitionedByDayWithTheExactSchema(@TempDir final Path tempDir) {
    // given a freshly created lake writer
    final IcebergLakeWriter writer = new IcebergLakeWriter(configFor(tempDir));
    try {
      // when the open_instances_gauge table is loaded
      final Table table = writer.openInstancesGaugeTable();

      // then its schema is exactly sampled_at (required timestamptz), process_id (required
      // string), open_instances (required long), in that order
      assertThat(table.schema().columns())
          .extracting(Types.NestedField::name)
          .containsExactly("sampled_at", "process_id", "open_instances");
      assertThat(table.schema().findField("sampled_at").isRequired()).isTrue();
      assertThat(table.schema().findField("sampled_at").type())
          .isEqualTo(Types.TimestampType.withZone());
      assertThat(table.schema().findField("process_id").isRequired()).isTrue();
      assertThat(table.schema().findField("process_id").type()).isEqualTo(Types.StringType.get());
      assertThat(table.schema().findField("open_instances").isRequired()).isTrue();
      assertThat(table.schema().findField("open_instances").type()).isEqualTo(Types.LongType.get());

      // and it is partitioned by days(sampled_at), and nothing else
      assertThat(table.spec().fields()).hasSize(1);
      final PartitionField partitionField = table.spec().fields().get(0);
      assertThat(partitionField.transform().toString()).isEqualTo("day");
      assertThat(table.schema().findColumnName(partitionField.sourceId())).isEqualTo("sampled_at");
    } finally {
      writer.close();
    }
  }

  @Test
  void shouldReloadTheSameTableRatherThanRecreatingIt(@TempDir final Path tempDir) {
    // given a table created by a first writer instance
    final LakeConfig config = configFor(tempDir);
    final IcebergLakeWriter first = new IcebergLakeWriter(config);
    try {
      assertThat(first.openInstancesGaugeTable()).isNotNull();
    } finally {
      first.close();
    }

    // when a second writer opens the same warehouse
    final IcebergLakeWriter second = new IcebergLakeWriter(config);
    try {
      // then it loads the existing table rather than failing or creating a duplicate
      assertThat(second.openInstancesGaugeTable().name())
          .isEqualTo(first.openInstancesGaugeTable().name());
    } finally {
      second.close();
    }
  }

  @Test
  void shouldCommitAFlushedBatchAsQueryableRowsWithNoOffsetStamp(@TempDir final Path tempDir)
      throws SQLException {
    // given a gauge writer built over a fresh lake
    final IcebergLakeWriter writer = new IcebergLakeWriter(configFor(tempDir));
    try {
      final OpenInstancesGaugeWriter gaugeWriter = new OpenInstancesGaugeWriter(writer);
      final long sampledAtMs = 1_700_000_000_000L; // fixed instant, not wall-clock-dependent

      // when a batch spanning two processes at the same tick is written
      gaugeWriter.writeBatch(
          List.of(
              new GaugeSample(sampledAtMs, "order-intake", 5L),
              new GaugeSample(sampledAtMs, "dispute-handling", 2L)));

      // then the table has a committed snapshot with no offset/frontier/watermark stamp -- gauge
      // rows are observations, not source-log-derived facts (see the class javadocs)
      final Table table = writer.openInstancesGaugeTable();
      table.refresh();
      assertThat(table.currentSnapshot()).isNotNull();
      assertThat(table.currentSnapshot().summary().keySet())
          .noneMatch(
              key ->
                  key.startsWith(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX)
                      || key.startsWith(IcebergLakeWriter.FRONTIER_PROPERTY_PREFIX)
                      || key.startsWith(IcebergLakeWriter.ZBPOS_PROPERTY_PREFIX));

      // and both rows landed with the correct per-process counts
      final List<String> rows = queryRows(table, writer.duckdbConnection());
      assertThat(rows).containsExactlyInAnyOrder("order-intake|5", "dispute-handling|2");
    } finally {
      writer.close();
    }
  }

  @Test
  void shouldAppendASecondBatchWithoutLosingTheFirst(@TempDir final Path tempDir)
      throws SQLException {
    // given one batch already committed
    final IcebergLakeWriter writer = new IcebergLakeWriter(configFor(tempDir));
    try {
      final OpenInstancesGaugeWriter gaugeWriter = new OpenInstancesGaugeWriter(writer);
      final long firstTickMs = 1_700_000_000_000L;
      final long secondTickMs = firstTickMs + 300_000L;
      gaugeWriter.writeBatch(List.of(new GaugeSample(firstTickMs, "order-intake", 5L)));

      // when a second batch (a later flush) is written
      gaugeWriter.writeBatch(List.of(new GaugeSample(secondTickMs, "order-intake", 7L)));

      // then both flushes' rows are present -- an append, not a replace
      final Table table = writer.openInstancesGaugeTable();
      final List<String> rows = queryRows(table, writer.duckdbConnection());
      assertThat(rows).containsExactlyInAnyOrder("order-intake|5", "order-intake|7");
    } finally {
      writer.close();
    }
  }

  private static LakeConfig configFor(final Path tempDir) {
    return new LakeConfig(
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
  }

  /** {@code process_id|open_instances} for every row currently live in {@code table}. */
  private static List<String> queryRows(final Table table, final Connection duckdb)
      throws SQLException {
    table.refresh();
    final List<String> locations = new ArrayList<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        final String location = task.file().location();
        if (!locations.contains(location)) {
          locations.add(location);
        }
      }
    } catch (final java.io.IOException e) {
      throw new SQLException("Failed to plan data files for " + table.name(), e);
    }
    if (locations.isEmpty()) {
      return List.of();
    }
    final String fileList =
        locations.stream()
            .map(location -> "'" + LocalFileIO.toFilesystemPath(location) + "'")
            .collect(Collectors.joining(", "));
    final List<String> rows = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT process_id, open_instances FROM read_parquet(["
                    + fileList
                    + "]) ORDER BY process_id")) {
      while (rs.next()) {
        rows.add(rs.getString(1) + "|" + rs.getLong(2));
      }
    }
    return rows;
  }
}

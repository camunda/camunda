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
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.pipeline.DirectCommitSink;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Builds a tiny lake (one file per flush, well past {@link
 * LakeCompactor#DATA_FILE_COMPACTION_THRESHOLD}) with {@link IcebergLakeWriter} directly, then
 * verifies {@link LakeCompactor#compactIfNeeded()} collapses the data files, preserves every row,
 * trims snapshot history, and — critically — leaves {@link IcebergLakeWriter#committedOffset(int)}
 * unchanged (see {@link LakeCompactor}'s "Offset re-stamping" javadoc for why that is not a given).
 */
class IcebergLakeWriterCompactionTest {

  private static final int BATCH_COUNT = 30;
  private static final int PARTITION = 0;

  @Test
  void shouldCompactDataFilesWithoutLosingRowsOrOffsets(@TempDir final Path tempDir)
      throws SQLException {
    // given a writer that has flushed one tiny batch per table on every one of 30 offsets, so both
    // tables sit well above the compaction threshold with one data file per flush
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
      for (int i = 0; i < BATCH_COUNT; i++) {
        writer.append(instanceRow(i));
        writer.append(activityRow(i));
        writer.flush(PARTITION, i);
      }

      final Table instancesTable = writer.instancesTable();
      final Table activitiesTable = writer.activitiesTable();
      final Connection duckdb = writer.duckdbConnection();

      final int instanceFilesBefore = dataFileCount(instancesTable);
      final int activityFilesBefore = dataFileCount(activitiesTable);
      final long instanceRowsBefore = rowCount(instancesTable, duckdb);
      final long activityRowsBefore = rowCount(activitiesTable, duckdb);
      final long committedOffsetBefore = writer.committedOffset(PARTITION);

      assertThat(instanceFilesBefore).isGreaterThan(LakeCompactor.DATA_FILE_COMPACTION_THRESHOLD);
      assertThat(activityFilesBefore).isGreaterThan(LakeCompactor.DATA_FILE_COMPACTION_THRESHOLD);
      assertThat(instanceRowsBefore).isEqualTo(BATCH_COUNT);
      assertThat(activityRowsBefore).isEqualTo(BATCH_COUNT);
      assertThat(committedOffsetBefore).isEqualTo(BATCH_COUNT - 1);

      // and: simulate a frontier stamp AND a zbpos (origin-position dedup watermark) stamp
      // DirectCommitSink would have already landed on this same table for a different partition --
      // this is the property every restamp site in write/ must carry forward exactly like it
      // already does for lake.offset.p*, or a maintenance commit (like the compaction below)
      // silently wipes it.
      final int frontierPartition = PARTITION + 1;
      final long stampedFrontierMs = 123_456_789L;
      final int zeebePartitionId = 7;
      final long stampedZeebeWatermark = 555_555L;
      final DirectCommitSink sink =
          new DirectCommitSink(instancesTable, writer.commitLock(instancesTable));
      sink.accept(
          new Descriptor(
              "instances",
              frontierPartition,
              List.of(),
              0L,
              0L,
              stampedFrontierMs,
              Map.of(zeebePartitionId, stampedZeebeWatermark)));
      assertThat(frontierOf(instancesTable, frontierPartition)).isEqualTo(stampedFrontierMs);
      assertThat(zeebeWatermarkOf(instancesTable, zeebePartitionId))
          .isEqualTo(stampedZeebeWatermark);

      // and: one more legacy buffered flush() lands on the SAME table -- this is the hardening
      // this test closes: IcebergLakeWriter#flushTable's own carry-forward (a different commit
      // site than DirectCommitSink's or LakeCompactor's) must re-stamp lake.zbpos.z* (and
      // lake.frontier.p*) exactly like it already does for lake.offset.p*, or this ordinary
      // legacy flush -- not just a maintenance pass -- would silently wipe both stamps the moment
      // it landed its own snapshot.
      writer.append(instanceRow(BATCH_COUNT));
      writer.append(activityRow(BATCH_COUNT));
      writer.flush(PARTITION, BATCH_COUNT);
      assertThat(writer.committedOffset(PARTITION)).isEqualTo(BATCH_COUNT);
      assertThat(frontierOf(instancesTable, frontierPartition)).isEqualTo(stampedFrontierMs);
      assertThat(zeebeWatermarkOf(instancesTable, zeebePartitionId))
          .isEqualTo(stampedZeebeWatermark);

      final long committedOffsetBeforeCompaction = writer.committedOffset(PARTITION);
      final long instanceRowsBeforeCompaction = rowCount(instancesTable, duckdb);
      final long activityRowsBeforeCompaction = rowCount(activitiesTable, duckdb);

      // when a compaction pass runs
      final LakeCompactor compactor = new LakeCompactor(writer);
      final LakeCompactor.CompactionReport report = compactor.compactIfNeeded();

      // then each table's live data-file count collapsed to a small, constant number
      assertThat(report.instances().dataFilesCompacted()).isTrue();
      assertThat(report.activities().dataFilesCompacted()).isTrue();
      final int instanceFilesAfter = dataFileCount(instancesTable);
      final int activityFilesAfter = dataFileCount(activitiesTable);
      assertThat(instanceFilesAfter).isBetween(1, 2);
      assertThat(activityFilesAfter).isBetween(1, 2);

      // and every row is still present -- compaction rewrote, it never dropped or duplicated rows
      assertThat(rowCount(instancesTable, duckdb)).isEqualTo(instanceRowsBeforeCompaction);
      assertThat(rowCount(activitiesTable, duckdb)).isEqualTo(activityRowsBeforeCompaction);

      // and snapshot history was trimmed to the configured retention
      assertThat(snapshotCount(instancesTable)).isLessThanOrEqualTo(3);
      assertThat(snapshotCount(activitiesTable)).isLessThanOrEqualTo(3);

      // and -- the critical assertion -- the durable offset survived compaction unchanged; a
      // regression here would silently force a full replay from scratch on the next restart
      assertThat(writer.committedOffset(PARTITION)).isEqualTo(committedOffsetBeforeCompaction);

      // and -- Fix 1's own critical assertion -- the frontier stamp survived compaction too, not
      // just the offset
      assertThat(frontierOf(instancesTable, frontierPartition)).isEqualTo(stampedFrontierMs);

      // and -- the zbpos (origin-position dedup watermark) stamp survived the same maintenance
      // commit path: LakeCompactor's own carry-forward must re-apply all three prefixes together,
      // not just the two offset/frontier already covered above
      assertThat(zeebeWatermarkOf(instancesTable, zeebePartitionId))
          .isEqualTo(stampedZeebeWatermark);
    } finally {
      writer.close();
    }
  }

  private static InstanceRow instanceRow(final int i) {
    final long startMs = i * 1000L;
    return new InstanceRow(
        i,
        1L,
        "compaction-test-process",
        1,
        "<default>",
        "COMPLETED",
        startMs,
        startMs + 500,
        500,
        "{}");
  }

  private static ActivityRow activityRow(final int i) {
    final long startMs = i * 1000L;
    return new ActivityRow(
        i,
        "compaction-test-process",
        1,
        "<default>",
        "task",
        "SERVICE_TASK",
        i,
        "COMPLETED",
        startMs,
        startMs + 200,
        200,
        startMs);
  }

  private static int dataFileCount(final Table table) throws SQLException {
    table.refresh();
    return currentFileLocations(table).size();
  }

  private static int snapshotCount(final Table table) {
    table.refresh();
    int count = 0;
    for (final Snapshot ignored : table.snapshots()) {
      count++;
    }
    return count;
  }

  /** The current snapshot's {@code lake.frontier.p<partition>} stamp, or {@code null}. */
  private static Long frontierOf(final Table table, final int partition) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    if (snapshot == null) {
      return null;
    }
    final String value =
        snapshot.summary().get(DirectCommitSink.FRONTIER_PROPERTY_PREFIX + partition);
    return value == null ? null : Long.parseLong(value);
  }

  /**
   * The current snapshot's {@code lake.zbpos.z<zeebePartitionId>} stamp, or {@code null}. Reads
   * this one table directly (unlike {@link IcebergLakeWriter#committedZeebeWatermark(int)}'s own
   * MIN-across-tables rule) — the same single-table convention {@link #frontierOf} already uses,
   * since this test only ever stamps the instances table.
   */
  private static Long zeebeWatermarkOf(final Table table, final int zeebePartitionId) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    if (snapshot == null) {
      return null;
    }
    final String value =
        snapshot.summary().get(DirectCommitSink.ZBPOS_PROPERTY_PREFIX + zeebePartitionId);
    return value == null ? null : Long.parseLong(value);
  }

  /** Row count across every data file currently live in {@code table}'s current snapshot. */
  private static long rowCount(final Table table, final Connection duckdb) throws SQLException {
    table.refresh();
    final List<String> locations = currentFileLocations(table);
    if (locations.isEmpty()) {
      return 0L;
    }
    final String fileList =
        locations.stream()
            .map(location -> "'" + LocalFileIO.toFilesystemPath(location) + "'")
            .collect(Collectors.joining(", "));
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery("SELECT count(*) FROM read_parquet([" + fileList + "])")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static List<String> currentFileLocations(final Table table) throws SQLException {
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
    return locations;
  }
}

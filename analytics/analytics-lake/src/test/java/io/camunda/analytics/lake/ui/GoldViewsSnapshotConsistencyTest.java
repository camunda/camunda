/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.model.ActivityRow;
import io.camunda.analytics.lake.model.InstanceRow;
import io.camunda.analytics.lake.write.GoldTables;
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Task A's snapshot-consistency proof, mirroring {@link LakeUiServerSnapshotConsistencyTest} but
 * for the three gold tables (see {@code GoldTables}' class javadoc): every {@link
 * GoldTables#recompute()} call replaces a gold table's <b>entire</b> contents wholesale in one
 * commit, so two recomputes back to back leave the pre-second-recompute data file physically on
 * disk (the 3-snapshot retention policy keeps it) while the CURRENT snapshot only ever references
 * the latest one. {@link LakeUiServer#currentDataFilePaths(Table)} -- the same helper {@link
 * LakeUiServer} uses to build {@code instance_kpis}'s (and the other two gold tables') view -- must
 * see only the latest file, never both; a naive directory glob would double-count every row
 * instead.
 *
 * <p>Opens its own independent {@link JdbcCatalog}/DuckDB connection to construct {@link
 * GoldTables} against, exactly like {@code GoldTablesStandaloneRunner} and {@link LakeUiServer}
 * themselves do -- never the writer's own catalog/connection (see {@link LakeUiServer}'s class
 * javadoc for why sharing those would be unsafe).
 */
class GoldViewsSnapshotConsistencyTest {

  private static final int PARTITION = 0;
  private static final String PROCESS_ID = "gold-view-consistency-process";
  private static final long PROCESS_DEFINITION_KEY = 500L;

  @Test
  void shouldServeOnlyTheLatestRecomputesContentsThroughTheSnapshotConsistentView(
      @TempDir final Path tempDir) throws Exception {
    // given a writer holding one finished instance, and a GoldTables instance built against a
    // SEPARATE catalog/DuckDB connection (never the writer's own -- see class javadoc)
    final Path warehouseDir = tempDir.resolve("warehouse");
    final LakeConfig config =
        new LakeConfig(
            "http://localhost:0",
            "test-topic",
            "test-group",
            warehouseDir,
            tempDir.resolve("state"),
            1,
            2000L,
            0L,
            0L,
            0,
            null);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    final JdbcCatalog readCatalog = openReadOnlyCatalog(warehouseDir);
    final Connection readDuckdb = DriverManager.getConnection("jdbc:duckdb:");
    try {
      writer.append(instanceRow(1L));
      writer.append(activityRow(1L));
      writer.flush(PARTITION, 0L);

      final Table instancesTable =
          readCatalog.loadTable(TableIdentifier.of(Namespace.of("lake"), "instances"));
      final Table activitiesTable =
          readCatalog.loadTable(TableIdentifier.of(Namespace.of("lake"), "activities"));
      final GoldTables goldTables =
          new GoldTables(readCatalog, readDuckdb, instancesTable, activitiesTable);

      // when the first recompute pass runs (one instance -> one instance_kpis row)
      final GoldTables.GoldRecomputeResult firstResult = goldTables.recompute();
      assertThat(firstResult.recomputed()).isTrue();
      assertThat(firstResult.instanceKpisRows()).isEqualTo(1L);

      final Table instanceKpisTable =
          readCatalog.loadTable(TableIdentifier.of(Namespace.of("lake"), "instance_kpis"));
      final List<Path> firstFiles = LakeUiServer.currentDataFilePaths(instanceKpisTable);
      assertThat(firstFiles).hasSize(1);
      assertThat(readRowCount(readDuckdb, firstFiles)).isEqualTo(1L);

      // and a second, distinct instance lands and is flushed
      writer.append(instanceRow(2L));
      writer.append(activityRow(2L));
      writer.flush(PARTITION, 1L);

      // when the second recompute pass runs (both instances now known -> two instance_kpis rows,
      // wholesale-replacing the first pass's single-row file with one fresh two-row file)
      final GoldTables.GoldRecomputeResult secondResult = goldTables.recompute();
      assertThat(secondResult.recomputed()).isTrue();
      assertThat(secondResult.instanceKpisRows()).isEqualTo(2L);

      // then the snapshot-consistent view -- the same helper LakeUiServer uses -- sees ONLY the
      // second pass's file, with exactly the second pass's row count (not 1 + 2 = 3, which is what
      // a naive directory glob over every *.parquet file physically present would double-count to)
      final List<Path> secondFiles = LakeUiServer.currentDataFilePaths(instanceKpisTable);
      assertThat(secondFiles).hasSize(1);
      assertThat(secondFiles).doesNotContainAnyElementsOf(firstFiles);
      assertThat(readRowCount(readDuckdb, secondFiles)).isEqualTo(2L);

      // and -- proving this is a genuine metadata-vs-directory distinction, not just an artifact of
      // the old file having already been deleted -- the first pass's file is still physically
      // present on disk (RETAIN_LAST_SNAPSHOTS keeps more than the 2 snapshots created so far)
      assertThat(firstFiles.get(0)).exists();

      // and a plain directory glob over both files together WOULD double-count to 3 rows -- the
      // exact bug the snapshot-consistent view (and this test) exists to rule out
      final List<Path> bothFiles = List.of(firstFiles.get(0), secondFiles.get(0));
      assertThat(readRowCount(readDuckdb, bothFiles)).isEqualTo(3L);
    } finally {
      readDuckdb.close();
      readCatalog.close();
      writer.close();
    }
  }

  private static long readRowCount(final Connection duckdb, final List<Path> files)
      throws SQLException {
    final String fileList =
        files.stream().map(path -> "'" + path + "'").collect(Collectors.joining(", "));
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery("SELECT count(*) FROM read_parquet([" + fileList + "])")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Mirrors {@link LakeUiServer#openCatalog(Path)} / {@link IcebergLakeWriter}'s construction. */
  private static JdbcCatalog openReadOnlyCatalog(final Path warehouseDir) {
    final JdbcCatalog catalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    final String h2Url = "jdbc:h2:file:" + warehouseDir.toAbsolutePath().resolve("catalog");
    final String warehouseLocation = warehouseFileUri(warehouseDir);
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI, h2Url,
            CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));
    return catalog;
  }

  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  private static InstanceRow instanceRow(final long instanceKey) {
    final long startMs = instanceKey * 1000L;
    return new InstanceRow(
        instanceKey,
        PROCESS_DEFINITION_KEY,
        PROCESS_ID,
        1,
        "<default>",
        "COMPLETED",
        startMs,
        startMs + 500,
        500,
        "{}");
  }

  private static ActivityRow activityRow(final long instanceKey) {
    final long startMs = instanceKey * 1000L;
    return new ActivityRow(
        instanceKey,
        PROCESS_ID,
        1,
        "<default>",
        "task",
        "SERVICE_TASK",
        instanceKey,
        "COMPLETED",
        startMs,
        startMs + 200,
        200,
        startMs);
  }
}

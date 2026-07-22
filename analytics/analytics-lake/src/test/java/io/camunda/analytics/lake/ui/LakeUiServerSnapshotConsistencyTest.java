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
import io.camunda.analytics.lake.write.IcebergLakeWriter;
import io.camunda.analytics.lake.write.LakeCompactor;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link LakeUiServer#currentDataFilePaths(Table)} — the exact helper {@link LakeUiServer}
 * uses to build its {@code instances}/{@code activities} views — is snapshot-consistent: once
 * {@link LakeCompactor} rewrites a table's data files, the pre-compaction files it replaces (which
 * the 3-snapshot retention policy legitimately keeps physically on disk for a while, see {@link
 * LakeCompactor}'s "Orphan litter" javadoc) must NOT appear in the file list, even though a naive
 * directory glob would still pick them up and double-count every row.
 *
 * <p>Deliberately opens its own {@link JdbcCatalog} handle (mirroring {@link
 * LakeUiServer#openCatalog} / {@link IcebergLakeWriter}'s own construction) rather than reaching
 * into the writer's package-private {@code instancesTable()}/{@code activitiesTable()} — that is
 * the whole point of the fix under test: the UI must never share the writer's own {@code Table}
 * instances.
 */
class LakeUiServerSnapshotConsistencyTest {

  private static final int PARTITION = 0;
  // Comfortably above LakeCompactor's compaction threshold (20 live data files) so one
  // compaction pass is guaranteed to actually rewrite the table.
  private static final int BATCH_COUNT = 25;

  @Test
  void shouldExcludeCompactionRetainedFilesFromTheViewFileList(@TempDir final Path tempDir)
      throws Exception {
    // given a writer that has flushed one tiny batch per table on every one of 25 offsets, so
    // both tables sit well above the compaction threshold with one data file per flush
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
    try {
      for (int i = 0; i < BATCH_COUNT; i++) {
        writer.append(instanceRow(i));
        writer.append(activityRow(i));
        writer.flush(PARTITION, i);
      }

      // A SEPARATE, independent catalog handle -- not the writer's own Table instance -- exactly
      // like LakeUiServer opens for itself.
      final Namespace namespace = Namespace.of("lake");
      final Table instancesTable =
          readCatalog.loadTable(TableIdentifier.of(namespace, "instances"));

      final List<Path> preCompactionFiles = LakeUiServer.currentDataFilePaths(instancesTable);
      assertThat(preCompactionFiles).hasSize(BATCH_COUNT);

      // when a compaction pass rewrites the instances table's data files
      final LakeCompactor compactor = new LakeCompactor(writer);
      final LakeCompactor.CompactionReport report = compactor.compactIfNeeded();
      assertThat(report.instances().dataFilesCompacted()).isTrue();

      // then the metadata-driven file list -- the same one LakeUiServer's view uses -- contains
      // ONLY the compacted replacement(s), never the pre-compaction files
      final List<Path> postCompactionFiles = LakeUiServer.currentDataFilePaths(instancesTable);
      assertThat(postCompactionFiles).isNotEmpty();
      assertThat(postCompactionFiles.size()).isLessThan(preCompactionFiles.size());
      assertThat(postCompactionFiles).doesNotContainAnyElementsOf(preCompactionFiles);

      // and -- proving this is a genuine metadata-vs-directory distinction, not just an artifact
      // of expireSnapshots deleting files immediately -- the replaced files are still physically
      // present on disk, which is exactly what would make a directory glob double-count rows
      for (final Path oldFile : preCompactionFiles) {
        assertThat(oldFile).exists();
      }
    } finally {
      readCatalog.close();
      writer.close();
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

  private static InstanceRow instanceRow(final int i) {
    final long startMs = i * 1000L;
    return new InstanceRow(
        i,
        1L,
        "snapshot-consistency-test-process",
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
        "snapshot-consistency-test-process",
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
}

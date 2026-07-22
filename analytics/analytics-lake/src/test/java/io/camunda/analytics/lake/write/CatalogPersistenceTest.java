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
import java.nio.file.Path;
import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that tables committed through {@link IcebergLakeWriter} are durable in the underlying H2
 * file database: closing and reopening a fresh {@link JdbcCatalog} instance against the same
 * warehouse directory loads tables with the same current snapshot and summary properties intact.
 *
 * <p>This guards against catalog data loss hazards and validates that H2 is a suitable backend for
 * Iceberg's metadata (replacing atomic-rename-dependent file catalogs on object stores).
 */
class CatalogPersistenceTest {

  private static final int PARTITION = 0;
  private static final int FLUSH_COUNT = 5;

  @Test
  void shouldPersistTableMetadataAndSnapshotSummaryAcrossCatalogReopen(
      @TempDir final Path tempDir) {
    // given a warehouse with tables committed via IcebergLakeWriter
    final LakeConfig config = buildConfig(tempDir);
    final IcebergLakeWriter writer = new IcebergLakeWriter(config);
    final long expectedOffset = FLUSH_COUNT - 1;
    final String offsetProperty = IcebergLakeWriter.OFFSET_PROPERTY_PREFIX + PARTITION;
    try {
      for (int i = 0; i < FLUSH_COUNT; i++) {
        writer.append(instanceRow(i));
        writer.append(activityRow(i));
        writer.flush(PARTITION, i);
      }

      // Capture the committed offset and snapshot id before closing
      final Table instancesTable = writer.instancesTable();
      instancesTable.refresh();
      final Snapshot priorSnapshot = instancesTable.currentSnapshot();
      assertThat(priorSnapshot).isNotNull();
      final Map<String, String> priorSummary = priorSnapshot.summary();
      final String offsetValue = priorSummary.get(offsetProperty);
      assertThat(offsetValue).isNotNull().isEqualTo(String.valueOf(expectedOffset));
    } finally {
      writer.close();
    }

    // when opening a fresh catalog instance against the same warehouse directory
    final Path warehouseDir = tempDir.resolve("warehouse");
    final JdbcCatalog freshCatalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    final String h2Url = "jdbc:h2:file:" + warehouseDir.toAbsolutePath().resolve("catalog");
    final String warehouseLocation = warehouseFileUri(warehouseDir);
    freshCatalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI, h2Url,
            CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));
    try {
      // then the instances table loads with the same snapshot and summary properties
      final Table freshInstancesTable =
          freshCatalog.loadTable(TableIdentifier.of(Namespace.of("lake"), "instances"));
      freshInstancesTable.refresh();
      final Snapshot freshSnapshot = freshInstancesTable.currentSnapshot();
      assertThat(freshSnapshot).isNotNull();
      final Map<String, String> freshSummary = freshSnapshot.summary();
      assertThat(freshSummary.get(offsetProperty))
          .as("offset property persisted across catalog reopen")
          .isEqualTo(String.valueOf(expectedOffset));

      // and the activities table also loads correctly
      final Table freshActivitiesTable =
          freshCatalog.loadTable(TableIdentifier.of(Namespace.of("lake"), "activities"));
      freshActivitiesTable.refresh();
      final Snapshot freshActivitySnapshot = freshActivitiesTable.currentSnapshot();
      assertThat(freshActivitySnapshot).isNotNull();
      final String activityOffsetValue = freshActivitySnapshot.summary().get(offsetProperty);
      assertThat(activityOffsetValue)
          .as("activities offset property persisted across catalog reopen")
          .isEqualTo(String.valueOf(expectedOffset));
    } finally {
      freshCatalog.close();
    }
  }

  private static LakeConfig buildConfig(final Path tempDir) {
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

  private static InstanceRow instanceRow(final int i) {
    final long startMs = i * 1000L;
    return new InstanceRow(
        i,
        1L,
        "persistence-test-process",
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
        "persistence-test-process",
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

  /** {@code file:} URI form of a local directory, with any trailing slash stripped. */
  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}

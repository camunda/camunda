/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The committed-snapshot contract: with an Iceberg catalog present, a view serves exactly the files
 * the table's CURRENT snapshot references — an uncommitted (in-progress or orphaned) Parquet file
 * sitting in the very same {@code data/} directory is invisible. The directory glob this registry
 * used to build would have read both.
 */
class LakeViewRegistryCommittedSnapshotTest {

  @TempDir Path warehouseDir;

  private Connection duckdb;
  private LakeViewRegistry registry;

  @AfterEach
  void tearDown() throws Exception {
    if (registry != null) {
      registry.onShutdown();
    }
    if (duckdb != null) {
      duckdb.close();
    }
  }

  @Test
  void shouldServeOnlyCommittedFiles() throws Exception {
    // given: a catalog-backed table with one committed 3-row file and one uncommitted 5-row
    // orphan in the same data directory
    final JdbcCatalog catalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI,
            "jdbc:h2:file:" + warehouseDir.toAbsolutePath().resolve("catalog"),
            CatalogProperties.WAREHOUSE_LOCATION,
            warehouseDir.toAbsolutePath().toUri().toString().replaceAll("/$", "")));
    catalog.createNamespace(Namespace.of("lake"));
    final Schema schema = new Schema(Types.NestedField.required(1, "id", Types.LongType.get()));
    final Table table =
        catalog.createTable(
            TableIdentifier.of("lake", "orders"), schema, PartitionSpec.unpartitioned());

    duckdb = DriverManager.getConnection("jdbc:duckdb:");
    final String committedLocation = table.location() + "/data/committed.parquet";
    final Path committedPath = writeParquet(committedLocation, 3);
    table
        .newAppend()
        .appendFile(
            DataFiles.builder(PartitionSpec.unpartitioned())
                .withPath(committedLocation)
                .withFormat(FileFormat.PARQUET)
                .withRecordCount(3)
                .withFileSizeInBytes(Files.size(committedPath))
                .build())
        .commit();
    writeParquet(table.location() + "/data/orphan.parquet", 5);
    catalog.close();

    // when
    registry =
        new LakeViewRegistry(
            duckdb,
            new LakeServingProperties(
                warehouseDir.toString(),
                null,
                500,
                15,
                5_000_000,
                null,
                LakeServingProperties.DEFAULT_VIEW_REFRESH_MS));
    final List<String> views = registry.refresh();

    // then: the view exists and reads the committed 3 rows, not 3 + 5
    assertThat(views).contains("orders");
    try (Statement statement = duckdb.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM orders")) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getLong(1)).isEqualTo(3);
    }
  }

  private Path writeParquet(final String location, final int rows) throws Exception {
    final Path physical = LocalFileIO.toFilesystemPath(location);
    Files.createDirectories(physical.getParent());
    try (Statement statement = duckdb.createStatement()) {
      statement.execute(
          "COPY (SELECT range AS id FROM range("
              + rows
              + ")) TO '"
              + physical.toAbsolutePath()
              + "' (FORMAT PARQUET)");
    }
    return physical;
  }
}

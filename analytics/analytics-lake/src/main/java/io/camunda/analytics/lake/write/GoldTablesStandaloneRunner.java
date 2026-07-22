/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot CLI entry point: opens an <b>existing</b> lake warehouse directory and runs only {@link
 * GoldTables#recompute()} against it -- no raw-table compaction, no offset changes, nothing that
 * touches {@code instances}/{@code activities} beyond reading their current snapshot.
 *
 * <p>Deliberately opens its own, independent {@link JdbcCatalog} handle and its own embedded DuckDB
 * connection -- mirroring {@code io.camunda.analytics.lake.ui.LakeUiServer}'s own catalog handling
 * -- rather than requiring a live {@link IcebergLakeWriter}/translator process. This makes it
 * usable against a warehouse the translator process is no longer running against (e.g. a preserved
 * demo directory), safely: H2 in embedded mode supports multiple independent connections to the
 * same catalog file from within one JVM, and this class only ever reads {@code instances}/{@code
 * activities} (via {@link Table#newScan()}), never commits to them.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 *   java -cp ... io.camunda.analytics.lake.write.GoldTablesStandaloneRunner [warehouseDir]
 * </pre>
 *
 * <p>{@code warehouseDir} defaults to {@code ./data/lake} (the same default {@link
 * io.camunda.analytics.lake.LakePocApp} uses for {@code lake.dir}) when omitted.
 */
public final class GoldTablesStandaloneRunner {

  private static final Logger LOG = LoggerFactory.getLogger(GoldTablesStandaloneRunner.class);

  private GoldTablesStandaloneRunner() {}

  public static void main(final String[] args) throws SQLException {
    final Path warehouseDir =
        args.length > 0 ? Path.of(args[0]) : Path.of(System.getProperty("lake.dir", "./data/lake"));
    LOG.info("Opening lake warehouse at {} (read-only for the raw tables)", warehouseDir);

    final JdbcCatalog catalog = openCatalog(warehouseDir);
    final Connection duckdb = DriverManager.getConnection("jdbc:duckdb:");
    try {
      final Namespace namespace = Namespace.of("lake");
      final Table instancesTable = catalog.loadTable(TableIdentifier.of(namespace, "instances"));
      final Table activitiesTable = catalog.loadTable(TableIdentifier.of(namespace, "activities"));

      final GoldTables goldTables =
          new GoldTables(catalog, duckdb, instancesTable, activitiesTable);
      final GoldTables.GoldRecomputeResult result = goldTables.recompute();

      if (!result.recomputed()) {
        LOG.info(
            "Gold-table recompute skipped: no instances/activities data in this warehouse yet");
      } else {
        LOG.info(
            "Gold-table recompute done against {} -- transitions: {} row(s), "
                + "instance_kpis: {} row(s), element_bits: {} row(s)",
            warehouseDir,
            result.transitionsRows(),
            result.instanceKpisRows(),
            result.elementBitsRows());
      }
    } finally {
      duckdb.close();
      catalog.close();
    }
  }

  /** Mirrors {@code LakeUiServer#openCatalog(Path)} / {@link IcebergLakeWriter}'s construction. */
  private static JdbcCatalog openCatalog(final Path warehouseDir) {
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

  /** {@code file:} URI form of a local directory, with any trailing slash stripped. */
  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Discovers the lake's tables by <b>listing directories</b> under {@code <warehouseDir>/lake/} —
 * deliberately dynamic, never a hardcoded table name list, since tables appear one at a time as
 * other lanes merge their own writers. For each table directory found, registers a DuckDB view
 * (named after the directory) over {@code <table>/data/*&#47;*.parquet} if — and only if — at least
 * one matching Parquet file already exists; a table directory with no data yet (or ever) is skipped
 * and logged, never treated as an error.
 *
 * <p>Runs once at startup ({@link #registerViews()}), not lazily per request: this is the
 * scaffold's proof-of-life discovery pass. A follow-up lane can add a refresh/rescan endpoint if
 * new tables appearing after startup needs to be picked up without a restart.
 */
@Component
public class LakeViewRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(LakeViewRegistry.class);

  private final Connection connection;
  private final Path warehouseDir;
  private final List<String> registeredTables = new ArrayList<>();

  public LakeViewRegistry(final Connection connection, final LakeServingProperties properties) {
    this.connection = connection;
    warehouseDir = properties.warehouseDirPath();
  }

  @PostConstruct
  void registerViews() {
    final Path lakeDir = warehouseDir.resolve("lake");
    if (!Files.isDirectory(lakeDir)) {
      LOG.info(
          "No lake directory found at {} yet -- no views registered. Views appear once the lake "
              + "writer has flushed at least one table.",
          lakeDir);
      return;
    }

    final List<Path> tableDirs;
    try (Stream<Path> entries = Files.list(lakeDir)) {
      tableDirs = entries.filter(Files::isDirectory).sorted().toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list lake tables under " + lakeDir, e);
    }

    for (final Path tableDir : tableDirs) {
      registerViewIfDataPresent(tableDir.getFileName().toString(), tableDir);
    }
    LOG.info(
        "Discovered {} lake table director{} under {}; registered {} view(s) with data: {}",
        tableDirs.size(),
        tableDirs.size() == 1 ? "y" : "ies",
        lakeDir,
        registeredTables.size(),
        registeredTables);
  }

  /** Names of the views this registry successfully created at startup. */
  public List<String> registeredTables() {
    return List.copyOf(registeredTables);
  }

  private void registerViewIfDataPresent(final String tableName, final Path tableDir) {
    final Path dataDir = tableDir.resolve("data");
    if (!containsParquetFiles(dataDir)) {
      LOG.info(
          "Skipping view '{}': no Parquet files found yet under {}/*/*.parquet",
          tableName,
          dataDir);
      return;
    }
    final String glob = dataDir.resolve("*").resolve("*.parquet").toString();
    final String sql =
        "CREATE OR REPLACE VIEW "
            + quoteIdentifier(tableName)
            + " AS SELECT * FROM read_parquet('"
            + escapeSqlLiteral(glob)
            + "')";
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
      registeredTables.add(tableName);
    } catch (final SQLException e) {
      LOG.warn("Failed to register view '{}' from {}: {}", tableName, glob, e.getMessage());
    }
  }

  private static boolean containsParquetFiles(final Path dataDir) {
    if (!Files.isDirectory(dataDir)) {
      return false;
    }
    try (Stream<Path> partitionDirs = Files.list(dataDir)) {
      return partitionDirs
          .filter(Files::isDirectory)
          .anyMatch(LakeViewRegistry::containsParquetFile);
    } catch (final IOException e) {
      return false;
    }
  }

  private static boolean containsParquetFile(final Path partitionDir) {
    try (Stream<Path> files = Files.list(partitionDir)) {
      return files.anyMatch(f -> f.getFileName().toString().endsWith(".parquet"));
    } catch (final IOException e) {
      return false;
    }
  }

  private static String quoteIdentifier(final String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  private static String escapeSqlLiteral(final String value) {
    return value.replace("'", "''");
  }
}

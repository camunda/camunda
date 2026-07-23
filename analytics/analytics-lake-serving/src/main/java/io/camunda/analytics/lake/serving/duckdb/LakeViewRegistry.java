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
import java.util.Set;
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
 * <p>Runs once at startup ({@link #registerViews()}) and again on demand: {@link #refresh()}
 * re-runs the same scan (e.g. behind {@code POST /api/refresh}), and {@link
 * #ensureAvailable(String...)} triggers exactly one such rescan when a request names a view that
 * isn't registered yet — never a per-query rescan, so a single request that issues several SQL
 * statements against the same still-missing view only ever re-triggers discovery once. Re-running
 * the scan is safe to call concurrently with itself or a request in flight: {@link
 * #registeredTables} is swapped atomically, and {@code CREATE OR REPLACE VIEW} makes a raced
 * re-registration harmless.
 */
@Component
public class LakeViewRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(LakeViewRegistry.class);

  private final Connection connection;
  private final Path warehouseDir;
  private volatile List<String> registeredTables = List.of();

  public LakeViewRegistry(final Connection connection, final LakeServingProperties properties) {
    this.connection = connection;
    warehouseDir = properties.warehouseDirPath();
  }

  @PostConstruct
  void onStartup() {
    refresh();
  }

  /**
   * Re-runs view discovery from scratch and returns the resulting view list. Safe to call
   * repeatedly (e.g. once per {@code POST /api/refresh} call, or lazily from {@link
   * #ensureAvailable}): a table directory that still has no Parquet data is skipped exactly as at
   * startup, and an already-registered view is simply {@code CREATE OR REPLACE}d again.
   */
  public synchronized List<String> refresh() {
    final Path lakeDir = warehouseDir.resolve("lake");
    if (!Files.isDirectory(lakeDir)) {
      LOG.info(
          "No lake directory found at {} yet -- no views registered. Views appear once the lake "
              + "writer has flushed at least one table.",
          lakeDir);
      registeredTables = List.of();
      return registeredTables;
    }

    final List<Path> tableDirs;
    try (Stream<Path> entries = Files.list(lakeDir)) {
      tableDirs = entries.filter(Files::isDirectory).sorted().toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list lake tables under " + lakeDir, e);
    }

    final List<String> discovered = new ArrayList<>();
    for (final Path tableDir : tableDirs) {
      registerViewIfDataPresent(tableDir.getFileName().toString(), tableDir, discovered);
    }
    registeredTables = List.copyOf(discovered);
    LOG.info(
        "Discovered {} lake table director{} under {}; registered {} view(s) with data: {}",
        tableDirs.size(),
        tableDirs.size() == 1 ? "y" : "ies",
        lakeDir,
        registeredTables.size(),
        registeredTables);
    return registeredTables;
  }

  /** Names of the views currently registered. */
  public List<String> registeredTables() {
    return registeredTables;
  }

  /**
   * Ensures every named view is registered, refreshing discovery <b>at most once</b> if any is
   * currently missing. Returns whether all of them are present after that single attempt — {@code
   * false} means the backing table genuinely has no data yet (or never will, e.g. {@code
   * object_lifecycle} in a warehouse that predates it), which callers use to degrade gracefully
   * rather than error.
   */
  public boolean ensureAvailable(final String... viewNames) {
    final Set<String> present = Set.copyOf(registeredTables());
    boolean allPresent = true;
    for (final String name : viewNames) {
      if (!present.contains(name)) {
        allPresent = false;
        break;
      }
    }
    if (allPresent) {
      return true;
    }
    final Set<String> refreshed = Set.copyOf(refresh());
    for (final String name : viewNames) {
      if (!refreshed.contains(name)) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code viewName} is registered right now, without triggering a rescan. */
  public boolean has(final String viewName) {
    return registeredTables().contains(viewName);
  }

  private void registerViewIfDataPresent(
      final String tableName, final Path tableDir, final List<String> discovered) {
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
      discovered.add(tableName);
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

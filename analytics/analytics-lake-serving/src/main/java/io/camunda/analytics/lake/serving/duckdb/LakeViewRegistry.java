/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import io.camunda.analytics.lake.serving.config.LakeServingProperties;
import io.camunda.analytics.lake.write.LocalFileIO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers one DuckDB view per lake table, derived — whenever the warehouse has an Iceberg catalog
 * — from each table's <b>current committed snapshot</b>: the view is {@code SELECT * FROM
 * read_parquet([...])} over exactly the data files {@code table.newScan().planFiles()} reports.
 * Reading what the catalog says is committed (instead of globbing the {@code data/} directories, as
 * this class originally did) is load-bearing three times over, all three defects observed or
 * documented against the glob:
 *
 * <ul>
 *   <li><b>Torn reads.</b> With the ingest engine hosted in this same process ({@code
 *       lake.serving.ingest.enabled}), the sink streams Parquet segment files into the very
 *       directories a glob reads — a query racing an in-progress file fails with DuckDB's "No magic
 *       bytes found at end of file". A committed snapshot only ever references complete files.
 *   <li><b>Double counting.</b> Compaction rewrites data files but retains recent snapshots, so a
 *       superseded file legitimately stays on disk until snapshot expiry — a glob reads the old
 *       file AND its replacement (see {@code LakeUiServer#ensureIcebergBackedView}, which made the
 *       same move for the same reason).
 *   <li><b>Read-uncommitted.</b> Files written but not yet (or never, after a crash) committed are
 *       invisible to the snapshot, keeping reads consistent with the lake's offset authority (the
 *       snapshot summary): what you can query is exactly what has been durably committed.
 * </ul>
 *
 * <p>Discovery is catalog-driven too ({@code listTables} on the {@code lake} namespace — never a
 * hardcoded table list). The registry opens its own read-side {@link JdbcCatalog} handle against
 * the warehouse's H2 catalog file; it deliberately does NOT share the in-process writer's catalog
 * or {@link Table} instances (same unsynchronized-concurrent-access reasoning as {@code
 * LakeUiServer}'s own independent handle). Views are re-derived on {@link #refresh()} — called at
 * startup, on {@code POST /api/refresh}, lazily by {@link #ensureAvailable(String...)}, and every
 * {@link LakeServingProperties#viewRefreshMs()} by a background refresher so dashboards track new
 * commits without manual refreshes.
 *
 * <p><b>Catalog-less fallback:</b> a warehouse without a {@code catalog.mv.db} (the test fixtures'
 * plain-Parquet directory trees) falls back to the original directory glob over {@code
 * <table>/data/*&#47;*.parquet}. That mode keeps the fixture-built warehouses working and is safe
 * there precisely because no writer or compactor ever touches them mid-read.
 *
 * <p>Concurrency: {@link #refresh()} is {@code synchronized}; {@link #registeredTables} is swapped
 * atomically, and {@code CREATE OR REPLACE VIEW} makes a raced re-registration harmless. (H2 note:
 * the writer opens the catalog in plain embedded mode, so this same-JVM second handle is fine — the
 * one-backend shape — while a reader in a <em>separate</em> process would need the writer to switch
 * its catalog URL to {@code AUTO_SERVER=TRUE}.)
 */
@Component
public class LakeViewRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(LakeViewRegistry.class);
  private static final Namespace LAKE_NAMESPACE = Namespace.of("lake");

  private final Connection connection;
  private final Path warehouseDir;
  private final long viewRefreshMs;
  private final Map<TableIdentifier, Table> tableHandles = new HashMap<>();
  private JdbcCatalog catalog;
  private ScheduledExecutorService refresher;
  private volatile List<String> registeredTables = List.of();

  public LakeViewRegistry(final Connection connection, final LakeServingProperties properties) {
    this.connection = connection;
    warehouseDir = properties.warehouseDirPath();
    viewRefreshMs = properties.viewRefreshMs();
  }

  @PostConstruct
  void onStartup() {
    refresh();
    if (viewRefreshMs > 0) {
      refresher =
          Executors.newSingleThreadScheduledExecutor(
              runnable -> {
                final Thread thread = new Thread(runnable, "lake-view-refresh");
                thread.setDaemon(true);
                return thread;
              });
      refresher.scheduleWithFixedDelay(
          this::refreshQuietly, viewRefreshMs, viewRefreshMs, TimeUnit.MILLISECONDS);
    }
  }

  @PreDestroy
  synchronized void onShutdown() {
    if (refresher != null) {
      refresher.shutdownNow();
      refresher = null;
    }
    if (catalog != null) {
      catalog.close();
      catalog = null;
    }
  }

  /** {@link #refresh()} for the background refresher: never lets an exception kill the loop. */
  private void refreshQuietly() {
    try {
      refresh();
    } catch (final RuntimeException e) {
      LOG.warn("Background view refresh failed (will retry): {}", e.getMessage());
    }
  }

  /**
   * Re-derives every view and returns the resulting view list. Catalog mode re-plans each table's
   * current snapshot; fallback mode re-runs the directory scan. Safe to call repeatedly and
   * concurrently with queries in flight (see class javadoc).
   */
  public synchronized List<String> refresh() {
    if (catalog == null) {
      openCatalogIfPresent();
    }
    final List<String> discovered =
        catalog != null ? refreshFromCatalog() : refreshFromDirectoryGlob();
    registeredTables = List.copyOf(discovered);
    return registeredTables;
  }

  /** Names of the views currently registered. */
  public List<String> registeredTables() {
    return registeredTables;
  }

  /**
   * Ensures every named view is registered, refreshing discovery <b>at most once</b> if any is
   * currently missing. Returns whether all of them are present after that single attempt — {@code
   * false} means the backing table genuinely has no committed data yet (or never will, e.g. {@code
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

  // ---------------------------------------------------------------------------------------------
  // Committed-snapshot mode
  // ---------------------------------------------------------------------------------------------

  /**
   * Opens the read-side catalog handle once the warehouse's H2 catalog file exists (the writer
   * creates it with its first table). Until then this stays {@code null} and discovery uses the
   * directory fallback — a later {@link #refresh()} upgrades to catalog mode automatically.
   */
  private void openCatalogIfPresent() {
    if (!Files.isRegularFile(warehouseDir.resolve("catalog.mv.db"))) {
      return;
    }
    try {
      final JdbcCatalog opened = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
      opened.initialize(
          "lake",
          Map.of(
              CatalogProperties.URI,
              "jdbc:h2:file:" + warehouseDir.toAbsolutePath().resolve("catalog"),
              CatalogProperties.WAREHOUSE_LOCATION,
              warehouseFileUri(warehouseDir)));
      catalog = opened;
      LOG.info(
          "Opened the Iceberg catalog at {} -- views now track committed snapshots", warehouseDir);
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not open the Iceberg catalog at {} ({}); falling back to directory discovery",
          warehouseDir,
          e.getMessage());
    }
  }

  private List<String> refreshFromCatalog() {
    final List<String> discovered = new ArrayList<>();
    final List<TableIdentifier> identifiers;
    try {
      identifiers = new ArrayList<>(catalog.listTables(LAKE_NAMESPACE));
    } catch (final NoSuchNamespaceException e) {
      LOG.info("No '{}' namespace in the catalog yet -- no views registered", LAKE_NAMESPACE);
      return discovered;
    }
    identifiers.sort(Comparator.comparing(TableIdentifier::name));
    for (final TableIdentifier identifier : identifiers) {
      final Table table;
      try {
        table = tableHandles.computeIfAbsent(identifier, catalog::loadTable);
      } catch (final RuntimeException e) {
        LOG.warn("Skipping table {} (not loadable yet): {}", identifier, e.getMessage());
        continue;
      }
      final List<Path> files = currentDataFilePaths(table);
      if (files.isEmpty()) {
        continue; // no committed data yet -- same graceful skip as an absent table directory.
      }
      final String fileList =
          files.stream()
              .map(path -> "'" + escapeSqlLiteral(path.toString()) + "'")
              .collect(Collectors.joining(", "));
      final String sql =
          "CREATE OR REPLACE VIEW "
              + quoteIdentifier(identifier.name())
              + " AS SELECT * FROM read_parquet(["
              + fileList
              + "])";
      try (Connection session = ((DuckDBConnection) connection).duplicate();
          Statement statement = session.createStatement()) {
        statement.execute(sql);
        discovered.add(identifier.name());
      } catch (final SQLException e) {
        LOG.warn(
            "Failed to register view '{}' over {} committed file(s): {}",
            identifier.name(),
            files.size(),
            e.getMessage());
      }
    }
    LOG.debug(
        "Committed-snapshot refresh registered {} view(s): {}", discovered.size(), discovered);
    return discovered;
  }

  /**
   * Snapshot-consistent list of {@code table}'s current data-file paths: refreshes the handle,
   * plans the current snapshot's files, dedupes by file location (one physical file can be split
   * into several scan-task byte ranges), and normalizes each location to a plain filesystem path
   * DuckDB can open (same idiom as {@code LakeUiServer#currentDataFilePaths}).
   */
  private static List<Path> currentDataFilePaths(final Table table) {
    table.refresh();
    final LinkedHashSet<String> locations = new LinkedHashSet<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        locations.add(task.file().location());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to plan data files for " + table.name(), e);
    }
    return locations.stream().map(LocalFileIO::toFilesystemPath).toList();
  }

  /** {@code file:} URI form of a local directory, with any trailing slash stripped. */
  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  // ---------------------------------------------------------------------------------------------
  // Catalog-less fallback (fixture warehouses): the original directory-glob discovery
  // ---------------------------------------------------------------------------------------------

  private List<String> refreshFromDirectoryGlob() {
    final Path lakeDir = warehouseDir.resolve("lake");
    if (!Files.isDirectory(lakeDir)) {
      LOG.info(
          "No catalog and no lake directory at {} yet -- no views registered. Views appear once "
              + "the lake writer has flushed at least one table.",
          lakeDir);
      return List.of();
    }
    final List<Path> tableDirs;
    try (Stream<Path> entries = Files.list(lakeDir)) {
      tableDirs = entries.filter(Files::isDirectory).sorted().toList();
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to list lake tables under " + lakeDir, e);
    }
    final List<String> discovered = new ArrayList<>();
    for (final Path tableDir : tableDirs) {
      registerGlobViewIfDataPresent(tableDir.getFileName().toString(), tableDir, discovered);
    }
    LOG.info(
        "Directory discovery (no catalog) registered {} view(s) with data: {}",
        discovered.size(),
        discovered);
    return discovered;
  }

  private void registerGlobViewIfDataPresent(
      final String tableName, final Path tableDir, final List<String> discovered) {
    final Path dataDir = tableDir.resolve("data");
    if (!containsParquetFiles(dataDir)) {
      return;
    }
    final String glob = dataDir.resolve("*").resolve("*.parquet").toString();
    final String sql =
        "CREATE OR REPLACE VIEW "
            + quoteIdentifier(tableName)
            + " AS SELECT * FROM read_parquet('"
            + escapeSqlLiteral(glob)
            + "')";
    try (Connection session = ((DuckDBConnection) connection).duplicate();
        Statement statement = session.createStatement()) {
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

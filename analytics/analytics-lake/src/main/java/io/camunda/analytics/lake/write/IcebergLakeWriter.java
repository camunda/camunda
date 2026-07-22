/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.model.ActivityRow;
import io.camunda.analytics.lake.model.InstanceRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.types.Types;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LakeWriter} where DuckDB writes the Parquet files and iceberg-core commits them: DuckDB
 * never touches the catalog, and iceberg-core never touches row data — it only ever sees a finished
 * file's path, format, row count and byte size, wrapped in a {@link DataFile} it can register.
 *
 * <h2>Exactly-once via per-table offset stamping</h2>
 *
 * <p>The two tables ({@code instances}, {@code activities}) are committed to <b>independently</b> —
 * one {@code newAppend()} per table per {@link #flush(int, long)} call, never a single transaction
 * spanning both. A crash between the two commits is possible and expected: one table ends up
 * durably ahead of the other for that source partition. {@link #committedOffset(int)} therefore
 * reports the <em>minimum</em> of the two tables' committed offsets, and {@link #flush(int, long)}
 * independently no-ops a table whose own committed offset already covers {@code throughOffset}.
 * Together these two rules make replay converge without any separate dedup table: after a crash,
 * the translator resumes at {@code committedOffset() + 1}, replays forward, and every table whose
 * commit had already landed silently skips re-appending rows it already has.
 *
 * <h2>Why every commit re-stamps every partition's offset, not just the flushed one</h2>
 *
 * <p>Iceberg does not carry a snapshot's custom summary properties forward into the next snapshot —
 * each commit's summary reflects only what that specific operation set. {@link
 * #committedOffset(int)} and the flush skip-check both do a single current-snapshot lookup rather
 * than walking snapshot history (that's what the {@link LakeWriter} javadoc's "current snapshot
 * summary" language means literally). If a commit only stamped the partition it just flushed, an
 * older partition's last-known offset would appear to regress to {@code -1} the instant a
 * <em>different</em> partition's flush landed the next snapshot. So {@link #flushTable} reads the
 * table's current summary before committing and re-sets every {@code lake.offset.p*} entry found
 * there, plus the one partition actually advancing — the new snapshot's summary is always a
 * complete, self-contained map of every partition ever committed to that table.
 *
 * <h2>Constraint inherited from the app loop</h2>
 *
 * <p>The in-memory buffers ({@link #bufferedInstances}, {@link #bufferedActivities}) hold rows for
 * exactly one source partition at any time {@link #flush(int, long)} is called — the app flushes
 * whenever it is about to move to a different partition's records, never interleaving two
 * partitions' rows in one buffer. This class does not itself enforce that; it trusts the caller.
 *
 * <h2>No longer the production raw-ingest path</h2>
 *
 * <p>{@code LakePocApp}'s production wiring no longer calls {@link #append(InstanceRow)}/{@link
 * #append(ActivityRow)}/{@link #flush(int, long)}: raw-table ingest goes through the L0 sink's
 * {@code io.camunda.analytics.lake.sink.pipeline.SinkPipeline} + {@code
 * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} instead (see {@code
 * LakeTranslator}/{@code LakePocApp}). This class is retained for: table creation (schemas + field
 * ids + name-mapping property, both tables' authoritative source), {@link #committedOffset(int)}
 * and the offset-stamping property/carry-forward pattern {@link DirectCommitSink} reuses, and the
 * embedded DuckDB connection {@link LakeCompactor}/{@code GoldTables} still drive for compaction
 * rewrites and gold-table derivation (neither of which is raw-table ingest). The buffered
 * append/flush methods below stay because the compaction/gold-table/UI test suites still construct
 * this class directly and use them to seed fixture data — removing them would force rewriting five
 * otherwise-unrelated test files for no behavioral gain (see the module's L0-sink integration test
 * for how the new pipelines are exercised instead).
 */
public final class IcebergLakeWriter implements LakeWriter {

  /**
   * Prefix of the snapshot summary property this writer uses to stamp a partition's offset. Public
   * (not package-private): {@link LakeCompactor} re-stamps these same properties onto every
   * snapshot its own rewrite operations produce, for the same reason {@link #flushTable} does here
   * — see this class's javadoc and {@link LakeCompactor}'s for why a snapshot's summary is never
   * carried forward automatically — and {@code
   * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} (a different package: the L0 sink's
   * direct-commit path, which replaced this class's own buffered append/flush for raw-table ingest)
   * reuses the exact same property key so {@link #committedOffset(int)} keeps working unchanged
   * regardless of which of the two commits a snapshot.
   */
  public static final String OFFSET_PROPERTY_PREFIX = "lake.offset.p";

  private static final String INSTANCES_STAGING_TABLE = "staging_instances";
  private static final String ACTIVITIES_STAGING_TABLE = "staging_activities";

  // CREATE OR REPLACE: idempotent across repeated flushes on the same long-lived DuckDB
  // connection, and cheap — this is schema DDL, not data.
  private static final String INSTANCES_STAGING_DDL =
      "CREATE OR REPLACE TEMP TABLE "
          + INSTANCES_STAGING_TABLE
          + " (key BIGINT, process_definition_key BIGINT, process_id VARCHAR, version INTEGER, "
          + "tenant_id VARCHAR, state VARCHAR, start_ms BIGINT, end_ms BIGINT, "
          + "duration_ms BIGINT, vars_json VARCHAR)";

  private static final String ACTIVITIES_STAGING_DDL =
      "CREATE OR REPLACE TEMP TABLE "
          + ACTIVITIES_STAGING_TABLE
          + " (instance_key BIGINT, process_id VARCHAR, version INTEGER, tenant_id VARCHAR, "
          + "element_id VARCHAR, element_type VARCHAR, element_key BIGINT, state VARCHAR, "
          + "start_ms BIGINT, end_ms BIGINT, duration_ms BIGINT, instance_start_ms BIGINT)";

  /**
   * Mirrors {@link InstanceRow} field for field. {@code start_ms}/{@code end_ms} are plain {@link
   * Types.LongType} millis columns rather than Iceberg's {@code timestamp} logical type — a
   * deliberate PoC simplification. Parquet's timestamp encoding carries a unit (millis/micros) and
   * an "is UTC adjusted" flag that DuckDB, iceberg-core and any downstream reader all have to agree
   * on; a plain 64-bit integer column sidesteps that entirely at the cost of every consumer having
   * to remember these are millis-since-epoch, not a temporal type (queries must {@code
   * to_timestamp(start_ms / 1000)} explicitly — see the README's demo queries).
   *
   * <p>{@code vars_json} (field id 10) is {@link Types.BinaryType}, not {@link Types.StringType}:
   * the L0 sink's {@code io.camunda.analytics.lake.sink.ColumnType#BINARY} column for this field
   * stores raw UTF-8 bytes (never boxes a {@code String} on the hot path — see {@code
   * TableSchema}'s javadoc), so the catalog schema's own field type has to agree, or
   * iceberg-parquet's generic writer hands a {@code ByteBuffer} to a column configured for {@code
   * CharSequence} and throws a {@code ClassCastException}. This does not disturb this class's own
   * legacy DuckDB-appender path (still exercised by the compaction/gold-table/UI tests that
   * construct {@link IcebergLakeWriter} directly): DuckDB writes Parquet files by inferring types
   * from the staged {@code VARCHAR} column, never consulting this catalog {@link Schema}, and every
   * reader of those legacy files (DuckDB {@code read_parquet}) resolves columns by their own
   * physical Parquet metadata, not by this catalog schema either.
   */
  private static final Schema INSTANCE_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "key", Types.LongType.get()),
              Types.NestedField.required(2, "process_definition_key", Types.LongType.get()),
              Types.NestedField.required(3, "process_id", Types.StringType.get()),
              Types.NestedField.required(4, "version", Types.IntegerType.get()),
              Types.NestedField.required(5, "tenant_id", Types.StringType.get()),
              Types.NestedField.required(6, "state", Types.StringType.get()),
              Types.NestedField.required(7, "start_ms", Types.LongType.get()),
              Types.NestedField.required(8, "end_ms", Types.LongType.get()),
              Types.NestedField.required(9, "duration_ms", Types.LongType.get()),
              Types.NestedField.required(10, "vars_json", Types.BinaryType.get())));

  /**
   * Mirrors {@link ActivityRow} field for field; see {@link #INSTANCE_SCHEMA} for the millis note.
   */
  private static final Schema ACTIVITY_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "instance_key", Types.LongType.get()),
              Types.NestedField.required(2, "process_id", Types.StringType.get()),
              Types.NestedField.required(3, "version", Types.IntegerType.get()),
              Types.NestedField.required(4, "tenant_id", Types.StringType.get()),
              Types.NestedField.required(5, "element_id", Types.StringType.get()),
              Types.NestedField.required(6, "element_type", Types.StringType.get()),
              Types.NestedField.required(7, "element_key", Types.LongType.get()),
              Types.NestedField.required(8, "state", Types.StringType.get()),
              Types.NestedField.required(9, "start_ms", Types.LongType.get()),
              Types.NestedField.required(10, "end_ms", Types.LongType.get()),
              Types.NestedField.required(11, "duration_ms", Types.LongType.get()),
              // The owning instance's start -- the family date activities will be partitioned and
              // retired by (see ActivityRow#instanceStartMs).
              Types.NestedField.required(12, "instance_start_ms", Types.LongType.get())));

  private static final Logger LOG = LoggerFactory.getLogger(IcebergLakeWriter.class);

  private final JdbcCatalog catalog;
  private final Table instancesTable;
  private final Table activitiesTable;
  private final Connection duckdb;

  private final List<InstanceRow> bufferedInstances = new ArrayList<>();
  private final List<ActivityRow> bufferedActivities = new ArrayList<>();

  public IcebergLakeWriter(final LakeConfig config) {
    try {
      Files.createDirectories(config.warehouseDir());
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to create warehouse directory " + config.warehouseDir(), e);
    }

    // iceberg-core's JdbcCatalog constructor takes an ioBuilder function rather than a FileIO
    // instance directly, so it can defer construction until initialize() has the catalog
    // properties available (LocalFileIO happens not to need them, but the shape is fixed by the
    // constructor). Passing null for the client-pool builder keeps the default JdbcClientPool,
    // which is a small connection pool over java.sql.DriverManager — nothing custom needed for a
    // local H2 file database.
    catalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    final String h2Url =
        "jdbc:h2:file:" + config.warehouseDir().toAbsolutePath().resolve("catalog");
    final String warehouseLocation = warehouseFileUri(config.warehouseDir());
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI, h2Url,
            CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));

    final Namespace namespace = Namespace.of("lake");
    if (!catalog.namespaceExists(namespace)) {
      catalog.createNamespace(namespace);
    }
    instancesTable = tableOrCreate(TableIdentifier.of(namespace, "instances"), INSTANCE_SCHEMA);
    activitiesTable = tableOrCreate(TableIdentifier.of(namespace, "activities"), ACTIVITY_SCHEMA);

    try {
      // One embedded, in-process DuckDB instance for the life of this writer. It never persists
      // anything itself (no ATTACH'd database file) -- it is used purely as a Parquet-writing
      // engine: rows land in a TEMP table via the Appender API, then COPY ... TO reads that TEMP
      // table back out as a Parquet file on disk.
      duckdb = DriverManager.getConnection("jdbc:duckdb:");
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to open embedded DuckDB connection", e);
    }
  }

  private Table tableOrCreate(final TableIdentifier identifier, final Schema schema) {
    if (catalog.tableExists(identifier)) {
      return catalog.loadTable(identifier);
    }
    // UNPARTITIONED is a deliberate PoC simplification (see LakeConfig / module README):
    // partitioning by e.g. a truncated start_ms would speed up time-range queries at the cost of
    // small-file proliferation from this writer's one-file-per-flush pattern, which is a tuning
    // question deferred past this PoC.
    //
    // The name mapping is load-bearing for interoperability: the Parquet files written by the
    // embedded engine carry no Iceberg field ids, so without a default name mapping any strict
    // external reader (Spark, PyIceberg, warehouses mounting the table) would resolve every
    // column to null. The mapping tells readers to resolve columns by name instead.
    return catalog.createTable(
        identifier,
        schema,
        PartitionSpec.unpartitioned(),
        Map.of(
            TableProperties.DEFAULT_NAME_MAPPING,
            NameMappingParser.toJson(MappingUtil.create(schema))));
  }

  /** {@code file:} URI form of a local directory, with any trailing slash stripped. */
  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  @Override
  public void append(final InstanceRow row) {
    bufferedInstances.add(row);
  }

  @Override
  public void append(final ActivityRow row) {
    bufferedActivities.add(row);
  }

  @Override
  public void flush(final int sourcePartition, final long throughOffset) {
    // The two commits below are independent and NOT atomic with each other -- see the class
    // javadoc. Order doesn't matter for correctness: if the process dies between them, one table
    // is left durably ahead of the other for this partition, committedOffset() reports the
    // (lagging) minimum, and the next flush's per-table skip-check converges both tables without
    // re-appending anything already durable.
    flushTable(
        instancesTable,
        bufferedInstances,
        "instances",
        INSTANCES_STAGING_DDL,
        INSTANCES_STAGING_TABLE,
        IcebergLakeWriter::appendInstanceRow,
        sourcePartition,
        throughOffset);
    flushTable(
        activitiesTable,
        bufferedActivities,
        "activities",
        ACTIVITIES_STAGING_DDL,
        ACTIVITIES_STAGING_TABLE,
        IcebergLakeWriter::appendActivityRow,
        sourcePartition,
        throughOffset);
  }

  private <T> void flushTable(
      final Table table,
      final List<T> bufferedRows,
      final String tableLabel,
      final String stagingDdl,
      final String stagingTableName,
      final RowAppender<T> rowAppender,
      final int partition,
      final long throughOffset) {
    table.refresh();
    final Snapshot current = table.currentSnapshot();
    final Map<String, String> priorSummary = current == null ? Map.of() : current.summary();
    final long committed = offsetOf(priorSummary, partition);
    if (throughOffset <= committed) {
      // Idempotent replay: this table's data for this partition through throughOffset is already
      // durable (either from a prior process's crash-then-resume, or because this table's own
      // flush for a later offset already landed in an earlier call within this same process).
      bufferedRows.clear();
      return;
    }

    final AppendFiles append = table.newAppend();
    // Re-stamp every partition this table has ever seen (see class javadoc) -- not just the one
    // advancing now -- so the new snapshot's summary remains a complete map.
    priorSummary.forEach(
        (key, value) -> {
          if (key.startsWith(OFFSET_PROPERTY_PREFIX)) {
            append.set(key, value);
          }
        });
    append.set(OFFSET_PROPERTY_PREFIX + partition, Long.toString(throughOffset));

    if (bufferedRows.isEmpty()) {
      // Zero rows of this row type in the flushed range (e.g. a batch of pure activity
      // completions produces no instance rows), but the offset still needs to advance so this
      // table doesn't lag the other one. We choose a metadata-only commit (an append with no
      // added files) over accepting the lag: it keeps both tables' committedOffset() in lockstep,
      // which keeps the app's cheap per-record replay-guard check simple (one committed offset
      // per partition, not one per (table, partition)). The cost is an extra small snapshot/
      // manifest-list write with no data -- negligible at PoC scale.
      LOG.info(
          "Advancing {} table {} partition {} offset to {} (no rows this flush)",
          tableLabel,
          table.name(),
          partition,
          throughOffset);
    } else {
      final DataFile file =
          writeParquet(
              table,
              tableLabel,
              stagingDdl,
              stagingTableName,
              rowAppender,
              bufferedRows,
              partition,
              committed + 1,
              throughOffset);
      append.appendFile(file);
    }
    append.commit();
    bufferedRows.clear();
  }

  private <T> DataFile writeParquet(
      final Table table,
      final String tableLabel,
      final String stagingDdl,
      final String stagingTableName,
      final RowAppender<T> rowAppender,
      final List<T> rows,
      final int partition,
      final long fromOffset,
      final long throughOffset) {
    final String fileName =
        String.format("p%d-%d-%d.parquet", partition, fromOffset, throughOffset);
    // Logical location Iceberg tracks in the DataFile -- keeps the file: URI scheme every other
    // table/warehouse location in this catalog uses.
    final String location = table.location() + "/data/" + fileName;
    // Physical path DuckDB actually writes bytes to; see LocalFileIO's javadoc for why these two
    // differ and how they're reconciled.
    final Path physicalPath = LocalFileIO.toFilesystemPath(location);
    try {
      if (physicalPath.getParent() != null) {
        Files.createDirectories(physicalPath.getParent());
      }
      try (Statement ddl = duckdb.createStatement()) {
        ddl.execute(stagingDdl);
      }
      // DuckDBConnection is DuckDB's JDBC-driver-specific extension of java.sql.Connection; the
      // object returned by DriverManager.getConnection(...) for a jdbc:duckdb: URL already IS one
      // (the driver's own Connection implementation), so this is a plain downcast, not an
      // unwrap() through a connection-pool proxy.
      final DuckDBConnection duckdbConnection = (DuckDBConnection) duckdb;
      try (DuckDBAppender appender = duckdbConnection.createAppender(stagingTableName)) {
        for (final T row : rows) {
          appender.beginRow();
          rowAppender.appendRow(appender, row);
          appender.endRow();
        }
        // DuckDBAppender.close() flushes buffered rows into the staging table; there is no
        // separate explicit flush() call needed here because try-with-resources calls close().
      }
      try (Statement copy = duckdb.createStatement()) {
        copy.execute(
            "COPY (SELECT * FROM "
                + stagingTableName
                + ") TO '"
                + physicalPath
                + "' (FORMAT PARQUET)");
      }
      final long fileSizeBytes = Files.size(physicalPath);
      LOG.info(
          "Flushed {} rows ({} table, partition {}, offsets {}-{}) to {}",
          rows.size(),
          tableLabel,
          partition,
          fromOffset,
          throughOffset,
          physicalPath);
      // PartitionSpec.unpartitioned() must match the table's own spec -- DataFiles.Builder uses
      // it only to decide whether a partition value is expected, which for an unpartitioned table
      // it never is.
      return DataFiles.builder(PartitionSpec.unpartitioned())
          .withPath(location)
          .withFormat(FileFormat.PARQUET)
          .withRecordCount(rows.size())
          .withFileSizeInBytes(fileSizeBytes)
          .build();
    } catch (final SQLException | IOException e) {
      throw new IllegalStateException(
          "Failed to write Parquet file for " + tableLabel + " partition " + partition, e);
    }
  }

  @Override
  public long committedOffset(final int sourcePartition) {
    return Math.min(
        currentOffsetOf(instancesTable, sourcePartition),
        currentOffsetOf(activitiesTable, sourcePartition));
  }

  private static long currentOffsetOf(final Table table, final int partition) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    return snapshot == null ? -1L : offsetOf(snapshot.summary(), partition);
  }

  private static long offsetOf(final Map<String, String> summary, final int partition) {
    final String value = summary.get(OFFSET_PROPERTY_PREFIX + partition);
    return value == null ? -1L : Long.parseLong(value);
  }

  @Override
  public void close() {
    try {
      duckdb.close();
    } catch (final SQLException e) {
      LOG.warn("Failed to close embedded DuckDB connection", e);
    }
    catalog.close();
  }

  /**
   * Handle for {@link LakeCompactor} (constructed with, and only ever called from, the same
   * poll-loop thread that drives {@link #flush(int, long)} — see {@link LakeCompactor}'s class
   * javadoc for why that makes sharing this connection safe without any synchronization) and for
   * {@code LakePocApp}'s L0 sink wiring, which builds its {@code
   * io.camunda.analytics.lake.sink.pipeline.SinkPipeline}s and {@code
   * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink}s directly against this {@link Table}
   * (a different package, hence public rather than package-private).
   */
  public Table instancesTable() {
    return instancesTable;
  }

  /** See {@link #instancesTable()}. */
  public Table activitiesTable() {
    return activitiesTable;
  }

  /** See {@link #instancesTable()}. */
  Connection duckdbConnection() {
    return duckdb;
  }

  /**
   * Package-private handle for {@link GoldTables} (constructed by {@link LakeCompactor}), which
   * needs the catalog only to create-or-load its own gold tables in the same {@code lake} namespace
   * -- it never touches {@link #instancesTable}/{@link #activitiesTable} through it.
   */
  JdbcCatalog catalog() {
    return catalog;
  }

  private static void appendInstanceRow(final DuckDBAppender appender, final InstanceRow row)
      throws SQLException {
    appender.append(row.key());
    appender.append(row.processDefinitionKey());
    appender.append(row.processId());
    appender.append(row.version());
    appender.append(row.tenantId());
    appender.append(row.state());
    appender.append(row.startMs());
    appender.append(row.endMs());
    appender.append(row.durationMs());
    appender.append(row.varsJson());
  }

  private static void appendActivityRow(final DuckDBAppender appender, final ActivityRow row)
      throws SQLException {
    appender.append(row.instanceKey());
    appender.append(row.processId());
    appender.append(row.version());
    appender.append(row.tenantId());
    appender.append(row.elementId());
    appender.append(row.elementType());
    appender.append(row.elementKey());
    appender.append(row.state());
    appender.append(row.startMs());
    appender.append(row.endMs());
    appender.append(row.durationMs());
    appender.append(row.instanceStartMs());
  }

  /** Appends one row's columns, in schema order, to an in-flight DuckDB appender row. */
  @FunctionalInterface
  private interface RowAppender<T> {
    void appendRow(DuckDBAppender appender, T row) throws SQLException;
  }
}

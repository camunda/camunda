/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.ExpireSnapshots;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.OverwriteFiles;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives three read-optimized "gold" tables — {@code transitions}, {@code instance_kpis}, {@code
 * element_bits} — from the current contents of the {@code activities} (joined with {@code
 * instances}) raw lake tables, and replaces each gold table's contents wholesale on every pass.
 *
 * <h2>Full recompute, not incremental (deliberate PoC decision)</h2>
 *
 * <p>Every {@link #recompute()} call re-derives all three tables from scratch off the raw tables'
 * <em>current</em> snapshots and replaces their entire contents — it never diffs against the
 * previous pass. This is the simplest correct thing at PoC scale (bounded by how much of {@code
 * activities} exists at any point) but does not scale: a production version would recompute
 * <b>incrementally behind a settle frontier</b> — only re-deriving the slice of instances whose
 * activities landed since the last pass and whose family {@code day} is old enough that no more
 * activity for it is expected (the "settle frontier"), rather than re-scanning every historical row
 * on every pass.
 *
 * <h2>Derived cache, never truth — offset protocol</h2>
 *
 * <p>These three tables are recomputable caches: nothing here is a source of durable state. Their
 * commits deliberately carry <b>no</b> {@code lake.offset.*} summary properties (contrast with
 * {@link IcebergLakeWriter}/{@link LakeCompactor}, where re-stamping those properties on every
 * commit is load-bearing), and {@link IcebergLakeWriter#committedOffset(int)} does not — and must
 * not — know these tables exist. Crash recovery ignores them entirely: if the process dies
 * mid-recompute, the next pass simply overwrites whatever partial state was left, and nothing reads
 * a gold table to decide where to resume the raw pipeline.
 *
 * <h2>{@code process_definition_key} comes from an inner join, not {@code activities} alone</h2>
 *
 * <p>{@link io.camunda.analytics.lake.model.ActivityRow} does not carry {@code
 * process_definition_key} (only {@code process_id} + {@code version}), but every gold table's
 * schema requires it (the brief's derivation columns). This class resolves it by joining {@code
 * activities} against {@code instances} on {@code instance_key} — which means an instance whose
 * {@code instances} row hasn't landed yet (still running) is <b>excluded</b> from all three gold
 * tables until its instance row completes and the next recompute pass picks it up. This is a
 * deliberate, documented consequence of the join, not a bug: it trades "gold tables cover only
 * instances known to have finished" for a correct, non-null {@code process_definition_key} column
 * everywhere.
 *
 * <h2>Definition choices baked into the derivation</h2>
 *
 * <ul>
 *   <li>{@code transitions} does not filter by {@code element_type} — every element kind
 *       contributes directly-follows edges. This is an evolvable choice (a future version might
 *       exclude e.g. boundary events or gateways) documented here rather than hidden in SQL.
 *   <li>{@code gap_ms} on a consecutive-pair edge is the raw {@code next.start_ms - prev.end_ms}
 *       and is kept <b>as computed, including negative values</b> — a negative gap is expected for
 *       parallel branches (the "next" element by {@code start_ms} order may have started before the
 *       "prev" element in the same ordering finished). {@code instance_kpis.total_wait_ms} excludes
 *       negative gaps deliberately (see its own column note); {@code
 *       transitions.total_gap_ms}/{@code min_gap_ms}/{@code max_gap_ms} do not.
 *   <li>{@code instance_kpis.elements_seen} is a bitmask over {@code element_bits}' per-{@code
 *       process_definition_key} bit assignment (0-indexed, alphabetical by {@code element_id}). It
 *       is {@code NULL} whenever that definition has more than 64 distinct element ids in this
 *       recompute (a {@code BIGINT} cannot address more than 64 bits) — {@code element_bits} itself
 *       still carries the full dictionary for such definitions, only the derived bitmask column
 *       gives up.
 * </ul>
 *
 * <h2>Atomic wholesale replace</h2>
 *
 * <p>Each gold table's contents are replaced with a single {@link Table#newOverwrite()} commit that
 * deletes every data file the table's current snapshot references and adds one freshly-written
 * replacement (skipped entirely when there is nothing to delete and nothing to add) — a reader can
 * never observe an empty or half-written table. This class deliberately never opts into {@link
 * OverwriteFiles#validateNoConflictingData()}/{@link OverwriteFiles#validateNoConflictingDeletes()}
 * (the ancestor-history-walking validations {@link LakeCompactor#rewriteDataFiles} has to work
 * around for {@link org.apache.iceberg.RewriteFiles} with an explicit {@code
 * validateFromSnapshot}): plain delete+add without those opt-ins does not require unbroken ancestor
 * history, so this class's own snapshot expiry (see below) never breaks the *next* pass's commit
 * the way it would for a {@code RewriteFiles} operation.
 *
 * <h2>Idempotence</h2>
 *
 * <p>Running {@link #recompute()} twice in a row with no new raw data in between produces the exact
 * same logical rows both times (the derivation is a pure function of the raw tables' current
 * contents) — only the physical Parquet file backing each gold table changes (a fresh {@link
 * UUID}-named file each pass), which is invisible to anything reading the table through
 * iceberg-core.
 *
 * <h2>Metadata hygiene</h2>
 *
 * <p>Same two table properties {@link LakeCompactor} sets on the raw tables — {@link
 * TableProperties#METADATA_DELETE_AFTER_COMMIT_ENABLED} and {@link
 * TableProperties#METADATA_PREVIOUS_VERSIONS_MAX} — are applied to all three gold tables at
 * construction (retroactively too, mirroring {@link LakeCompactor}), and {@link #recompute()}
 * expires old snapshots down to the last {@value #RETAIN_LAST_SNAPSHOTS} after every successful
 * replace, so gold-table metadata does not accumulate indefinitely either.
 */
public final class GoldTables {

  static final String TRANSITIONS_TABLE = "transitions";
  static final String INSTANCE_KPIS_TABLE = "instance_kpis";
  static final String ELEMENT_BITS_TABLE = "element_bits";

  /** {@code expireSnapshots().retainLast(...)} argument -- see {@link LakeCompactor}'s own. */
  private static final int RETAIN_LAST_SNAPSHOTS = 3;

  private static final Logger LOG = LoggerFactory.getLogger(GoldTables.class);

  private static final Schema TRANSITIONS_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "process_id", Types.StringType.get()),
              Types.NestedField.required(2, "process_definition_key", Types.LongType.get()),
              Types.NestedField.required(3, "tenant_id", Types.StringType.get()),
              Types.NestedField.required(4, "day", Types.StringType.get()),
              Types.NestedField.required(5, "from_element", Types.StringType.get()),
              Types.NestedField.required(6, "to_element", Types.StringType.get()),
              Types.NestedField.required(7, "n", Types.LongType.get()),
              Types.NestedField.required(8, "total_gap_ms", Types.LongType.get()),
              Types.NestedField.required(9, "min_gap_ms", Types.LongType.get()),
              Types.NestedField.required(10, "max_gap_ms", Types.LongType.get())));

  private static final Schema INSTANCE_KPIS_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "instance_key", Types.LongType.get()),
              Types.NestedField.required(2, "process_id", Types.StringType.get()),
              Types.NestedField.required(3, "process_definition_key", Types.LongType.get()),
              Types.NestedField.required(4, "tenant_id", Types.StringType.get()),
              Types.NestedField.required(5, "day", Types.StringType.get()),
              Types.NestedField.required(6, "activity_count", Types.LongType.get()),
              Types.NestedField.required(7, "distinct_element_count", Types.LongType.get()),
              Types.NestedField.required(8, "rework_count", Types.LongType.get()),
              Types.NestedField.required(9, "total_wait_ms", Types.LongType.get()),
              // Optional, not required -- see class javadoc's "elements_seen" bullet: NULL when the
              // owning definition has more than 64 distinct element ids in this recompute.
              Types.NestedField.optional(10, "elements_seen", Types.LongType.get())));

  private static final Schema ELEMENT_BITS_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "process_definition_key", Types.LongType.get()),
              Types.NestedField.required(2, "element_id", Types.StringType.get()),
              Types.NestedField.required(3, "bit", Types.IntegerType.get())));

  private static final String GOLD_ENRICHED_DDL =
      """
      CREATE OR REPLACE TEMP VIEW gold_enriched AS
      SELECT
        a.instance_key,
        a.process_id,
        i.process_definition_key,
        a.tenant_id,
        strftime(to_timestamp(a.instance_start_ms / 1000), '%Y-%m-%d') AS day,
        a.element_id,
        a.element_key,
        a.start_ms,
        a.end_ms,
        a.instance_start_ms
      FROM gold_activities_src a
      JOIN gold_instances_src i ON a.instance_key = i.instance_key
      """;

  private static final String GOLD_ORDERED_DDL =
      """
      CREATE OR REPLACE TEMP VIEW gold_ordered AS
      SELECT
        *,
        row_number() OVER w AS rn,
        count(*) OVER (PARTITION BY instance_key) AS activity_count,
        lag(element_id) OVER w AS prev_element_id,
        lag(end_ms) OVER w AS prev_end_ms
      FROM gold_enriched
      WINDOW w AS (PARTITION BY instance_key ORDER BY start_ms, element_key)
      """;

  private static final String GOLD_ELEMENT_BITS_DDL =
      """
      CREATE OR REPLACE TEMP VIEW gold_element_bits AS
      SELECT
        process_definition_key,
        element_id,
        CAST(row_number() OVER (PARTITION BY process_definition_key ORDER BY element_id) - 1
             AS INTEGER) AS bit
      FROM (SELECT DISTINCT process_definition_key, element_id FROM gold_enriched)
      """;

  private static final String GOLD_DEF_ELEMENT_COUNTS_DDL =
      """
      CREATE OR REPLACE TEMP VIEW gold_def_element_counts AS
      SELECT process_definition_key, count(*) AS n_elements
      FROM gold_element_bits
      GROUP BY process_definition_key
      """;

  /**
   * Directly-follows edges -- see class javadoc for the {@code __START__}/{@code __END__} sentinel
   * and raw (possibly negative) {@code gap_ms} semantics.
   */
  private static final String TRANSITIONS_SELECT_SQL =
      """
      WITH edges AS (
        SELECT process_id, process_definition_key, tenant_id, day,
               prev_element_id AS from_element, element_id AS to_element,
               start_ms - prev_end_ms AS gap_ms
        FROM gold_ordered
        WHERE prev_element_id IS NOT NULL
        UNION ALL
        SELECT process_id, process_definition_key, tenant_id, day,
               '__START__' AS from_element, element_id AS to_element,
               start_ms - instance_start_ms AS gap_ms
        FROM gold_ordered
        WHERE rn = 1
        UNION ALL
        SELECT process_id, process_definition_key, tenant_id, day,
               element_id AS from_element, '__END__' AS to_element,
               CAST(0 AS BIGINT) AS gap_ms
        FROM gold_ordered
        WHERE rn = activity_count
      )
      SELECT
        process_id, process_definition_key, tenant_id, day,
        from_element, to_element,
        CAST(count(*) AS BIGINT) AS n,
        CAST(sum(gap_ms) AS BIGINT) AS total_gap_ms,
        CAST(min(gap_ms) AS BIGINT) AS min_gap_ms,
        CAST(max(gap_ms) AS BIGINT) AS max_gap_ms
      FROM edges
      GROUP BY process_id, process_definition_key, tenant_id, day, from_element, to_element
      """;

  /**
   * One row per instance seen in the join (see class javadoc's inner-join caveat). {@code
   * total_wait_ms} sums only <em>positive</em> consecutive gaps -- a negative gap (parallel
   * branches, see class javadoc) contributes zero wait, never a negative reduction.
   */
  private static final String INSTANCE_KPIS_SELECT_SQL =
      """
      WITH per_instance AS (
        SELECT
          instance_key, process_id, process_definition_key, tenant_id, day,
          CAST(max(activity_count) AS BIGINT) AS activity_count,
          CAST(count(DISTINCT element_id) AS BIGINT) AS distinct_element_count,
          CAST(sum(CASE
                     WHEN prev_element_id IS NOT NULL AND (start_ms - prev_end_ms) > 0
                       THEN start_ms - prev_end_ms
                     ELSE 0
                   END) AS BIGINT) AS total_wait_ms
        FROM gold_ordered
        GROUP BY instance_key, process_id, process_definition_key, tenant_id, day
      ),
      rework AS (
        SELECT instance_key, CAST(count(*) AS BIGINT) AS rework_count
        FROM (
          SELECT instance_key, element_id
          FROM gold_ordered
          GROUP BY instance_key, element_id
          HAVING count(*) >= 2
        )
        GROUP BY instance_key
      ),
      instance_elements AS (
        SELECT DISTINCT instance_key, process_definition_key, element_id
        FROM gold_ordered
      ),
      instance_bits AS (
        SELECT
          ie.instance_key,
          ie.process_definition_key,
          CAST(sum(CASE WHEN eb.bit < 64 THEN (CAST(1 AS BIGINT) << eb.bit) ELSE 0 END)
               AS BIGINT) AS elements_seen_raw
        FROM instance_elements ie
        JOIN gold_element_bits eb
          ON ie.process_definition_key = eb.process_definition_key AND ie.element_id = eb.element_id
        GROUP BY ie.instance_key, ie.process_definition_key
      )
      SELECT
        pi.instance_key,
        pi.process_id,
        pi.process_definition_key,
        pi.tenant_id,
        pi.day,
        pi.activity_count,
        pi.distinct_element_count,
        CAST(coalesce(rw.rework_count, 0) AS BIGINT) AS rework_count,
        pi.total_wait_ms,
        CASE WHEN defc.n_elements > 64 THEN NULL ELSE ib.elements_seen_raw END AS elements_seen
      FROM per_instance pi
      LEFT JOIN rework rw ON pi.instance_key = rw.instance_key
      LEFT JOIN instance_bits ib ON pi.instance_key = ib.instance_key
      LEFT JOIN gold_def_element_counts defc ON pi.process_definition_key = defc.process_definition_key
      """;

  /** The full bit dictionary -- kept complete even for definitions with more than 64 elements. */
  private static final String ELEMENT_BITS_SELECT_SQL =
      """
      SELECT process_definition_key, element_id, bit
      FROM gold_element_bits
      """;

  private final Connection duckdb;
  private final Table instancesTable;
  private final Table activitiesTable;
  private final Table transitionsTable;
  private final Table instanceKpisTable;
  private final Table elementBitsTable;

  /**
   * @param catalog the same (or an independently-opened, see this module's {@code LakeUiServer}
   *     javadoc for why that is safe against an H2-backed {@link JdbcCatalog}) catalog the raw
   *     tables were loaded from; used only to create-or-load the three gold tables
   * @param duckdb an embedded DuckDB connection this instance may issue arbitrary statements on
   * @param instancesTable read-only handle -- this class never commits to it
   * @param activitiesTable read-only handle -- this class never commits to it
   */
  public GoldTables(
      final JdbcCatalog catalog,
      final Connection duckdb,
      final Table instancesTable,
      final Table activitiesTable) {
    this.duckdb = duckdb;
    this.instancesTable = instancesTable;
    this.activitiesTable = activitiesTable;

    final Namespace namespace = Namespace.of("lake");
    transitionsTable = tableOrCreate(catalog, namespace, TRANSITIONS_TABLE, TRANSITIONS_SCHEMA);
    instanceKpisTable =
        tableOrCreate(catalog, namespace, INSTANCE_KPIS_TABLE, INSTANCE_KPIS_SCHEMA);
    elementBitsTable = tableOrCreate(catalog, namespace, ELEMENT_BITS_TABLE, ELEMENT_BITS_SCHEMA);
    // Retroactive, like LakeCompactor#configureMetadataCleanup -- applies on every construction,
    // not only to freshly created tables, so metadata cleanup stays effective across restarts too.
    configureMetadataCleanup(transitionsTable, TRANSITIONS_TABLE);
    configureMetadataCleanup(instanceKpisTable, INSTANCE_KPIS_TABLE);
    configureMetadataCleanup(elementBitsTable, ELEMENT_BITS_TABLE);
  }

  private static Table tableOrCreate(
      final JdbcCatalog catalog,
      final Namespace namespace,
      final String name,
      final Schema schema) {
    final TableIdentifier identifier = TableIdentifier.of(namespace, name);
    if (catalog.tableExists(identifier)) {
      return catalog.loadTable(identifier);
    }
    // Same name-mapping rationale as IcebergLakeWriter#tableOrCreate: the Parquet files DuckDB
    // writes for these tables carry no Iceberg field ids either.
    return catalog.createTable(
        identifier,
        schema,
        PartitionSpec.unpartitioned(),
        Map.of(
            TableProperties.DEFAULT_NAME_MAPPING,
            NameMappingParser.toJson(MappingUtil.create(schema))));
  }

  private static void configureMetadataCleanup(final Table table, final String label) {
    try {
      table
          .updateProperties()
          .set(TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED, "true")
          .set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "5")
          .commit();
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to set metadata-cleanup properties on {} gold table; leaving defaults", label, e);
    }
  }

  /**
   * Runs one full recompute-and-replace pass over all three gold tables. Throws on any failure
   * (callers driving this from a context that must never propagate an exception -- e.g. {@link
   * LakeCompactor}'s poll-loop-thread contract -- are expected to guard this call themselves, the
   * same way {@link LakeCompactor#compactIfNeeded()} guards its own per-table steps).
   *
   * <p>A no-op (returns {@link GoldRecomputeResult#skipped()}) when either raw table has no live
   * data files yet -- there is nothing to derive, and joining against an empty {@code
   * read_parquet([])} file list is a DuckDB bind error, not an empty result set.
   */
  public GoldRecomputeResult recompute() {
    instancesTable.refresh();
    activitiesTable.refresh();
    final List<String> instanceFiles = currentDataFileLocations(instancesTable);
    final List<String> activityFiles = currentDataFileLocations(activitiesTable);
    if (instanceFiles.isEmpty() || activityFiles.isEmpty()) {
      LOG.info(
          "Skipping gold-table recompute: no {} data yet",
          instanceFiles.isEmpty() ? "instances" : "activities");
      return GoldRecomputeResult.skipped();
    }
    try {
      createOrReplaceSourceViews(instanceFiles, activityFiles);
      final long transitionsRows =
          writeAndReplace(transitionsTable, TRANSITIONS_TABLE, TRANSITIONS_SELECT_SQL);
      final long instanceKpisRows =
          writeAndReplace(instanceKpisTable, INSTANCE_KPIS_TABLE, INSTANCE_KPIS_SELECT_SQL);
      final long elementBitsRows =
          writeAndReplace(elementBitsTable, ELEMENT_BITS_TABLE, ELEMENT_BITS_SELECT_SQL);
      LOG.info(
          "Gold-table recompute done: transitions={} instance_kpis={} element_bits={}",
          transitionsRows,
          instanceKpisRows,
          elementBitsRows);
      return new GoldRecomputeResult(true, transitionsRows, instanceKpisRows, elementBitsRows);
    } catch (final SQLException | IOException e) {
      throw new IllegalStateException("Gold-table recompute failed", e);
    }
  }

  private void createOrReplaceSourceViews(
      final List<String> instanceFiles, final List<String> activityFiles) throws SQLException {
    final String activityFileList = fileListLiteral(activityFiles);
    final String instanceFileList = fileListLiteral(instanceFiles);
    try (Statement ddl = duckdb.createStatement()) {
      ddl.execute(
          "CREATE OR REPLACE TEMP VIEW gold_activities_src AS "
              + "SELECT * FROM read_parquet(["
              + activityFileList
              + "])");
      ddl.execute(
          "CREATE OR REPLACE TEMP VIEW gold_instances_src AS "
              + "SELECT key AS instance_key, process_definition_key FROM read_parquet(["
              + instanceFileList
              + "])");
      ddl.execute(GOLD_ENRICHED_DDL);
      ddl.execute(GOLD_ORDERED_DDL);
      ddl.execute(GOLD_ELEMENT_BITS_DDL);
      ddl.execute(GOLD_DEF_ELEMENT_COUNTS_DDL);
    }
  }

  /**
   * Writes {@code selectSql}'s result to a fresh Parquet file under {@code table}'s data directory,
   * then replaces {@code table}'s entire contents with it in one {@link Table#newOverwrite()}
   * commit -- see class javadoc's "Atomic wholesale replace" section. Returns the row count written
   * (0 when {@code selectSql} produced no rows; the table is then left/made empty).
   */
  private long writeAndReplace(final Table table, final String label, final String selectSql)
      throws SQLException, IOException {
    table.refresh();
    final Map<String, DataFile> existingFiles = currentLiveFiles(table);

    final String fileName = "gold-" + UUID.randomUUID() + ".parquet";
    final String location = table.location() + "/data/" + fileName;
    final Path physicalPath = LocalFileIO.toFilesystemPath(location);
    if (physicalPath.getParent() != null) {
      Files.createDirectories(physicalPath.getParent());
    }
    try (Statement copy = duckdb.createStatement()) {
      copy.execute("COPY (" + selectSql + ") TO '" + physicalPath + "' (FORMAT PARQUET)");
    }
    final long rows = countRows(physicalPath);

    if (existingFiles.isEmpty() && rows == 0) {
      // Nothing to delete, nothing to add -- an OverwriteFiles commit with no changes at all is
      // rejected by iceberg-core, and there is genuinely nothing for a reader to see differently.
      LOG.info("Gold table {} has no rows this pass; leaving it as-is (empty)", label);
      return 0;
    }

    final OverwriteFiles overwrite = table.newOverwrite();
    existingFiles.values().forEach(overwrite::deleteFile);
    if (rows > 0) {
      final long fileSizeBytes = Files.size(physicalPath);
      final DataFile newFile =
          DataFiles.builder(PartitionSpec.unpartitioned())
              .withPath(location)
              .withFormat(FileFormat.PARQUET)
              .withRecordCount(rows)
              .withFileSizeInBytes(fileSizeBytes)
              .build();
      overwrite.addFile(newFile);
    }
    // Deliberately NO lake.offset.* stamping here -- gold tables are derived caches, never truth
    // (see class javadoc's offset-protocol section / the module's HARD CONSTRAINT on this).
    overwrite.commit();

    expireGoldSnapshots(table, label);
    LOG.info(
        "Replaced {} gold table contents: {} data file(s) -> {} ({} rows)",
        label,
        existingFiles.size(),
        rows > 0 ? 1 : 0,
        rows);
    return rows;
  }

  private static void expireGoldSnapshots(final Table table, final String label) {
    try {
      final ExpireSnapshots expire =
          table
              .expireSnapshots()
              .expireOlderThan(System.currentTimeMillis())
              .retainLast(RETAIN_LAST_SNAPSHOTS);
      expire.commit();
    } catch (final RuntimeException e) {
      LOG.warn(
          "Snapshot expiry failed for {} gold table; leaving existing snapshots in place",
          label,
          e);
    }
  }

  private long countRows(final Path parquetFile) throws SQLException {
    try (Statement count = duckdb.createStatement();
        ResultSet rs =
            count.executeQuery("SELECT count(*) FROM read_parquet('" + parquetFile + "')")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String fileListLiteral(final List<String> locations) {
    return locations.stream()
        .map(location -> quote(LocalFileIO.toFilesystemPath(location).toString()))
        .collect(Collectors.joining(", "));
  }

  private static String quote(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  /** Data-file locations currently referenced by {@code table}'s current snapshot, deduped. */
  private static List<String> currentDataFileLocations(final Table table) {
    final LinkedHashSet<String> locations = new LinkedHashSet<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        locations.add(task.file().location());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to plan data files for " + table.name(), e);
    }
    return new ArrayList<>(locations);
  }

  /** Like {@link #currentDataFileLocations(Table)}, but keeping the {@link DataFile} handles. */
  private static Map<String, DataFile> currentLiveFiles(final Table table) {
    final Map<String, DataFile> files = new LinkedHashMap<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        files.put(task.file().location(), task.file());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to plan data files for " + table.name(), e);
    }
    return files;
  }

  /** Package-private so a test can assert on gold-table contents directly. */
  Table transitionsTable() {
    return transitionsTable;
  }

  /** See {@link #transitionsTable()}. */
  Table instanceKpisTable() {
    return instanceKpisTable;
  }

  /** See {@link #transitionsTable()}. */
  Table elementBitsTable() {
    return elementBitsTable;
  }

  /**
   * Outcome of one {@link #recompute()} call.
   *
   * @param recomputed {@code false} when skipped (no raw data yet) -- {@code true} means all three
   *     tables were (re)derived and committed, even if some ended up with zero rows
   * @param transitionsRows rows written to {@code transitions}, or {@code -1} if skipped
   * @param instanceKpisRows rows written to {@code instance_kpis}, or {@code -1} if skipped
   * @param elementBitsRows rows written to {@code element_bits}, or {@code -1} if skipped
   */
  public record GoldRecomputeResult(
      boolean recomputed, long transitionsRows, long instanceKpisRows, long elementBitsRows) {

    static GoldRecomputeResult skipped() {
      return new GoldRecomputeResult(false, -1, -1, -1);
    }

    static GoldRecomputeResult unavailable() {
      return new GoldRecomputeResult(false, -1, -1, -1);
    }

    @Override
    public String toString() {
      return recomputed
          ? "goldTables[transitions="
              + transitionsRows
              + ", instance_kpis="
              + instanceKpisRows
              + ", element_bits="
              + elementBitsRows
              + ']'
          : "goldTables[skipped]";
    }
  }
}

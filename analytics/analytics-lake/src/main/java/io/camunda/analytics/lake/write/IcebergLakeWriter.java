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
import io.camunda.analytics.lake.sink.TableSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToLongFunction;
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
import org.apache.iceberg.types.Type;
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
 * embedded DuckDB connection {@link LakeCompactor} still drives for compaction rewrites (not
 * raw-table ingest). The buffered append/flush methods below stay because the compaction/UI test
 * suites still construct this class directly and use them to seed fixture data — removing them
 * would force rewriting several otherwise-unrelated test files for no behavioral gain (see the
 * module's L0-sink integration test for how the new pipelines are exercised instead).
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

  /**
   * Prefix of the snapshot summary property {@code
   * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} uses to stamp a partition's local
   * frontier. Lives here, next to {@link #OFFSET_PROPERTY_PREFIX}, rather than on {@code
   * DirectCommitSink} itself: every commit that re-stamps one carry-forward property must re-stamp
   * the other (see {@link #flushTable}'s and {@link LakeCompactor}'s own carry-forward code) — a
   * commit that only knew about one prefix would silently wipe the other the moment it landed a
   * snapshot, exactly the bug class {@link #OFFSET_PROPERTY_PREFIX}'s own javadoc describes.
   * Keeping both prefixes on this one class means every restamp site only has to import one
   * constant to get both right.
   */
  public static final String FRONTIER_PROPERTY_PREFIX = "lake.frontier.p";

  /**
   * Prefix of the snapshot summary property {@code
   * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} uses to stamp the origin-position
   * dedup watermark {@code io.camunda.analytics.lake.translate.LakeTranslator} keeps per Zeebe
   * partition — see that class's own "Origin-position dedup" javadoc section for what the watermark
   * protects against. Unlike {@link #OFFSET_PROPERTY_PREFIX}/{@link #FRONTIER_PROPERTY_PREFIX}, the
   * suffix here is a Zeebe {@code partitionId}, not an Event Bridge source partition id: the same
   * Zeebe partition's records can in principle reach these tables through more than one Event
   * Bridge source partition over the process's lifetime, so the watermark this prefix stamps is
   * keyed by where the records actually originated, not by which source partition happened to carry
   * them. Lives here, next to the other two prefixes, for the same reason {@link
   * #FRONTIER_PROPERTY_PREFIX} does: every commit that re-stamps one carry-forward property must
   * re-stamp all three, or a commit that only knew about two of them would silently wipe the third
   * the moment it landed a snapshot — see {@link #OFFSET_PROPERTY_PREFIX}'s own javadoc for the bug
   * class this guards against.
   */
  public static final String ZBPOS_PROPERTY_PREFIX = "lake.zbpos.z";

  /**
   * Table property carrying the declaration fingerprint a generated partials table was created
   * under — the guard that stops rows produced under a changed declaration from silently merging
   * with incompatible stored partials (see {@link #partialsTableOrCreate}).
   */
  public static final String FINGERPRINT_PROPERTY = "lake.decl.fingerprint";

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
   * Projects the staging table's raw millisecond columns into the catalog schema's {@code
   * timestamptz} columns (schema v2 -- see {@link #INSTANCE_SCHEMA}'s javadoc): DuckDB's {@code
   * to_timestamp} takes fractional seconds and returns {@code TIMESTAMP WITH TIME ZONE} natively,
   * so dividing the staged millisecond column by 1000.0 is the whole conversion. Every other column
   * passes through unchanged.
   */
  private static final String INSTANCES_SELECT_SQL =
      "SELECT key, process_definition_key, process_id, version, tenant_id, state, "
          + "to_timestamp(start_ms / 1000.0) AS started_at, "
          + "to_timestamp(end_ms / 1000.0) AS ended_at, "
          + "duration_ms, vars_json FROM "
          + INSTANCES_STAGING_TABLE;

  /** See {@link #INSTANCES_SELECT_SQL}; same idea, plus {@code instance_started_at}. */
  private static final String ACTIVITIES_SELECT_SQL =
      "SELECT instance_key, process_id, version, tenant_id, element_id, element_type, "
          + "element_key, state, "
          + "to_timestamp(start_ms / 1000.0) AS started_at, "
          + "to_timestamp(end_ms / 1000.0) AS ended_at, "
          + "duration_ms, "
          + "to_timestamp(instance_start_ms / 1000.0) AS instance_started_at FROM "
          + ACTIVITIES_STAGING_TABLE;

  /** Family-day bucketing granularity for the legacy buffered write path (see {@link #flush}). */
  private static final long MILLIS_PER_DAY = 86_400_000L;

  /**
   * Mirrors {@link InstanceRow} field for field. {@code started_at}/{@code ended_at} are Iceberg
   * {@code timestamptz} columns (schema v2 — see the README's "Schema v2" note): Parquet stores a
   * {@code timestamptz} value as epoch <b>microseconds</b>, which is why every producer of these
   * columns (the L0 sink's batch vectors, and this class's own legacy DuckDB-appender path below)
   * has to convert from Zeebe's native epoch milliseconds at the point of writing, never before.
   * {@code duration_ms} and every other {@code *_ms} column stay plain {@link Types.LongType}
   * milliseconds — only the two instant columns changed shape.
   *
   * <p>{@code variant_hash} (field id 11, schema v3) is an <b>optional</b> {@link
   * Types.StringType}: 16 lowercase hex characters of the variant-k1 running hash (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Variant capture" javadoc section), or
   * absent when the instance completed with no live accumulator (e.g. state loss). Optional, not
   * required: a data file predating this field (written by an older schema version, or by this
   * class's own legacy DuckDB-appender path below, which never populates it) must still resolve the
   * column to {@code null} via name mapping rather than fail to read.
   *
   * <p>{@code vars_json} (field id 10) is {@link Types.BinaryType}, not {@link Types.StringType}:
   * the L0 sink's {@code io.camunda.analytics.lake.sink.ColumnType#BINARY} column for this field
   * stores raw UTF-8 bytes (never boxes a {@code String} on the hot path — see {@code
   * TableSchema}'s javadoc), so the catalog schema's own field type has to agree, or
   * iceberg-parquet's generic writer hands a {@code ByteBuffer} to a column configured for {@code
   * CharSequence} and throws a {@code ClassCastException}. This does not disturb this class's own
   * legacy DuckDB-appender path (still exercised by the compaction/UI tests that construct {@link
   * IcebergLakeWriter} directly): DuckDB writes Parquet files by inferring types from the staged
   * column's own {@code SELECT} projection, never consulting this catalog {@link Schema}, and every
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
              Types.NestedField.required(7, "started_at", Types.TimestampType.withZone()),
              Types.NestedField.required(8, "ended_at", Types.TimestampType.withZone()),
              Types.NestedField.required(9, "duration_ms", Types.LongType.get()),
              Types.NestedField.required(10, "vars_json", Types.BinaryType.get()),
              // Schema v3 -- see this field's own javadoc paragraph above.
              Types.NestedField.optional(11, "variant_hash", Types.StringType.get())));

  /**
   * Mirrors {@link ActivityRow} field for field; see {@link #INSTANCE_SCHEMA} for the {@code
   * timestamptz} note.
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
              Types.NestedField.required(9, "started_at", Types.TimestampType.withZone()),
              Types.NestedField.required(10, "ended_at", Types.TimestampType.withZone()),
              Types.NestedField.required(11, "duration_ms", Types.LongType.get()),
              // The owning instance's start -- the family date activities are partitioned and
              // retired by (see ActivityRow#instanceStartMs).
              Types.NestedField.required(12, "instance_started_at", Types.TimestampType.withZone()),
              // Schema v4 -- see io.camunda.analytics.lake.translate.RawTableSchemas#activities's
              // own javadoc paragraph for what this carries and why it's nullable. Optional (like
              // variant_hash before it): a data file predating this field must resolve to null via
              // name mapping, not fail to read. Never populated by this class's own legacy
              // DuckDB-appender path below (that path stays at schema v3 field-for-field).
              Types.NestedField.optional(13, "flow_scope_key", Types.LongType.get())));

  /**
   * The variant-k1 dictionary table's schema: one row per distinct (process id, version, variant
   * hash) triple ever seen (see {@code io.camunda.analytics.lake.translate.LakeTranslator}'s
   * "Variant capture" javadoc section). Created via the same plain {@link #tableOrCreate} path as
   * {@link #INSTANCE_SCHEMA}/{@link #ACTIVITY_SCHEMA} — unlike a generated partials table (see
   * {@link #partialsTableOrCreate}), this table carries no declaration fingerprint: its rows are
   * deterministic given the (process id, version, variant hash) key regardless of who wrote them or
   * when, so there is nothing for a fingerprint to guard against.
   */
  private static final Schema VARIANT_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "process_id", Types.StringType.get()),
              Types.NestedField.required(2, "version", Types.IntegerType.get()),
              Types.NestedField.required(3, "variant_hash", Types.StringType.get()),
              Types.NestedField.required(4, "elements", Types.BinaryType.get()),
              Types.NestedField.required(5, "flows", Types.BinaryType.get()),
              Types.NestedField.required(6, "first_seen", Types.TimestampType.withZone())));

  /**
   * The object-fabric sightings dictionary table's schema — one row per distinct (object type,
   * object id, instance, scope) ever sighted (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Object fabric capture" javadoc section
   * and {@code RawTableSchemas#objects}'s own javadoc). Created via the same plain {@link
   * #tableOrCreate} path as {@link #VARIANT_SCHEMA} — no declaration fingerprint, for the same
   * reason: a row's content is fully determined by its key.
   */
  private static final Schema OBJECT_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "object_type", Types.StringType.get()),
              Types.NestedField.required(2, "object_id", Types.StringType.get()),
              Types.NestedField.required(3, "instance_key", Types.LongType.get()),
              Types.NestedField.required(4, "process_id", Types.StringType.get()),
              Types.NestedField.required(5, "version", Types.IntegerType.get()),
              Types.NestedField.optional(6, "scope_key", Types.LongType.get()),
              Types.NestedField.required(7, "qualifier", Types.StringType.get()),
              Types.NestedField.required(8, "first_seen", Types.TimestampType.withZone())));

  /**
   * The call-activity instance-link dictionary table's schema — one row per child instance ever
   * created via a call activity (see {@code LakeTranslator}'s "Object fabric capture" javadoc
   * section and {@code RawTableSchemas#instanceLinks}'s own javadoc).
   */
  private static final Schema INSTANCE_LINKS_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "parent_instance_key", Types.LongType.get()),
              Types.NestedField.required(2, "child_instance_key", Types.LongType.get()),
              Types.NestedField.required(3, "link_type", Types.StringType.get()),
              Types.NestedField.optional(4, "via_element_instance_key", Types.LongType.get()),
              Types.NestedField.required(5, "linked_at", Types.TimestampType.withZone())));

  /**
   * The object-relations dictionary table's schema — one row per distinct (parent type, parent id,
   * child type, child id) edge derived at instance completion (see {@code LakeTranslator}'s "Object
   * fabric capture" javadoc section and {@code RawTableSchemas#objectRelations}'s own javadoc).
   */
  private static final Schema OBJECT_RELATIONS_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "parent_type", Types.StringType.get()),
              Types.NestedField.required(2, "parent_id", Types.StringType.get()),
              Types.NestedField.required(3, "child_type", Types.StringType.get()),
              Types.NestedField.required(4, "child_id", Types.StringType.get()),
              Types.NestedField.required(5, "first_seen", Types.TimestampType.withZone())));

  /**
   * The object-lifecycle fact table's schema — one row per object closing (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator}'s "Object lifecycle capture" javadoc
   * section and {@code RawTableSchemas#objectLifecycle}'s own javadoc). Created via the same plain
   * {@link #tableOrCreate} path as {@link #OBJECT_SCHEMA} — no declaration fingerprint: unlike a
   * generated partials table, this table's rows are not derived from a windowed fold that could
   * drift under a changed declaration, they are one-time facts written directly by the translator.
   */
  private static final Schema OBJECT_LIFECYCLE_SCHEMA =
      new Schema(
          List.of(
              Types.NestedField.required(1, "object_type", Types.StringType.get()),
              Types.NestedField.required(2, "object_id", Types.StringType.get()),
              Types.NestedField.required(3, "birth_qualifier", Types.StringType.get()),
              Types.NestedField.required(4, "birth_ts", Types.TimestampType.withZone()),
              Types.NestedField.required(5, "closed_at", Types.TimestampType.withZone()),
              Types.NestedField.required(6, "duration_ms", Types.LongType.get()),
              Types.NestedField.required(7, "outcome", Types.StringType.get()),
              Types.NestedField.required(8, "n_sightings", Types.IntegerType.get())));

  private static final Logger LOG = LoggerFactory.getLogger(IcebergLakeWriter.class);

  private final JdbcCatalog catalog;
  private final String jdbcUrl;
  private final Table instancesTable;
  private final Table activitiesTable;
  private final Table variantsTable;
  private final Table objectsTable;
  private final Table instanceLinksTable;
  private final Table objectRelationsTable;
  private final Table objectLifecycleTable;
  private final Connection duckdb;

  // One commit mutex per raw table (never a single shared lock across both) -- see #commitLock's
  // own javadoc for the invariant these guard and why instances/activities must never block each
  // other.
  private final ReentrantLock instancesCommitLock = new ReentrantLock();
  private final ReentrantLock activitiesCommitLock = new ReentrantLock();

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
    jdbcUrl = "jdbc:h2:file:" + config.warehouseDir().toAbsolutePath().resolve("catalog");
    final String warehouseLocation = warehouseFileUri(config.warehouseDir());
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI, jdbcUrl,
            CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));

    final Namespace namespace = Namespace.of("lake");
    if (!catalog.namespaceExists(namespace)) {
      catalog.createNamespace(namespace);
    }
    instancesTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "instances"),
            INSTANCE_SCHEMA,
            PartitionSpec.builderFor(INSTANCE_SCHEMA).day("started_at").build());
    activitiesTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "activities"),
            ACTIVITY_SCHEMA,
            PartitionSpec.builderFor(ACTIVITY_SCHEMA).day("instance_started_at").build());
    variantsTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "variants"),
            VARIANT_SCHEMA,
            PartitionSpec.builderFor(VARIANT_SCHEMA).day("first_seen").build());
    objectsTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "objects"),
            OBJECT_SCHEMA,
            PartitionSpec.builderFor(OBJECT_SCHEMA).day("first_seen").build());
    instanceLinksTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "instance_links"),
            INSTANCE_LINKS_SCHEMA,
            PartitionSpec.builderFor(INSTANCE_LINKS_SCHEMA).day("linked_at").build());
    objectRelationsTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "object_relations"),
            OBJECT_RELATIONS_SCHEMA,
            PartitionSpec.builderFor(OBJECT_RELATIONS_SCHEMA).day("first_seen").build());
    objectLifecycleTable =
        tableOrCreate(
            TableIdentifier.of(namespace, "object_lifecycle"),
            OBJECT_LIFECYCLE_SCHEMA,
            PartitionSpec.builderFor(OBJECT_LIFECYCLE_SCHEMA).day("birth_ts").build());

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

  private Table tableOrCreate(
      final TableIdentifier identifier, final Schema schema, final PartitionSpec spec) {
    if (catalog.tableExists(identifier)) {
      return catalog.loadTable(identifier);
    }
    // Partitioned by days(...) on the table's family-day column (schema v2 -- see the README's
    // "Schema v2" note): pruning a time-range query to the files of one or a few days is the whole
    // point, at the cost of small-file proliferation from this writer's one-file-per-flush pattern,
    // which LakeCompactor's periodic pass exists to fold back down.
    //
    // The name mapping is load-bearing for interoperability: the Parquet files written by the
    // embedded engine carry no Iceberg field ids, so without a default name mapping any strict
    // external reader (Spark, PyIceberg, warehouses mounting the table) would resolve every
    // column to null. The mapping tells readers to resolve columns by name instead.
    return catalog.createTable(
        identifier,
        schema,
        spec,
        Map.of(
            TableProperties.DEFAULT_NAME_MAPPING,
            NameMappingParser.toJson(MappingUtil.create(schema))));
  }

  /**
   * Loads or creates a <em>generated partials</em> table (a {@code _metrics}/{@code _hist} table
   * from {@code io.camunda.analytics.lake.metrics.CompiledEntityMetrics}): {@code days(...)}
   * partitioned on the schema's familyDaySource column (the window slot), name-mapped like the raw
   * tables, and stamped with the declaration fingerprint under {@value #FINGERPRINT_PROPERTY}.
   *
   * <p>On load, the stored fingerprint must equal {@code fingerprint} — a mismatch means rows
   * produced under a different declaration live in this table, and folding on top of them would
   * silently merge incompatible partials. This refuses loudly instead; the repair paths (rebuild
   * under the new declaration, or freeze the old table alongside a new one) are deliberately an
   * operator/upgrade concern, not something this writer improvises.
   */
  public Table partialsTableOrCreate(final TableSchema sinkSchema, final String fingerprint) {
    final TableIdentifier identifier = TableIdentifier.of(Namespace.of("lake"), sinkSchema.table());
    if (catalog.tableExists(identifier)) {
      final Table existing = catalog.loadTable(identifier);
      final String stored = existing.properties().get(FINGERPRINT_PROPERTY);
      if (!fingerprint.equals(stored)) {
        throw new IllegalStateException(
            "Partials table "
                + identifier
                + " was written under declaration fingerprint "
                + stored
                + " but the current declaration hashes to "
                + fingerprint
                + " — refusing to fold incompatible partials into it. Rebuild the table under the"
                + " new declaration (or keep the old one frozen) before starting.");
      }
      return existing;
    }
    final Schema schema = icebergSchemaOf(sinkSchema);
    final String familyDayColumn = sinkSchema.columns().get(sinkSchema.familyDayColumn()).name();
    return catalog.createTable(
        identifier,
        schema,
        PartitionSpec.builderFor(schema).day(familyDayColumn).build(),
        Map.of(
            TableProperties.DEFAULT_NAME_MAPPING,
            NameMappingParser.toJson(MappingUtil.create(schema)),
            FINGERPRINT_PROPERTY,
            fingerprint));
  }

  /**
   * The Iceberg twin of a generated sink schema. Field ids are taken as-declared (sequential from
   * 1); {@code createTable}'s fresh-id assignment walks the schema in order, so the created table's
   * ids coincide with the declared ones — the same property the raw tables rely on.
   */
  private static Schema icebergSchemaOf(final TableSchema sinkSchema) {
    final List<Types.NestedField> fields = new ArrayList<>(sinkSchema.columns().size());
    for (final TableSchema.Column column : sinkSchema.columns()) {
      final Type type =
          switch (column.type()) {
            case LONG ->
                column.logicalType() == TableSchema.LogicalType.TIMESTAMPTZ
                    ? Types.TimestampType.withZone()
                    : Types.LongType.get();
            case INT -> Types.IntegerType.get();
            case STRING_DICT -> Types.StringType.get();
            case BINARY -> Types.BinaryType.get();
            case DOUBLE -> Types.DoubleType.get();
          };
      fields.add(
          column.nullable()
              ? Types.NestedField.optional(column.icebergFieldId(), column.name(), type)
              : Types.NestedField.required(column.icebergFieldId(), column.name(), type));
    }
    return new Schema(fields);
  }

  /** The catalog's own JDBC url — what {@code LakeCommitCoordinator} transacts against. */
  public String jdbcUrl() {
    return jdbcUrl;
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
        INSTANCES_SELECT_SQL,
        IcebergLakeWriter::appendInstanceRow,
        InstanceRow::startMs,
        sourcePartition,
        throughOffset);
    flushTable(
        activitiesTable,
        bufferedActivities,
        "activities",
        ACTIVITIES_STAGING_DDL,
        ACTIVITIES_STAGING_TABLE,
        ACTIVITIES_SELECT_SQL,
        IcebergLakeWriter::appendActivityRow,
        ActivityRow::instanceStartMs,
        sourcePartition,
        throughOffset);
  }

  private <T> void flushTable(
      final Table table,
      final List<T> bufferedRows,
      final String tableLabel,
      final String stagingDdl,
      final String stagingTableName,
      final String selectSql,
      final RowAppender<T> rowAppender,
      final ToLongFunction<T> familyDayMillis,
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
    // advancing now -- so the new snapshot's summary remains a complete map. All three
    // carry-forward prefixes must be re-stamped together (see FRONTIER_PROPERTY_PREFIX's and
    // ZBPOS_PROPERTY_PREFIX's javadoc): this legacy flush path shares instancesTable/
    // activitiesTable with DirectCommitSink, so a commit here that only forwarded lake.offset.*
    // would silently wipe every frontier or origin-position dedup watermark stamp DirectCommitSink
    // had already landed on the same table.
    priorSummary.forEach(
        (key, value) -> {
          if (key.startsWith(OFFSET_PROPERTY_PREFIX)
              || key.startsWith(FRONTIER_PROPERTY_PREFIX)
              || key.startsWith(ZBPOS_PROPERTY_PREFIX)) {
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
      final List<DataFile> files =
          writeParquetByDay(
              table,
              tableLabel,
              stagingDdl,
              stagingTableName,
              selectSql,
              rowAppender,
              familyDayMillis,
              bufferedRows,
              partition,
              committed + 1,
              throughOffset);
      files.forEach(append::appendFile);
    }
    append.commit();
    bufferedRows.clear();
  }

  /**
   * Groups {@code rows} by family day (schema v2 -- see {@link #INSTANCE_SCHEMA}'s javadoc) and
   * writes one Parquet file per day, each registered with the day's own partition tuple: a
   * partitioned table's data file must carry exactly one partition value, so a single flush whose
   * rows happen to span more than one family day can no longer produce a single mixed file the way
   * the pre-v2 unpartitioned tables allowed.
   */
  private <T> List<DataFile> writeParquetByDay(
      final Table table,
      final String tableLabel,
      final String stagingDdl,
      final String stagingTableName,
      final String selectSql,
      final RowAppender<T> rowAppender,
      final ToLongFunction<T> familyDayMillis,
      final List<T> rows,
      final int partition,
      final long fromOffset,
      final long throughOffset) {
    final Map<Long, List<T>> rowsByDay = new LinkedHashMap<>();
    for (final T row : rows) {
      final long epochDay = Math.floorDiv(familyDayMillis.applyAsLong(row), MILLIS_PER_DAY);
      rowsByDay.computeIfAbsent(epochDay, ignored -> new ArrayList<>()).add(row);
    }
    final List<DataFile> files = new ArrayList<>(rowsByDay.size());
    for (final Map.Entry<Long, List<T>> dayRows : rowsByDay.entrySet()) {
      files.add(
          writeParquetForDay(
              table,
              tableLabel,
              stagingDdl,
              stagingTableName,
              selectSql,
              rowAppender,
              dayRows.getKey(),
              dayRows.getValue(),
              partition,
              fromOffset,
              throughOffset));
    }
    return files;
  }

  private <T> DataFile writeParquetForDay(
      final Table table,
      final String tableLabel,
      final String stagingDdl,
      final String stagingTableName,
      final String selectSql,
      final RowAppender<T> rowAppender,
      final long epochDay,
      final List<T> rows,
      final int partition,
      final long fromOffset,
      final long throughOffset) {
    final String fileName =
        String.format("p%d-%d-%d-day%d.parquet", partition, fromOffset, throughOffset, epochDay);
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
        copy.execute("COPY (" + selectSql + ") TO '" + physicalPath + "' (FORMAT PARQUET)");
      }
      final long fileSizeBytes = Files.size(physicalPath);
      LOG.info(
          "Flushed {} rows ({} table, partition {}, offsets {}-{}, day {}) to {}",
          rows.size(),
          tableLabel,
          partition,
          fromOffset,
          throughOffset,
          epochDay,
          physicalPath);
      // table.spec() is this table's days(...) partition spec (see #tableOrCreate); the partition
      // value is the day's own ISO date string -- Conversions#fromPartitionString parses a DATE
      // partition value as an ISO local date, not a raw integer epoch-day.
      return DataFiles.builder(table.spec())
          .withPath(location)
          .withFormat(FileFormat.PARQUET)
          .withPartitionValues(List.of(LocalDate.ofEpochDay(epochDay).toString()))
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

  /**
   * The durable origin-position dedup watermark for Zeebe partition {@code zeebePartitionId},
   * mirroring {@link #committedOffset(int)}'s own MIN-across-tables rule (see this class's
   * "Exactly-once via per-table offset stamping" javadoc): each table's own current-snapshot {@code
   * lake.zbpos.z*} stamp is read independently and the minimum of the two is returned, so a
   * partition whose stamp is missing (or lower) on one table pulls the seeded watermark down to
   * match — any record above that value must fold again, because its row was never guaranteed
   * durable on both tables. Callers seed a fresh {@code LakeTranslator} with this value at startup
   * (see {@code LakeTranslator#seedWatermark}'s javadoc); this method never reads any in-memory
   * translator state, only the tables' own durable stamps.
   *
   * @return the min of the two tables' stamped watermark for {@code zeebePartitionId}, or {@code
   *     -1} if neither table has ever stamped one
   */
  public long committedZeebeWatermark(final int zeebePartitionId) {
    return Math.min(
        currentZeebeWatermarkOf(instancesTable, zeebePartitionId),
        currentZeebeWatermarkOf(activitiesTable, zeebePartitionId));
  }

  private static long currentZeebeWatermarkOf(final Table table, final int zeebePartitionId) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    if (snapshot == null) {
      return -1L;
    }
    final String value = snapshot.summary().get(ZBPOS_PROPERTY_PREFIX + zeebePartitionId);
    return value == null ? -1L : Long.parseLong(value);
  }

  /**
   * Every Zeebe partition id either raw table's current snapshot has ever stamped a {@code
   * lake.zbpos.z*} watermark for — the set a caller must enumerate to seed every known partition's
   * {@code LakeTranslator} watermark at startup via {@link #committedZeebeWatermark(int)} (see its
   * own javadoc); a partition never yet stamped on either table is correctly absent, since {@link
   * #committedZeebeWatermark(int)} would return {@code -1} for it anyway.
   */
  public Set<Integer> stampedZeebePartitionIds() {
    final Set<Integer> ids = new TreeSet<>();
    collectZeebePartitionIds(instancesTable, ids);
    collectZeebePartitionIds(activitiesTable, ids);
    return ids;
  }

  private static void collectZeebePartitionIds(final Table table, final Set<Integer> out) {
    table.refresh();
    final Snapshot snapshot = table.currentSnapshot();
    if (snapshot == null) {
      return;
    }
    snapshot
        .summary()
        .keySet()
        .forEach(
            key -> {
              if (key.startsWith(ZBPOS_PROPERTY_PREFIX)) {
                out.add(Integer.parseInt(key.substring(ZBPOS_PROPERTY_PREFIX.length())));
              }
            });
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
   * Handle for {@link LakeCompactor} (constructed against this same {@link Table} instance, driven
   * from the poll-loop thread) and for {@code LakePocApp}'s L0 sink wiring, which builds its {@code
   * io.camunda.analytics.lake.sink.pipeline.SinkPipeline}s and {@code
   * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink}s directly against this {@link Table}
   * (a different package, hence public rather than package-private). Since {@code DirectCommitSink}
   * commits from each partition's own flush thread, concurrently with {@link LakeCompactor}'s
   * poll-thread-driven passes, both sides must serialize their commit sequences through {@link
   * #commitLock(Table)} — sharing the handle itself is safe, mutating through it concurrently is
   * not.
   */
  public Table instancesTable() {
    return instancesTable;
  }

  /** See {@link #instancesTable()}. */
  public Table activitiesTable() {
    return activitiesTable;
  }

  /**
   * The variant-k1 dictionary table — see {@link #VARIANT_SCHEMA}'s own javadoc. Unlike {@link
   * #instancesTable()}/{@link #activitiesTable()}, this table is never registered with {@link
   * #commitLock(Table)}: it is only ever committed through {@code CoordinatedDescriptorSink} (which
   * serializes concurrent committers through {@code LakeCommitCoordinator} instead), the same
   * arrangement every generated partials table (see {@link #partialsTableOrCreate}) already uses.
   */
  public Table variantsTable() {
    return variantsTable;
  }

  /**
   * The object-fabric sightings dictionary table — see {@link #OBJECT_SCHEMA}'s own javadoc; same
   * commit arrangement as {@link #variantsTable()}.
   */
  public Table objectsTable() {
    return objectsTable;
  }

  /**
   * The call-activity instance-link dictionary table — see {@link #INSTANCE_LINKS_SCHEMA}'s own
   * javadoc; same commit arrangement as {@link #variantsTable()}.
   */
  public Table instanceLinksTable() {
    return instanceLinksTable;
  }

  /**
   * The object-relations dictionary table — see {@link #OBJECT_RELATIONS_SCHEMA}'s own javadoc;
   * same commit arrangement as {@link #variantsTable()}.
   */
  public Table objectRelationsTable() {
    return objectRelationsTable;
  }

  /**
   * The object-lifecycle fact table — see {@link #OBJECT_LIFECYCLE_SCHEMA}'s own javadoc; same
   * commit arrangement as {@link #variantsTable()}.
   */
  public Table objectLifecycleTable() {
    return objectLifecycleTable;
  }

  /**
   * The commit mutex guarding every mutation sequence (refresh &rarr; rewrite/append &rarr; commit)
   * against {@code table} — one lock per raw table, not one shared lock for both, so instances and
   * activities commits never block each other. {@code table} must be exactly the {@link Table}
   * instance returned by {@link #instancesTable()} or {@link #activitiesTable()}.
   *
   * <p>Load-bearing since {@code io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} started
   * committing directly from each partition's own flush thread: {@link LakeCompactor}'s rewrite/
   * manifest-rewrite/expire passes run on the poll-loop thread and mutate the very same {@link
   * Table} objects, so both sides must hold this lock for the whole read-current-summary-then-
   * commit sequence, or a lost update (a wiped offset/frontier stamp, or a rewrite racing a fresh
   * append) becomes possible. See {@link LakeCompactor}'s class javadoc for the full picture.
   *
   * @throws IllegalArgumentException if {@code table} is neither of this writer's own tables
   */
  public ReentrantLock commitLock(final Table table) {
    if (table == instancesTable) {
      return instancesCommitLock;
    }
    if (table == activitiesTable) {
      return activitiesCommitLock;
    }
    throw new IllegalArgumentException("Unknown table " + table.name());
  }

  /** See {@link #instancesTable()}. */
  Connection duckdbConnection() {
    return duckdb;
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

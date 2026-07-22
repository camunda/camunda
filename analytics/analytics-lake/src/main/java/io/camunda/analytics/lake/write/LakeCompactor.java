/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.ExpireSnapshots;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.RewriteFiles;
import org.apache.iceberg.RewriteManifests;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.io.CloseableIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles three iceberg-core primitives — {@link Table#newRewrite() data-file rewrite}, {@link
 * Table#rewriteManifests() manifest consolidation} and {@link Table#expireSnapshots() snapshot
 * expiry} — plus a DuckDB-driven Parquet rewrite, into the PoC-scale equivalent of a managed
 * lakehouse's periodic {@code CHECKPOINT}/compaction.
 *
 * <h2>Concurrency: per-table commit lock (load-bearing)</h2>
 *
 * <p>This class is constructed with, and only ever reuses, {@link IcebergLakeWriter}'s own {@link
 * Table} handles and embedded DuckDB {@link Connection}. It used to be true that {@link
 * #compactIfNeeded()} ran on the very same single poll-loop thread that drove every commit against
 * these tables, making synchronization unnecessary by construction — that stopped being true once
 * {@code io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} started committing directly from
 * each source partition's own flush thread. Both this class's per-table pass ({@link
 * #compactTable}, covering the data-file rewrite, manifest rewrite, and snapshot-expiry steps
 * together) and {@code DirectCommitSink#accept} now hold the same table's commit mutex — obtained
 * from {@link IcebergLakeWriter#commitLock(Table)}, one lock per raw table — for the whole
 * refresh-then-commit sequence, so a flush thread's append can never interleave with this class's
 * rewrite/manifest/expire commits against the same table. The lock is per-table (not one shared
 * lock for both), so instances and activities compaction/commits never block each other.
 *
 * <p>The embedded DuckDB {@link Connection} itself needs no such lock: {@code DirectCommitSink}
 * never issues a DuckDB statement (it only calls Iceberg's {@code Table} commit API), so this
 * class's own poll-loop thread remains the DuckDB connection's sole user, exactly as before.
 *
 * <p>Gold-table recompute ({@link #recomputeGoldTablesSafely()}) is unaffected by any of this: it
 * only ever reads the raw tables (Iceberg reads are snapshot-isolated without external locking) and
 * writes to its own three gold tables, which no other thread ever commits to — see {@link
 * GoldTables}'s own class javadoc. It keeps its pre-existing single-threaded story and needs no
 * commit lock of its own.
 *
 * <h2>Failure handling</h2>
 *
 * <p>No exception from this class is ever allowed to propagate to the poll loop: every step is
 * individually guarded, logged at {@code WARN} with its cause, and skipped rather than thrown. A
 * failure partway through a table's pass (e.g. a mid-rewrite crash or a row-count mismatch) simply
 * leaves that table's existing files and snapshots exactly as they were — {@link
 * #compactIfNeeded()} always returns a (possibly partial) {@link CompactionReport}.
 *
 * <h2>Orphan litter</h2>
 *
 * <p>A crash (or any exception) between the DuckDB {@code COPY} that writes a compacted Parquet
 * file and the {@link RewriteFiles#commit()} that registers it leaves that file on disk,
 * unreferenced by any Iceberg snapshot. This is harmless — it is never read by anything — but it is
 * not swept automatically today; a future orphan-file scan (mirroring Iceberg's own {@code
 * removeOrphanFiles} action) is the natural follow-up, deliberately out of scope for this PoC pass.
 *
 * <h2>Offset and frontier re-stamping (critical)</h2>
 *
 * <p>Per {@link IcebergLakeWriter}'s own class javadoc, iceberg-core never carries a snapshot's
 * custom summary properties forward into the next snapshot. {@link IcebergLakeWriter#flush} relies
 * on those {@code lake.offset.p*} properties surviving in the <em>current</em> snapshot's summary
 * for {@link IcebergLakeWriter#committedOffset(int)} to work at all, and {@code
 * io.camunda.analytics.lake.sink.pipeline.DirectCommitSink} relies the same way on the {@code
 * lake.frontier.p*} properties it stamps. Both {@link #rewriteDataFiles} and {@link
 * #rewriteManifests} produce a new current snapshot, so each one re-reads the prior summary via
 * {@link #currentCarryForwardProperties} and re-sets every offset <em>and</em> frontier property
 * onto its own commit before committing — exactly the same pattern {@code flushTable} uses.
 * Skipping either prefix would silently reset every partition's committed offset (or its frontier
 * stamp) to a lost value the moment compaction first runs, forcing a full replay from scratch (or
 * silently regressing the frontier); the compaction integration test asserts on this explicitly.
 *
 * <h2>Gold tables (added step, after the raw-table work above)</h2>
 *
 * <p>Once the two raw tables' rewrite/manifest/expiry steps above have run, this class also drives
 * one {@link GoldTables#recompute()} pass. Unlike everything above, the gold-table step never reads
 * or writes any {@code lake.offset.p*} property — see {@link GoldTables}'s own class javadoc for
 * why that is correct rather than an oversight, and {@link IcebergLakeWriter#committedOffset(int)}
 * for confirmation that its table enumeration is untouched (still exactly {@code instances}/{@code
 * activities}).
 */
public final class LakeCompactor {

  /** Compact a table's data files once its live count exceeds this many. */
  static final int DATA_FILE_COMPACTION_THRESHOLD = 20;

  /** {@code expireSnapshots().retainLast(...)} argument — keep a short but non-trivial history. */
  private static final int RETAIN_LAST_SNAPSHOTS = 3;

  private static final String INSTANCES_LABEL = "instances";
  private static final String ACTIVITIES_LABEL = "activities";
  private static final String INSTANCES_SORT = "process_id, started_at";
  private static final String ACTIVITIES_SORT = "process_id, instance_key, started_at";

  private static final Logger LOG = LoggerFactory.getLogger(LakeCompactor.class);

  private final Connection duckdb;
  private final Table instancesTable;
  private final Table activitiesTable;
  private final ReentrantLock instancesCommitLock;
  private final ReentrantLock activitiesCommitLock;
  private final GoldTables goldTables;

  public LakeCompactor(final IcebergLakeWriter writer) {
    duckdb = writer.duckdbConnection();
    instancesTable = writer.instancesTable();
    activitiesTable = writer.activitiesTable();
    instancesCommitLock = writer.commitLock(instancesTable);
    activitiesCommitLock = writer.commitLock(activitiesTable);
    // Applied at construction, not only on tables created from now on -- this brings the
    // already-existing demo tables under the same metadata-cleanup policy retroactively (see the
    // README's Compaction section).
    configureMetadataCleanup(instancesTable, INSTANCES_LABEL);
    configureMetadataCleanup(activitiesTable, ACTIVITIES_LABEL);
    goldTables = new GoldTables(writer.catalog(), duckdb, instancesTable, activitiesTable);
  }

  /**
   * Runs one compaction pass over both lake tables, then one {@link GoldTables#recompute()} pass.
   * Never throws; see class javadoc.
   */
  public CompactionReport compactIfNeeded() {
    final TableCompactionResult instances =
        compactTable(INSTANCES_LABEL, instancesTable, instancesCommitLock, INSTANCES_SORT);
    final TableCompactionResult activities =
        compactTable(ACTIVITIES_LABEL, activitiesTable, activitiesCommitLock, ACTIVITIES_SORT);
    final GoldTables.GoldRecomputeResult gold = recomputeGoldTablesSafely();
    return new CompactionReport(instances, activities, gold);
  }

  /**
   * {@link GoldTables#recompute()} throws on failure (see its javadoc); this class's own contract
   * is that nothing it does ever reaches the poll loop (see this class's "Failure handling" javadoc
   * section), so the same guard-and-log pattern {@link #compactTable} uses for its own steps
   * applies here too.
   */
  private GoldTables.GoldRecomputeResult recomputeGoldTablesSafely() {
    try {
      return goldTables.recompute();
    } catch (final RuntimeException e) {
      LOG.warn("Gold-table recompute failed; leaving existing gold tables in place", e);
      return GoldTables.GoldRecomputeResult.unavailable();
    }
  }

  private static void configureMetadataCleanup(final Table table, final String label) {
    try {
      table
          .updateProperties()
          .set(TableProperties.METADATA_DELETE_AFTER_COMMIT_ENABLED, "true")
          .set(TableProperties.METADATA_PREVIOUS_VERSIONS_MAX, "5")
          .commit();
    } catch (final RuntimeException e) {
      LOG.warn("Failed to set metadata-cleanup properties on {} table; leaving defaults", label, e);
    }
  }

  /**
   * Runs one table's full compaction pass (data-file rewrite, manifest rewrite, snapshot expiry)
   * under {@code commitLock} held for the entire sequence — see this class's "Concurrency" javadoc
   * section for why that must span all three steps together rather than being acquired and released
   * per step: a {@code DirectCommitSink} append landing between, say, the data-file rewrite and the
   * manifest rewrite would otherwise see this pass's intermediate (and momentarily inconsistent)
   * state.
   */
  private TableCompactionResult compactTable(
      final String label,
      final Table table,
      final ReentrantLock commitLock,
      final String sortClause) {
    commitLock.lock();
    try {
      table.refresh();
      final Map<String, DataFile> filesBefore = currentDataFiles(table);
      final int before = filesBefore.size();

      boolean dataCompacted = false;
      if (before > DATA_FILE_COMPACTION_THRESHOLD) {
        dataCompacted = rewriteDataFiles(table, label, filesBefore, sortClause);
      }

      final boolean manifestsRewritten = rewriteManifests(table, label);
      final int snapshotsExpired = expireSnapshots(table, label);

      table.refresh();
      final int after = dataCompacted ? currentDataFiles(table).size() : before;

      return new TableCompactionResult(
          label, dataCompacted, before, after, snapshotsExpired, manifestsRewritten);
    } catch (final RuntimeException e) {
      // Belt and braces beyond the individual per-step guards below: nothing from this method may
      // ever reach the poll loop.
      LOG.warn("Compaction pass failed unexpectedly for {} table; skipping this cycle", label, e);
      return TableCompactionResult.unavailable(label);
    } finally {
      commitLock.unlock();
    }
  }

  /** Data files currently referenced by the table's current snapshot, deduped by location. */
  private static Map<String, DataFile> currentDataFiles(final Table table) {
    final Map<String, DataFile> files = new LinkedHashMap<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        // A data file can be split into multiple scan tasks (byte ranges) -- dedupe by location so
        // each physical file is only counted/rewritten once.
        files.put(task.file().location(), task.file());
      }
    } catch (final IOException e) {
      throw new UncheckedCompactionException("Failed to plan data files", e);
    }
    return files;
  }

  /**
   * Day-scoped rewrite (schema v2 — see {@link IcebergLakeWriter#INSTANCE_SCHEMA}'s javadoc): both
   * raw tables are now partitioned by {@code days(...)} on their family-day column, and a
   * partitioned table's data file must carry exactly one partition tuple, so files are grouped by
   * partition value first and each day's files are rewritten into that day's own single output file
   * — never mixing two family days into one file. A day whose group already has only one file is
   * left untouched (nothing to gain); every touched day's old files and new file are deleted/added
   * in the same {@link RewriteFiles} commit.
   */
  private boolean rewriteDataFiles(
      final Table table,
      final String label,
      final Map<String, DataFile> filesToReplace,
      final String sortClause) {
    Path lastWrittenPath = null;
    try {
      final Map<Integer, List<DataFile>> filesByDay = groupByPartitionDay(filesToReplace.values());
      final List<DataFile> filesRewritten = new ArrayList<>();
      final List<DataFile> newFiles = new ArrayList<>();
      // Tracked separately from newFiles.size() even though the two happen to coincide today (one
      // output file per compacted day) -- the log line below must report the actual day count, not
      // stand in a file count for it (see B3 in the review).
      int daysCompacted = 0;
      for (final Map.Entry<Integer, List<DataFile>> dayGroup : filesByDay.entrySet()) {
        final List<DataFile> dayFiles = dayGroup.getValue();
        if (dayFiles.size() < 2) {
          continue; // a single file for this day is already minimal
        }
        final int epochDay = dayGroup.getKey();
        final long expectedRows = dayFiles.stream().mapToLong(DataFile::recordCount).sum();
        final String fileList =
            dayFiles.stream()
                .map(f -> quote(LocalFileIO.toFilesystemPath(f.location()).toString()))
                .collect(Collectors.joining(", "));
        final String fileName = "compacted-" + UUID.randomUUID() + "-day" + epochDay + ".parquet";
        final String location = table.location() + "/data/" + fileName;
        final Path compactedPhysicalPath = LocalFileIO.toFilesystemPath(location);
        lastWrittenPath = compactedPhysicalPath;
        if (compactedPhysicalPath.getParent() != null) {
          Files.createDirectories(compactedPhysicalPath.getParent());
        }

        try (Statement copy = duckdb.createStatement()) {
          copy.execute(
              "COPY (SELECT * FROM read_parquet(["
                  + fileList
                  + "]) ORDER BY "
                  + sortClause
                  + ") TO '"
                  + compactedPhysicalPath
                  + "' (FORMAT PARQUET)");
        }

        final long actualRows = countRows(compactedPhysicalPath);
        if (actualRows != expectedRows) {
          LOG.warn(
              "Compaction row-count mismatch for {} table day {}: expected {} but compacted file "
                  + "has {}; skipping this day's rewrite (orphan file left at {} for a future "
                  + "sweep)",
              label,
              epochDay,
              expectedRows,
              actualRows,
              compactedPhysicalPath);
          continue;
        }

        final long fileSizeBytes = Files.size(compactedPhysicalPath);
        final DataFile newFile =
            DataFiles.builder(table.spec())
                .withPath(location)
                .withFormat(FileFormat.PARQUET)
                .withPartitionValues(List.of(LocalDate.ofEpochDay(epochDay).toString()))
                .withRecordCount(actualRows)
                .withFileSizeInBytes(fileSizeBytes)
                .build();
        filesRewritten.addAll(dayFiles);
        newFiles.add(newFile);
        daysCompacted++;
      }

      if (newFiles.isEmpty()) {
        return false;
      }

      // Read BEFORE constructing the rewrite producer -- same ordering IcebergLakeWriter#flushTable
      // uses, so the carry-forward properties captured are unambiguously the pre-rewrite current
      // summary.
      final Map<String, String> carryForwardProperties = currentCarryForwardProperties(table);
      final RewriteFiles rewrite = table.newRewrite();
      // Without this, RewriteFiles validates against the FULL table history (starting snapshot
      // null) — which a prior pass's expireSnapshots has truncated, failing every later rewrite
      // with "Cannot determine history between starting snapshot null and the last known
      // ancestor". Validating from the snapshot the scan planned against is also the semantically
      // correct bound: this pass runs on the single poll thread, so nothing can commit in between.
      if (table.currentSnapshot() != null) {
        rewrite.validateFromSnapshot(table.currentSnapshot().snapshotId());
      }
      carryForwardProperties.forEach(rewrite::set);
      filesRewritten.forEach(rewrite::deleteFile);
      newFiles.forEach(rewrite::addFile);
      rewrite.commit();

      LOG.info(
          "Compacted {} table {}: {} data file(s) across {} day(s) -> {} file(s)",
          label,
          table.name(),
          filesRewritten.size(),
          daysCompacted,
          newFiles.size());
      return true;
    } catch (final SQLException | IOException | RuntimeException e) {
      LOG.warn(
          "Data-file compaction failed for {} table; leaving existing files in place{}",
          label,
          lastWrittenPath == null
              ? ""
              : " (orphan file(s) possibly left, e.g. at " + lastWrittenPath + ")",
          e);
      return false;
    }
  }

  /** Groups {@code files} by their {@code days(...)} partition value (epoch day). */
  private static Map<Integer, List<DataFile>> groupByPartitionDay(
      final Collection<DataFile> files) {
    final Map<Integer, List<DataFile>> byDay = new LinkedHashMap<>();
    for (final DataFile file : files) {
      final int epochDay = file.partition().get(0, Integer.class);
      byDay.computeIfAbsent(epochDay, ignored -> new ArrayList<>()).add(file);
    }
    return byDay;
  }

  private long countRows(final Path parquetFile) throws SQLException {
    try (Statement count = duckdb.createStatement();
        ResultSet rs =
            count.executeQuery("SELECT count(*) FROM read_parquet('" + parquetFile + "')")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String quote(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private boolean rewriteManifests(final Table table, final String label) {
    try {
      // Read BEFORE constructing the rewrite producer -- see the comment in #rewriteDataFiles.
      final Map<String, String> carryForwardProperties = currentCarryForwardProperties(table);
      // Cluster everything into one bucket -- the goal here is "many small manifests -> one",
      // not partition-aware clustering (the tables are unpartitioned anyway).
      final RewriteManifests rewrite = table.rewriteManifests().clusterBy(file -> 0);
      carryForwardProperties.forEach(rewrite::set);
      rewrite.commit();
      return true;
    } catch (final RuntimeException e) {
      LOG.warn(
          "Manifest rewrite failed for {} table; leaving existing manifests in place", label, e);
      return false;
    }
  }

  /**
   * The table's current {@code lake.offset.p*} <em>and</em> {@code lake.frontier.p*} snapshot
   * summary properties, read fresh — see this class's "Offset and frontier re-stamping" javadoc
   * section for why every {@link RewriteFiles}/{@link RewriteManifests} commit this class issues
   * must re-apply both prefixes before committing.
   */
  private static Map<String, String> currentCarryForwardProperties(final Table table) {
    table.refresh();
    final Snapshot current = table.currentSnapshot();
    if (current == null) {
      return Map.of();
    }
    final Map<String, String> carryForward = new LinkedHashMap<>();
    current
        .summary()
        .forEach(
            (key, value) -> {
              if (key.startsWith(IcebergLakeWriter.OFFSET_PROPERTY_PREFIX)
                  || key.startsWith(IcebergLakeWriter.FRONTIER_PROPERTY_PREFIX)) {
                carryForward.put(key, value);
              }
            });
    return carryForward;
  }

  private int expireSnapshots(final Table table, final String label) {
    try {
      final ExpireSnapshots expire =
          table
              .expireSnapshots()
              .expireOlderThan(System.currentTimeMillis())
              .retainLast(RETAIN_LAST_SNAPSHOTS);
      final List<Snapshot> toExpire = expire.apply();
      final int count = toExpire.size();
      expire.commit();
      return count;
    } catch (final RuntimeException e) {
      LOG.warn(
          "Snapshot expiry failed for {} table; leaving existing snapshots in place", label, e);
      return 0;
    }
  }

  /** Wraps a checked failure from within {@link #currentDataFiles(Table)}'s try-with-resources. */
  private static final class UncheckedCompactionException extends RuntimeException {
    UncheckedCompactionException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Aggregate result of one {@link #compactIfNeeded()} call: one entry per raw lake table, plus the
   * gold-table recompute outcome.
   */
  public record CompactionReport(
      TableCompactionResult instances,
      TableCompactionResult activities,
      GoldTables.GoldRecomputeResult goldTables) {

    @Override
    public String toString() {
      return "CompactionReport{" + instances + ", " + activities + ", " + goldTables + '}';
    }
  }

  /**
   * One table's outcome from a compaction pass.
   *
   * @param tableName {@code instances} or {@code activities}
   * @param dataFilesCompacted whether the data-file rewrite ran (only true once the live count
   *     exceeded {@link #DATA_FILE_COMPACTION_THRESHOLD})
   * @param dataFilesBefore live data-file count before this pass
   * @param dataFilesAfter live data-file count after this pass (equal to {@code before} when no
   *     rewrite was needed or it was aborted)
   * @param snapshotsExpired how many snapshots {@link Table#expireSnapshots()} removed
   * @param manifestsRewritten whether manifest consolidation succeeded
   */
  public record TableCompactionResult(
      String tableName,
      boolean dataFilesCompacted,
      int dataFilesBefore,
      int dataFilesAfter,
      int snapshotsExpired,
      boolean manifestsRewritten) {

    private static TableCompactionResult unavailable(final String tableName) {
      return new TableCompactionResult(tableName, false, -1, -1, 0, false);
    }

    @Override
    public String toString() {
      return tableName
          + "["
          + "dataFiles "
          + dataFilesBefore
          + "->"
          + dataFilesAfter
          + (dataFilesCompacted ? " (compacted)" : "")
          + ", snapshotsExpired="
          + snapshotsExpired
          + ", manifestsRewritten="
          + manifestsRewritten
          + ']';
    }
  }
}

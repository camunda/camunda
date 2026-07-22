/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.catalog;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.io.FileIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commits changes to <em>several</em> Iceberg tables atomically: all pointer swaps execute inside
 * one transaction against the same embedded H2 database the lake's {@code JdbcCatalog} keeps its
 * {@code iceberg_tables} rows in — see this package's {@code package-info} for why that is the
 * whole trick.
 *
 * <p>Usage: pass one {@link TableChange} per table; each change receives a <em>staged</em> view of
 * its table and applies ordinary iceberg-core operations to it ({@code newAppend()...commit()},
 * property updates, ...). File and manifest writing happen immediately (write-ahead, before any
 * pointer moves); the pointer swaps are captured and executed together by {@link #commitAll}.
 *
 * <p>Conflict handling: the swap for each table is predicated on the metadata location the staging
 * was based on. If any table's predicate misses (a single-table commit — e.g. the compactor — won
 * the race), the whole transaction rolls back, the staged metadata files are queued for sweep, and
 * the <em>entire batch</em> is re-staged from fresh state and retried. Retrying the whole batch,
 * never a partial one, is what "all or nothing" means here. Swaps execute in deterministic
 * identifier order so two concurrent batches cannot deadlock on row locks.
 *
 * <p>Bookkeeping in the same transaction: one commit-journal row per batch (which tables advanced
 * to which snapshots, plus the caller's stamps — the audit trail of cuts), and the pending-deletes
 * queue (a transaction cannot atomically delete files, so deletion intent is journaled and {@link
 * #sweepPendingDeletes(FileIO)} executes it later).
 */
public final class LakeCommitCoordinator {

  /** One table's contribution to an atomic batch. */
  public record TableChange(TableIdentifier identifier, Table table, Consumer<Table> change) {}

  /** What a successful batch committed: the journal row id and each table's executed swap. */
  public record CommitAllResult(long journalId, List<StagedCommit> commits) {}

  private static final Logger LOG = LoggerFactory.getLogger(LakeCommitCoordinator.class);
  private static final int MAX_ATTEMPTS = 3;

  // The exact conditional-update shapes iceberg-core's JdbcCatalog uses for its own commits, so
  // both commit paths contend correctly on the same rows. Which one applies depends on the catalog
  // schema version actually present (V1 added the iceberg_type discriminator); probed once at
  // construction.
  private static final String SWAP_SQL_V0 =
      "UPDATE iceberg_tables SET metadata_location = ?, previous_metadata_location = ? "
          + "WHERE catalog_name = ? AND table_namespace = ? AND table_name = ? "
          + "AND metadata_location = ?";
  private static final String SWAP_SQL_V1 =
      SWAP_SQL_V0 + " AND (iceberg_type = 'TABLE' OR iceberg_type IS NULL)";

  private static final String JOURNAL_DDL =
      "CREATE TABLE IF NOT EXISTS lake_commit_journal ("
          + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
          + "committed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
          + "tables VARCHAR NOT NULL, "
          + "stamps VARCHAR NOT NULL)";

  private static final String PENDING_DELETES_DDL =
      "CREATE TABLE IF NOT EXISTS lake_pending_deletes ("
          + "path VARCHAR PRIMARY KEY, "
          + "reason VARCHAR NOT NULL, "
          + "enqueued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";

  private final String jdbcUrl;
  private final String catalogName;
  private final String swapSql;

  public LakeCommitCoordinator(final String jdbcUrl, final String catalogName) {
    this.jdbcUrl = jdbcUrl;
    this.catalogName = catalogName;
    try (final Connection connection = DriverManager.getConnection(jdbcUrl);
        final Statement statement = connection.createStatement()) {
      statement.execute(JOURNAL_DDL);
      statement.execute(PENDING_DELETES_DDL);
      swapSql = hasIcebergTypeColumn(connection) ? SWAP_SQL_V1 : SWAP_SQL_V0;
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to initialize coordinator tables in " + jdbcUrl, e);
    }
  }

  /** JdbcCatalog's V0 schema predates the {@code iceberg_type} discriminator column. */
  private static boolean hasIcebergTypeColumn(final Connection connection) throws SQLException {
    try (final ResultSet columns =
        connection.getMetaData().getColumns(null, null, "%", "ICEBERG_TYPE")) {
      while (columns.next()) {
        if ("iceberg_tables".equalsIgnoreCase(columns.getString("TABLE_NAME"))) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Applies every change and commits all resulting pointer swaps atomically. Changes that turn out
   * to be no-ops (the consumer never committed an update) are skipped; if the whole batch is a
   * no-op, nothing is written at all and the result carries journal id {@code -1}.
   *
   * @param changes one entry per table; a table must appear at most once
   * @param stamps caller bookkeeping journaled with the batch (offsets, positions, frontiers) —
   *     purely observational, the authoritative stamps live in the snapshot summaries
   * @throws CommitFailedException when {@value #MAX_ATTEMPTS} whole-batch attempts all lost their
   *     race
   * @throws CommitStateUnknownException when the transaction's own fate is unknown (driver failure
   *     during commit) — staged files are deliberately NOT queued for deletion in that case
   */
  public CommitAllResult commitAll(
      final List<TableChange> changes, final Map<String, String> stamps) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      final List<StagedCommit> staged = stageAll(changes);
      if (staged.isEmpty()) {
        return new CommitAllResult(-1, List.of());
      }
      final Long journalId = trySwapAll(staged, stamps);
      if (journalId != null) {
        // Only now may live handles observe the new state; refresh eagerly so callers holding
        // these Table instances don't serve stale metadata until their next lazy refresh.
        changes.forEach(change -> change.table().refresh());
        return new CommitAllResult(journalId, staged);
      }
      LOG.debug(
          "commitAll attempt {}/{} lost a compare-and-swap race across {} tables; re-staging",
          attempt,
          MAX_ATTEMPTS,
          staged.size());
      enqueueDeletes(
          staged.stream().map(StagedCommit::newMetadataLocation).toList(), "commit-conflict");
      changes.forEach(change -> change.table().refresh());
    }
    throw new CommitFailedException(
        "commitAll gave up after %s attempts: another committer kept winning the swap race",
        MAX_ATTEMPTS);
  }

  /** Runs every change against a staged view of its table, collecting the deferred swaps. */
  private List<StagedCommit> stageAll(final List<TableChange> changes) {
    final List<StagedCommit> staged = new ArrayList<>(changes.size());
    for (final TableChange change : changes) {
      final DeferredCommitTableOperations deferred =
          new DeferredCommitTableOperations(
              change.identifier(), ((HasTableOperations) change.table()).operations());
      change.change().accept(new BaseTable(deferred, change.identifier().toString()));
      if (deferred.staged() != null) {
        staged.add(deferred.staged());
      }
    }
    return staged;
  }

  /**
   * Executes all swaps plus the journal row in one transaction. Returns the journal id, or {@code
   * null} if any swap's predicate missed (transaction rolled back, caller re-stages).
   */
  private Long trySwapAll(final List<StagedCommit> staged, final Map<String, String> stamps) {
    // Deterministic row-lock order across concurrent batches.
    final List<StagedCommit> ordered =
        staged.stream()
            .sorted(Comparator.comparing(commit -> commit.identifier().toString()))
            .toList();
    try (final Connection connection = DriverManager.getConnection(jdbcUrl)) {
      connection.setAutoCommit(false);
      try {
        try (final PreparedStatement swap = connection.prepareStatement(swapSql)) {
          for (final StagedCommit commit : ordered) {
            swap.setString(1, commit.newMetadataLocation());
            swap.setString(2, commit.expectedMetadataLocation());
            swap.setString(3, catalogName);
            swap.setString(4, commit.identifier().namespace().toString());
            swap.setString(5, commit.identifier().name());
            swap.setString(6, commit.expectedMetadataLocation());
            if (swap.executeUpdate() != 1) {
              connection.rollback();
              return null;
            }
          }
        }
        final long journalId = insertJournalRow(connection, ordered, stamps);
        connection.commit();
        return journalId;
      } catch (final SQLException e) {
        try {
          connection.rollback();
        } catch (final SQLException rollbackFailure) {
          e.addSuppressed(rollbackFailure);
        }
        throw new CommitStateUnknownException(
            new IllegalStateException("commitAll transaction failed against " + jdbcUrl, e));
      }
    } catch (final SQLException e) {
      throw new CommitStateUnknownException(
          new IllegalStateException("commitAll could not reach the catalog db " + jdbcUrl, e));
    }
  }

  private static long insertJournalRow(
      final Connection connection,
      final List<StagedCommit> commits,
      final Map<String, String> stamps)
      throws SQLException {
    final StringBuilder tables = new StringBuilder();
    for (final StagedCommit commit : commits) {
      if (!tables.isEmpty()) {
        tables.append(',');
      }
      tables
          .append(commit.identifier())
          .append('@')
          .append(
              commit.metadata().currentSnapshot() == null
                  ? "-"
                  : commit.metadata().currentSnapshot().snapshotId());
    }
    final StringBuilder stampsText = new StringBuilder();
    stamps.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              if (!stampsText.isEmpty()) {
                stampsText.append('\n');
              }
              stampsText.append(entry.getKey()).append('=').append(entry.getValue());
            });
    try (final PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO lake_commit_journal (tables, stamps) VALUES (?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
      insert.setString(1, tables.toString());
      insert.setString(2, stampsText.toString());
      insert.executeUpdate();
      try (final ResultSet keys = insert.getGeneratedKeys()) {
        keys.next();
        return keys.getLong(1);
      }
    }
  }

  /**
   * Queues files for deletion by {@link #sweepPendingDeletes(FileIO)}; its own small transaction
   * (used after a rollback, and by callers skipping a redelivered descriptor whose replay-produced
   * files are orphans). Never throws — losing a delete intent only leaks an unreferenced file.
   */
  public void enqueueDeletes(final List<String> paths, final String reason) {
    try (final Connection connection = DriverManager.getConnection(jdbcUrl);
        final PreparedStatement insert =
            connection.prepareStatement(
                "MERGE INTO lake_pending_deletes (path, reason) KEY (path) VALUES (?, ?)")) {
      for (final String path : paths) {
        insert.setString(1, path);
        insert.setString(2, reason);
        insert.executeUpdate();
      }
    } catch (final SQLException e) {
      // Losing a delete intent only leaks an unreferenced file; never fail a commit path over it.
      LOG.warn("Failed to enqueue {} orphaned files for sweep; they will leak", paths.size(), e);
    }
  }

  /**
   * Deletes queued files and clears their rows; failures keep the row for the next sweep. Returns
   * how many files were deleted. Meant to be called from the same housekeeping cadence as snapshot
   * expiry.
   */
  public int sweepPendingDeletes(final FileIO io) {
    final List<String> paths = new ArrayList<>();
    try (final Connection connection = DriverManager.getConnection(jdbcUrl);
        final Statement select = connection.createStatement();
        final ResultSet rows = select.executeQuery("SELECT path FROM lake_pending_deletes")) {
      while (rows.next()) {
        paths.add(rows.getString(1));
      }
    } catch (final SQLException e) {
      LOG.warn("Pending-deletes sweep could not list queued files", e);
      return 0;
    }
    int deleted = 0;
    for (final String path : paths) {
      try {
        io.deleteFile(path);
        try (final Connection connection = DriverManager.getConnection(jdbcUrl);
            final PreparedStatement clear =
                connection.prepareStatement("DELETE FROM lake_pending_deletes WHERE path = ?")) {
          clear.setString(1, path);
          clear.executeUpdate();
        }
        deleted++;
      } catch (final RuntimeException | SQLException e) {
        LOG.warn("Pending-deletes sweep failed for {}; keeping it queued", path, e);
      }
    }
    return deleted;
  }
}

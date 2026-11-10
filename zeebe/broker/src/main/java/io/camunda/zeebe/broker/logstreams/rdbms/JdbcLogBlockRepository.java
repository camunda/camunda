/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * {@link LogBlockRepository} in plain, portable JDBC. Every method is one transaction on its own
 * pooled connection.
 *
 * <p>An append first takes the partition's {@code LOG_PARTITION} row lock with a no-op update that
 * also checks the term, then batch-inserts the blocks, then commits. The row lock serializes
 * appends with {@link #claimTerm}, so a new leader's claim either waits for an in-flight append to
 * finish or makes it fail; a check without the lock would let a deposed leader's append commit
 * after the new leader has read where the log ends.
 */
public final class JdbcLogBlockRepository implements LogBlockRepository {

  /** SQLSTATE class for integrity constraint violations, e.g. a duplicate primary key. */
  private static final String INTEGRITY_CONSTRAINT_VIOLATION = "23";

  private static final String BLOCK_COLUMNS = "LOW_POSITION, HIGH_POSITION, TERM, DATA";

  private final DataSource dataSource;
  private final int partitionId;

  private final String raiseTermSql;
  private final String selectTermSql;
  private final String insertTermSql;
  private final String lockTermSql;
  private final String insertBlockSql;
  private final String selectBlockAtOrBeforeSql;
  private final String selectBlocksAfterSql;

  public JdbcLogBlockRepository(
      final DataSource dataSource, final String tablePrefix, final int partitionId) {
    this.dataSource = dataSource;
    this.partitionId = partitionId;

    final var partitionTable = tablePrefix + "LOG_PARTITION";
    final var blockTable = tablePrefix + "LOG_BLOCK";
    raiseTermSql = "UPDATE " + partitionTable + " SET TERM = ? WHERE PARTITION_ID = ? AND TERM < ?";
    selectTermSql = "SELECT TERM FROM " + partitionTable + " WHERE PARTITION_ID = ?";
    insertTermSql = "INSERT INTO " + partitionTable + " (PARTITION_ID, TERM) VALUES (?, ?)";
    // relies on the update count being the number of matched rows, not changed ones; MySQL and
    // MariaDB drivers report matched rows unless useAffectedRows is enabled
    lockTermSql =
        "UPDATE " + partitionTable + " SET TERM = TERM WHERE PARTITION_ID = ? AND TERM = ?";
    insertBlockSql =
        "INSERT INTO "
            + blockTable
            + " (PARTITION_ID, "
            + BLOCK_COLUMNS
            + ") VALUES (?, ?, ?, ?, ?)";
    selectBlockAtOrBeforeSql =
        "SELECT "
            + BLOCK_COLUMNS
            + " FROM "
            + blockTable
            + " WHERE PARTITION_ID = ? AND LOW_POSITION <= ? ORDER BY LOW_POSITION DESC";
    selectBlocksAfterSql =
        "SELECT "
            + BLOCK_COLUMNS
            + " FROM "
            + blockTable
            + " WHERE PARTITION_ID = ? AND LOW_POSITION > ? ORDER BY LOW_POSITION";
  }

  @Override
  public boolean claimTerm(final long term) {
    try (final var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        final var claimed = claimTerm(connection, term);
        connection.commit();
        return claimed;
      } catch (final SQLException | RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (final SQLException e) {
      throw new LogBlockRepositoryException(
          "Failed to claim partition %d for term %d".formatted(partitionId, term), e);
    }
  }

  @Override
  public void append(final long term, final List<LogBlock> blocks) {
    if (blocks.isEmpty()) {
      return;
    }

    try (final var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        lockTerm(connection, term);
        insertBlocks(connection, blocks);
        connection.commit();
      } catch (final SQLException | RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (final SQLException e) {
      throw new LogBlockRepositoryException(
          "Failed to append %d blocks to partition %d, starting at position %d"
              .formatted(blocks.size(), partitionId, blocks.getFirst().lowPosition()),
          e);
    }
  }

  @Override
  public Optional<LogBlock> findBlockAtOrBefore(final long position) {
    final var blocks = query(selectBlockAtOrBeforeSql, position, 1);
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.getFirst());
  }

  @Override
  public List<LogBlock> readBlocksAfter(final long lowPosition, final int maxBlocks) {
    return query(selectBlocksAfterSql, lowPosition, maxBlocks);
  }

  /**
   * Raises the term if it is lower than ours, or inserts it if the partition was never claimed. A
   * peer may insert the row between our failed update and our insert; we then start over and see
   * its term.
   */
  private boolean claimTerm(final Connection connection, final long term) throws SQLException {
    while (true) {
      if (update(connection, raiseTermSql, term, partitionId, term) == 1) {
        return true;
      }

      final var currentTerm = selectTerm(connection);
      if (currentTerm.isPresent()) {
        return currentTerm.get() == term;
      }

      try {
        update(connection, insertTermSql, partitionId, term);
        return true;
      } catch (final SQLException e) {
        if (!isIntegrityConstraintViolation(e)) {
          throw e;
        }
        connection.rollback();
      }
    }
  }

  private Optional<Long> selectTerm(final Connection connection) throws SQLException {
    try (final var statement = connection.prepareStatement(selectTermSql)) {
      statement.setInt(1, partitionId);
      try (final var result = statement.executeQuery()) {
        return result.next() ? Optional.of(result.getLong(1)) : Optional.empty();
      }
    }
  }

  private void lockTerm(final Connection connection, final long term) throws SQLException {
    if (update(connection, lockTermSql, partitionId, term) != 1) {
      throw new TermMismatchException(partitionId, term);
    }
  }

  private void insertBlocks(final Connection connection, final List<LogBlock> blocks)
      throws SQLException {
    try (final var statement = connection.prepareStatement(insertBlockSql)) {
      for (final var block : blocks) {
        statement.setInt(1, partitionId);
        statement.setLong(2, block.lowPosition());
        statement.setLong(3, block.highPosition());
        statement.setLong(4, block.term());
        statement.setBytes(5, block.data());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private List<LogBlock> query(final String sql, final long position, final int maxBlocks) {
    try (final var connection = dataSource.getConnection();
        final var statement = connection.prepareStatement(sql)) {
      statement.setInt(1, partitionId);
      statement.setLong(2, position);
      // instead of LIMIT, which is not portable across the supported databases
      statement.setMaxRows(maxBlocks);
      try (final var result = statement.executeQuery()) {
        final var blocks = new ArrayList<LogBlock>();
        while (result.next()) {
          blocks.add(toBlock(result));
        }
        return blocks;
      }
    } catch (final SQLException e) {
      throw new LogBlockRepositoryException(
          "Failed to read blocks of partition %d around position %d"
              .formatted(partitionId, position),
          e);
    }
  }

  private static LogBlock toBlock(final ResultSet result) throws SQLException {
    return new LogBlock(
        result.getLong("LOW_POSITION"),
        result.getLong("HIGH_POSITION"),
        result.getLong("TERM"),
        result.getBytes("DATA"));
  }

  private static int update(final Connection connection, final String sql, final Number... params)
      throws SQLException {
    try (final PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        statement.setObject(i + 1, params[i]);
      }
      return statement.executeUpdate();
    }
  }

  private static boolean isIntegrityConstraintViolation(final SQLException e) {
    final var sqlState = e.getSQLState();
    return sqlState != null && sqlState.startsWith(INTEGRITY_CONSTRAINT_VIOLATION);
  }
}

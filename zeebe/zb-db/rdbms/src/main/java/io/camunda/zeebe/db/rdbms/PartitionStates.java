/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The state rows, one per partition, which hold the id of the partition's state and the term of the
 * leader that owns it.
 */
final class PartitionStates {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionStates.class);

  private final RdbmsStorage storage;

  PartitionStates(final RdbmsStorage storage) {
    this.storage = storage;
  }

  /**
   * Finds the partition's state without claiming it, in a short transaction of its own.
   *
   * @return the id of the state, or empty if the partition has none yet
   */
  Optional<String> find(final int partitionId) {
    final var connection = storage.borrowConnection();
    try (final var session =
        storage.sqlSessionFactory().openSession(ExecutorType.SIMPLE, connection)) {
      return session.getMapper(StateMapper.class).findStateId(partitionId);
    } catch (final PersistenceException e) {
      throw SqlErrors.translate(e);
    } finally {
      rollbackAndClose(connection);
    }
  }

  /**
   * Claims the partition's state for the term, in a short transaction of its own. Without an
   * expected state, it creates the state if the partition has none yet.
   *
   * <p>The claim waits for a write in progress of an older term, and from then on that term's
   * writes are rejected.
   *
   * @param expectedStateId the state named by the snapshot the partition starts from, if any; the
   *     database must hold it as the partition's state
   * @return the id of the claimed state
   * @throws StateFencedException if a leader of a newer term owns the state
   * @throws IllegalStateException if the database doesn't hold the expected state
   */
  String attach(final int partitionId, final Optional<String> expectedStateId, final long term) {
    try {
      return attachOnce(partitionId, expectedStateId, term);
    } catch (final PersistenceException e) {
      if (!SqlErrors.isUniqueViolation(e)) {
        throw SqlErrors.translate(e);
      }
    }
    // another replica of the partition created the state at the same time, so it exists now
    try {
      return attachOnce(partitionId, expectedStateId, term);
    } catch (final PersistenceException e) {
      throw SqlErrors.translate(e);
    }
  }

  private String attachOnce(
      final int partitionId, final Optional<String> expectedStateId, final long term) {
    final var connection = storage.borrowConnection();
    try (final var session =
        storage.sqlSessionFactory().openSession(ExecutorType.SIMPLE, connection)) {
      final var state = session.getMapper(StateMapper.class);
      final var stateId = findOrCreate(state, partitionId, expectedStateId, term);
      if (state.claim(partitionId, stateId, term) == 0) {
        throw new StateFencedException(
            "Expected to claim the state %s of partition %d with term %d, but a leader of a newer term owns it"
                .formatted(stateId, partitionId, term));
      }
      connection.commit();
      return stateId;
    } catch (final SQLException e) {
      throw SqlErrors.translate(e);
    } finally {
      rollbackAndClose(connection);
    }
  }

  private static String findOrCreate(
      final StateMapper state,
      final int partitionId,
      final Optional<String> expectedStateId,
      final long term) {
    final var found = state.findStateId(partitionId);
    if (expectedStateId.isPresent()) {
      final var expected = expectedStateId.get();
      if (!found.equals(expectedStateId)) {
        // never start over with an empty state: the log was compacted up to the snapshot
        throw new IllegalStateException(
            "Expected the database to hold the state %s of partition %d, named by the snapshot, but it holds %s"
                .formatted(
                    expected, partitionId, found.map(id -> "the state " + id).orElse("none")));
      }
      return expected;
    }
    if (found.isPresent()) {
      return found.get();
    }
    final var stateId = UUID.randomUUID().toString();
    state.createState(partitionId, stateId, term);
    // rows are kept by partition, so a state deleted by hand may have left some behind
    state.deleteRows(partitionId);
    return stateId;
  }

  /** Rolls back what wasn't committed, which a failed statement may require before reuse. */
  private static void rollbackAndClose(final Connection connection) {
    try (connection) {
      connection.rollback();
    } catch (final SQLException e) {
      LOG.warn("Failed to roll back and return the connection used to find or claim a state", e);
    }
  }
}

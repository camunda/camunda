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
import java.util.OptionalLong;
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

  /**
   * Every failed attempt means that another partition created its state at the same time, so the
   * number of partitions bounds the attempts. Reaching this many means something else keeps failing
   * the insert.
   */
  private static final int MAX_ATTEMPTS = 1_000;

  private final RdbmsStorage storage;

  PartitionStates(final RdbmsStorage storage) {
    this.storage = storage;
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
  long attach(final StateIdentity identity, final OptionalLong expectedStateId, final long term) {
    for (int attempt = 1; ; attempt++) {
      try {
        return attachOnce(identity, expectedStateId, term);
      } catch (final PersistenceException e) {
        if (!SqlErrors.isUniqueViolation(e) || attempt >= MAX_ATTEMPTS) {
          throw SqlErrors.translate(e);
        }
      }
    }
  }

  private long attachOnce(
      final StateIdentity identity, final OptionalLong expectedStateId, final long term) {
    final var connection = storage.borrowConnection();
    try (final var session =
        storage.sqlSessionFactory().openSession(ExecutorType.SIMPLE, connection)) {
      final var state = session.getMapper(StateMapper.class);
      final long stateId = findOrCreate(state, identity, expectedStateId, term);
      if (state.claim(stateId, term) == 0) {
        throw new StateFencedException(
            "Expected to claim the state %d of partition %s with term %d, but a leader of a newer term owns it"
                .formatted(stateId, identity, term));
      }
      connection.commit();
      return stateId;
    } catch (final SQLException e) {
      throw SqlErrors.translate(e);
    } finally {
      rollbackAndClose(connection);
    }
  }

  private static long findOrCreate(
      final StateMapper state,
      final StateIdentity identity,
      final OptionalLong expectedStateId,
      final long term) {
    final var found = state.findStateId(identity.partitionGroup(), identity.partitionId());
    if (expectedStateId.isPresent()) {
      final long expected = expectedStateId.getAsLong();
      if (found.isEmpty() || found.get() != expected) {
        // never start over with an empty state: the log was compacted up to the snapshot
        throw new IllegalStateException(
            "Expected the database to hold the state %d of partition %s, named by the snapshot, but it holds %s"
                .formatted(expected, identity, found.map(id -> "the state " + id).orElse("none")));
      }
      return expected;
    }
    if (found.isPresent()) {
      return found.get();
    }
    final long stateId = state.maxStateId() + 1;
    state.createState(stateId, identity.partitionGroup(), identity.partitionId(), term);
    return stateId;
  }

  /** Rolls back what wasn't committed, which a failed statement may require before reuse. */
  private static void rollbackAndClose(final Connection connection) {
    try (connection) {
      connection.rollback();
    } catch (final SQLException e) {
      LOG.warn("Failed to roll back and return the connection used to attach a state", e);
    }
  }
}
